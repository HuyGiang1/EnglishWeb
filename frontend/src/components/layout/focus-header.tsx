"use client";

import { LanguageSwitcher } from "@/components/auth/language-switcher";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { useLocale } from "@/lib/i18n/locale-provider";

export function FocusHeader() {
  const { t } = useLocale();
  return <header className="sticky top-0 z-30 flex min-h-14 flex-wrap items-center justify-between gap-2 border-b border-border bg-background px-4 py-2 sm:px-8">
    <Link href="/dashboard" className={buttonVariants({ variant: "ghost", size: "sm" })}>
      <ArrowLeft className="size-4" aria-hidden="true" />{t("exitWorkspace")}
    </Link>
    <div className="flex flex-wrap items-center gap-3"><span className="text-xs text-muted-foreground">{t("focusMode")}</span><LanguageSwitcher /></div>
  </header>;
}
