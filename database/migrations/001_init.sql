-- Vayomitra schema
-- Applied automatically on backend startup (see SchemaMigrator).
-- Safe to re-run only through the migrator; this file is not idempotent.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS schema_migrations (
    filename TEXT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash TEXT NOT NULL,
    role VARCHAR(20) NOT NULL,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_role_chk CHECK (role IN ('ELDER', 'VOLUNTEER', 'ADMIN')),
    CONSTRAINT users_status_chk CHECK (account_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE TABLE elders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(32) NOT NULL,
    phone VARCHAR(15) NOT NULL,
    address TEXT NOT NULL,
    locality VARCHAR(120) NOT NULL,
    city VARCHAR(80) NOT NULL,
    state VARCHAR(80) NOT NULL,
    country VARCHAR(80) NOT NULL DEFAULT 'India',
    pin_code VARCHAR(10) NOT NULL,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    emergency_contact_name VARCHAR(120) NOT NULL,
    emergency_contact_phone VARCHAR(15) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT elders_user_unique UNIQUE (user_id),
    CONSTRAINT elders_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT elders_gender_chk CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    CONSTRAINT elders_phone_chk CHECK (phone ~ '^[0-9]{10}$'),
    CONSTRAINT elders_emergency_phone_chk CHECK (emergency_contact_phone ~ '^[0-9]{10}$'),
    CONSTRAINT elders_pin_chk CHECK (pin_code ~ '^[0-9]{6}$'),
    CONSTRAINT elders_lat_chk CHECK (latitude IS NULL OR (latitude BETWEEN -90 AND 90)),
    CONSTRAINT elders_lng_chk CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180))
);

CREATE TABLE volunteers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(32) NOT NULL,
    phone VARCHAR(15) NOT NULL,
    address TEXT NOT NULL,
    locality VARCHAR(120) NOT NULL,
    city VARCHAR(80) NOT NULL,
    state VARCHAR(80) NOT NULL,
    country VARCHAR(80) NOT NULL DEFAULT 'India',
    pin_code VARCHAR(10) NOT NULL,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    educational_qualification VARCHAR(160) NOT NULL,
    skills TEXT NOT NULL,
    experience_years INTEGER NOT NULL DEFAULT 0,
    preferred_service_radius_km NUMERIC(6, 2) NOT NULL DEFAULT 15,
    offers_driving BOOLEAN NOT NULL DEFAULT FALSE,
    driving_licence_number VARCHAR(40),
    driving_licence_expiry DATE,
    max_active_assignments INTEGER NOT NULL DEFAULT 3,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT volunteers_user_unique UNIQUE (user_id),
    CONSTRAINT volunteers_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT volunteers_gender_chk CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    CONSTRAINT volunteers_phone_chk CHECK (phone ~ '^[0-9]{10}$'),
    CONSTRAINT volunteers_pin_chk CHECK (pin_code ~ '^[0-9]{6}$'),
    CONSTRAINT volunteers_lat_chk CHECK (latitude IS NULL OR (latitude BETWEEN -90 AND 90)),
    CONSTRAINT volunteers_lng_chk CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180)),
    CONSTRAINT volunteers_experience_chk CHECK (experience_years >= 0 AND experience_years <= 60),
    CONSTRAINT volunteers_radius_chk CHECK (preferred_service_radius_km > 0 AND preferred_service_radius_km <= 200),
    CONSTRAINT volunteers_capacity_chk CHECK (max_active_assignments BETWEEN 1 AND 20),
    CONSTRAINT volunteers_licence_required_chk CHECK (
        offers_driving = FALSE
        OR (driving_licence_number IS NOT NULL AND length(trim(driving_licence_number)) >= 6)
    )
);

