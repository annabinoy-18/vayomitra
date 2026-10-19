/*
# Vayomitra schema — Part 2: Requests, assignments, matches, history

Creates:
- assistance_requests: help requests created by elders
- volunteer_assignments: links a volunteer to a request with lifecycle status
- match_recommendations: ranked volunteer suggestions for a request
- request_status_history: audit trail of status changes

All tables have RLS enabled with ownership-based policies.
*/

-- ============ ASSISTANCE REQUESTS ============
CREATE TABLE IF NOT EXISTS assistance_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  public_id varchar(20) NOT NULL UNIQUE,
  elder_id uuid NOT NULL REFERENCES elders(id) ON DELETE RESTRICT,
  assistance_type varchar(40) NOT NULL REFERENCES assistance_types(code) ON DELETE RESTRICT,
  title varchar(160) NOT NULL,
  description text NOT NULL,
  preferred_date date NOT NULL,
  preferred_time_start time NOT NULL,
  preferred_time_end time NOT NULL,
  service_address text NOT NULL,
  locality varchar(120),
  city varchar(80),
  state varchar(80),
  pin_code varchar(10),
  latitude numeric(9,6),
  longitude numeric(9,6),
  priority varchar(20) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
  requires_driving boolean NOT NULL DEFAULT false,
  additional_details text,
  status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','MATCHED','ASSIGNED','ACCEPTED','IN_PROGRESS','COMPLETED','CANCELLED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ar_time_chk CHECK (preferred_time_end > preferred_time_start)
);
ALTER TABLE assistance_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "requests_select_elder" ON assistance_requests;
CREATE POLICY "requests_select_elder" ON assistance_requests FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM elders e WHERE e.id = elder_id AND e.user_id = auth.uid()));
DROP POLICY IF EXISTS "requests_select_volunteer_open" ON assistance_requests;
CREATE POLICY "requests_select_volunteer_open" ON assistance_requests FOR SELECT TO authenticated
  USING (status IN ('PENDING','MATCHED') AND EXISTS (SELECT 1 FROM volunteers v WHERE v.user_id = auth.uid()));
DROP POLICY IF EXISTS "requests_select_admin" ON assistance_requests;
CREATE POLICY "requests_select_admin" ON assistance_requests FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));
DROP POLICY IF EXISTS "requests_insert_elder" ON assistance_requests;
CREATE POLICY "requests_insert_elder" ON assistance_requests FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM elders e WHERE e.id = elder_id AND e.user_id = auth.uid()));
DROP POLICY IF EXISTS "requests_update_elder" ON assistance_requests;
CREATE POLICY "requests_update_elder" ON assistance_requests FOR UPDATE TO authenticated
  USING (EXISTS (SELECT 1 FROM elders e WHERE e.id = elder_id AND e.user_id = auth.uid()))
  WITH CHECK (EXISTS (SELECT 1 FROM elders e WHERE e.id = elder_id AND e.user_id = auth.uid()));

-- ============ VOLUNTEER ASSIGNMENTS ============
CREATE TABLE IF NOT EXISTS volunteer_assignments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id uuid NOT NULL UNIQUE REFERENCES assistance_requests(id) ON DELETE CASCADE,
  volunteer_id uuid NOT NULL REFERENCES volunteers(id) ON DELETE RESTRICT,
  status varchar(20) NOT NULL CHECK (status IN ('ASSIGNED','ACCEPTED','IN_PROGRESS','COMPLETED','DECLINED')),
  assigned_at timestamptz NOT NULL DEFAULT now(),
  accepted_at timestamptz,
  started_at timestamptz,
  completed_at timestamptz
);
ALTER TABLE volunteer_assignments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "assignments_select_involved" ON volunteer_assignments;
CREATE POLICY "assignments_select_involved" ON volunteer_assignments FOR SELECT TO authenticated
  USING (
    EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid())
    OR EXISTS (SELECT 1 FROM elders e, assistance_requests r WHERE r.id = request_id AND e.id = r.elder_id AND e.user_id = auth.uid())
    OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN')
  );
DROP POLICY IF EXISTS "assignments_insert_volunteer" ON volunteer_assignments;
CREATE POLICY "assignments_insert_volunteer" ON volunteer_assignments FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));
DROP POLICY IF EXISTS "assignments_update_volunteer" ON volunteer_assignments;
CREATE POLICY "assignments_update_volunteer" ON volunteer_assignments FOR UPDATE TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()))
  WITH CHECK (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));

