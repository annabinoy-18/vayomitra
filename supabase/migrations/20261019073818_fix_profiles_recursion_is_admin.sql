/*
# Fix infinite RLS recursion on profiles

The admin SELECT policies reference profiles to verify the caller's role,
which re-triggers profiles' own admin policy → infinite recursion.

Fix: a SECURITY DEFINER function is_admin() reads profiles with owner
 privileges (bypassing RLS), and every admin policy calls it instead of
 sub-querying profiles.
*/

CREATE OR REPLACE FUNCTION is_admin()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER SET search_path = public
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1 FROM profiles
    WHERE id = auth.uid() AND role = 'ADMIN'
  );
$$;

REVOKE EXECUTE ON FUNCTION is_admin() FROM anon;
REVOKE EXECUTE ON FUNCTION is_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION is_admin() TO authenticated;

-- profiles
DROP POLICY IF EXISTS "profiles_select_admin" ON profiles;
CREATE POLICY "profiles_select_admin" ON profiles FOR SELECT TO authenticated
  USING (is_admin());

-- volunteers
DROP POLICY IF EXISTS "volunteers_select_admin" ON volunteers;
CREATE POLICY "volunteers_select_admin" ON volunteers FOR SELECT TO authenticated
  USING (is_admin());

-- elders
DROP POLICY IF EXISTS "elders_select_admin" ON elders;
CREATE POLICY "elders_select_admin" ON elders FOR SELECT TO authenticated
  USING (is_admin());

-- assistance_requests
DROP POLICY IF EXISTS "requests_select_admin" ON assistance_requests;
CREATE POLICY "requests_select_admin" ON assistance_requests FOR SELECT TO authenticated
  USING (is_admin());

-- match_recommendations
DROP POLICY IF EXISTS "matches_select_admin" ON match_recommendations;
CREATE POLICY "matches_select_admin" ON match_recommendations FOR SELECT TO authenticated
  USING (is_admin());

-- request_status_history: admin arm
DROP POLICY IF EXISTS "rsh_select_involved" ON request_status_history;
CREATE POLICY "rsh_select_involved" ON request_status_history FOR SELECT TO authenticated
  USING (
    is_admin()
    OR EXISTS (SELECT 1 FROM elders e, assistance_requests r
               WHERE r.id = request_status_history.request_id
                 AND e.id = r.elder_id AND e.user_id = auth.uid())
    OR EXISTS (SELECT 1 FROM volunteers v, volunteer_assignments va
               WHERE va.request_id = request_status_history.request_id
                 AND va.volunteer_id = v.id AND v.user_id = auth.uid())
  );

-- elder_documents admin arm
DROP POLICY IF EXISTS "ed_select_own" ON elder_documents;
CREATE POLICY "ed_select_own" ON elder_documents FOR SELECT TO authenticated
  USING (
    is_admin()
    OR EXISTS (SELECT 1 FROM elders e
               WHERE e.id = elder_documents.elder_id AND e.user_id = auth.uid())
  );

DROP POLICY IF EXISTS "ed_update_admin" ON elder_documents;
CREATE POLICY "ed_update_admin" ON elder_documents FOR UPDATE TO authenticated
  USING (is_admin()) WITH CHECK (is_admin());

-- volunteer_certificates admin arm
DROP POLICY IF EXISTS "vc_select_own" ON volunteer_certificates;
CREATE POLICY "vc_select_own" ON volunteer_certificates FOR SELECT TO authenticated
  USING (
    is_admin()
    OR EXISTS (SELECT 1 FROM volunteers v
               WHERE v.id = volunteer_certificates.volunteer_id AND v.user_id = auth.uid())
  );

DROP POLICY IF EXISTS "vc_update_admin" ON volunteer_certificates;
CREATE POLICY "vc_update_admin" ON volunteer_certificates FOR UPDATE TO authenticated
  USING (is_admin()) WITH CHECK (is_admin());

-- volunteer_documents admin arm
DROP POLICY IF EXISTS "vd_select_own" ON volunteer_documents;
CREATE POLICY "vd_select_own" ON volunteer_documents FOR SELECT TO authenticated
  USING (
    is_admin()
    OR EXISTS (SELECT 1 FROM volunteers v
               WHERE v.id = volunteer_documents.volunteer_id AND v.user_id = auth.uid())
  );

DROP POLICY IF EXISTS "vd_update_admin" ON volunteer_documents;
CREATE POLICY "vd_update_admin" ON volunteer_documents FOR UPDATE TO authenticated
  USING (is_admin()) WITH CHECK (is_admin());

-- volunteer_assignments admin arm
DROP POLICY IF EXISTS "assignments_select_involved" ON volunteer_assignments;
CREATE POLICY "assignments_select_involved" ON volunteer_assignments FOR SELECT TO authenticated
  USING (
    is_admin()
    OR EXISTS (SELECT 1 FROM volunteers v
               WHERE v.id = volunteer_assignments.volunteer_id AND v.user_id = auth.uid())
    OR EXISTS (SELECT 1 FROM elders e, assistance_requests r
               WHERE r.id = volunteer_assignments.request_id
                 AND e.id = r.elder_id AND e.user_id = auth.uid())
  );
