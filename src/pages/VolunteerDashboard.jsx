import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  listVolunteerAvailableRequests,
  listVolunteerAssignments,
  listVolunteerDocuments,
  getVolunteerReadiness,
  uploadVolunteerDocument,
  applyToRequest,
  transitionAssignment,
  listVolunteerAvailability,
  addVolunteerAvailability,
  removeVolunteerAvailability,
} from "../api/client.js";
import { useAuth } from "../auth/AuthContext.jsx";
import { Alert } from "../components/FormBits.jsx";

export default function VolunteerDashboard() {
  const { user } = useAuth();
  const [available, setAvailable] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [readiness, setReadiness] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [workingOn, setWorkingOn] = useState("");
  const [availability, setAvailability] = useState([]);
  const [newAvail, setNewAvail] = useState({ weekday: 1, startTime: "09:00", endTime: "17:00" });

  async function load() {
    setError(""); setLoading(true);
    try {
      const [requestList, assignmentList, documentList, profileReadiness, availList] = await Promise.all([
        listVolunteerAvailableRequests(),
        listVolunteerAssignments(),
        listVolunteerDocuments(),
        getVolunteerReadiness(),
        listVolunteerAvailability(),
      ]);
      setAvailable(requestList); setAssignments(assignmentList);
      setDocuments(documentList); setReadiness(profileReadiness);
      setAvailability(availList);
    } catch (item) { setError(item.message); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  async function addAvailability() {
    setError(""); setMessage("");
    try {
      await addVolunteerAvailability(Number(newAvail.weekday), newAvail.startTime, newAvail.endTime);
      setMessage("Availability slot added.");
      await load();
    } catch (item) { setError(item.message); }
  }

  async function removeAvailability(id) {
    setError("");
    try {
      await removeVolunteerAvailability(id);
      await load();
    } catch (item) { setError(item.message); }
  }

  async function upload(event) {
    event.preventDefault();
    const formEl = event.currentTarget;
    setError(""); setMessage(""); setUploading(true);
    try {
      const formData = new FormData(formEl);
      await uploadVolunteerDocument(formData.get("documentType"), formData.get("file"));
      formEl.reset();
      setMessage("Document uploaded and waiting for verification.");
      await load();
    } catch (item) { setError(item.message); }
    finally { setUploading(false); }
  }

  async function apply(id) {
    setError(""); setWorkingOn(id);
    try {
      await applyToRequest(id, user.volunteerId);
      setMessage("Request accepted and added to your assignments.");
      await load();
    } catch (item) { setError(item.message); }
    finally { setWorkingOn(""); }
  }

  async function act(id, action) {
    setError(""); setWorkingOn(id);
    try {
      await transitionAssignment(id, action);
      setMessage("Assignment updated.");
      await load();
    } catch (item) { setError(item.message); }
    finally { setWorkingOn(""); }
  }

  return (
    <>
      <section className="hero">
        <h1>Volunteer desk{user?.fullName ? ` — ${user.fullName}` : ""}</h1>
        <p className="muted">Mandatory 10th and qualification certificates must be verified before matching can include you. Driving, nursing, and emergency work have extra checks.</p>
      </section>
      <Alert type="error">{error}</Alert>
      <Alert type="ok">{message}</Alert>
      <section className="dashboard-section">
        <h2>Your profile and service area</h2>
        <dl className="profile-details">
          <div><dt>Name</dt><dd>{user?.fullName || "Not provided"}</dd></div>
          <div><dt>Email</dt><dd>{user?.email || "Not provided"}</dd></div>
          <div><dt>Phone</dt><dd>{user?.phone || "Not provided"}</dd></div>
          <div><dt>Date of birth</dt><dd>{user?.dateOfBirth || "Not provided"}</dd></div>
          <div><dt>Gender</dt><dd>{user?.gender || "Not provided"}</dd></div>
          <div><dt>Locality</dt><dd>{user?.locality || "Not provided"}</dd></div>
          <div><dt>City</dt><dd>{user?.city || "Not provided"}</dd></div>
          <div><dt>State</dt><dd>{user?.state || "Not provided"}</dd></div>
          <div><dt>Country</dt><dd>{user?.country || "Not provided"}</dd></div>
          <div><dt>PIN code</dt><dd>{user?.pinCode || "Not provided"}</dd></div>
          <div className="profile-detail-wide"><dt>Address</dt><dd>{user?.address || "Not provided"}</dd></div>
          <div><dt>Coordinates</dt><dd>{user?.latitude != null && user?.longitude != null ? `${user.latitude}, ${user.longitude}` : "Not shared"}</dd></div>
          <div><dt>Experience</dt><dd>{user?.experienceYears ?? 0} years</dd></div>
          <div><dt>Service radius</dt><dd>{user?.preferredServiceRadiusKm ?? "Not set"} km</dd></div>
          <div><dt>Qualification</dt><dd>{user?.educationalQualification || "Not provided"}</dd></div>
          <div><dt>Driving</dt><dd>{user?.offersDriving ? `Offered${user?.drivingLicenceExpiry ? ` · licence expires ${user.drivingLicenceExpiry}` : ""}` : "Not offered"}</dd></div>
          {user?.offersDriving ? <div><dt>Licence number</dt><dd>{user?.drivingLicenceNumber || "Not provided"}</dd></div> : null}
          <div className="profile-detail-wide"><dt>Skills</dt><dd>{user?.skills || "Not provided"}</dd></div>
          <div className="profile-detail-wide"><dt>Services offered</dt><dd>{user?.assistanceTypes?.join(", ") || "None selected"}</dd></div>
        </dl>
      </section>
      <section className="dashboard-section matching-readiness">
        <h2>Matching eligibility</h2>
        <p className="muted">Requests appear here only when your profile meets the service, verification, schedule, and location requirements.</p>
        {readiness ? (
          <ul className="readiness-list">
            <li className={readiness.tenthCertificateVerified ? "ready" : "needed"}>
              10th-standard certificate: {readiness.tenthCertificateVerified ? "verified" : "upload and await verification"}
            </li>
            <li className={readiness.qualificationCertificateVerified ? "ready" : "needed"}>
              Qualification certificate: {readiness.qualificationCertificateVerified ? "verified" : "upload and await verification"}
            </li>
            {readiness.offersDriving ? (
              <li className={readiness.drivingLicenceVerified && !readiness.drivingLicenceExpired ? "ready" : "needed"}>
                Driving licence: {readiness.drivingLicenceVerified && !readiness.drivingLicenceExpired ? "verified" : readiness.drivingLicenceExpired ? "expired; update your licence" : "upload and await verification"}
              </li>
            ) : null}
            {readiness.assistanceTypes?.includes("HOME_NURSE_ASSISTANCE") ? (
              <li className={readiness.healthcareCertificateVerified ? "ready" : "needed"}>
                Healthcare certificate for nursing requests: {readiness.healthcareCertificateVerified ? "verified" : "upload and await verification"}
              </li>
            ) : null}
            {readiness.assistanceTypes?.includes("EMERGENCY_ASSISTANCE") ? (
              <li className={readiness.firstAidCertificateVerified ? "ready" : "needed"}>
                First-aid certificate for emergency requests: {readiness.firstAidCertificateVerified ? "verified" : "upload and await verification"}
              </li>
            ) : null}
          </ul>
        ) : loading ? <p className="muted">Checking your verification status...</p> : null}
      </section>
      <section className="dashboard-section">
        <h2>Your weekly availability</h2>
        <p className="muted">Add time slots so the matching system knows when you can help. Without any availability, you will not appear in request matches.</p>
        <div className="grid two">
          <label>Day<select value={newAvail.weekday} onChange={(e) => setNewAvail({ ...newAvail, weekday: e.target.value })}>
            <option value={1}>Monday</option><option value={2}>Tuesday</option><option value={3}>Wednesday</option>
            <option value={4}>Thursday</option><option value={5}>Friday</option><option value={6}>Saturday</option><option value={7}>Sunday</option>
          </select></label>
          <label>Start time<input type="time" value={newAvail.startTime} onChange={(e) => setNewAvail({ ...newAvail, startTime: e.target.value })} /></label>
          <label>End time<input type="time" value={newAvail.endTime} onChange={(e) => setNewAvail({ ...newAvail, endTime: e.target.value })} /></label>
          <button className="btn" type="button" onClick={addAvailability}>Add slot</button>
        </div>
        <div className="grid">
          {!loading && availability.length === 0 ? <p className="muted">No availability set yet.</p> : null}
          {availability.map((a) => (
            <p key={a.id}>
              {["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][a.weekday]} {a.startTime}–{a.endTime}
              <button className="link" onClick={() => removeAvailability(a.id)}>Remove</button>
            </p>
          ))}
        </div>
      </section>
      <section className="dashboard-section">
        <h2>Documents</h2>
        <form onSubmit={upload} className="grid two">
          <label>Document type
            <select name="documentType">
              <option value="TENTH_CERTIFICATE">10th-standard certificate</option>
              <option value="QUALIFICATION_CERTIFICATE">Qualification certificate</option>
              <option value="DRIVING_LICENCE">Driving licence</option>
              <option value="HEALTHCARE_CERTIFICATE">Healthcare certificate</option>
              <option value="FIRST_AID_CERTIFICATE">First-aid certificate</option>
              <option value="OTHER">Other supporting document</option>
            </select>
          </label>
          <label>File<input name="file" type="file" accept=".pdf,.jpg,.jpeg,.png" required /></label>
          <button className="btn" type="submit" disabled={uploading}>{uploading ? "Uploading..." : "Upload"}</button>
        </form>
        <div className="grid">
          {!loading && !error && documents.length === 0 ? <p className="muted">No documents uploaded yet.</p> : null}
          {documents.map((doc) => (
            <p key={doc.documentId}><span className="badge">{doc.verificationStatus}</span> {doc.documentType} — {doc.originalFilename}</p>
          ))}
        </div>
      </section>
      <section className="dashboard-section">
        <h2>Available requests</h2>
        {loading ? <p className="muted">Loading requests...</p> : null}
        {!loading && !error && available.length === 0 ? (
          <p className="muted">No requests match your current verified services, availability, assignment capacity, and service area. Check the eligibility status above and your selected services below.</p>
        ) : null}
        {available.map((request) => (
          <article className="request-card" key={request.id}>
            <h3>{request.title}</h3>
            <p>{request.assistanceType} · {request.preferredDate} · match score {request.matchScore ?? "n/a"}{request.requiresDriving ? " · Driver required" : ""}</p>
            <p>{request.description}</p>
            <div className="actions">
              <button className="btn" disabled={workingOn === request.id} onClick={() => apply(request.id)}>
                {workingOn === request.id ? "Accepting..." : "Accept request"}
              </button>
              <Link to={`/volunteer/requests/${request.id}`}>Details</Link>
            </div>
          </article>
        ))}
      </section>
      <section className="dashboard-section">
        <h2>My assignments</h2>
        {loading ? <p className="muted">Loading assignments...</p> : null}
        {!loading && !error && assignments.length === 0 ? <p className="muted">Accepted requests will appear here.</p> : null}
        {assignments.map((request) => (
          <article className="request-card" key={request.id}>
            <div className="badge">{request.status}</div>
            <h3>{request.title}</h3>
            <p>{request.serviceAddress}</p>
            <div className="actions">
              {request.status === "ASSIGNED" ? <button className="btn" disabled={workingOn === request.id} onClick={() => act(request.id, "accept")}>Accept</button> : null}
              {request.status === "ACCEPTED" ? <button className="btn" disabled={workingOn === request.id} onClick={() => act(request.id, "start")}>Start</button> : null}
              {request.status === "IN_PROGRESS" ? <button className="btn" disabled={workingOn === request.id} onClick={() => act(request.id, "complete")}>Complete</button> : null}
              <Link to={`/volunteer/requests/${request.id}`}>Details</Link>
            </div>
          </article>
        ))}
      </section>
    </>
  );
}
