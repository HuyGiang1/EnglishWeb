"use client";

import { useLocale } from "@/lib/i18n/locale-provider";

export function AuthCopy({ kind }: { kind: "tagline" | "privacy" }) {
  const { t } = useLocale();
  return <>{t(kind)}</>;
}
