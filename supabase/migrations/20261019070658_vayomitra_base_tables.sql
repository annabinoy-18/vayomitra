/*
# Vayomitra schema — Part 1: Base tables

Creates profiles, elders, volunteers, assistance_types, matching_config,
volunteer_service_preferences, volunteer_availability, and elder/volunteer documents.
All with RLS enabled and owner-scoped policies.
*/

-- ============ PROFILES ============
CREATE TABLE IF NOT EXISTS profiles (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  email text NOT NULL,
  role text NOT NULL CHECK (role IN ('ELDER','VOLUNTEER','ADMIN')),
  account_status text NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE','INACTIVE','SUSPENDED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "profiles_select_own" ON profiles;
CREATE POLICY "profiles_select_own" ON profiles FOR SELECT TO authenticated USING (auth.uid() = id);
DROP POLICY IF EXISTS "profiles_insert_own" ON profiles;
CREATE POLICY "profiles_insert_own" ON profiles FOR INSERT TO authenticated WITH CHECK (auth.uid() = id);
DROP POLICY IF EXISTS "profiles_update_own" ON profiles;
CREATE POLICY "profiles_update_own" ON profiles FOR UPDATE TO authenticated USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

-- ============ ELDERS ============
CREATE TABLE IF NOT EXISTS elders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES profiles(id) ON DELETE CASCADE,
  full_name varchar(120) NOT NULL,
  date_of_birth date NOT NULL,
  gender varchar(32) NOT NULL CHECK (gender IN ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY')),
  phone varchar(15) NOT NULL,
  address text NOT NULL,
  locality varchar(120) NOT NULL,
  city varchar(80) NOT NULL,
  state varchar(80) NOT NULL,
  country varchar(80) NOT NULL DEFAULT 'India',
  pin_code varchar(10) NOT NULL,
  latitude numeric(9,6),
  longitude numeric(9,6),
  emergency_contact_name varchar(120) NOT NULL,
  emergency_contact_phone varchar(15) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE elders ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "elders_select_own" ON elders;
CREATE POLICY "elders_select_own" ON elders FOR SELECT TO authenticated USING (auth.uid() = user_id);
DROP POLICY IF EXISTS "elders_insert_own" ON elders;
CREATE POLICY "elders_insert_own" ON elders FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
DROP POLICY IF EXISTS "elders_update_own" ON elders;
CREATE POLICY "elders_update_own" ON elders FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ============ VOLUNTEERS ============
CREATE TABLE IF NOT EXISTS volunteers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES profiles(id) ON DELETE CASCADE,
  full_name varchar(120) NOT NULL,
  date_of_birth date NOT NULL,
  gender varchar(32) NOT NULL CHECK (gender IN ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY')),
  phone varchar(15) NOT NULL,
  address text NOT NULL,
  locality varchar(120) NOT NULL,
  city varchar(80) NOT NULL,
  state varchar(80) NOT NULL,
  country varchar(80) NOT NULL DEFAULT 'India',
  pin_code varchar(10) NOT NULL,
  latitude numeric(9,6),
  longitude numeric(9,6),
  educational_qualification varchar(160) NOT NULL,
  skills text NOT NULL,
  experience_years integer NOT NULL DEFAULT 0 CHECK (experience_years >= 0 AND experience_years <= 60),
  preferred_service_radius_km numeric(6,2) NOT NULL DEFAULT 15 CHECK (preferred_service_radius_km > 0 AND preferred_service_radius_km <= 200),
  offers_driving boolean NOT NULL DEFAULT false,
  driving_licence_number varchar(40),
  driving_licence_expiry date,
  max_active_assignments integer NOT NULL DEFAULT 3 CHECK (max_active_assignments BETWEEN 1 AND 20),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE volunteers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "volunteers_select_own" ON volunteers;
CREATE POLICY "volunteers_select_own" ON volunteers FOR SELECT TO authenticated USING (auth.uid() = user_id);
DROP POLICY IF EXISTS "volunteers_insert_own" ON volunteers;
CREATE POLICY "volunteers_insert_own" ON volunteers FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
DROP POLICY IF EXISTS "volunteers_update_own" ON volunteers;
CREATE POLICY "volunteers_update_own" ON volunteers FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ============ ASSISTANCE TYPES ============
CREATE TABLE IF NOT EXISTS assistance_types (
  code varchar(40) PRIMARY KEY,
  display_name varchar(120) NOT NULL,
  description text NOT NULL,
  requires_driving_licence boolean NOT NULL DEFAULT false,
  requires_healthcare_certificate boolean NOT NULL DEFAULT false,
  requires_first_aid_certificate boolean NOT NULL DEFAULT false,
  public_disclaimer text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE assistance_types ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "types_select_all" ON assistance_types;
CREATE POLICY "types_select_all" ON assistance_types FOR SELECT TO authenticated USING (true);

INSERT INTO assistance_types (code, display_name, description, requires_driving_licence, requires_healthcare_certificate, requires_first_aid_certificate, public_disclaimer)
VALUES
  ('TRANSPORTATION','Transportation Assistance','Help elders travel to hospitals, appointments, and other destinations.',true,false,false,'Driving assignments require a verified driving licence.'),
  ('GROCERY_SUPPORT','Grocery / Essential / Medicine Shopping','Help purchase groceries, daily necessities, and medicines.',false,false,false,'Volunteers may shop on your behalf.'),
  ('TECHNOLOGY_SUPPORT','Technology Support','Help with phones, apps, video calls, and digital services.',false,false,false,'Volunteers help with everyday digital tasks.'),
  ('LONELINESS_SUPPORT','Companionship / Loneliness Support','Provide companionship, conversation, and social interaction.',false,false,false,'This is friendly companionship, not professional counselling.'),
  ('BOOKING_SUPPORT','Booking Support','Help book appointments, transport, tickets, and other services.',false,false,false,'Volunteers help with bookings.'),
  ('HOME_NURSE_ASSISTANCE','Home Nurse Assistance','Help arrange appropriate nursing and home healthcare support.',false,true,false,'Volunteers help coordinate care.'),
  ('PET_ASSISTANCE','Pet Assistance','Help with pet feeding, walking, and basic pet-care tasks.',false,false,false,'This covers basic pet care, not veterinary treatment.'),
  ('MINOR_HOME_MAINTENANCE','Minor Home Maintenance','Help with small household tasks and minor repairs.',false,false,false,'This is help with small tasks, not licensed work.'),
  ('EMERGENCY_ASSISTANCE','Emergency Assistance','Help elders access appropriate urgent support and emergency services.',false,false,true,'Call 112 for life-threatening emergencies.')
ON CONFLICT (code) DO NOTHING;

-- ============ MATCHING CONFIG ============
CREATE TABLE IF NOT EXISTS matching_config (
  id smallint PRIMARY KEY,
  skill_weight numeric(4,3) NOT NULL,
  distance_weight numeric(4,3) NOT NULL,
  availability_weight numeric(4,3) NOT NULL,
  qualification_weight numeric(4,3) NOT NULL,
  default_radius_km numeric(6,2) NOT NULL DEFAULT 15
);
ALTER TABLE matching_config ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "config_select_all" ON matching_config;
CREATE POLICY "config_select_all" ON matching_config FOR SELECT TO authenticated USING (true);

INSERT INTO matching_config (id, skill_weight, distance_weight, availability_weight, qualification_weight, default_radius_km)
VALUES (1, 0.40, 0.25, 0.20, 0.15, 15)
ON CONFLICT (id) DO NOTHING;

-- ============ VOLUNTEER SERVICE PREFERENCES ============
CREATE TABLE IF NOT EXISTS volunteer_service_preferences (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  volunteer_id uuid NOT NULL REFERENCES volunteers(id) ON DELETE CASCADE,
  assistance_type varchar(40) NOT NULL REFERENCES assistance_types(code) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (volunteer_id, assistance_type)
);
ALTER TABLE volunteer_service_preferences ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "vsp_select_own" ON volunteer_service_preferences;
CREATE POLICY "vsp_select_own" ON volunteer_service_preferences FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));
DROP POLICY IF EXISTS "vsp_insert_own" ON volunteer_service_preferences;
CREATE POLICY "vsp_insert_own" ON volunteer_service_preferences FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));
DROP POLICY IF EXISTS "vsp_delete_own" ON volunteer_service_preferences;
CREATE POLICY "vsp_delete_own" ON volunteer_service_preferences FOR DELETE TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));

