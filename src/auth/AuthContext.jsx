import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import { api, getToken, setToken } from "../api/client.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  async function refresh({ throwOnError = false } = {}) {
    if (!getToken()) {
      setUser(null);
      setLoading(false);
      return;
    }
    try {
      const me = await api("/api/auth/me");
      setUser(me);
    } catch (error) {
      setToken(null);
      setUser(null);
      if (throwOnError) {
        throw error;
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      async login(email, password, role) {
        const data = await api("/api/auth/login", {
          method: "POST",
          body: { email, password, role }
        });
        setToken(data.token);
        try {
          await refresh({ throwOnError: true });
        } catch (error) {
          setToken(null);
          setUser(null);
          throw error;
        }
        return data;
      },
      async logout() {
        try {
          await api("/api/auth/logout", { method: "POST" });
        } catch {
          /* still clear local state */
        }
        setToken(null);
        setUser(null);
      },
      refresh
    }),
    [user, loading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
