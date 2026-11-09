"use client";

import { useLocale } from "@/lib/i18n/locale-provider";
import { resolveLocale } from "@/lib/i18n/config";

export function LanguageSwitcher() {
  const { locale, setLocale, isSaving, saveFailed, t } = useLocale();
  return (
    <div className="space-y-1">
      <label className="flex flex-wrap items-center justify-end gap-2 text-xs text-muted-foreground">
        <span>{t("language")}</span>
        <select value={locale} onChange={(event) => setLocale(resolveLocale(event.target.value))}
          className="min-h-9 rounded-md border border-border bg-background px-2 text-foreground focus-visible:outline-2 focus-visible:outline-offset-2">
          <option value="vi-VN" lang="vi-VN">Tiếng Việt</option>
          <option value="en" lang="en">English</option>
        </select>
      </label>
      {isSaving && <p role="status" className="text-xs text-muted-foreground">{t("localeSaving")}</p>}
      {saveFailed && <p role="alert" className="text-xs text-destructive">{t("localeSaveFailed")}</p>}
    </div>
  );
}