CREATE TABLE assistance_types (
    code VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    requires_driving_licence BOOLEAN NOT NULL DEFAULT FALSE,
    requires_healthcare_certificate BOOLEAN NOT NULL DEFAULT FALSE,
    requires_first_aid_certificate BOOLEAN NOT NULL DEFAULT FALSE,
    public_disclaimer TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE volunteer_service_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    volunteer_id UUID NOT NULL,
    assistance_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT vsp_unique UNIQUE (volunteer_id, assistance_type),
    CONSTRAINT vsp_volunteer_fk FOREIGN KEY (volunteer_id) REFERENCES volunteers (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT vsp_type_fk FOREIGN KEY (assistance_type) REFERENCES assistance_types (code) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE TABLE volunteer_availability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    volunteer_id UUID NOT NULL,
    weekday SMALLINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT va_volunteer_fk FOREIGN KEY (volunteer_id) REFERENCES volunteers (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT va_weekday_chk CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT va_time_chk CHECK (end_time > start_time)
);

CREATE TABLE elder_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    elder_id UUID NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes INTEGER NOT NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ed_elder_fk FOREIGN KEY (elder_id) REFERENCES elders (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ed_type_chk CHECK (document_type IN ('IDENTITY_PROOF', 'OTHER')),
    CONSTRAINT ed_status_chk CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ed_size_chk CHECK (file_size_bytes > 0 AND file_size_bytes <= 5242880)
);

CREATE TABLE volunteer_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    volunteer_id UUID NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes INTEGER NOT NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT vd_volunteer_fk FOREIGN KEY (volunteer_id) REFERENCES volunteers (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT vd_type_chk CHECK (document_type IN (
        'TENTH_CERTIFICATE',
        'QUALIFICATION_CERTIFICATE',
        'DRIVING_LICENCE',
        'HEALTHCARE_CERTIFICATE',
        'FIRST_AID_CERTIFICATE',
        'OTHER'
    )),
    CONSTRAINT vd_status_chk CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT vd_size_chk CHECK (file_size_bytes > 0 AND file_size_bytes <= 5242880)
);

CREATE TABLE volunteer_certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    volunteer_id UUID NOT NULL,
    document_id UUID,
    certificate_type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    issuer VARCHAR(160),
    issue_date DATE,
    expiry_date DATE,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT vc_volunteer_fk FOREIGN KEY (volunteer_id) REFERENCES volunteers (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT vc_document_fk FOREIGN KEY (document_id) REFERENCES volunteer_documents (id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT vc_type_chk CHECK (certificate_type IN (
        'TENTH_STANDARD',
        'QUALIFICATION',
        'DRIVING_LICENCE',
        'HEALTHCARE',
        'FIRST_AID',
        'OTHER'
    )),
    CONSTRAINT vc_status_chk CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE TABLE assistance_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id VARCHAR(20) NOT NULL,
    elder_id UUID NOT NULL,
    assistance_type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    preferred_date DATE NOT NULL,
    preferred_time_start TIME NOT NULL,
    preferred_time_end TIME NOT NULL,
    service_address TEXT NOT NULL,
    locality VARCHAR(120),
    city VARCHAR(80),
    state VARCHAR(80),
    pin_code VARCHAR(10),
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    requires_driving BOOLEAN NOT NULL DEFAULT FALSE,
    additional_details TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ar_public_id_unique UNIQUE (public_id),
    CONSTRAINT ar_elder_fk FOREIGN KEY (elder_id) REFERENCES elders (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ar_type_fk FOREIGN KEY (assistance_type) REFERENCES assistance_types (code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ar_priority_chk CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ar_status_chk CHECK (status IN (
        'PENDING', 'MATCHED', 'ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT ar_time_chk CHECK (preferred_time_end > preferred_time_start),
    CONSTRAINT ar_lat_chk CHECK (latitude IS NULL OR (latitude BETWEEN -90 AND 90)),
    CONSTRAINT ar_lng_chk CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180))
);

CREATE TABLE volunteer_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    volunteer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT va_asg_request_unique UNIQUE (request_id),
    CONSTRAINT va_asg_request_fk FOREIGN KEY (request_id) REFERENCES assistance_requests (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT va_asg_volunteer_fk FOREIGN KEY (volunteer_id) REFERENCES volunteers (id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT va_asg_status_chk CHECK (status IN ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'DECLINED'))
);

CREATE TABLE match_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    volunteer_id UUID NOT NULL,
    total_score NUMERIC(5, 2) NOT NULL,
    skill_score NUMERIC(5, 2) NOT NULL,
    distance_score NUMERIC(5, 2) NOT NULL,
    availability_score NUMERIC(5, 2) NOT NULL,
    qualification_score NUMERIC(5, 2) NOT NULL,
    distance_km NUMERIC(8, 3),
    explanation TEXT NOT NULL,
    missing_information TEXT,
    rank_position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT mr_unique UNIQUE (request_id, volunteer_id),
    CONSTRAINT mr_request_fk FOREIGN KEY (request_id) REFERENCES assistance_requests (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT mr_volunteer_fk FOREIGN KEY (volunteer_id) REFERENCES volunteers (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT mr_score_chk CHECK (total_score BETWEEN 0 AND 100)
);

CREATE TABLE matching_config (
    id SMALLINT PRIMARY KEY,
    skill_weight NUMERIC(4, 3) NOT NULL,
    distance_weight NUMERIC(4, 3) NOT NULL,
    availability_weight NUMERIC(4, 3) NOT NULL,
    qualification_weight NUMERIC(4, 3) NOT NULL,
    default_radius_km NUMERIC(6, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT mc_weights_sum_chk CHECK (
        (skill_weight + distance_weight + availability_weight + qualification_weight) BETWEEN 0.999 AND 1.001
    )
);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT auth_sessions_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT auth_sessions_token_unique UNIQUE (token_hash)
);

CREATE TABLE request_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by UUID,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT rsh_request_fk FOREIGN KEY (request_id) REFERENCES assistance_requests (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT rsh_user_fk FOREIGN KEY (changed_by) REFERENCES users (id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID,
    event_type VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_actor_fk FOREIGN KEY (actor_user_id) REFERENCES users (id) ON UPDATE CASCADE ON DELETE SET NULL
);

CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (account_status);
CREATE INDEX idx_elders_city ON elders (city);
CREATE INDEX idx_volunteers_city ON volunteers (city);
CREATE INDEX idx_vsp_type ON volunteer_service_preferences (assistance_type);
CREATE INDEX idx_requests_elder ON assistance_requests (elder_id);
CREATE INDEX idx_requests_status ON assistance_requests (status);
CREATE INDEX idx_requests_type ON assistance_requests (assistance_type);
CREATE INDEX idx_requests_date ON assistance_requests (preferred_date);
CREATE INDEX idx_assignments_volunteer ON volunteer_assignments (volunteer_id);
CREATE INDEX idx_assignments_status ON volunteer_assignments (status);
CREATE INDEX idx_sessions_user ON auth_sessions (user_id);
CREATE INDEX idx_sessions_expires ON auth_sessions (expires_at);
CREATE INDEX idx_match_request_score ON match_recommendations (request_id, total_score DESC);
CREATE INDEX idx_vd_volunteer_status ON volunteer_documents (volunteer_id, verification_status);
CREATE INDEX idx_vc_volunteer_type ON volunteer_certificates (volunteer_id, certificate_type);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_elders_updated BEFORE UPDATE ON elders FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_volunteers_updated BEFORE UPDATE ON volunteers FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_assistance_types_updated BEFORE UPDATE ON assistance_types FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_vsp_updated BEFORE UPDATE ON volunteer_service_preferences FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_va_updated BEFORE UPDATE ON volunteer_availability FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_ed_updated BEFORE UPDATE ON elder_documents FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_vd_updated BEFORE UPDATE ON volunteer_documents FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_vc_updated BEFORE UPDATE ON volunteer_certificates FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_ar_updated BEFORE UPDATE ON assistance_requests FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_asg_updated BEFORE UPDATE ON volunteer_assignments FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_mr_updated BEFORE UPDATE ON match_recommendations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_mc_updated BEFORE UPDATE ON matching_config FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_sessions_updated BEFORE UPDATE ON auth_sessions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
