"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutDashboard, BookOpen, FolderKanban, Headphones, Mic, HelpCircle, Settings, Sparkles, LogOut } from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/lib/auth/auth-context";
import { useLocale } from "@/lib/i18n/locale-provider";
import type { TranslationKey } from "@/lib/i18n/dictionaries";

export interface NavItem {
  titleKey: TranslationKey;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
}
export const navWorkspaceItems: NavItem[] = [
  { titleKey: "navDashboard", href: "/dashboard", icon: LayoutDashboard },
  { titleKey: "navVocabulary", href: "/vocabulary", icon: BookOpen },
  { titleKey: "navCollections", href: "/collections", icon: FolderKanban },
];
export const navStudyItems: NavItem[] = [
  { titleKey: "navReview", href: "/review", icon: Sparkles },
  { titleKey: "navDictation", href: "/dictation", icon: Headphones },
  { titleKey: "navShadowing", href: "/shadowing", icon: Mic },
  { titleKey: "navQuiz", href: "/quiz", icon: HelpCircle },
];
export const navPreferenceItems: NavItem[] = [
  { titleKey: "navSettings", href: "/settings", icon: Settings },
];

export function SidebarContent({ onNavClick }: { onNavClick?: () => void }) {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const { t } = useLocale();
  const section = (title: TranslationKey, items: NavItem[]) => (
    <div className="space-y-1">
      <p className="px-3 text-xs font-semibold text-muted-foreground">{t(title)}</p>
      <nav aria-label={t(title)} className="space-y-0.5">
        {items.map(({ href, titleKey, icon: Icon }) => {
          const active = pathname === href || pathname.startsWith(`${href}/`);
          return <Link key={href} href={href} onClick={onNavClick} aria-current={active ? "page" : undefined}
            className={cn("flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1",
              active ? "bg-foreground text-background" : "text-foreground/80 hover:bg-muted")}>
            <Icon className="size-4 shrink-0" aria-hidden="true" /><span>{t(titleKey)}</span>
          </Link>;
        })}
      </nav>
    </div>
  );
  return (
    <div className="flex min-h-full flex-col justify-between gap-6 p-4">
      <div className="space-y-6">
        <div className="flex items-center gap-2.5 px-3 py-1">
          <span className="flex size-7 items-center justify-center rounded-md bg-foreground text-background font-bold text-xs" aria-hidden="true">EF</span>
          <span className="font-semibold text-sm">EnglishFlow</span>
        </div>
        <div className="space-y-5">{section("navWorkspace", navWorkspaceItems)}{section("navStudy", navStudyItems)}{section("navPreferences", navPreferenceItems)}</div>
      </div>
      {user && <div className="flex items-center justify-between gap-2 border-t border-border pt-4">
        <div className="min-w-0"><p className="truncate text-xs font-semibold">{user.displayName}</p><p className="truncate text-xs text-muted-foreground">{user.email}</p></div>
        <button type="button" onClick={() => logout()} title={t("signOut")} aria-label={t("signOut")}
          className="flex size-9 shrink-0 items-center justify-center rounded-md hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring">
          <LogOut className="size-4" aria-hidden="true" />
        </button>
      </div>}
    </div>
  );
}
export function Sidebar() {
  return <aside className="hidden lg:block w-60 shrink-0 border-r border-border bg-sidebar h-screen sticky top-0 overflow-y-auto"><SidebarContent /></aside>;
}
