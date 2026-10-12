"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  BookOpen,
  FolderKanban,
  Headphones,
  Mic,
  HelpCircle,
  Settings,
  Sparkles,
} from "lucide-react";
import { cn } from "@/lib/utils";

export interface NavItem {
  title: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  badge?: string;
  isUpcoming?: boolean;
}

export const navWorkspaceItems: NavItem[] = [
  {
    title: "Dashboard",
    href: "/dashboard",
    icon: LayoutDashboard,
  },
  {
    title: "Vocabulary",
    href: "/vocabulary",
    icon: BookOpen,
    badge: "M1",
  },
  {
    title: "Collections",
    href: "/collections",
    icon: FolderKanban,
    badge: "M2",
    isUpcoming: true,
  },
];

export const navStudyItems: NavItem[] = [
  {
    title: "SRS Review",
    href: "/review",
    icon: Sparkles,
    badge: "Focus",
  },
  {
    title: "Dictation",
    href: "/dictation",
    icon: Headphones,
    badge: "M2",
    isUpcoming: true,
  },
  {
    title: "Shadowing",
    href: "/shadowing",
    icon: Mic,
    badge: "M2",
    isUpcoming: true,
  },
  {
    title: "Quiz",
    href: "/quiz",
    icon: HelpCircle,
    badge: "M2",
    isUpcoming: true,
  },
];

export const navPreferenceItems: NavItem[] = [
  {
    title: "Settings",
    href: "/settings",
    icon: Settings,
    badge: "M2",
    isUpcoming: true,
  },
];

interface SidebarContentProps {
  onNavClick?: () => void;
}

export function SidebarContent({ onNavClick }: SidebarContentProps) {
  const pathname = usePathname();

  const renderNavSection = (title: string, items: NavItem[]) => (
    <div className="space-y-1">
      <p className="px-3 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
        {title}
      </p>
      <nav className="space-y-0.5">
        {items.map((item) => {
          const isActive = pathname === item.href;
          const Icon = item.icon;

          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavClick}
              className={cn(
                "group flex items-center justify-between rounded-md px-3 py-2 text-sm font-medium transition-colors outline-none",
                "focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1",
                isActive
                  ? "bg-foreground text-background font-semibold"
                  : "text-foreground/80 hover:bg-muted hover:text-foreground"
              )}
            >
              <div className="flex items-center gap-3">
                <Icon
                  className={cn(
                    "size-4 shrink-0 transition-colors",
                    isActive
                      ? "text-background"
                      : "text-muted-foreground group-hover:text-foreground"
                  )}
                />
                <span>{item.title}</span>
              </div>
              {item.badge && (
                <span
                  className={cn(
                    "rounded px-1.5 py-0.5 text-[10px] font-mono tracking-tight",
                    isActive
                      ? "bg-background/20 text-background"
                      : "bg-muted text-muted-foreground border border-border"
                  )}
                >
                  {item.badge}
                </span>
              )}
            </Link>
          );
        })}
      </nav>
    </div>
  );

  return (
    <div className="flex h-full flex-col justify-between p-4">
      <div className="space-y-6">
        {/* App Branding */}
        <div className="flex items-center gap-2.5 px-3 py-1">
          <div className="flex size-7 items-center justify-center rounded-md bg-foreground text-background font-bold text-xs tracking-tighter">
            EF
          </div>
          <div className="flex flex-col">
            <span className="font-semibold text-sm tracking-tight text-foreground">
              EnglishFlow
            </span>
            <span className="text-[10px] font-mono text-muted-foreground">
              v0.1.0 · M1 Slice
            </span>
          </div>
        </div>

        {/* Navigation Sections */}
        <div className="space-y-5">
          {renderNavSection("Workspace", navWorkspaceItems)}
          {renderNavSection("Study Modules", navStudyItems)}
          {renderNavSection("Preferences", navPreferenceItems)}
        </div>
      </div>

      {/* Footer System Status */}
      <div className="rounded-md border border-border bg-card p-3 text-xs">
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground">Backend Target</span>
          <span className="font-mono text-[11px] text-foreground font-medium">
            :8080 (Proxy)
          </span>
        </div>
        <div className="mt-1 flex items-center justify-between">
          <span className="text-muted-foreground">Auth Model</span>
          <span className="font-mono text-[11px] text-foreground font-medium">
            Session Cookie
          </span>
        </div>
      </div>
    </div>
  );
}

export function Sidebar() {
  return (
    <aside className="hidden lg:flex w-60 shrink-0 flex-col border-r border-border bg-sidebar h-screen sticky top-0">
      <SidebarContent />
    </aside>
  );
}
