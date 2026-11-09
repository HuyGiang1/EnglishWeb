export const locales = ["vi-VN", "en"] as const;
export type Locale = (typeof locales)[number];
export const defaultLocale: Locale = "vi-VN";
export const localeCookieName = "englishflow_locale";

export function isLocale(value: unknown): value is Locale {
  return value === "en" || value === "vi-VN";
}

export function resolveLocale(value?: string | null): Locale {
  return locales.find((locale) => locale === value) ?? defaultLocale;
}

export function localeCookie(locale: Locale, secure: boolean): string {
  return `${localeCookieName}=${locale}; Path=/; Max-Age=31536000; SameSite=Lax${secure ? "; Secure" : ""}`;
}
