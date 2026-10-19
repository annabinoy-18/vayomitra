/*
# Create assign_volunteer SECURITY DEFINER function

Elders assign volunteers from their match list, but they cannot read the
volunteers table (RLS). This function runs as owner, looks up the volunteer's
name and phone, inserts the assignment with denormalized fields, and updates
the request status.

Authorization: caller must be the elder who owns the request.
*/

CREATE OR REPLACE FUNCTION assign_volunteer(p_request_id uuid, p_volunteer_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
  v_request assistance_requests%ROWTYPE;
  v_elder elders%ROWTYPE;
  v_vol volunteers%ROWTYPE;
  v_existing volunteer_assignments%ROWTYPE;
BEGIN
  SELECT * INTO v_request FROM assistance_requests WHERE id = p_request_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Request not found'; END IF;

  SELECT * INTO v_elder FROM elders WHERE id = v_request.elder_id;
  IF NOT EXISTS (SELECT 1 FROM profiles WHERE id = auth.uid() AND id = v_elder.user_id) THEN
    RAISE EXCEPTION 'Not authorized';
  END IF;

  IF v_request.status NOT IN ('PENDING', 'MATCHED') THEN
    RAISE EXCEPTION 'This request is no longer open for assignment';
  END IF;

  -- Check no existing active assignment
  SELECT * INTO v_existing FROM volunteer_assignments WHERE request_id = p_request_id;
  IF FOUND THEN RAISE EXCEPTION 'This request already has a volunteer assigned'; END IF;

  SELECT * INTO v_vol FROM volunteers WHERE id = p_volunteer_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Volunteer not found'; END IF;

  INSERT INTO volunteer_assignments (request_id, volunteer_id, status, volunteer_name, volunteer_phone)
  VALUES (p_request_id, p_volunteer_id, 'ASSIGNED', v_vol.full_name, v_vol.phone);

  UPDATE assistance_requests SET status = 'ASSIGNED' WHERE id = p_request_id;

  INSERT INTO request_status_history (request_id, old_status, new_status, note, changed_by)
  VALUES (p_request_id, v_request.status, 'ASSIGNED', 'Volunteer assigned', auth.uid());
END;
$$;

REVOKE EXECUTE ON FUNCTION assign_volunteer(uuid, uuid) FROM anon;
REVOKE EXECUTE ON FUNCTION assign_volunteer(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION assign_volunteer(uuid, uuid) TO authenticated;
