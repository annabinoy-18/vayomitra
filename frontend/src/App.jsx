import React from "react";
import { Link, Navigate, Route, Routes, useNavigate } from "react-router-dom";
import { useAuth } from "./auth/AuthContext.jsx";
import HomePage from "./pages/HomePage.jsx";
import LoginPage from "./pages/LoginPage.jsx";
import ElderRegisterPage from "./pages/ElderRegisterPage.jsx";
import VolunteerRegisterPage from "./pages/VolunteerRegisterPage.jsx";
import ElderDashboard from "./pages/ElderDashboard.jsx";
import NewRequestPage from "./pages/NewRequestPage.jsx";
import RequestDetailPage from "./pages/RequestDetailPage.jsx";
import VolunteerDashboard from "./pages/VolunteerDashboard.jsx";
import AdminPage from "./pages/AdminPage.jsx";

function Guard({ roles, children }) {
  const { user, loading } = useAuth();
  if (loading) {
    return <section className="card">Loading your session...</section>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }
  return children;
}

export default function App() {
  const auth = useAuth();
  const navigate = useNavigate();

  async function logout() {
    await auth.logout();
    navigate("/");
  }

  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="topbar">
        <div className="shell topbar-inner">
          <Link className="brand" to="/">Vayomitra</Link>
          <nav className="nav" aria-label="Main">
            <Link to="/">Home</Link>
            {auth.user?.role === "ELDER" ? <Link to="/elder">Elder desk</Link> : null}
            {auth.user?.role === "VOLUNTEER" ? <Link to="/volunteer">Volunteer desk</Link> : null}
            {auth.user?.role === "ADMIN" ? <Link to="/admin">Admin</Link> : null}
            {auth.user ? (
              <button className="link" onClick={logout}>Log out</button>
            ) : (
              <>
                <Link to="/login">Log in</Link>
                <Link to="/register/elder">Register</Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main id="main" className="shell">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register/elder" element={<ElderRegisterPage />} />
          <Route path="/register/volunteer" element={<VolunteerRegisterPage />} />
          <Route path="/elder" element={<Guard roles={["ELDER"]}><ElderDashboard /></Guard>} />
          <Route path="/elder/requests/new" element={<Guard roles={["ELDER"]}><NewRequestPage /></Guard>} />
          <Route path="/elder/requests/:id" element={<Guard roles={["ELDER"]}><RequestDetailPage /></Guard>} />
          <Route path="/volunteer" element={<Guard roles={["VOLUNTEER"]}><VolunteerDashboard /></Guard>} />
          <Route path="/volunteer/requests/:id" element={<Guard roles={["VOLUNTEER"]}><RequestDetailPage /></Guard>} />
          <Route path="/admin" element={<Guard roles={["ADMIN"]}><AdminPage /></Guard>} />
        </Routes>
        <footer>
          Vayomitra keeps passwords hashed, documents off the public frontend folder, and matching rules in the Java API.
        </footer>
      </main>
    </>
  );
}
