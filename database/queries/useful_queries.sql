-- Useful operational queries for Vayomitra (run against vayomitra_database).

-- Registration: find an account without returning the password hash to clients.
SELECT id, email, role, account_status, created_at
FROM users
WHERE lower(email) = lower(:email);

-- Authentication-related session data (never select password_hash in API responses).
SELECT s.id, s.user_id, u.role, u.account_status, s.expires_at, s.revoked_at
FROM auth_sessions s
JOIN users u ON u.id = s.user_id
WHERE s.token_hash = :token_hash
  AND s.revoked_at IS NULL
  AND s.expires_at > NOW();

-- Open assistance requests for an elder.
SELECT public_id, assistance_type, title, status, preferred_date, created_at
FROM assistance_requests
WHERE elder_id = :elder_id
ORDER BY created_at DESC;

-- Active assignment for a request (single-volunteer model).
SELECT va.id, va.status, v.full_name, v.phone, va.assigned_at
FROM volunteer_assignments va
JOIN volunteers v ON v.id = va.volunteer_id
WHERE va.request_id = :request_id
  AND va.status <> 'DECLINED';

-- Matching eligibility starting set: active volunteers who offer a type.
SELECT v.id, v.full_name, v.city, v.preferred_service_radius_km
FROM volunteers v
JOIN users u ON u.id = v.user_id
JOIN volunteer_service_preferences p ON p.volunteer_id = v.id
WHERE u.account_status = 'ACTIVE'
  AND p.assistance_type = :assistance_type;

-- Mandatory document check for a volunteer.
SELECT document_type, verification_status
FROM volunteer_documents
WHERE volunteer_id = :volunteer_id
  AND document_type IN ('TENTH_CERTIFICATE', 'QUALIFICATION_CERTIFICATE');

-- Capacity: count assignments that still occupy a volunteer.
SELECT COUNT(*) AS active_count
FROM volunteer_assignments
WHERE volunteer_id = :volunteer_id
  AND status IN ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS');
