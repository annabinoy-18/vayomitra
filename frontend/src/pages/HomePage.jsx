import React from "react";
import { Link } from "react-router-dom";
import "../styles.css";

export default function HomePage() {
  return (
    <main className="home-page">
      <nav className="home-navbar">
        <Link to="/" className="home-logo">
          Vayomitra
        </Link>

        <div className="home-nav-links">
          <a href="#home">Home</a>
          <a href="#about">About</a>
          <a href="#services">Services</a>
          <a href="#contact">Contact</a>
        </div>

        <div className="home-nav-actions">
          <Link to="/login" className="login-link">
            Log in
          </Link>
          <Link to="/register/volunteer" className="register-button">
            Join as Volunteer
          </Link>
        </div>
      </nav>

      <section className="home-hero" id="home">
        <div className="hero-content">
          <p className="hero-tagline">
            <span>♥</span> CARE THAT CONNECTS GENERATIONS
          </p>

          <h1>
            A little care.
            <br />
            <span>A lot of love.</span>
          </h1>

          <p className="hero-description">
            Vayomitra brings elders and caring volunteers together,
            creating meaningful connections and making everyday life
            a little easier, safer, and happier.
          </p>

          <div className="hero-buttons">
            <Link to="/register/elder" className="primary-button">
              Get Assistance
            </Link>

            <Link
              to="/register/volunteer"
              className="secondary-button"
            >
              Become a Volunteer
            </Link>
          </div>

          <div className="hero-note">
            <span className="note-heart">♥</span>
            Every act of kindness makes a difference.
          </div>
        </div>

        <div className="hero-image-container">
          <div className="hero-image-decoration"></div>

          <img
            src="/love-care.png"
            alt="An illustration representing love, care, and support"
            className="hero-image"
          />

          <div className="floating-card">
            <span className="floating-heart">♥</span>
            <div>
              <strong>Care with compassion</strong>
              <p>Building a kinder community</p>
            </div>
          </div>
        </div>
      </section>

      <section className="home-about" id="about">
        <p className="section-label">ABOUT VAYOMITRA</p>
        <h2>Because everyone deserves a helping hand.</h2>
        <p>
          We believe that kindness, companionship, and a supportive
          community can make a meaningful difference in the lives of
          older adults. Vayomitra helps connect people who need
          assistance with volunteers who are ready to help.
        </p>
      </section>

      <section className="home-services" id="services">
        <p className="section-label">HOW WE HELP</p>
        <h2>Small acts. Meaningful connections.</h2>

        <div className="service-cards">
          <article className="service-card">
            <div className="service-icon">♥</div>
            <h3>Companionship</h3>
            <p>
              Friendly connections and caring company to make
              everyday life feel less lonely.
            </p>
          </article>

          <article className="service-card">
            <div className="service-icon">✿</div>
            <h3>Everyday Assistance</h3>
            <p>
              Connect with volunteers for everyday support and
              practical help.
            </p>
          </article>

          <article className="service-card">
            <div className="service-icon">❀</div>
            <h3>A Caring Community</h3>
            <p>
              Bring people together through empathy, kindness,
              and a shared desire to help.
            </p>
          </article>
        </div>
      </section>

      <section className="home-contact" id="contact">
        <p className="section-label">BE PART OF SOMETHING GOOD</p>
        <h2>Let’s make care a little more personal.</h2>
        <p>
          Whether you need a helping hand or want to offer one,
          there is a place for you in the Vayomitra community.
        </p>

        <div className="hero-buttons">
          <Link to="/register/elder" className="primary-button">
            Get Started
          </Link>
          <Link to="/register/volunteer" className="secondary-button">
            Join Our Community
          </Link>
        </div>
      </section>

      <footer className="home-footer">
        <p>♥ Vayomitra — Care, connection, and compassion.</p>
      </footer>
    </main>
  );
}