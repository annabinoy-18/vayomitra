import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client.js";
import { useAuth } from "../auth/AuthContext.jsx";
import { Alert } from "../components/FormBits.jsx";

export default function VolunteerDashboard() {
  const { user } = useAuth();
  const [available, setAvailable] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function load() {
    try {
      setAvailable(await api("/api/requests"));
      setAssignments(await api("/api/requests?scope=assignments"));
      setDocuments(await api("/api/volunteers/me/documents"));
    } catch (item) {
      setError(item.message);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function upload(event) {
    event.preventDefault();
    const form = new FormData(event.target);
    try {
      await api("/api/volunteers/me/documents", { method: "POST", body: form, isForm: true });
      setMessage("Document uploaded and waiting for verification.");
      await load();
    } catch (item) {
      setError(item.message);
    }
  }

  async function apply(id) {
    try {
      await api(`/api/requests/${id}/apply`, { method: "POST" });
      await load();
    } catch (item) {
      setError(item.message);
    }
  }

  async function act(id, action) {
    try {
      await api(`/api/requests/${id}/${action}`, { method: "POST" });
      await load();
    } catch (item) {
      setError(item.message);
    }
  }

  return (
    <>
      <section className="hero">
        <h1>Volunteer desk{user?.fullName ? ` — ${user.fullName}` : ""}</h1>
        <p className="muted">Mandatory 10th and qualification certificates must be verified before matching can include you. Driving, nursing, and emergency work have extra checks.</p>
      </section>
      <Alert type="error">{error}</Alert>
      <Alert type="ok">{message}</Alert>
      <section className="card">
        <h2>Documents</h2>
        <form onSubmit={upload} className="grid two">
          <label>Document type
            <select name="documentType">
              <option value="TENTH_CERTIFICATE">10th-standard certificate</option>
              <option value="QUALIFICATION_CERTIFICATE">Qualification certificate</option>
              <option value="DRIVING_LICENCE">Driving licence</option>
              <option value="HEALTHCARE_CERTIFICATE">Healthcare certificate</option>
              <option value="FIRST_AID_CERTIFICATE">First-aid certificate</option>
            </select>
          </label>
          <label>File
            <input name="file" type="file" accept=".pdf,.jpg,.jpeg,.png" required />
          </label>
          <button className="btn" type="submit">Upload</button>
        </form>
        <div className="grid">
          {documents.map((doc) => (
            <p key={doc.documentId}><span className="badge">{doc.verificationStatus}</span> {doc.documentType} — {doc.originalFilename}</p>
          ))}
        </div>
      </section>
      <section className="card">
        <h2>Available requests</h2>
        {available.map((request) => (
          <article className="request-card" key={request.id}>
            <h3>{request.title}</h3>
            <p>{request.assistanceType} · {request.preferredDate} · match score {request.matchScore ?? "n/a"}</p>
            <p>{request.description}</p>
            <button className="btn" onClick={() => apply(request.id)}>Apply / accept matching</button>
          </article>
        ))}
      </section>
      <section className="card">
        <h2>My assignments</h2>
        {assignments.map((request) => (
          <article className="request-card" key={request.id}>
            <div className="badge">{request.status}</div>
            <h3>{request.title}</h3>
            <p>{request.serviceAddress}</p>
            <div className="actions">
              {request.status === "ASSIGNED" ? <button className="btn" onClick={() => act(request.id, "accept")}>Accept</button> : null}
              {request.status === "ACCEPTED" ? <button className="btn" onClick={() => act(request.id, "start")}>Start</button> : null}
              {request.status === "IN_PROGRESS" ? <button className="btn" onClick={() => act(request.id, "complete")}>Complete</button> : null}
              <Link to={`/volunteer/requests/${request.id}`}>Details</Link>
            </div>
          </article>
        ))}
      </section>
    </>
  );
}