-- ============ VOLUNTEER AVAILABILITY ============
CREATE TABLE IF NOT EXISTS volunteer_availability (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  volunteer_id uuid NOT NULL REFERENCES volunteers(id) ON DELETE CASCADE,
  weekday smallint NOT NULL CHECK (weekday BETWEEN 1 AND 7),
  start_time time NOT NULL,
  end_time time NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT va_time_chk CHECK (end_time > start_time)
);
ALTER TABLE volunteer_availability ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "va_select_own" ON volunteer_availability;
CREATE POLICY "va_select_own" ON volunteer_availability FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));
DROP POLICY IF EXISTS "va_insert_own" ON volunteer_availability;
CREATE POLICY "va_insert_own" ON volunteer_availability FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));
DROP POLICY IF EXISTS "va_delete_own" ON volunteer_availability;
CREATE POLICY "va_delete_own" ON volunteer_availability FOR DELETE TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));

-- ============ ELDER DOCUMENTS ============
CREATE TABLE IF NOT EXISTS elder_documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  elder_id uuid NOT NULL REFERENCES elders(id) ON DELETE CASCADE,
  document_type varchar(40) NOT NULL CHECK (document_type IN ('IDENTITY_PROOF','OTHER')),
  original_filename varchar(255) NOT NULL,
  storage_path text NOT NULL,
  content_type varchar(100) NOT NULL,
  file_size_bytes integer NOT NULL,
  verification_status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED')),
  reviewer_notes text,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE elder_documents ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "ed_select_own" ON elder_documents;
