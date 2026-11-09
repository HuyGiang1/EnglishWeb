"use client";
import { useLocale } from "@/lib/i18n/locale-provider";
export function SkipContentLink() {
  const { t } = useLocale();
  return <a href="#main-content" className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded focus:bg-background focus:p-3 focus:ring-2">{t("skipContent")}</a>;
}
