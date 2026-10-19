import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, AssistanceApi } from "../api/client.js";
import {
  Alert,
  Field,
  GENDERS,
  fillLocation,
  useForm,
} from "../components/FormBits.jsx";

export default function VolunteerRegisterPage() {
  const navigate = useNavigate();
  const [types, setTypes] = useState([]);
  const [typesError, setTypesError] = useState("");
  const [typesLoading, setTypesLoading] = useState(true);

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
    assistanceTypes: [],
  });

  async function loadTypes() {
    setTypesError("");
    setTypesLoading(true);
    try {
      setTypes(await AssistanceApi.types());
    } catch (error) {
      setTypes([]);
      setTypesError(error.message);
    } finally {
      setTypesLoading(false);
    }
  }

  useEffect(() => {
    loadTypes();
  }, []);

  function toggleType(code) {
    form.setValues((current) => {
      const selected = new Set(current.assistanceTypes);

      if (selected.has(code)) {
        selected.delete(code);
      } else {
        selected.add(code);
      }

      return {
        ...current,
        assistanceTypes: [...selected],
      };
    });
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

  async function onSubmit(event) {
    event.preventDefault();

    form.setBusy(true);
    form.setError("");
    form.setErrors({});

    try {
      const body = { ...form.values };

      if (!body.latitude) delete body.latitude;
      if (!body.longitude) delete body.longitude;

      await api("/api/auth/volunteers/register", {
        method: "POST",
        body,
      });

      form.setMessage(
        "Registration successful. After login, upload your 10th and qualification certificates."
      );

      setTimeout(() => navigate("/login?role=VOLUNTEER"), 800);
    } catch (error) {
      form.setError(error.message);
      form.setErrors(error.fields || {});
    } finally {
      form.setBusy(false);
    }
  }

  return (
    <section className="card registration-card volunteer-registration-page">
      <div className="registration-heading">
        <span className="registration-eyebrow">
          MAKE A DIFFERENCE WITH VAYOMITRA
        </span>

        <h1>Volunteer Registration</h1>

        <p className="muted">
          Join our community of caring volunteers. Tell us about
          yourself, your skills, and the assistance you can provide.
        </p>
      </div>

      <Alert type="error">{form.error}</Alert>
      <Alert type="ok">{form.message}</Alert>

      <form onSubmit={onSubmit}>
        <section className="registration-section">
          <h2>Personal Details</h2>
          <p className="registration-section-description">
            Enter your basic information to create your account.
          </p>

          <div className="grid two registration-grid">
            <Field label="Full name" error={form.errors.fullName}>
              <input
                name="fullName"
                autoComplete="name"
                placeholder="Enter your full name"
                value={form.values.fullName}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field label="Email address" error={form.errors.email}>
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

            <Field label="Password" error={form.errors.password}>
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

            <Field label="Gender">
              <select
                name="gender"
                value={form.values.gender}
                onChange={form.onChange}
              >
                {GENDERS.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Phone number" error={form.errors.phone}>
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
            Add your address to help us match you with nearby requests.
          </p>

          <Field label="Street address" error={form.errors.address}>
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
            <Field label="Locality" error={form.errors.locality}>
              <input
                name="locality"
                placeholder="Enter your locality"
                value={form.values.locality}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field label="City" error={form.errors.city}>
              <input
                name="city"
                autoComplete="address-level2"
                placeholder="Enter your city"
                value={form.values.city}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field label="State" error={form.errors.state}>
              <input
                name="state"
                autoComplete="address-level1"
                placeholder="Enter your state"
                value={form.values.state}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field label="Country" error={form.errors.country}>
              <input
                name="country"
                autoComplete="country-name"
                placeholder="Enter your country"
                value={form.values.country}
                onChange={form.onChange}
                required
              />
            </Field>

            <Field label="PIN code" error={form.errors.pinCode}>
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

          <div className="volunteer-location">
            <button
              type="button"
              className="btn ghost location-button"
              onClick={handleLocation}
              disabled={form.busy}
            >
              Use my current location
            </button>

            <p className="registration-help">
              Location sharing is optional.
            </p>

            <div className="grid two registration-grid">
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
          </div>
        </section>

        <section className="registration-section">
          <h2>Education and Skills</h2>
          <p className="registration-section-description">
            Share your qualifications and the skills you can contribute.
          </p>

          <Field
            label="Educational qualification"
            error={form.errors.educationalQualification}
          >
            <input
              name="educationalQualification"
              placeholder="e.g. Higher Secondary, Diploma, Degree"
              value={form.values.educationalQualification}
              onChange={form.onChange}
              required
            />
          </Field>

          <Field label="Skills" error={form.errors.skills}>
            <textarea
              name="skills"
              rows={4}
              placeholder="Describe your skills and areas of experience"
              value={form.values.skills}
              onChange={form.onChange}
              required
            />
          </Field>
        </section>

        <section className="registration-section">
          <h2>Certificates</h2>
          <p className="registration-section-description">
            Upload your 10th-standard, qualification, and any required driving-licence documents from your volunteer dashboard after registration. Accepted formats: PDF, JPG, and PNG.
          </p>
        </section>

        <section className="registration-section">
          <h2>Service Preferences</h2>
          <p className="registration-section-description">
            Tell us how much experience you have and how far you can travel.
          </p>

          <div className="grid two registration-grid">
            <Field
              label="Experience (years)"
              error={form.errors.experienceYears}
            >
              <input
                name="experienceYears"
                type="number"
                min="0"
                value={form.values.experienceYears}
                onChange={form.onChange}
              />
            </Field>

            <Field
              label="Preferred service radius (km)"
              error={form.errors.preferredServiceRadiusKm}
            >
              <input
                name="preferredServiceRadiusKm"
                type="number"
                min="1"
                value={form.values.preferredServiceRadiusKm}
                onChange={form.onChange}
              />
            </Field>
          </div>
        </section>

        <section className="registration-section">
          <h2>Assistance Types</h2>
          <p className="registration-section-description">
            Select the types of assistance you can offer.
          </p>

          <Field
            label="Assistance types you can provide"
            error={form.errors.assistanceTypes}
          >
            {types.length > 0 ? (
              <div className="assistance-options">
                {types.map((type) => (
                  <label
                    className="assistance-option"
                    key={type.code}
                  >
                    <input
                      type="checkbox"
                      checked={form.values.assistanceTypes.includes(
                        type.code
                      )}
                      onChange={() => toggleType(type.code)}
                    />

                    <span>{type.displayName}</span>
                  </label>
                ))}
              </div>
            ) : (
              <p className="registration-help">
                {typesLoading ? "Loading assistance types..." : "Assistance types are unavailable."}
              </p>
            )}
          </Field>
          {typesError ? (
            <div>
              <Alert type="error">{typesError}</Alert>
              <button type="button" className="btn ghost" onClick={loadTypes} disabled={typesLoading}>
                {typesLoading ? "Loading..." : "Retry loading assistance types"}
              </button>
            </div>
          ) : null}
        </section>

        <section className="registration-section">
          <h2>Driving Assistance</h2>
          <p className="registration-section-description">
            Let us know if you can provide driving-related help.
          </p>

          <label className="registration-checkbox">
            <input
              type="checkbox"
              name="offersDriving"
              checked={form.values.offersDriving}
              onChange={form.onChange}
            />

            <span>I can offer driving-related help</span>
          </label>

          {form.values.offersDriving && (
            <div className="grid two registration-grid driving-fields">
              <Field
                label="Driving licence number"
                error={form.errors.drivingLicenceNumber}
              >
                <input
                  name="drivingLicenceNumber"
                  placeholder="Enter licence number"
                  value={form.values.drivingLicenceNumber}
                  onChange={form.onChange}
                  required
                />
              </Field>

              <Field
                label="Licence expiry date"
                error={form.errors.drivingLicenceExpiry}
              >
                <input
                  name="drivingLicenceExpiry"
                  type="date"
                  value={form.values.drivingLicenceExpiry}
                  onChange={form.onChange}
                  required
                />
              </Field>
            </div>
          )}
        </section>

        <div className="registration-actions">
          <button
            className="btn registration-submit"
            disabled={form.busy || typesLoading || types.length === 0}
            type="submit"
          >
            {form.busy
              ? "Creating account..."
              : "Create volunteer account"}
          </button>

          <p className="registration-login">
            Already registered? <Link to="/login">Log in here</Link>
          </p>
        </div>
      </form>
    </section>
  );
}
