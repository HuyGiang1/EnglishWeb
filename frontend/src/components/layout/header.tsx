"use client";

import { LanguageSwitcher } from "@/components/auth/language-switcher";
import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { SidebarContent, navWorkspaceItems, navStudyItems, navPreferenceItems } from "./sidebar";
import { useLocale } from "@/lib/i18n/locale-provider";

export function Header() {
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);
  const { t } = useLocale();
  const route = [...navWorkspaceItems, ...navStudyItems, ...navPreferenceItems]
    .find((item) => pathname === item.href || pathname.startsWith(`${item.href}/`));
  return (
    <header className="sticky top-0 z-30 flex min-h-14 w-full flex-wrap items-center justify-between gap-2 border-b border-border bg-background/95 px-4 py-2 sm:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
          <SheetTrigger render={<Button variant="outline" size="icon" className="lg:hidden size-9" aria-label={t("openNavigation")}><Menu className="size-4" aria-hidden="true" /></Button>} />
          <SheetContent side="left" className="w-64 max-w-[calc(100vw-2rem)] overflow-y-auto p-0 bg-sidebar border-r border-border">
            <SheetHeader className="sr-only"><SheetTitle>{t("navigationMenu")}</SheetTitle></SheetHeader>
            <SidebarContent onNavClick={() => setMobileOpen(false)} />
          </SheetContent>
        </Sheet>
        <nav aria-label={t("breadcrumbs")} className="flex min-w-0 flex-wrap items-center gap-2 text-sm">
          <Link href="/dashboard" className="hidden sm:inline text-muted-foreground rounded focus-visible:ring-2">EnglishFlow</Link>
          <span className="hidden sm:inline text-muted-foreground" aria-hidden="true">/</span>
          <span aria-current="page" className="font-semibold">{route ? t(route.titleKey) : "EnglishFlow"}</span>
        </nav>
      </div>
      {(pathname.startsWith("/vocabulary") || pathname.startsWith("/collections")) && <LanguageSwitcher />}
      <Link href="/review" className={buttonVariants({ variant: "outline", size: "sm" })}>{t("navReview")}</Link>
    </header>
  );
}
