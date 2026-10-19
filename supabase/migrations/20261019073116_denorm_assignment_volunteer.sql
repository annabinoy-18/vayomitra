/*
# Denormalize volunteer name/phone into volunteer_assignments

Elders cannot read the volunteers table (RLS), so we store the volunteer's
name and phone directly on the assignment row when it's created.
The compute_matches function already populates volunteer_name etc. on
match_recommendations, but we also need it on assignments for after
a volunteer is assigned.
*/

ALTER TABLE volunteer_assignments
  ADD COLUMN IF NOT EXISTS volunteer_name varchar(120),
  ADD COLUMN IF NOT EXISTS volunteer_phone varchar(15);
