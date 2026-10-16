import React from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client.js";
import { Alert, Field, GENDERS, fillLocation, useForm } from "../components/FormBits.jsx";

export default function ElderRegisterPage() {
  const navigate = useNavigate();
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
    emergencyContactName: "",
    emergencyContactPhone: ""
  });

  async function onSubmit(event) {
    event.preventDefault();
    form.setBusy(true);
    form.setError("");
    form.setErrors({});
    try {
      const body = { ...form.values };
      if (!body.latitude) {
        delete body.latitude;
      }
      if (!body.longitude) {
        delete body.longitude;
      }
      await api("/api/auth/elders/register", { method: "POST", body });
      form.setMessage("Registration successful. You can log in now.");
      setTimeout(() => navigate("/login"), 700);
    } catch (error) {
      form.setError(error.message);
      form.setErrors(error.fields || {});
    } finally {
      form.setBusy(false);
    }
  }

  return (
    <section className="card">
      <h1>Elder registration</h1>
      <p className="muted">We store your details in PostgreSQL only after the server checks them. Location is optional.</p>
      <Alert type="error">{form.error}</Alert>
      <Alert type="ok">{form.message}</Alert>
      <form onSubmit={onSubmit}>
        <div className="grid two">
          <Field label="Full name" error={form.errors.fullName}><input name="fullName" value={form.values.fullName} onChange={form.onChange} /></Field>
          <Field label="Email" error={form.errors.email}><input name="email" type="email" value={form.values.email} onChange={form.onChange} /></Field>
          <Field label="Password" error={form.errors.password}><input name="password" type="password" value={form.values.password} onChange={form.onChange} /></Field>
          <Field label="Confirm password" error={form.errors.confirmPassword}><input name="confirmPassword" type="password" value={form.values.confirmPassword} onChange={form.onChange} /></Field>
          <Field label="Date of birth" error={form.errors.dateOfBirth}><input name="dateOfBirth" type="date" value={form.values.dateOfBirth} onChange={form.onChange} /></Field>
          <Field label="Gender" error={form.errors.gender}>
            <select name="gender" value={form.values.gender} onChange={form.onChange}>
              {GENDERS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
            </select>
          </Field>
          <Field label="Phone" error={form.errors.phone}><input name="phone" value={form.values.phone} onChange={form.onChange} /></Field>
          <Field label="PIN code" error={form.errors.pinCode}><input name="pinCode" value={form.values.pinCode} onChange={form.onChange} /></Field>
        </div>
        <Field label="Address" error={form.errors.address}><input name="address" value={form.values.address} onChange={form.onChange} /></Field>
        <div className="grid two">
          <Field label="Locality" error={form.errors.locality}><input name="locality" value={form.values.locality} onChange={form.onChange} /></Field>
          <Field label="City" error={form.errors.city}><input name="city" value={form.values.city} onChange={form.onChange} /></Field>
          <Field label="State" error={form.errors.state}><input name="state" value={form.values.state} onChange={form.onChange} /></Field>
          <Field label="Country" error={form.errors.country}><input name="country" value={form.values.country} onChange={form.onChange} /></Field>
        </div>
        <div className="actions">
          <button type="button" className="btn ghost" onClick={async () => {
            try {
              await fillLocation(form.setValues);
              form.setMessage("Location saved on this form. It is used only for distance matching.");
            } catch (error) {
              form.setError(error.message);
            }
          }}>Use my current location</button>
        </div>
        <div className="grid two">
          <Field label="Latitude (optional)" error={form.errors.location}><input name="latitude" value={form.values.latitude} onChange={form.onChange} /></Field>
          <Field label="Longitude (optional)"><input name="longitude" value={form.values.longitude} onChange={form.onChange} /></Field>
        </div>
        <div className="grid two">
          <Field label="Emergency contact name" error={form.errors.emergencyContactName}><input name="emergencyContactName" value={form.values.emergencyContactName} onChange={form.onChange} /></Field>
          <Field label="Emergency contact phone" error={form.errors.emergencyContactPhone}><input name="emergencyContactPhone" value={form.values.emergencyContactPhone} onChange={form.onChange} /></Field>
        </div>
        <div className="actions">
          <button className="btn" disabled={form.busy} type="submit">Create elder account</button>
          <Link className="btn ghost" to="/login">Already registered?</Link>
        </div>
      </form>
    </section>
  );
}
