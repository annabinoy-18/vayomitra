import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createRequest, AssistanceApi } from "../api/client.js";
import { useAuth } from "../auth/AuthContext.jsx";
import { Alert, Field, fillLocation, useForm } from "../components/FormBits.jsx";

export default function NewRequestPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [types, setTypes] = useState([]);
  const [typesError, setTypesError] = useState("");
  const [typesLoading, setTypesLoading] = useState(true);
  const form = useForm({
    assistanceType: "GROCERY_SUPPORT",
    title: "", description: "",
    preferredDate: "", preferredTimeStart: "10:00", preferredTimeEnd: "12:00",
    serviceAddress: "", locality: "", city: "", state: "", pinCode: "",
    latitude: "", longitude: "",
    priority: "NORMAL", requiresDriving: false, additionalDetails: "",
  });
  const selectedType = types.find((type) => type.code === form.values.assistanceType);
  const drivingRequired = Boolean(selectedType?.requiresDrivingLicence);

  async function loadTypes() {
    setTypesError(""); setTypesLoading(true);
    try { setTypes(await AssistanceApi.types()); }
    catch (error) { setTypesError(error.message); }
    finally { setTypesLoading(false); }
  }
  useEffect(() => { loadTypes(); }, []);

  async function onSubmit(event) {
    event.preventDefault();
    form.setBusy(true); form.setError(""); form.setErrors({});
    try {
      const body = { ...form.values };
      if (!body.city.trim() && !body.pinCode.trim() && (!body.latitude || !body.longitude)) {
        form.setErrors({ city: "Enter a city or PIN code, or use your current location." });
        form.setError("Add a location to find nearby volunteers.");
        return;
      }
      body.requiresDriving = body.requiresDriving || drivingRequired;
      if (!body.latitude) delete body.latitude;
      if (!body.longitude) delete body.longitude;
      const created = await createRequest(body, user);
      navigate(`/elder/requests/${created.id}`);
    } catch (error) {
      form.setError(error.message);
      form.setErrors(error.fields || {});
    } finally {
      form.setBusy(false);
    }
  }

  return (
    <section className="request-form-page card">
      <h1>New assistance request</h1>
      <Alert type="error">{form.error}</Alert>
      {typesError ? (
        <div><Alert type="error">{typesError}</Alert>
        <button type="button" className="btn ghost" onClick={loadTypes} disabled={typesLoading}>{typesLoading ? "Loading..." : "Retry loading assistance types"}</button></div>
      ) : null}
      <form onSubmit={onSubmit}>
        <Field label="Assistance type" error={form.errors.assistanceType}>
          <select name="assistanceType" value={form.values.assistanceType} onChange={form.onChange} required disabled={typesLoading || types.length === 0}>
            {types.map((type) => <option key={type.code} value={type.code}>{type.displayName}</option>)}
          </select>
        </Field>
        {selectedType?.requiresHealthcareCertificate ? <p className="muted">Matching requires volunteers with a verified healthcare certificate.</p> : null}
        {selectedType?.requiresFirstAidCertificate ? <p className="muted">Matching requires volunteers with a verified first-aid certificate.</p> : null}
        <Field label="Title" error={form.errors.title}><input name="title" value={form.values.title} onChange={form.onChange} required /></Field>
        <Field label="Description" error={form.errors.description}><textarea name="description" value={form.values.description} onChange={form.onChange} required /></Field>
        <div className="grid two">
          <Field label="Preferred date" error={form.errors.preferredDate}><input type="date" name="preferredDate" min={new Date().toISOString().slice(0, 10)} required value={form.values.preferredDate} onChange={form.onChange} /></Field>
          <Field label="Priority"><select name="priority" value={form.values.priority} onChange={form.onChange}><option>LOW</option><option>NORMAL</option><option>HIGH</option><option>URGENT</option></select></Field>
          <Field label="Start time" error={form.errors.preferredTimeStart}><input type="time" name="preferredTimeStart" value={form.values.preferredTimeStart} onChange={form.onChange} required /></Field>
          <Field label="End time" error={form.errors.preferredTimeEnd}><input type="time" name="preferredTimeEnd" value={form.values.preferredTimeEnd} onChange={form.onChange} required /></Field>
        </div>
        <Field label="Service address" error={form.errors.serviceAddress}><input name="serviceAddress" value={form.values.serviceAddress} onChange={form.onChange} required /></Field>
        <div className="grid two">
          <Field label="Locality"><input name="locality" value={form.values.locality} onChange={form.onChange} /></Field>
          <Field label="City" error={form.errors.city}><input name="city" value={form.values.city} onChange={form.onChange} /></Field>
          <Field label="State"><input name="state" value={form.values.state} onChange={form.onChange} /></Field>
          <Field label="PIN code" error={form.errors.pinCode}><input name="pinCode" value={form.values.pinCode} onChange={form.onChange} /></Field>
        </div>
        <button type="button" className="btn ghost" onClick={async () => { try { await fillLocation(form.setValues); } catch (error) { form.setError(error.message); } }}>Use my current location</button>
        <div className="grid two">
          <Field label="Latitude"><input name="latitude" value={form.values.latitude} onChange={form.onChange} /></Field>
          <Field label="Longitude"><input name="longitude" value={form.values.longitude} onChange={form.onChange} /></Field>
        </div>
        <label><input type="checkbox" name="requiresDriving" checked={form.values.requiresDriving || drivingRequired} onChange={form.onChange} disabled={drivingRequired} /> This request needs a volunteer who can drive{drivingRequired ? " (required for transportation)" : ""}</label>
        <Field label="Additional details"><textarea name="additionalDetails" value={form.values.additionalDetails} onChange={form.onChange} /></Field>
        <div className="actions"><button className="btn" disabled={form.busy || typesLoading || types.length === 0} type="submit">{form.busy ? "Finding volunteers..." : "Save and find volunteers"}</button></div>
      </form>
    </section>
  );
}
