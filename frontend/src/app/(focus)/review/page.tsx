import Link from "next/link";
import { ArrowLeft, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export default function ReviewFocusPage() {
  return (
    <div className="w-full flex flex-col items-center gap-6">
      {/* Session Progress Header */}
      <div className="w-full flex items-center justify-between text-xs text-muted-foreground font-mono">
        <span>Focus Mode Verification Preview</span>
        <span>Milestone M1</span>
      </div>

      <div className="w-full bg-muted rounded-full h-1 overflow-hidden">
        <div className="bg-foreground h-full w-1/3" />
      </div>

      {/* Central Study Card Canvas */}
      <Card className="w-full p-8 sm:p-12 border-border bg-card text-center flex flex-col items-center justify-center min-h-[320px]">
        <div className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted/60 px-2.5 py-0.5 text-[11px] font-mono text-muted-foreground mb-4">
          <Sparkles className="size-3 text-foreground" />
          <span>Flashcard Preview</span>
        </div>

        <h2 className="text-3xl sm:text-4xl font-bold tracking-tight text-foreground">
          resilience
        </h2>

        <p className="mt-2 text-sm font-mono text-muted-foreground">
          /rɪˈzɪl.jəns/
        </p>

        <p className="mt-4 text-sm text-foreground/80 max-w-md leading-relaxed">
          The capacity to recover quickly from difficulties; toughness.
        </p>

        <div className="mt-8 pt-6 border-t border-border w-full flex flex-col sm:flex-row items-center justify-center gap-2 text-xs text-muted-foreground">
          <span className="font-mono text-[11px]">Keyboard Shortcuts:</span>
          <div className="flex items-center gap-2 font-mono text-[10px]">
            <kbd className="rounded border border-border bg-muted px-1.5 py-0.5">Space</kbd> Flip
            <kbd className="rounded border border-border bg-muted px-1.5 py-0.5">1</kbd> Again
            <kbd className="rounded border border-border bg-muted px-1.5 py-0.5">2</kbd> Hard
            <kbd className="rounded border border-border bg-muted px-1.5 py-0.5">3</kbd> Good
            <kbd className="rounded border border-border bg-muted px-1.5 py-0.5">4</kbd> Easy
          </div>
        </div>
      </Card>

      {/* Return to Workspace Action */}
      <div className="flex items-center gap-3">
        <Link href="/dashboard">
          <Button variant="outline" size="sm" className="gap-2 text-xs">
            <ArrowLeft className="size-3.5" />
            <span>Return to Workspace Dashboard</span>
          </Button>
        </Link>
      </div>
    </div>
  );
}
