import React from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext.jsx";
import { Alert, Field, useForm } from "../components/FormBits.jsx";

export default function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const requestedRole = searchParams.get("role")?.toUpperCase();
  const initialRole = ["ELDER", "VOLUNTEER", "ADMIN"].includes(requestedRole) ? requestedRole : "ELDER";
  const form = useForm({ email: "", password: "", role: initialRole });

  async function onSubmit(event) {
    event.preventDefault();
    form.setBusy(true);
    form.setError("");
    try {
      const data = await auth.login(form.values.email, form.values.password);
      const requestedPath = location.state?.from?.pathname;
      const permittedPrefix = data.role === "ELDER" ? "/elder"
        : data.role === "VOLUNTEER" ? "/volunteer"
          : "/admin";
      navigate(requestedPath?.startsWith(permittedPrefix) ? requestedPath : permittedPrefix, { replace: true });
    } catch (error) {
      form.setError(error.message);
    } finally {
      form.setBusy(false);
    }
  }

  return (
    <section className="login-page">
      <div className="login-visual">
        <img src="/love-care.jpg" alt="A caregiver bringing a warm drink to an older woman" />
        <div className="login-visual-content">
          <span className="auth-eyebrow">CARE, CLOSE TO HOME</span>
          <h2>Small acts of care can change an ordinary day.</h2>
          <p>Welcome to a community built around kindness, dignity, and connection.</p>
        </div>
      </div>
      <div className="login-panel">
        <div className="login-panel-heading">
          <span className="registration-eyebrow">WELCOME BACK</span>
          <h1>Log in</h1>
          <p className="muted">Sign in to continue to your Vayomitra account.</p>
        </div>
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
            <button className="btn primary-btn" disabled={form.busy} type="submit">{form.busy ? "Signing in..." : "Log in"}</button>
          </div>
        </form>
        <p className="login-register">New to Vayomitra? <Link to="/register/elder">Register as an elder</Link> or <Link to="/register/volunteer">join as a volunteer</Link>.</p>
      </div>
    </section>
  );
}