CREATE POLICY "ed_select_own" ON elder_documents FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM elders e WHERE e.id = elder_id AND e.user_id = auth.uid())
     OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));
DROP POLICY IF EXISTS "ed_insert_own" ON elder_documents;
CREATE POLICY "ed_insert_own" ON elder_documents FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM elders e WHERE e.id = elder_id AND e.user_id = auth.uid()));
DROP POLICY IF EXISTS "ed_update_admin" ON elder_documents;
CREATE POLICY "ed_update_admin" ON elder_documents FOR UPDATE TO authenticated
  USING (EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));

-- ============ VOLUNTEER DOCUMENTS ============
CREATE TABLE IF NOT EXISTS volunteer_documents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  volunteer_id uuid NOT NULL REFERENCES volunteers(id) ON DELETE CASCADE,
  document_type varchar(40) NOT NULL CHECK (document_type IN ('TENTH_CERTIFICATE','QUALIFICATION_CERTIFICATE','DRIVING_LICENCE','HEALTHCARE_CERTIFICATE','FIRST_AID_CERTIFICATE','OTHER')),
  original_filename varchar(255) NOT NULL,
  storage_path text NOT NULL,
  content_type varchar(100) NOT NULL,
  file_size_bytes integer NOT NULL,
  verification_status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED')),
  reviewer_notes text,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE volunteer_documents ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "vd_select_own" ON volunteer_documents;
CREATE POLICY "vd_select_own" ON volunteer_documents FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid())
     OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));
DROP POLICY IF EXISTS "vd_insert_own" ON volunteer_documents;
CREATE POLICY "vd_insert_own" ON volunteer_documents FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));
DROP POLICY IF EXISTS "vd_update_admin" ON volunteer_documents;
CREATE POLICY "vd_update_admin" ON volunteer_documents FOR UPDATE TO authenticated
  USING (EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));

-- ============ VOLUNTEER CERTIFICATES ============
CREATE TABLE IF NOT EXISTS volunteer_certificates (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  volunteer_id uuid NOT NULL REFERENCES volunteers(id) ON DELETE CASCADE,
  document_id uuid REFERENCES volunteer_documents(id) ON DELETE SET NULL,
  certificate_type varchar(40) NOT NULL CHECK (certificate_type IN ('TENTH_STANDARD','QUALIFICATION','DRIVING_LICENCE','HEALTHCARE','FIRST_AID','OTHER')),
  title varchar(160) NOT NULL,
  verification_status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED')),
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE volunteer_certificates ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "vc_select_own" ON volunteer_certificates;
CREATE POLICY "vc_select_own" ON volunteer_certificates FOR SELECT TO authenticated
  USING (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid())
     OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));
DROP POLICY IF EXISTS "vc_insert_own" ON volunteer_certificates;
CREATE POLICY "vc_insert_own" ON volunteer_certificates FOR INSERT TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM volunteers v WHERE v.id = volunteer_id AND v.user_id = auth.uid()));
DROP POLICY IF EXISTS "vc_update_admin" ON volunteer_certificates;
CREATE POLICY "vc_update_admin" ON volunteer_certificates FOR UPDATE TO authenticated
  USING (EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN'));

-- ============ INDEXES ============
CREATE INDEX IF NOT EXISTS idx_profiles_role ON profiles (role);
CREATE INDEX IF NOT EXISTS idx_elders_city ON elders (city);
CREATE INDEX IF NOT EXISTS idx_volunteers_city ON volunteers (city);
CREATE INDEX IF NOT EXISTS idx_vd_volunteer_status ON volunteer_documents (volunteer_id, verification_status);
CREATE INDEX IF NOT EXISTS idx_vc_volunteer_type ON volunteer_certificates (volunteer_id, certificate_type);
