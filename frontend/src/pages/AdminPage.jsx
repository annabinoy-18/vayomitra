import React, { useEffect, useState } from "react";
import { api } from "../api/client.js";
import { Alert } from "../components/FormBits.jsx";

export default function AdminPage() {
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState("");

  async function load() {
    try {
      const data = await api("/api/admin/documents");
      setDocuments(data.documents || []);
    } catch (item) {
      setError(item.message);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function review(id, status) {
    try {
      await api(`/api/admin/documents/${id}/review`, { method: "POST", body: { status, notes: "Reviewed in demo console" } });
      await load();
    } catch (item) {
      setError(item.message);
    }
  }

  return (
    <section className="card">
      <h1>Document verification</h1>
      <p className="muted">Unverified or rejected mandatory documents never satisfy matching eligibility.</p>
      <Alert type="error">{error}</Alert>
      {documents.map((doc) => (
        <article className="request-card" key={doc.documentId}>
          <h3>{doc.volunteerName}</h3>
          <p>{doc.volunteerEmail} · {doc.documentType} · {doc.originalFilename}</p>
          <div className="actions">
            <a className="btn ghost" href={`/api/documents/${doc.documentId}/file`} target="_blank" rel="noreferrer">View file</a>
            <button className="btn" onClick={() => review(doc.documentId, "VERIFIED")}>Verify</button>
            <button className="btn danger" onClick={() => review(doc.documentId, "REJECTED")}>Reject</button>
          </div>
        </article>
      ))}
      {documents.length === 0 ? <p className="muted">No pending documents.</p> : null}
    </section>
  );
}
