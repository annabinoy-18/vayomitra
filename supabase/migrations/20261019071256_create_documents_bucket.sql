/*
# Create documents storage bucket

Creates a private storage bucket named "documents" for elder and volunteer
identity/certificate uploads. Files are organized under elders/<id>/ and
volunteers/<id>/ paths. Public access is disabled; access is via signed URLs
generated for the owning user or admin.
*/

INSERT INTO storage.buckets (id, name, public)
VALUES ('documents', 'documents', false)
ON CONFLICT (id) DO NOTHING;

-- Authenticated users can upload to their own folder
DROP POLICY IF EXISTS "doc_upload_own" ON storage.objects;
CREATE POLICY "doc_upload_own" ON storage.objects
  FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'documents'
    AND (storage.foldername(name))[1] IN ('elders', 'volunteers')
  );

-- Owners can read their own files; admins can read all
DROP POLICY IF EXISTS "doc_read_own" ON storage.objects;
CREATE POLICY "doc_read_own" ON storage.objects
  FOR SELECT TO authenticated
  USING (
    bucket_id = 'documents'
    AND (
      EXISTS (
        SELECT 1 FROM elders e
        WHERE e.user_id = auth.uid()
        AND (storage.foldername(name))[1] = 'elders'
      )
      OR EXISTS (
        SELECT 1 FROM volunteers v
        WHERE v.user_id = auth.uid()
        AND (storage.foldername(name))[1] = 'volunteers'
      )
      OR EXISTS (SELECT 1 FROM profiles p WHERE p.id = auth.uid() AND p.role = 'ADMIN')
    )
  );

-- Owners can delete their own files
DROP POLICY IF EXISTS "doc_delete_own" ON storage.objects;
CREATE POLICY "doc_delete_own" ON storage.objects
  FOR DELETE TO authenticated
  USING (
    bucket_id = 'documents'
    AND (
      EXISTS (
        SELECT 1 FROM elders e
        WHERE e.user_id = auth.uid()
        AND (storage.foldername(name))[1] = 'elders'
      )
      OR EXISTS (
        SELECT 1 FROM volunteers v
        WHERE v.user_id = auth.uid()
        AND (storage.foldername(name))[1] = 'volunteers'
      )
    )
  );
