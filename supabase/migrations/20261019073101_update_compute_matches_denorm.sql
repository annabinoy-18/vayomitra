/*
# Update compute_matches to populate denormalized volunteer fields
*/

CREATE OR REPLACE FUNCTION compute_matches(p_request_id uuid)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_request assistance_requests%ROWTYPE;
  v_config matching_config%ROWTYPE;
  v_type assistance_types%ROWTYPE;
  v_elder elders%ROWTYPE;
  v_vol volunteers%ROWTYPE;
  v_profile profiles%ROWTYPE;
  v_verified_certs text[];
  v_pref_codes text[];
  v_avail record;
  v_is_available boolean;
  v_iso_day smallint;
  v_active_count integer;
  v_distance_km double precision;
  v_distance_score numeric(5,2);
  v_skill_score numeric(5,2);
  v_qual_score numeric(5,2);
  v_avail_score numeric(5,2) := 85;
  v_total numeric(5,2);
  v_missing text[];
  v_eligible_count integer := 0;
  v_rank integer := 1;
  v_req_lat double precision;
  v_req_lng double precision;
  v_vol_lat double precision;
  v_vol_lng double precision;
  v_same_area boolean;
  v_vol_email text;
  v_avail_row record;
BEGIN
  SELECT * INTO v_request FROM assistance_requests WHERE id = p_request_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Request not found'; END IF;

  SELECT * INTO v_elder FROM elders WHERE id = v_request.elder_id;
  IF NOT EXISTS (
    SELECT 1 FROM profiles p
    WHERE p.id = auth.uid() AND (p.id = v_elder.user_id OR p.role = 'ADMIN')
  ) THEN RAISE EXCEPTION 'Not authorized'; END IF;

  SELECT * INTO v_config FROM matching_config WHERE id = 1;
  IF NOT FOUND THEN v_config := row(1, 0.40, 0.25, 0.20, 0.15, 15)::matching_config; END IF;

  SELECT * INTO v_type FROM assistance_types WHERE code = v_request.assistance_type;

  DELETE FROM match_recommendations WHERE request_id = p_request_id;

  v_iso_day := EXTRACT(isodow FROM v_request.preferred_date)::smallint;

  v_req_lat := NULL; v_req_lng := NULL;
  IF v_request.latitude IS NOT NULL AND v_request.longitude IS NOT NULL THEN
    v_req_lat := v_request.latitude::double precision;
    v_req_lng := v_request.longitude::double precision;
  END IF;

  FOR v_vol IN SELECT * FROM volunteers LOOP
    SELECT * INTO v_profile FROM profiles WHERE id = v_vol.user_id;
    IF NOT FOUND OR v_profile.account_status != 'ACTIVE' THEN CONTINUE; END IF;

    SELECT array_agg(assistance_type) INTO v_pref_codes
    FROM volunteer_service_preferences WHERE volunteer_id = v_vol.id;
    IF v_pref_codes IS NULL OR NOT (v_request.assistance_type = ANY(v_pref_codes)) THEN CONTINUE; END IF;

    IF v_request.requires_driving THEN
      IF NOT v_vol.offers_driving THEN CONTINUE; END IF;
      IF v_vol.driving_licence_expiry IS NOT NULL AND v_vol.driving_licence_expiry < CURRENT_DATE THEN CONTINUE; END IF;
    END IF;

    SELECT array_agg(certificate_type) INTO v_verified_certs
    FROM volunteer_certificates WHERE volunteer_id = v_vol.id AND verification_status = 'VERIFIED';
    IF v_verified_certs IS NULL OR NOT (v_verified_certs @> ARRAY['TENTH_STANDARD', 'QUALIFICATION']) THEN CONTINUE; END IF;
    IF v_type.requires_healthcare_certificate AND NOT (v_verified_certs @> ARRAY['HEALTHCARE']) THEN CONTINUE; END IF;
    IF v_type.requires_first_aid_certificate AND NOT (v_verified_certs @> ARRAY['FIRST_AID']) THEN CONTINUE; END IF;

    v_is_available := false;
    FOR v_avail_row IN SELECT weekday, start_time, end_time FROM volunteer_availability WHERE volunteer_id = v_vol.id LOOP
      IF v_avail_row.weekday = v_iso_day
         AND v_avail_row.start_time <= v_request.preferred_time_start
         AND v_avail_row.end_time >= v_request.preferred_time_end THEN
        v_is_available := true; EXIT;
      END IF;
    END LOOP;
    IF NOT v_is_available THEN CONTINUE; END IF;

    SELECT count(*) INTO v_active_count
    FROM volunteer_assignments
    WHERE volunteer_id = v_vol.id AND status IN ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS');
    IF v_active_count >= v_vol.max_active_assignments THEN CONTINUE; END IF;

    v_missing := ARRAY[]::text[];
    v_distance_km := NULL;
    v_distance_score := 0;
    v_vol_lat := NULL; v_vol_lng := NULL;
    IF v_vol.latitude IS NOT NULL AND v_vol.longitude IS NOT NULL THEN
      v_vol_lat := v_vol.latitude::double precision;
      v_vol_lng := v_vol.longitude::double precision;
    END IF;

    IF v_req_lat IS NOT NULL AND v_vol_lat IS NOT NULL THEN
      v_distance_km := 6371.0 * 2.0 * atan2(
        sqrt(power(sin(radians((v_vol_lat - v_req_lat) / 2.0)), 2) +
             cos(radians(v_req_lat)) * cos(radians(v_vol_lat)) *
             power(sin(radians((v_vol_lng - v_req_lng) / 2.0)), 2)),
        sqrt(1.0 - (power(sin(radians((v_vol_lat - v_req_lat) / 2.0)), 2) +
             cos(radians(v_req_lat)) * cos(radians(v_vol_lat)) *
             power(sin(radians((v_vol_lng - v_req_lng) / 2.0)), 2))));
      IF v_distance_km > v_vol.preferred_service_radius_km::double precision THEN CONTINUE; END IF;
      v_distance_score := GREATEST(0::numeric, 100.0 * (1.0 - v_distance_km / v_vol.preferred_service_radius_km::double precision));
    ELSE
      v_same_area := false;
      IF v_request.pin_code IS NOT NULL AND v_vol.pin_code = v_request.pin_code THEN v_same_area := true;
      ELSIF v_request.city IS NOT NULL AND v_vol.city IS NOT NULL AND lower(v_request.city) = lower(v_vol.city) THEN v_same_area := true;
      END IF;
      IF NOT v_same_area THEN CONTINUE; END IF;
      v_missing := array_append(v_missing, 'LOCATION');
      v_distance_score := 35::numeric;
    END IF;

    IF v_vol.experience_years <= 0 THEN v_missing := array_append(v_missing, 'EXPERIENCE'); END IF;

    v_skill_score := 70::numeric;
    IF v_request.requires_driving AND v_vol.offers_driving THEN v_skill_score := v_skill_score + 6; END IF;
    v_skill_score := LEAST(100::numeric, v_skill_score);

    v_qual_score := 50::numeric;
    IF lower(v_vol.educational_qualification) LIKE '%post%' OR lower(v_vol.educational_qualification) LIKE '%master%' OR lower(v_vol.educational_qualification) LIKE '%nurs%' THEN v_qual_score := 90;
    ELSIF lower(v_vol.educational_qualification) LIKE '%bachelor%' OR lower(v_vol.educational_qualification) LIKE '%graduate%' OR lower(v_vol.educational_qualification) LIKE '%diploma%' THEN v_qual_score := 75;
    ELSIF lower(v_vol.educational_qualification) LIKE '%12%' OR lower(v_vol.educational_qualification) LIKE '%higher%' THEN v_qual_score := 60;
    END IF;
    BEGIN
      v_qual_score := LEAST(100::numeric, v_qual_score * 0.45 + LEAST(100::numeric, (v_vol.experience_years * 8)::numeric) * 0.4);
    END;

    v_total := round((v_skill_score * v_config.skill_weight + v_distance_score * v_config.distance_weight + v_avail_score * v_config.availability_weight + v_qual_score * v_config.qualification_weight)::numeric, 2);

    SELECT email INTO v_vol_email FROM profiles WHERE id = v_vol.user_id;

    INSERT INTO match_recommendations (
      request_id, volunteer_id, total_score,
      skill_score, distance_score, availability_score, qualification_score,
      distance_km, explanation, missing_information, rank_position,
      volunteer_name, volunteer_phone, volunteer_email, volunteer_city,
      volunteer_experience_years, volunteer_educational_qualification, volunteer_skills
    ) VALUES (
      p_request_id, v_vol.id, v_total,
      round(v_skill_score, 2), round(v_distance_score, 2), v_avail_score, round(v_qual_score, 2),
      CASE WHEN v_distance_km IS NOT NULL THEN round(v_distance_km::numeric, 2) ELSE NULL END,
      'Skill ' || round(v_skill_score,2) || '/100, distance ' || round(v_distance_score,2) || '/100, availability 85/100, qualification ' || round(v_qual_score,2) || '/100. Weighted total ' || v_total || '.',
      CASE WHEN array_length(v_missing, 1) IS NOT NULL THEN array_to_string(v_missing, ',') ELSE NULL END,
      v_rank,
      v_vol.full_name, v_vol.phone, v_vol_email, v_vol.city,
      v_vol.experience_years, v_vol.educational_qualification, v_vol.skills
    );

    v_rank := v_rank + 1;
    v_eligible_count := v_eligible_count + 1;
    IF v_rank > 10 THEN EXIT; END IF;
  END LOOP;

  IF v_eligible_count > 0 AND v_request.status = 'PENDING' THEN
    UPDATE assistance_requests SET status = 'MATCHED' WHERE id = p_request_id AND status = 'PENDING';
    INSERT INTO request_status_history (request_id, old_status, new_status, note, changed_by)
    VALUES (p_request_id, 'PENDING', 'MATCHED', 'Eligible volunteer matches generated', auth.uid());
  END IF;

  RETURN v_eligible_count;
END;
$$;

REVOKE EXECUTE ON FUNCTION compute_matches(uuid) FROM anon;
REVOKE EXECUTE ON FUNCTION compute_matches(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION compute_matches(uuid) TO authenticated;
