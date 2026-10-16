const TOKEN_KEY = "vayomitra.token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

async function parseBody(response) {
  const text = await response.text();
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch {
    throw new Error("The server returned an unexpected response.");
  }
}

export async function api(path, { method = "GET", body, headers = {}, isForm = false } = {}) {
  const token = getToken();
  const requestHeaders = { ...headers };
  if (token) {
    requestHeaders.Authorization = `Bearer ${token}`;
  }
  if (!isForm && body !== undefined) {
    requestHeaders["Content-Type"] = "application/json";
  }
  const response = await fetch(path, {
    method,
    headers: requestHeaders,
    body: isForm ? body : body === undefined ? undefined : JSON.stringify(body)
  });
  const payload = await parseBody(response);
  if (!response.ok || payload.ok === false) {
    const error = new Error(payload?.error?.message || "Request failed.");
    error.status = response.status;
    error.fields = payload?.error?.fields || {};
    error.code = payload?.error?.code;
    throw error;
  }
  return payload.data;
}

export const AssistanceApi = {
  types: () => api("/api/assistance-types"),
  health: () => api("/api/health")
};
