import React, { useEffect, useState } from "react";
import { api, openAuthenticatedFile } from "../api/client.js";
import { Alert } from "../components/FormBits.jsx";

export default function AdminPage() {
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [reviewing, setReviewing] = useState("");

  async function load() {
    setError("");
    setLoading(true);
    try {
      const data = await api("/api/admin/documents");
      setDocuments(data.documents || []);
    } catch (item) {
      setError(item.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function review(id, status) {
    setError("");
    setReviewing(id);
    try {
      await api(`/api/admin/documents/${id}/review`, { method: "POST", body: { status, notes: "Reviewed by an administrator." } });
      await load();
    } catch (item) {
      setError(item.message);
    } finally {
      setReviewing("");
    }
  }

  async function viewFile(id) {
    setError("");
    try {
      await openAuthenticatedFile(`/api/documents/${id}/file`);
    } catch (item) {
      setError(item.message);
    }
  }

  return (
    <section className="admin-page">
      <h1>Document verification</h1>
      <p className="muted">Unverified or rejected mandatory documents never satisfy matching eligibility.</p>
      <Alert type="error">{error}</Alert>
      {documents.map((doc) => (
        <article className="request-card" key={doc.documentId}>
          <h3>{doc.ownerType === "ELDER" ? "Elder" : "Volunteer"}: {doc.volunteerName}</h3>
          <p>{doc.volunteerEmail} · {doc.documentType} · {doc.originalFilename}</p>
          <div className="actions">
            <button className="btn ghost" onClick={() => viewFile(doc.documentId)}>View file</button>
            <button className="btn" disabled={reviewing === doc.documentId} onClick={() => review(doc.documentId, "VERIFIED")}>Verify</button>
            <button className="btn danger" disabled={reviewing === doc.documentId} onClick={() => review(doc.documentId, "REJECTED")}>Reject</button>
          </div>
        </article>
      ))}
      {loading ? <p className="muted">Loading documents...</p> : null}
      {!loading && !error && documents.length === 0 ? <p className="muted">No pending documents.</p> : null}
    </section>
  );
}
