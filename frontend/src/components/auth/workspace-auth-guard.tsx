"use client";

import React, { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { useAuth } from "@/lib/auth/auth-context";

export function WorkspaceAuthGuard({ children }: { children: React.ReactNode }) {
  const { user, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !user) {
      router.replace("/login");
    }
  }, [user, isLoading, router]);

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3">
          <div className="flex size-8 items-center justify-center rounded-lg bg-foreground text-background font-bold text-xs tracking-tighter">
            EF
          </div>
          <div className="flex items-center gap-2 text-xs font-mono text-muted-foreground">
            <Loader2 className="size-3.5 animate-spin" />
            <span>Verifying session...</span>
          </div>
        </div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  return <>{children}</>;
}
