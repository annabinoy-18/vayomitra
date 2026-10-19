/*
# Fix infinite RLS recursion on match_recommendations

The policies volunteers_select_matched_elder → match_recommendations →
matches_select_volunteer → volunteers create an infinite recursion.

Fix: denormalize volunteer contact details into match_recommendations
so elders never need to read the volunteers table. Remove the recursive
policies on volunteers and profiles.
*/

-- Add denormalized volunteer columns to match_recommendations
ALTER TABLE match_recommendations
  ADD COLUMN IF NOT EXISTS volunteer_name varchar(120),
  ADD COLUMN IF NOT EXISTS volunteer_phone varchar(15),
  ADD COLUMN IF NOT EXISTS volunteer_email text,
  ADD COLUMN IF NOT EXISTS volunteer_city varchar(80),
  ADD COLUMN IF NOT EXISTS volunteer_experience_years integer,
  ADD COLUMN IF NOT EXISTS volunteer_educational_qualification varchar(160),
  ADD COLUMN IF NOT EXISTS volunteer_skills text;

-- Remove the recursive policies
DROP POLICY IF EXISTS "volunteers_select_matched_elder" ON volunteers;
DROP POLICY IF EXISTS "profiles_select_matched_volunteer" ON profiles;
