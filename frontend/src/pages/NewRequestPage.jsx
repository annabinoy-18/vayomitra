import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, AssistanceApi } from "../api/client.js";
import { Alert, Field, fillLocation, useForm } from "../components/FormBits.jsx";

export default function NewRequestPage() {
  const navigate = useNavigate();
  const [types, setTypes] = useState([]);
  const form = useForm({
    assistanceType: "GROCERY_SUPPORT",
    title: "",
    description: "",
    preferredDate: "",
    preferredTimeStart: "10:00",
    preferredTimeEnd: "12:00",
    serviceAddress: "",
    locality: "",
    city: "",
    state: "",
    pinCode: "",
    latitude: "",
    longitude: "",
    priority: "NORMAL",
    requiresDriving: false,
    additionalDetails: ""
  });

  useEffect(() => {
    AssistanceApi.types().then(setTypes).catch(() => setTypes([]));
  }, []);

  async function onSubmit(event) {
    event.preventDefault();
    form.setBusy(true);
    form.setError("");
    form.setErrors({});
    try {
      const body = { ...form.values };
      if (!body.latitude) delete body.latitude;
      if (!body.longitude) delete body.longitude;
      const created = await api("/api/requests", { method: "POST", body });
      navigate(`/elder/requests/${created.id}`);
    } catch (error) {
      form.setError(error.message);
      form.setErrors(error.fields || {});
    } finally {
      form.setBusy(false);
    }
  }

  return (
    <section className="card">
      <h1>New assistance request</h1>
      <Alert type="error">{form.error}</Alert>
      <form onSubmit={onSubmit}>
        <Field label="Assistance type" error={form.errors.assistanceType}>
          <select name="assistanceType" value={form.values.assistanceType} onChange={form.onChange}>
            {types.map((type) => <option key={type.code} value={type.code}>{type.displayName}</option>)}
          </select>
        </Field>
        <Field label="Title" error={form.errors.title}><input name="title" value={form.values.title} onChange={form.onChange} /></Field>
        <Field label="Description" error={form.errors.description}><textarea name="description" value={form.values.description} onChange={form.onChange} /></Field>
        <div className="grid two">
          <Field label="Preferred date" error={form.errors.preferredDate}><input type="date" name="preferredDate" value={form.values.preferredDate} onChange={form.onChange} /></Field>
          <Field label="Priority">
            <select name="priority" value={form.values.priority} onChange={form.onChange}>
              <option>LOW</option><option>NORMAL</option><option>HIGH</option><option>URGENT</option>
            </select>
          </Field>
          <Field label="Start time" error={form.errors.preferredTimeStart}><input type="time" name="preferredTimeStart" value={form.values.preferredTimeStart} onChange={form.onChange} /></Field>
          <Field label="End time" error={form.errors.preferredTimeEnd}><input type="time" name="preferredTimeEnd" value={form.values.preferredTimeEnd} onChange={form.onChange} /></Field>
        </div>
        <Field label="Service address" error={form.errors.serviceAddress}><input name="serviceAddress" value={form.values.serviceAddress} onChange={form.onChange} /></Field>
        <div className="grid two">
          <Field label="Locality"><input name="locality" value={form.values.locality} onChange={form.onChange} /></Field>
          <Field label="City"><input name="city" value={form.values.city} onChange={form.onChange} /></Field>
          <Field label="State"><input name="state" value={form.values.state} onChange={form.onChange} /></Field>
          <Field label="PIN code" error={form.errors.pinCode}><input name="pinCode" value={form.values.pinCode} onChange={form.onChange} /></Field>
        </div>
        <button type="button" className="btn ghost" onClick={async () => {
          try { await fillLocation(form.setValues); } catch (error) { form.setError(error.message); }
        }}>Use my current location</button>
        <div className="grid two">
          <Field label="Latitude"><input name="latitude" value={form.values.latitude} onChange={form.onChange} /></Field>
          <Field label="Longitude"><input name="longitude" value={form.values.longitude} onChange={form.onChange} /></Field>
        </div>
        <label>
          <input type="checkbox" name="requiresDriving" checked={form.values.requiresDriving} onChange={form.onChange} /> This request needs a volunteer who can drive
        </label>
        <Field label="Additional details"><textarea name="additionalDetails" value={form.values.additionalDetails} onChange={form.onChange} /></Field>
        <div className="actions">
          <button className="btn" disabled={form.busy} type="submit">Save and find volunteers</button>
        </div>
      </form>
    </section>
  );
}
