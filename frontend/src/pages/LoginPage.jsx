import React from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import { Alert, Field, useForm } from "../components/FormBits.jsx";

export default function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const form = useForm({ email: "", password: "", role: "ELDER" });

  async function onSubmit(event) {
    event.preventDefault();
    form.setBusy(true);
    form.setError("");
    try {
      const data = await auth.login(form.values.email, form.values.password, form.values.role);
      if (data.role === "ADMIN") {
        navigate("/admin");
      } else if (data.role === "VOLUNTEER") {
        navigate("/volunteer");
      } else {
        navigate("/elder");
      }
    } catch (error) {
      form.setError(error.message);
    } finally {
      form.setBusy(false);
    }
  }

  return (
    <section className="card">
      <h1>Log in</h1>
      <Alert type="error">{form.error}</Alert>
      <form onSubmit={onSubmit}>
        <Field label="I am logging in as">
          <select name="role" value={form.values.role} onChange={form.onChange}>
            <option value="ELDER">Elder</option>
            <option value="VOLUNTEER">Volunteer</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </Field>
        <Field label="Email address">
          <input name="email" type="email" autoComplete="username" value={form.values.email} onChange={form.onChange} required />
        </Field>
        <Field label="Password">
          <input name="password" type="password" autoComplete="current-password" value={form.values.password} onChange={form.onChange} required />
        </Field>
        <div className="actions">
          <button className="btn" disabled={form.busy} type="submit">{form.busy ? "Please wait..." : "Log in"}</button>
        </div>
      </form>
      <p className="muted">New here? <Link to="/register/elder">Elder registration</Link> or <Link to="/register/volunteer">Volunteer registration</Link>.</p>
    </section>
  );
}
