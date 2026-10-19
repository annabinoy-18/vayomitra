/*
# Add volunteer read policy for matched elders

Allows an elder to read volunteer profile details (name, phone, city, etc.)
for volunteers that appear in their request's match_recommendations.
This is needed so the elder can see the ranked volunteer list with contact info.
*/

DROP POLICY IF EXISTS "volunteers_select_matched_elder" ON volunteers;
CREATE POLICY "volunteers_select_matched_elder" ON volunteers FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM match_recommendations m
      JOIN assistance_requests r ON r.id = m.request_id
      JOIN elders e ON e.id = r.elder_id
      WHERE m.volunteer_id = volunteers.id
      AND e.user_id = auth.uid()
    )
    OR EXISTS (
      SELECT 1 FROM volunteer_assignments va
      JOIN assistance_requests r ON r.id = va.request_id
      JOIN elders e ON e.id = r.elder_id
      WHERE va.volunteer_id = volunteers.id
      AND e.user_id = auth.uid()
    )
    OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN')
  );

-- Also allow elders to read the volunteer's profile email for matched volunteers
DROP POLICY IF EXISTS "profiles_select_matched_volunteer" ON profiles;
CREATE POLICY "profiles_select_matched_volunteer" ON profiles FOR SELECT TO authenticated
  USING (
    auth.uid() = id
    OR EXISTS (
      SELECT 1 FROM match_recommendations m
      JOIN assistance_requests r ON r.id = m.request_id
      JOIN elders e ON e.id = r.elder_id
      JOIN volunteers v ON v.id = m.volunteer_id
      WHERE v.user_id = profiles.id
      AND e.user_id = auth.uid()
    )
    OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN')
  );
