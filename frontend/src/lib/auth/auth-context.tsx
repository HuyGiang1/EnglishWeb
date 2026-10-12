"use client";

import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { authApi, User, LoginPayload, RegisterPayload } from "@/lib/api/auth";
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

  const refreshSession = useCallback(async (): Promise<User | null> => {
    try {
      const currentUser = await authApi.getMe();
      setUser(currentUser);
      setError(null);
      return currentUser;
    } catch {
      setUser(null);
      return null;
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshSession();
  }, [refreshSession]);

  const login = useCallback(async (payload: LoginPayload): Promise<User> => {
    setIsLoading(true);
    setError(null);
    try {
      const loggedInUser = await authApi.login(payload);
      setUser(loggedInUser);
      return loggedInUser;
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Failed to log in.";
      setError(message);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const register = useCallback(async (payload: RegisterPayload): Promise<User> => {
    setIsLoading(true);
    setError(null);
    try {
      const registeredUser = await authApi.register(payload);
      setUser(registeredUser);
      return registeredUser;
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Failed to create account.";
      setError(message);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    try {
      await authApi.logout();
    } catch {
      // Ignore logout failure, proceed to clear local user state
    } finally {
      setUser(null);
      setError(null);
      setIsLoading(false);
      router.push("/login");
    }
  }, [router]);

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
