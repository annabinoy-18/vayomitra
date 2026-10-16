import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AssistanceApi } from "../api/client.js";

export default function HomePage() {
  const [types, setTypes] = useState([]);

  useEffect(() => {
    AssistanceApi.types().then(setTypes).catch(() => setTypes([]));
  }, []);

  return (
    <>
      <section className="hero">
        <p className="badge">Human Values and Professional Ethics project</p>
        <h1>Vayomitra is a neighbourly help desk for elders.</h1>
        <p className="muted">
          Elders can request practical help. Volunteers can offer time, skills, and companionship.
          Matching happens on the Java backend using skills, distance, availability, and verified qualifications.
        </p>
        <div className="actions">
          <Link className="btn" to="/register/elder">I need assistance</Link>
          <Link className="btn secondary" to="/register/volunteer">I want to volunteer</Link>
          <Link className="btn ghost" to="/login">Log in</Link>
        </div>
      </section>
      <section className="card">
        <h2>Nine kinds of help</h2>
        <div className="grid three">
          {types.map((type) => (
            <article className="type-card" key={type.code}>
              <h3>{type.displayName}</h3>
              <p className="muted">{type.description}</p>
            </article>
          ))}
        </div>
      </section>
    </>
  );
}
