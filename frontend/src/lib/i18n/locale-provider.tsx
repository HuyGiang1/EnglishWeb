"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { isLocale, localeCookie, resolveLocale, type Locale } from "./config";
import { translate, type TranslationParams, type TranslationKey } from "./dictionaries";
import { settingsApi } from "@/lib/api/settings";
import { authApi } from "@/lib/api/auth";

const LocaleContext = createContext<{
  locale: Locale;
  setLocale: (locale: Locale) => void;
  synchronizeAccount: (account: { id: string; preferredLocale?: unknown } | null) => void;
  isSaving: boolean;
  saveFailed: boolean;
  t: (key: TranslationKey, params?: TranslationParams) => string;
} | null>(null);

export function LocaleProvider({ initialLocale, children }: { initialLocale: Locale; children: ReactNode }) {
  const [locale, setActiveLocale] = useState(initialLocale);
  const [isSaving, setSaving] = useState(false);
  const [saveFailed, setSaveFailed] = useState(false);
  const current = useRef(initialLocale);
  const confirmed = useRef(initialLocale);
  const account = useRef<string | null>(null);
  const sessionVersion = useRef(0);
  const selectionVersion = useRef(0);
  const queue = useRef<Promise<void>>(Promise.resolve());

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  useEffect(() => () => { sessionVersion.current++; }, []);

  const applyLocale = useCallback((next: Locale) => {
    current.current = next;
    document.cookie = localeCookie(next, window.location.protocol === "https:");
    setActiveLocale(next);
  }, []);

  // Called only after an auth response, before redirect; bootstrap never writes preferences.
  const synchronizeAccount = useCallback((user: { id: string; preferredLocale?: unknown } | null) => {
    sessionVersion.current++;
    selectionVersion.current++;
    account.current = user?.id ?? null;
    const next = user && isLocale(user.preferredLocale) ? user.preferredLocale : current.current;
    confirmed.current = next;
    applyLocale(next);
    setSaving(false);
    setSaveFailed(false);
  }, [applyLocale]);

  function setLocale(value: Locale) {
    const next = resolveLocale(value);
    const selection = ++selectionVersion.current;
    const session = sessionVersion.current;
    applyLocale(next);
    setSaveFailed(false);
    if (!account.current) {
      confirmed.current = next;
      return;
    }
    setSaving(true);
    // Serialize writes, including rapid en→vi→en changes. Stale responses cannot
    // overwrite the latest UI, and the server receives changes in selection order.
    queue.current = queue.current.then(async () => {
      if (session !== sessionVersion.current) return;
      try {
        await authApi.ensureCsrfToken();
        if (session !== sessionVersion.current) return;
        const saved = await settingsApi.updatePreferences({ preferredLocale: next });
        if (session !== sessionVersion.current) return;
        if (saved.preferredLocale !== next) throw new Error("Locale persistence was not confirmed");
        confirmed.current = next;
        if (selection === selectionVersion.current) setSaving(false);
      } catch {
        if (session !== sessionVersion.current || selection !== selectionVersion.current) return;
        applyLocale(confirmed.current);
        setSaving(false);
        setSaveFailed(true);
      }
    });
  }

  return <LocaleContext.Provider value={{ locale, setLocale, synchronizeAccount, isSaving, saveFailed, t: (key, params) => translate(locale, key, params) }}>{children}</LocaleContext.Provider>;
}

export function useLocale() {
  const context = useContext(LocaleContext);
  if (!context) throw new Error("useLocale requires LocaleProvider");
  return context;
}
