/*
# Fix infinite RLS recursion on assistance_requests

Loop: assistance_requests.requests_select_assigned_volunteer
  -> volunteer_assignments.assignments_select_involved (elder branch
     joins assistance_requests)
  -> back to assistance_requests policies -> loop.

Fix: SECURITY DEFINER function is_request_elder() checks elder ownership
 with owner privileges (bypassing RLS), so volunteer_assignments no longer
 needs to join assistance_requests in its policy.
*/

CREATE OR REPLACE FUNCTION is_request_elder(p_request_id uuid)
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER SET search_path = public
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1 FROM elders e
    JOIN assistance_requests r ON r.elder_id = e.id
    WHERE r.id = p_request_id AND e.user_id = auth.uid()
  );
$$;

REVOKE EXECUTE ON FUNCTION is_request_elder(uuid) FROM anon;
REVOKE EXECUTE ON FUNCTION is_request_elder(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION is_request_elder(uuid) TO authenticated;

-- Replace the elder-branch in volunteer_assignments to avoid the join
DROP POLICY IF EXISTS "assignments_select_involved" ON volunteer_assignments;
CREATE POLICY "assignments_select_involved" ON volunteer_assignments FOR SELECT TO authenticated
  USING (
    is_admin()
    OR EXISTS (SELECT 1 FROM volunteers v
               WHERE v.id = volunteer_assignments.volunteer_id AND v.user_id = auth.uid())
    OR is_request_elder(volunteer_assignments.request_id)
  );

-- Also simplify request_status_history's elder branch the same way
DROP POLICY IF EXISTS "rsh_select_involved" ON request_status_history;
CREATE POLICY "rsh_select_involved" ON request_status_history FOR SELECT TO authenticated
  USING (
    is_admin()
    OR is_request_elder(request_status_history.request_id)
    OR EXISTS (SELECT 1 FROM volunteers v, volunteer_assignments va
               WHERE va.request_id = request_status_history.request_id
                 AND va.volunteer_id = v.id AND v.user_id = auth.uid())
  );
