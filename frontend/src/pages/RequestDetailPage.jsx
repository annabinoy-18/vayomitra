import React, { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api/client.js";
import { Alert } from "../components/FormBits.jsx";

export default function RequestDetailPage() {
  const { id } = useParams();
  const [request, setRequest] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  async function load() {
    try {
      setRequest(await api(`/api/requests/${id}`));
    } catch (item) {
      setError(item.message);
    }
  }

  useEffect(() => {
    load();
  }, [id]);

  async function run(path, body) {
    setError("");
    try {
      const data = await api(path, { method: "POST", body });
      setRequest(data);
      setMessage("Updated.");
    } catch (item) {
      setError(item.message);
    }
  }

  if (!request) {
    return <section className="card"><Alert type="error">{error}</Alert><p>Loading...</p></section>;
  }

  return (
    <section className="card">
      <div className="badge">{request.status}</div>
      <h1>{request.title}</h1>
      <p>{request.publicId} · {request.assistanceType} · {request.preferredDate} {request.preferredTimeStart}–{request.preferredTimeEnd}</p>
      <p>{request.description}</p>
      <p className="muted">{request.serviceAddress}</p>
      <Alert type="error">{error}</Alert>
      <Alert type="ok">{message}</Alert>
      {request.assignment ? (
        <p>Assigned to <strong>{request.assignment.volunteerName}</strong> ({request.assignment.status})</p>
      ) : null}
      <div className="actions">
        {["PENDING", "MATCHED", "ASSIGNED", "ACCEPTED"].includes(request.status) ? (
          <button className="btn danger" onClick={() => run(`/api/requests/${id}/cancel`)}>Cancel request</button>
        ) : null}
      </div>
      <h2>Recommended volunteers</h2>
      <p className="muted">Scores come from the Java matching service. Exact home coordinates are not shown here.</p>
      <div className="grid">
        {(request.matches || []).map((match) => (
          <article className="request-card" key={match.volunteerId}>
            <div className="score">{match.totalScore}</div>
            <h3>{match.fullName}</h3>
            <p>{match.city} · {match.experienceYears} years · {match.educationalQualification}</p>
            <p>{match.explanation}</p>
            {match.missingInformation ? <p className="badge warn">Missing: {match.missingInformation}</p> : null}
            {["PENDING", "MATCHED"].includes(request.status) ? (
              <button className="btn" onClick={() => run(`/api/requests/${id}/assign`, { volunteerId: match.volunteerId })}>
                Assign this volunteer
              </button>
            ) : null}
          </article>
        ))}
        {(request.matches || []).length === 0 ? <p className="muted">No eligible volunteer is available yet. Documents may still be pending verification.</p> : null}
      </div>
    </section>
  );
}
