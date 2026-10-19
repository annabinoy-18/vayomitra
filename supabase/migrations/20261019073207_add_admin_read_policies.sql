/*
# Add admin read access to volunteers and profiles

Admins need to see volunteer names/emails and elder names/emails for the
document verification page. The policies already dropped the recursive
matched-elder policies, so we add clean admin-only SELECT policies here.
*/

DROP POLICY IF EXISTS "volunteers_select_admin" ON volunteers;
CREATE POLICY "volunteers_select_admin" ON volunteers FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));

DROP POLICY IF EXISTS "profiles_select_admin" ON profiles;
CREATE POLICY "profiles_select_admin" ON profiles FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM profiles p2 WHERE p2.id = auth.uid() AND p2.role = 'ADMIN'));

DROP POLICY IF EXISTS "elders_select_admin" ON elders;
CREATE POLICY "elders_select_admin" ON elders FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));
