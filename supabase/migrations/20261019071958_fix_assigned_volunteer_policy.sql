/*
# Fix assigned-volunteer request policy

The policy "requests_select_assigned_volunteer" had a self-referential bug:
it checked va.request_id = va.id (always false) instead of
va.request_id = assistance_requests.id. This prevented volunteers from
seeing requests they were assigned to.
*/

DROP POLICY IF EXISTS "requests_select_assigned_volunteer" ON assistance_requests;
CREATE POLICY "requests_select_assigned_volunteer" ON assistance_requests FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteer_assignments va WHERE va.request_id = assistance_requests.id AND va.volunteer_id = (SELECT v.id FROM volunteers v WHERE v.user_id = auth.uid())));
