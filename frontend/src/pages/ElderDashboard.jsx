import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client.js";
import { useAuth } from "../auth/AuthContext.jsx";
import { Alert } from "../components/FormBits.jsx";

export default function ElderDashboard() {
  const { user } = useAuth();
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api("/api/requests").then(setRequests).catch((item) => setError(item.message));
  }, []);

  return (
    <>
      <section className="hero">
        <h1>Namaste{user?.fullName ? `, ${user.fullName}` : ""}</h1>
        <p className="muted">Create a request and Vayomitra will rank eligible volunteers on the server.</p>
        <div className="actions">
          <Link className="btn" to="/elder/requests/new">Ask for help</Link>
        </div>
      </section>
      <section className="card">
        <h2>Your requests</h2>
        <Alert type="error">{error}</Alert>
        <div className="grid">
          {requests.length === 0 ? <p className="muted">No requests yet.</p> : null}
          {requests.map((request) => (
            <article className="request-card" key={request.id}>
              <div className="badge">{request.status}</div>
              <h3>{request.title}</h3>
              <p>{request.publicId} · {request.assistanceType} · {request.preferredDate}</p>
              <Link to={`/elder/requests/${request.id}`}>Open details</Link>
            </article>
          ))}
        </div>
      </section>
    </>
  );
}
