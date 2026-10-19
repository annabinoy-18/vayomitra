import React from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client.js";
import {
  Alert,
  Field,
  GENDERS,
  fillLocation,
  useForm,
} from "../components/FormBits.jsx";

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
    emergencyContactPhone: "",
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

      await api("/api/auth/elders/register", {
        method: "POST",
        body,
      });

      form.setMessage(
        "Registration successful. You can log in now."
      );

      setTimeout(() => navigate("/login?role=ELDER"), 700);
    } catch (error) {
      form.setError(error.message);
      form.setErrors(error.fields || {});
    } finally {
      form.setBusy(false);
    }
  }

  async function handleLocation() {
    form.setError("");

    try {
      await fillLocation(form.setValues);
      form.setMessage(
        "Location added to the form. It can be used for distance matching."
      );
    } catch (error) {
      form.setError(error.message);
    }
  }

  return (
    <section className="card registration-card elder-registration-page">
      <div className="registration-heading">
        <span className="registration-eyebrow">
          WELCOME TO VAYOMITRA
        </span>

        <h1>Elder Registration</h1>

        <p className="muted">
          Create your account to connect with caring volunteers.
          Please enter your details below. Your location is optional.
        </p>
      </div>

      <Alert type="error">{form.error}</Alert>
      <Alert type="ok">{form.message}</Alert>

      <form onSubmit={onSubmit}>
        <section className="registration-section">
          <h2>Personal Details</h2>
          <p className="registration-section-description">
            Tell us a little about yourself.
          </p>

          <div className="grid two registration-grid">
            <Field
              label="Full name"
              error={form.errors.fullName}
            >
              <input
                name="fullName"
                autoComplete="name"
                placeholder="Enter your full name"
                value={form.values.fullName}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="Email address"
              error={form.errors.email}
            >
              <input
                name="email"
                type="email"
                autoComplete="email"
                placeholder="Enter your email address"
                value={form.values.email}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="Password"
              error={form.errors.password}
            >
              <input
                name="password"
                type="password"
                autoComplete="new-password"
                placeholder="Create a password"
                value={form.values.password}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="Confirm password"
              error={form.errors.confirmPassword}
            >
              <input
                name="confirmPassword"
                type="password"
                autoComplete="new-password"
                placeholder="Re-enter your password"
                value={form.values.confirmPassword}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="Date of birth"
              error={form.errors.dateOfBirth}
            >
              <input
                name="dateOfBirth"
                type="date"
                autoComplete="bday"
                value={form.values.dateOfBirth}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="Gender"
              error={form.errors.gender}
            >
              <select
                name="gender"
                value={form.values.gender}
                onChange={form.onChange}
                required
              >
                {GENDERS.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </Field>

            <Field
              label="Phone number"
              error={form.errors.phone}
            >
              <input
                name="phone"
                type="tel"
                autoComplete="tel"
                placeholder="Enter your phone number"
                value={form.values.phone}
                onChange={form.onChange}
                required
              />
            </Field>
          </div>
        </section>

        <section className="registration-section">
          <h2>Address Details</h2>
          <p className="registration-section-description">
            Add your address to help with volunteer matching.
          </p>

          <Field
            label="Street address"
            error={form.errors.address}
          >
            <input
              name="address"
              autoComplete="street-address"
              placeholder="House number, street name"
              value={form.values.address}
              onChange={form.onChange}
              required
            />
          </Field>

          <div className="grid two registration-grid">
            <Field
              label="Locality"
              error={form.errors.locality}
            >
              <input
                name="locality"
                placeholder="Enter your locality"
                value={form.values.locality}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="City"
              error={form.errors.city}
            >
              <input
                name="city"
                autoComplete="address-level2"
                placeholder="Enter your city"
                value={form.values.city}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="State"
              error={form.errors.state}
            >
              <input
                name="state"
                autoComplete="address-level1"
                placeholder="Enter your state"
                value={form.values.state}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="Country"
              error={form.errors.country}
            >
              <input
                name="country"
                autoComplete="country-name"
                placeholder="Enter your country"
                value={form.values.country}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="PIN code"
              error={form.errors.pinCode}
            >
              <input
                name="pinCode"
                inputMode="numeric"
                autoComplete="postal-code"
                placeholder="Enter your PIN code"
                value={form.values.pinCode}
                onChange={form.onChange}
                required
              />
            </Field>
          </div>
        </section>

        <section className="registration-section">
          <h2>Optional Location Sharing</h2>
          <p className="registration-section-description">
            You can use your current location for distance matching.
            You may also leave these fields empty.
          </p>

          <button
            type="button"
            className="btn ghost location-button"
            onClick={handleLocation}
            disabled={form.busy}
          >
            Use my current location
          </button>

          <div className="grid two registration-grid location-grid">
            <Field
              label="Latitude (optional)"
              error={form.errors.latitude || form.errors.location}
            >
              <input
                name="latitude"
                inputMode="decimal"
                placeholder="Latitude"
                value={form.values.latitude}
                onChange={form.onChange}
              />
            </Field>

            <Field label="Longitude (optional)">
              <input
                name="longitude"
                inputMode="decimal"
                placeholder="Longitude"
                value={form.values.longitude}
                onChange={form.onChange}
              />
            </Field>
          </div>
        </section>

        <section className="registration-section">
          <h2>Emergency Contact</h2>
          <p className="registration-section-description">
            Provide someone we can contact in an emergency.
          </p>

          <div className="grid two registration-grid">
            <Field
              label="Emergency contact name"
              error={form.errors.emergencyContactName}
            >
              <input
                name="emergencyContactName"
                placeholder="Enter contact person's name"
                value={form.values.emergencyContactName}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field
              label="Emergency contact phone"
              error={form.errors.emergencyContactPhone}
            >
              <input
                name="emergencyContactPhone"
                type="tel"
                placeholder="Enter contact phone number"
                value={form.values.emergencyContactPhone}
                onChange={form.onChange}
                required
              />
            </Field>
          </div>
        </section>

        <div className="registration-actions">
          <button
            className="btn registration-submit"
            disabled={form.busy}
            type="submit"
          >
            {form.busy ? "Creating account..." : "Create elder account"}
          </button>

          <p className="registration-login">
            Already registered? <Link to="/login">Log in here</Link>
          </p>
        </div>
      </form>
    </section>
  );
}
