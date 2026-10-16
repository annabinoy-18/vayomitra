import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, AssistanceApi } from "../api/client.js";
import { Alert, Field, GENDERS, fillLocation, useForm } from "../components/FormBits.jsx";

export default function VolunteerRegisterPage() {
  const navigate = useNavigate();
  const [types, setTypes] = useState([]);
  const form = useForm({
    fullName: "",
    email: "",
    password: "",
    confirmPassword: "",
    dateOfBirth: "",
    gender: "FEMALE",
    phone: "",
    address: "",
    locality: "",
    city: "",
    state: "",
    country: "India",
    pinCode: "",
    latitude: "",
    longitude: "",
    educationalQualification: "",
    skills: "",
    experienceYears: "0",
    preferredServiceRadiusKm: "15",
    offersDriving: false,
    drivingLicenceNumber: "",
    drivingLicenceExpiry: "",
    assistanceTypes: []
  });

  useEffect(() => {
    AssistanceApi.types().then(setTypes).catch(() => setTypes([]));
  }, []);

  function toggleType(code) {
    form.setValues((current) => {
      const selected = new Set(current.assistanceTypes);
      if (selected.has(code)) {
        selected.delete(code);
      } else {
        selected.add(code);
      }
      return { ...current, assistanceTypes: [...selected] };
    });
  }

  async function onSubmit(event) {
    event.preventDefault();
    form.setBusy(true);
    form.setError("");
    form.setErrors({});
    try {
      const body = { ...form.values };
      if (!body.latitude) delete body.latitude;
      if (!body.longitude) delete body.longitude;
      await api("/api/auth/volunteers/register", { method: "POST", body });
      form.setMessage("Registration successful. After login, upload your 10th and qualification certificates.");
      setTimeout(() => navigate("/login"), 800);
    } catch (error) {
      form.setError(error.message);
      form.setErrors(error.fields || {});
    } finally {
      form.setBusy(false);
    }
  }

  return (
    <section className="card">
      <h1>Volunteer registration</h1>
      <Alert type="error">{form.error}</Alert>
      <Alert type="ok">{form.message}</Alert>
      <form onSubmit={onSubmit}>
        <div className="grid two">
          <Field label="Full name" error={form.errors.fullName}><input name="fullName" value={form.values.fullName} onChange={form.onChange} /></Field>
          <Field label="Email" error={form.errors.email}><input name="email" type="email" value={form.values.email} onChange={form.onChange} /></Field>
          <Field label="Password" error={form.errors.password}><input name="password" type="password" value={form.values.password} onChange={form.onChange} /></Field>
          <Field label="Confirm password" error={form.errors.confirmPassword}><input name="confirmPassword" type="password" value={form.values.confirmPassword} onChange={form.onChange} /></Field>
          <Field label="Date of birth" error={form.errors.dateOfBirth}><input name="dateOfBirth" type="date" value={form.values.dateOfBirth} onChange={form.onChange} /></Field>
          <Field label="Gender">
            <select name="gender" value={form.values.gender} onChange={form.onChange}>
              {GENDERS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
            </select>
          </Field>
          <Field label="Phone" error={form.errors.phone}><input name="phone" value={form.values.phone} onChange={form.onChange} /></Field>
          <Field label="PIN code" error={form.errors.pinCode}><input name="pinCode" value={form.values.pinCode} onChange={form.onChange} /></Field>
        </div>
        <Field label="Address" error={form.errors.address}><input name="address" value={form.values.address} onChange={form.onChange} /></Field>
        <div className="grid two">
          <Field label="Locality"><input name="locality" value={form.values.locality} onChange={form.onChange} /></Field>
          <Field label="City"><input name="city" value={form.values.city} onChange={form.onChange} /></Field>
          <Field label="State"><input name="state" value={form.values.state} onChange={form.onChange} /></Field>
          <Field label="Country"><input name="country" value={form.values.country} onChange={form.onChange} /></Field>
        </div>
        <button type="button" className="btn ghost" onClick={async () => {
          try { await fillLocation(form.setValues); } catch (error) { form.setError(error.message); }
        }}>Use my current location</button>
        <div className="grid two">
          <Field label="Latitude (optional)"><input name="latitude" value={form.values.latitude} onChange={form.onChange} /></Field>
          <Field label="Longitude (optional)"><input name="longitude" value={form.values.longitude} onChange={form.onChange} /></Field>
        </div>
        <Field label="Educational qualification" error={form.errors.educationalQualification}><input name="educationalQualification" value={form.values.educationalQualification} onChange={form.onChange} /></Field>
        <Field label="Skills" error={form.errors.skills}><textarea name="skills" value={form.values.skills} onChange={form.onChange} /></Field>
        
<Field label="Educational Qualification Certificate (PDF, JPG, PNG)">
  <input
    type="file"
    name="EducationCertificate"
    accept=".pdf,.jpg,.jpeg,.png"
  />
</Field>

<Field label="Experience Certificate (PDF, JPG, PNG)">
  <input
    type="file"
    name="experienceCertificate"
    accept=".pdf,.jpg,.jpeg,.png"
  />
</Field>

        <div className="grid two">
          <Field label="Experience (years)"><input name="experienceYears" type="number" min="0" value={form.values.experienceYears} onChange={form.onChange} /></Field>
          <Field label="Preferred service radius (km)"><input name="preferredServiceRadiusKm" type="number" min="1" value={form.values.preferredServiceRadiusKm} onChange={form.onChange} /></Field>
        </div>
        <Field label="Assistance types you can provide" error={form.errors.assistanceTypes}>
          <div className="grid two">
            {types.map((type) => (
              <label key={type.code}>
                <input type="checkbox" checked={form.values.assistanceTypes.includes(type.code)} onChange={() => toggleType(type.code)} /> {type.displayName}
              </label>
            ))}
          </div>
        </Field>
        <label>
          <input type="checkbox" name="offersDriving" checked={form.values.offersDriving} onChange={form.onChange} /> I can offer driving-related help
        </label>
        {form.values.offersDriving ? (
          <div className="grid two">
            <Field label="Driving licence number" error={form.errors.drivingLicenceNumber}><input name="drivingLicenceNumber" value={form.values.drivingLicenceNumber} onChange={form.onChange} /></Field>
            <Field label="Licence expiry" error={form.errors.drivingLicenceExpiry}><input name="drivingLicenceExpiry" type="date" value={form.values.drivingLicenceExpiry} onChange={form.onChange} /></Field>
            <Field label="Driving Licence (PDF, JPG, PNG)">
  <input
    type="file"
    name="drivingLicenceCertificate"
    accept=".pdf,.jpg,.jpeg,.png"
  />
</Field>
          </div>
        ) : null}
        <div className="actions">
          <button className="btn" disabled={form.busy} type="submit">Create volunteer account</button>
          <Link className="btn ghost" to="/login">Already registered?</Link>
        </div>
      </form>
    </section>
  );
}