-- Now add the policy for volunteers to see requests they're assigned to
DROP POLICY IF EXISTS "requests_select_assigned_volunteer" ON assistance_requests;
CREATE POLICY "requests_select_assigned_volunteer" ON assistance_requests FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteer_assignments va WHERE va.request_id = id AND va.volunteer_id = (SELECT v.id FROM volunteers v WHERE v.user_id = auth.uid())));

-- ============ MATCH RECOMMENDATIONS ============
CREATE TABLE IF NOT EXISTS match_recommendations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id uuid NOT NULL REFERENCES assistance_requests(id) ON DELETE CASCADE,
  volunteer_id uuid NOT NULL REFERENCES volunteers(id) ON DELETE CASCADE,
  total_score numeric(5,2) NOT NULL CHECK (total_score BETWEEN 0 AND 100),
  skill_score numeric(5,2) NOT NULL,
  distance_score numeric(5,2) NOT NULL,
  availability_score numeric(5,2) NOT NULL,
  qualification_score numeric(5,2) NOT NULL,
  distance_km numeric(8,3),
  explanation text NOT NULL,
  missing_information text,
  rank_position integer NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (request_id, volunteer_id)
);
ALTER TABLE match_recommendations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "matches_select_elder" ON match_recommendations;
CREATE POLICY "matches_select_elder" ON match_recommendations FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM elders e, assistance_requests r WHERE r.id = request_id AND e.id = r.elder_id AND e.user_id = auth.uid()));
DROP POLICY IF EXISTS "matches_select_volunteer" ON match_recommendations;
CREATE POLICY "matches_select_volunteer" ON match_recommendations FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));
DROP POLICY IF EXISTS "matches_select_admin" ON match_recommendations;
CREATE POLICY "matches_select_admin" ON match_recommendations FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));
DROP POLICY IF EXISTS "matches_delete_elder" ON match_recommendations;
CREATE POLICY "matches_delete_elder" ON match_recommendations FOR DELETE TO authenticated
  USING (EXISTS (SELECT 1 FROM elders e, assistance_requests r WHERE r.id = request_id AND e.id = r.elder_id AND e.user_id = auth.uid()));
DROP POLICY IF EXISTS "matches_insert_elder" ON match_recommendations;
CREATE POLICY "matches_insert_elder" ON match_recommendations FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM elders e, assistance_requests r WHERE r.id = request_id AND e.id = r.elder_id AND e.user_id = auth.uid()));

-- ============ REQUEST STATUS HISTORY ============
CREATE TABLE IF NOT EXISTS request_status_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id uuid NOT NULL REFERENCES assistance_requests(id) ON DELETE CASCADE,
  old_status varchar(20),
  new_status varchar(20) NOT NULL,
  changed_by uuid,
  note text,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE request_status_history ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "rsh_select_involved" ON request_status_history;
CREATE POLICY "rsh_select_involved" ON request_status_history FOR SELECT TO authenticated
  USING (
    EXISTS (SELECT 1 FROM elders e, assistance_requests r WHERE r.id = request_id AND e.id = r.elder_id AND e.user_id = auth.uid())
    OR EXISTS (SELECT 1 FROM volunteers v, volunteer_assignments va WHERE va.request_id = request_id AND v.id = va.volunteer_id AND v.user_id = auth.uid())
    OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN')
  );
DROP POLICY IF EXISTS "rsh_insert_involved" ON request_status_history;
CREATE POLICY "rsh_insert_involved" ON request_status_history FOR INSERT TO authenticated
  WITH CHECK (
    EXISTS (SELECT 1 FROM elders e, assistance_requests r WHERE r.id = request_id AND e.id = r.elder_id AND e.user_id = auth.uid())
    OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN')
  );

-- ============ INDEXES ============
CREATE INDEX IF NOT EXISTS idx_requests_elder ON assistance_requests (elder_id);
CREATE INDEX IF NOT EXISTS idx_requests_status ON assistance_requests (status);
CREATE INDEX IF NOT EXISTS idx_assignments_volunteer ON volunteer_assignments (volunteer_id);
CREATE INDEX IF NOT EXISTS idx_matches_request ON match_recommendations (request_id, total_score DESC);
