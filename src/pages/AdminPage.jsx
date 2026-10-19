import React, { useEffect, useState } from "react";
import { listAdminDocuments, reviewDocument, openDocumentFile } from "../api/client.js";
import { Alert } from "../components/FormBits.jsx";

export default function AdminPage() {
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [reviewing, setReviewing] = useState("");

  async function load() {
    setError(""); setLoading(true);
    try {
      const data = await listAdminDocuments();
      setDocuments(data.documents || []);
    } catch (item) { setError(item.message); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  async function review(doc, status) {
    setError(""); setReviewing(doc.documentId);
    try {
      await reviewDocument(doc.documentId, status, doc.ownerType);
      await load();
    } catch (item) { setError(item.message); }
    finally { setReviewing(""); }
  }

  async function viewFile(doc) {
    setError("");
    try { await openDocumentFile(doc.documentId, doc.ownerType); }
    catch (item) { setError(item.message); }
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
            <button className="btn ghost" onClick={() => viewFile(doc)}>View file</button>
            <button className="btn" disabled={reviewing === doc.documentId} onClick={() => review(doc, "VERIFIED")}>Verify</button>
            <button className="btn danger" disabled={reviewing === doc.documentId} onClick={() => review(doc, "REJECTED")}>Reject</button>
          </div>
        </article>
      ))}
      {loading ? <p className="muted">Loading documents...</p> : null}
      {!loading && !error && documents.length === 0 ? <p className="muted">No pending documents.</p> : null}
    </section>
  );
}
