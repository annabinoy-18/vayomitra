/*
# Revoke anon execute on compute_matches

The SECURITY DEFINER function compute_matches must only be callable by
authenticated users. Revoke execute from anon and PUBLIC to prevent
unauthenticated calls.
*/

REVOKE EXECUTE ON FUNCTION compute_matches(uuid) FROM anon;
REVOKE EXECUTE ON FUNCTION compute_matches(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION compute_matches(uuid) TO authenticated;
