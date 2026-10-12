"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";

export function FocusHeader() {
  return (
    <header className="sticky top-0 z-30 flex h-14 w-full items-center justify-between border-b border-border bg-background px-4 sm:px-8">
      {/* Left: Exit latch with Esc keyboard shortcut badge */}
      <div className="flex items-center gap-2">
        <Link href="/dashboard">
          <Button
            variant="ghost"
            size="sm"
            className="gap-2 text-xs font-medium text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="size-3.5" />
            <span>Exit to Workspace</span>
            <kbd className="hidden sm:inline-block rounded border border-border bg-muted px-1.5 py-0.5 text-[10px] font-mono text-muted-foreground">
              Esc
            </kbd>
          </Button>
        </Link>
      </div>

      {/* Center: Minimalist session badge */}
      <div className="flex items-center gap-2">
        <span className="size-1.5 rounded-full bg-foreground" />
        <span className="text-xs font-mono font-medium uppercase tracking-wider text-muted-foreground">
          Focus Mode
        </span>
      </div>

      {/* Right: Clean distraction-free session indicator */}
      <div className="flex items-center gap-2 text-xs font-mono text-muted-foreground">
        <span>Distraction-Free</span>
      </div>
    </header>
  );
}
