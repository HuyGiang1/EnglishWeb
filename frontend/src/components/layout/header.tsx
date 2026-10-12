"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu, Command } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { SidebarContent } from "./sidebar";

const routeTitles: Record<string, string> = {
  "/dashboard": "Workspace Overview",
  "/vocabulary": "Vocabulary Library",
  "/collections": "Vocabulary Collections",
  "/dictation": "Listening Dictation",
  "/shadowing": "Shadowing Practice",
  "/quiz": "Diagnostic Quiz",
  "/settings": "User Preferences",
  "/review": "Spaced Repetition Review",
};

export function Header() {
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);

  const currentTitle = routeTitles[pathname] || "EnglishFlow";

  return (
    <header className="sticky top-0 z-30 flex h-14 w-full items-center justify-between border-b border-border bg-background/95 px-4 backdrop-blur-sm sm:px-6">
      {/* Left: Mobile Menu Trigger + Current Context */}
      <div className="flex items-center gap-3">
        <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
          <SheetTrigger
            render={
              <Button
                variant="outline"
                size="icon"
                className="lg:hidden size-8"
                aria-label="Open navigation menu"
              >
                <Menu className="size-4" />
              </Button>
            }
          />
          <SheetContent side="left" className="w-64 p-0 bg-sidebar border-r border-border">
            <SheetHeader className="sr-only">
              <SheetTitle>Navigation Menu</SheetTitle>
            </SheetHeader>
            <SidebarContent onNavClick={() => setMobileOpen(false)} />
          </SheetContent>
        </Sheet>

        <div className="flex items-center gap-2">
          <Link
            href="/dashboard"
            className="text-xs font-mono text-muted-foreground hover:text-foreground transition-colors"
          >
            EnglishFlow
          </Link>
          <span className="text-muted-foreground text-xs">/</span>
          <h1 className="text-sm font-semibold text-foreground tracking-tight">
            {currentTitle}
          </h1>
        </div>
      </div>

      {/* Right: Monochrome Status Badges & Focus Mode Link */}
      <div className="flex items-center gap-2.5">
        <div className="hidden sm:flex items-center gap-1.5 rounded border border-border bg-muted/60 px-2 py-1 text-[11px] font-mono text-muted-foreground">
          <Command className="size-3" />
          <span>K</span>
          <span className="text-[10px] text-muted-foreground/70">Palette</span>
        </div>

        <Link href="/review">
          <Button
            variant="outline"
            size="sm"
            className="text-xs font-medium border-border hover:bg-muted"
          >
            Focus Review
          </Button>
        </Link>
      </div>
    </header>
  );
}
