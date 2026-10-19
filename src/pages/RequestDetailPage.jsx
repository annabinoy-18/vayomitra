import React, { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { getRequest, assignVolunteer, transitionAssignment, cancelRequest, refreshMatches } from "../api/client.js";
import { useAuth } from "../auth/AuthContext.jsx";
import { Alert } from "../components/FormBits.jsx";

const EXCLUSION_LABELS = {
  LOCATION_UNKNOWN_AND_AREA_MISMATCH: "Location is unknown or outside the request area",
  MANDATORY_CERTIFICATES_NOT_VERIFIED: "Mandatory education certificates are not verified",
  OUTSIDE_PREFERRED_RADIUS: "Outside the volunteer's service radius",
  UNAVAILABLE_FOR_REQUESTED_TIME: "Unavailable at the requested date and time",
  EXPERIENCE: "No experience listed",
  LOCATION: "Location coordinates not shared",
};

export default function RequestDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const [request, setRequest] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const matches = request?.matches || [];

  async function load() {
    setLoading(true); setError("");
    try {
      if (user?.role === "ELDER") {
        await refreshMatches(id);
      }
      setRequest(await getRequest(id, user?.role));
    } catch (item) { setError(item.message); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, [id, user?.role]);

  async function run(action, body) {
    setError(""); setBusy(true);
    try {
      if (action === "assign") await assignVolunteer(id, body.volunteerId);
      else if (action === "cancel") await cancelRequest(id);
      else await transitionAssignment(id, action);
      setMessage("Updated.");
      await load();
    } catch (item) { setError(item.message); }
    finally { setBusy(false); }
  }

  if (!request) {
    return (
      <section className="card">
        <h1>{loading ? "Loading request" : "Request unavailable"}</h1>
        <Alert type="error">{error}</Alert>
        {!loading ? <button className="btn" onClick={load}>Try again</button> : null}
      </section>
    );
  }

  return (
    <section className="request-detail-page">
      <div className="badge">{request.status}</div>
      <h1>{request.title}</h1>
      <p>{request.publicId} · {request.assistanceType} · {request.preferredDate} {request.preferredTimeStart}–{request.preferredTimeEnd}{request.requiresDriving ? " · Driver required" : ""}</p>
      <p>{request.description}</p>
      <p className="muted">{request.serviceAddress}</p>
      <p className="request-location-detail">{[request.locality, request.city, request.state, request.pinCode].filter(Boolean).join(", ") || "Structured service location not provided"}</p>
      <Alert type="error">{error}</Alert>
      <Alert type="ok">{message}</Alert>
      {request.assignment ? (
        <p>Assigned to <strong>{request.assignment.volunteerName}</strong> ({request.assignment.status})</p>
      ) : null}
      {user?.role === "VOLUNTEER" && request.assignment ? (
        <div className="actions">
          {request.assignment.status === "ASSIGNED" ? <button className="btn" disabled={busy} onClick={() => run("accept")}>Accept assignment</button> : null}
          {request.assignment.status === "ACCEPTED" ? <button className="btn" disabled={busy} onClick={() => run("start")}>Start assistance</button> : null}
          {request.assignment.status === "IN_PROGRESS" ? <button className="btn" disabled={busy} onClick={() => run("complete")}>Mark completed</button> : null}
        </div>
      ) : null}
      <div className="actions">
        {user?.role === "ELDER" && ["PENDING", "MATCHED", "ASSIGNED", "ACCEPTED"].includes(request.status) ? (
          <button className="btn danger" disabled={busy} onClick={() => run("cancel")}>Cancel request</button>
        ) : null}
      </div>
      {user?.role === "ELDER" ? (
        <>
          <h2>Eligible volunteers{matches.length ? ` (${matches.length})` : ""}</h2>
          <p className="muted">Showing up to 10 eligible volunteers, ranked by match score. Exact home coordinates are not shown.</p>
          <ol className="match-list">
            {matches.map((match) => (
              <li className="match-row" key={match.volunteerId}>
                <div className="match-rank" aria-label={`Rank ${match.rank}`}>{match.rank}</div>
                <div className="match-profile">
                  <div className="match-profile-heading">
                    <div>
                      <h3>{match.fullName}</h3>
                      <p className="match-location">{match.city || "Location not provided"}</p>
                    </div>
                    <div className="match-total"><strong>{match.totalScore}</strong><span>match points</span></div>
                  </div>
                  <dl className="match-details">
                    <div><dt>Experience</dt><dd>{match.experienceYears} years</dd></div>
                    <div><dt>Education</dt><dd>{match.educationalQualification}</dd></div>
                    <div><dt>Distance</dt><dd>{match.distanceKm == null ? "Not available" : `${match.distanceKm} km`}</dd></div>
                  </dl>
                  <div className="match-contact">
                    {match.phone ? <a href={`tel:${match.phone}`}>Call {match.phone}</a> : null}
                    {match.email ? <a href={`mailto:${match.email}`}>{match.email}</a> : null}
                  </div>
                  <p className="match-skills"><strong>Skills:</strong> {match.skills || "Not provided"}</p>
                  <div className="match-points" aria-label="Score breakdown">
                    <span>Skill <strong>{match.skillScore}</strong></span>
                    <span>Distance <strong>{match.distanceScore}</strong></span>
                    <span>Availability <strong>{match.availabilityScore}</strong></span>
                    <span>Qualification <strong>{match.qualificationScore}</strong></span>
                  </div>
                  <p className="match-explanation">{match.explanation}</p>
                  {match.missingInformation ? <p className="badge warn">Missing: {match.missingInformation.split(",").map((m) => EXCLUSION_LABELS[m] || m).join(", ")}</p> : null}
                  {["PENDING", "MATCHED"].includes(request.status) ? (
                    <button className="btn" disabled={busy} onClick={() => run("assign", { volunteerId: match.volunteerId })}>Assign this volunteer</button>
                  ) : null}
                </div>
              </li>
            ))}
          </ol>
          {matches.length === 0 ? (
            <div className="match-empty-state">
              <p className="muted">No eligible volunteers currently meet this request's requirements. More volunteers must register and meet the requirements before matches can appear.</p>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
