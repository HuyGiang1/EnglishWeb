"use client";

import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import { authApi, User, LoginPayload, RegisterPayload } from "@/lib/api/auth";
import { useLocale } from "@/lib/i18n/locale-provider";
import { ApiError } from "@/lib/api/client";

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  error: string | null;
  login: (payload: LoginPayload) => Promise<User>;
  register: (payload: RegisterPayload) => Promise<User>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<User | null>;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();
  const { locale, synchronizeAccount } = useLocale();
  const authVersion = useRef(0);
  const invalidateAuthRequests = useCallback(() => { authVersion.current++; }, []);

  const refreshSession = useCallback(async (): Promise<User | null> => {
    const version = ++authVersion.current;
    try {
      const currentUser = await authApi.getMe();
      if (version !== authVersion.current) return null;
      synchronizeAccount(currentUser);
      setUser(currentUser);
      setError(null);
      return currentUser;
    } catch {
      if (version !== authVersion.current) return null;
      synchronizeAccount(null);
      setUser(null);
      return null;
    } finally {
      if (version === authVersion.current) setIsLoading(false);
    }
  }, [synchronizeAccount]);

  useEffect(() => {
    refreshSession();
    return invalidateAuthRequests;
  }, [refreshSession, invalidateAuthRequests]);

  const login = useCallback(async (payload: LoginPayload): Promise<User> => {
    const version = ++authVersion.current;
    setIsLoading(true);
    setError(null);
    try {
      const loggedInUser = await authApi.login(payload);
      if (version === authVersion.current) {
        synchronizeAccount(loggedInUser);
        setUser(loggedInUser);
      }
      return loggedInUser;
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Failed to log in.";
      if (version === authVersion.current) setError(message);
      throw err;
    } finally {
      if (version === authVersion.current) setIsLoading(false);
    }
  }, [synchronizeAccount]);

  const register = useCallback(async (payload: RegisterPayload): Promise<User> => {
    const version = ++authVersion.current;
    setIsLoading(true);
    setError(null);
    try {
      const registeredUser = await authApi.register({ ...payload, preferredLocale: locale });
      if (version === authVersion.current) {
        synchronizeAccount(registeredUser);
        setUser(registeredUser);
      }
      return registeredUser;
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Failed to create account.";
      if (version === authVersion.current) setError(message);
      throw err;
    } finally {
      if (version === authVersion.current) setIsLoading(false);
    }
  }, [locale, synchronizeAccount]);

  const logout = useCallback(async (): Promise<void> => {
    const version = ++authVersion.current;
    synchronizeAccount(null);
    setIsLoading(true);
    try {
      await authApi.logout();
    } catch {
      // Ignore logout failure, proceed to clear local user state
    } finally {
      if (version !== authVersion.current) return;
      setUser(null);
      setError(null);
      setIsLoading(false);
      router.push("/login");
    }
  }, [router, synchronizeAccount]);

  const clearError = useCallback(() => setError(null), []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        error,
        login,
        register,
        logout,
        refreshSession,
        clearError,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
