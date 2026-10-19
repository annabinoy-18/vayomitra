import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listElderRequests, listElderDocuments, uploadElderDocument } from "../api/client.js";
import { useAuth } from "../auth/AuthContext.jsx";
import { Alert } from "../components/FormBits.jsx";

export default function ElderDashboard() {
  const { user } = useAuth();
  const [requests, setRequests] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);

  async function load() {
    setError(""); setLoading(true);
    try {
      const [requestList, documentList] = await Promise.all([listElderRequests(), listElderDocuments()]);
      setRequests(requestList); setDocuments(documentList);
    } catch (item) { setError(item.message); }
    finally { setLoading(false); }
  }

  useEffect(() => { load(); }, []);

  async function uploadProof(event) {
    event.preventDefault();
    const formEl = event.currentTarget;
    setError(""); setMessage(""); setUploading(true);
    try {
      const formData = new FormData(formEl);
      const documentType = formData.get("documentType");
      const file = formData.get("file");
      await uploadElderDocument(documentType, file);
      formEl.reset();
      setMessage("Identity document uploaded for verification.");
      await load();
    } catch (item) { setError(item.message); }
    finally { setUploading(false); }
  }

  return (
    <>
      <section className="hero">
        <h1>Namaste{user?.fullName ? `, ${user.fullName}` : ""}</h1>
        <p className="muted">Create a request and Vayomitra will rank eligible volunteers.</p>
        <div className="actions"><Link className="btn btn-cta" to="/elder/requests/new">Make a request</Link></div>
      </section>
      <Alert type="error">{error}</Alert>
      <section className="dashboard-section">
        <h2>Your profile</h2>
        <dl className="profile-details">
          <div><dt>Name</dt><dd>{user?.fullName || "Not provided"}</dd></div>
          <div><dt>Email</dt><dd>{user?.email || "Not provided"}</dd></div>
          <div><dt>Phone</dt><dd>{user?.phone || "Not provided"}</dd></div>
          <div><dt>Date of birth</dt><dd>{user?.dateOfBirth || "Not provided"}</dd></div>
          <div><dt>Gender</dt><dd>{user?.gender || "Not provided"}</dd></div>
          <div className="profile-detail-wide"><dt>Address</dt><dd>{user?.address || "Not provided"}</dd></div>
          <div><dt>Locality</dt><dd>{user?.locality || "Not provided"}</dd></div>
          <div><dt>City</dt><dd>{user?.city || "Not provided"}</dd></div>
          <div><dt>State</dt><dd>{user?.state || "Not provided"}</dd></div>
          <div><dt>Country</dt><dd>{user?.country || "Not provided"}</dd></div>
          <div><dt>PIN code</dt><dd>{user?.pinCode || "Not provided"}</dd></div>
          <div><dt>Coordinates</dt><dd>{user?.latitude != null && user?.longitude != null ? `${user.latitude}, ${user.longitude}` : "Not shared"}</dd></div>
          <div><dt>Emergency contact</dt><dd>{user?.emergencyContactName || "Not provided"}{user?.emergencyContactPhone ? ` · ${user.emergencyContactPhone}` : ""}</dd></div>
        </dl>
      </section>
      <section className="dashboard-section">
        <h2>Identity proof</h2>
        <p className="muted">Upload a PDF, JPG, or PNG identity document for administrator verification.</p>
        <Alert type="ok">{message}</Alert>
        <form onSubmit={uploadProof} className="grid two">
          <label>Document type
            <select name="documentType"><option value="IDENTITY_PROOF">Identity proof</option><option value="OTHER">Other supporting document</option></select>
          </label>
          <label>File<input name="file" type="file" accept=".pdf,.jpg,.jpeg,.png" required /></label>
          <button className="btn" type="submit" disabled={uploading}>{uploading ? "Uploading..." : "Upload document"}</button>
        </form>
        <div className="grid">
          {documents.map((document) => (
            <p key={document.documentId}><span className="badge">{document.verificationStatus}</span> {document.documentType} — {document.originalFilename}</p>
          ))}
        </div>
      </section>
      <section className="dashboard-section">
        <h2>Your requests</h2>
        <div className="grid">
          {!loading && !error && requests.length === 0 ? <p className="muted">No requests yet.</p> : null}
          {loading ? <p className="muted">Loading your requests...</p> : null}
          {requests.map((request) => (
            <article className="request-card" key={request.id}>
              <div className="badge">{request.status}</div>
              <h3>{request.title}</h3>
              <p>{request.publicId} · {request.assistanceType} · {request.preferredDate}</p>
              <p className="muted">Eligible volunteers: {request.eligibleVolunteerCount ?? 0}</p>
              <Link to={`/elder/requests/${request.id}`}>Open request and volunteer list</Link>
            </article>
          ))}
        </div>
      </section>
    </>
  );
}
