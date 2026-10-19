import React from "react";

export function Field({ label, error, children, hint }) {
  const id = React.useId();
  const isControl = React.isValidElement(children)
    && ["input", "select", "textarea"].includes(children.type);
  const control = isControl
    ? React.cloneElement(children, {
        id: children.props.id || id,
        "aria-invalid": error ? true : undefined,
        "aria-describedby": error ? `${id}-error` : undefined
      })
    : children;

  return (
    <div className="field">
      <label htmlFor={isControl ? id : undefined}>{label}</label>
      {control}
      {hint ? <div className="muted">{hint}</div> : null}
      {error ? <div className="field-error" id={`${id}-error`}>{error}</div> : null}
    </div>
  );
}

export function Alert({ type = "info", children }) {
  if (!children) {
    return null;
  }
  return <div className={`alert ${type}`} role={type === "error" ? "alert" : "status"}>{children}</div>;
}

export function useForm(initial) {
  const [values, setValues] = React.useState(initial);
  const [errors, setErrors] = React.useState({});
  const [message, setMessage] = React.useState("");
  const [error, setError] = React.useState("");
  const [busy, setBusy] = React.useState(false);

  function onChange(event) {
    const { name, value, type, checked } = event.target;
    setValues((current) => ({ ...current, [name]: type === "checkbox" ? checked : value }));
    setErrors((current) => {
      if (!current[name]) return current;
      const next = { ...current };
      delete next[name];
      return next;
    });
  }

  return { values, setValues, errors, setErrors, message, setMessage, error, setError, busy, setBusy, onChange };
}

export async function fillLocation(setValues) {
  if (!navigator.geolocation) {
    throw new Error("This browser does not support location sharing.");
  }
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setValues((current) => ({
          ...current,
          latitude: position.coords.latitude.toFixed(6),
          longitude: position.coords.longitude.toFixed(6)
        }));
        resolve();
      },
      () => reject(new Error("Location permission was denied. You can continue with your address instead.")),
      { enableHighAccuracy: true, timeout: 10000 }
    );
  });
}

export const GENDERS = [
  { value: "FEMALE", label: "Female" },
  { value: "MALE", label: "Male" },
  { value: "OTHER", label: "Other" },
  { value: "PREFER_NOT_TO_SAY", label: "Prefer not to say" }
];
