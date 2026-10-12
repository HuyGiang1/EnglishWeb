import Link from "next/link";
import {
  Sparkles,
  Headphones,
  Mic,
  HelpCircle,
  ArrowRight,
  Info,
  Calendar,
} from "lucide-react";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";

export default function DashboardPage() {
  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground">
            Overview
          </h2>
          <p className="text-xs sm:text-sm text-muted-foreground mt-0.5">
            Daily command center for spaced repetition and language acquisition.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link href="/review">
            <Button size="sm" className="text-xs gap-1.5 h-8">
              <Sparkles className="size-3.5" />
              <span>Review Session</span>
              <ArrowRight className="size-3" />
            </Button>
          </Link>
        </div>
      </div>

      {/* Milestone Scaffolding Status Alert */}
      <div className="rounded-md border border-border bg-muted/40 p-4">
        <div className="flex items-start gap-3">
          <Info className="size-4 text-foreground shrink-0 mt-0.5" />
          <div className="text-xs text-muted-foreground leading-relaxed">
            <span className="font-semibold text-foreground">Milestone M1 Foundation: </span>
            Frontend Next.js application, monochrome design system tokens, and same-origin API proxy are active.
            Real-time SRS queues and learning event telemetry will populate as backend domain services are connected in subsequent tasks.
          </div>
        </div>
      </div>

      {/* Key Metric Overview Row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Due For Review */}
        <Card className="border-border bg-card">
          <CardHeader className="pb-2">
            <CardDescription className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Due For Review
            </CardDescription>
            <CardTitle className="text-3xl font-bold text-foreground">
              0
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <p className="text-xs text-muted-foreground">
              SRS review queue is currently empty.
            </p>
            <div className="mt-3">
              <Link href="/review">
                <Button variant="outline" size="sm" className="text-xs h-7 gap-1">
                  Launch Review <ArrowRight className="size-3" />
                </Button>
              </Link>
            </div>
          </CardContent>
        </Card>

        {/* Daily Goal */}
        <Card className="border-border bg-card">
          <CardHeader className="pb-2">
            <CardDescription className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Daily Goal
            </CardDescription>
            <CardTitle className="text-3xl font-bold text-foreground">
              0 / 20
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <div className="w-full bg-muted rounded-full h-1.5 overflow-hidden my-2">
              <div className="bg-foreground h-full w-0" />
            </div>
            <p className="text-xs text-muted-foreground">
              Goal tracking configured in Settings (M2).
            </p>
          </CardContent>
        </Card>

        {/* Study Streak */}
        <Card className="border-border bg-card">
          <CardHeader className="pb-2">
            <CardDescription className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Study Streak
            </CardDescription>
            <CardTitle className="text-3xl font-bold text-foreground">
              0 Days
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <span className="size-2 rounded-full bg-border" />
              <span>Awaiting first completed session</span>
            </div>
            <p className="text-xs text-muted-foreground/80 mt-1">
              Streak updates upon study submission.
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Learning Activity Heatmap Section */}
      <Card className="border-border bg-card p-5">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-4 gap-2 border-b border-border">
          <div>
            <h3 className="text-sm font-semibold text-foreground">
              Learning Activity Heatmap
            </h3>
            <p className="text-xs text-muted-foreground">
              GitHub-style greyscale intensity mapping across 365 rolling days.
            </p>
          </div>
          {/* Greyscale Ramp Legend */}
          <div className="flex items-center gap-1.5 text-[11px] font-mono text-muted-foreground">
            <span>Less</span>
            <span className="size-2.5 rounded-[1px] bg-[#f5f5f5] border border-border" title="Level 0: 0 actions" />
            <span className="size-2.5 rounded-[1px] bg-[#d4d4d4]" title="Level 1: 1-9 actions" />
            <span className="size-2.5 rounded-[1px] bg-[#a3a3a3]" title="Level 2: 10-19 actions" />
            <span className="size-2.5 rounded-[1px] bg-[#525252]" title="Level 3: 20-39 actions" />
            <span className="size-2.5 rounded-[1px] bg-[#171717]" title="Level 4: 40+ actions" />
            <span>More</span>
          </div>
        </div>

        {/* Placeholder Grid Representation */}
        <div className="pt-5 pb-2">
          <div className="rounded-md border border-dashed border-border bg-muted/30 p-8 text-center">
            <Calendar className="size-6 text-muted-foreground mx-auto mb-2" />
            <p className="text-xs font-medium text-foreground">
              Activity Heatmap Canvas (M2-T04)
            </p>
            <p className="text-xs text-muted-foreground mt-1 max-w-md mx-auto">
              Ready for learning event stream ingestion. Will render real user reviews, dictations, and quizzes with local timezone reconciliation.
            </p>
          </div>
        </div>
      </Card>

      {/* Quick Launch Learning Modules */}
      <div>
        <h3 className="text-sm font-semibold text-foreground mb-3">
          Learning Modalities
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <Link href="/review" className="group">
            <Card className="p-4 border-border bg-card transition-colors hover:border-foreground/50 h-full">
              <div className="flex items-center justify-between">
                <Sparkles className="size-4 text-foreground" />
                <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-mono text-foreground border border-border">
                  Vertical Slice
                </span>
              </div>
              <h4 className="mt-3 text-sm font-semibold text-foreground group-hover:underline">
                Flashcard SRS
              </h4>
              <p className="mt-1 text-xs text-muted-foreground">
                Spaced repetition card testing with SuperMemo-2 algorithm.
              </p>
            </Card>
          </Link>

          <Link href="/dictation" className="group">
            <Card className="p-4 border-border bg-card transition-colors hover:border-foreground/50 h-full">
              <div className="flex items-center justify-between">
                <Headphones className="size-4 text-muted-foreground" />
                <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-mono text-muted-foreground border border-border">
                  Milestone M2
                </span>
              </div>
              <h4 className="mt-3 text-sm font-semibold text-foreground">
                Listening Dictation
              </h4>
              <p className="mt-1 text-xs text-muted-foreground">
                Sentence audio playback and precision word transcription.
              </p>
            </Card>
          </Link>

          <Link href="/shadowing" className="group">
            <Card className="p-4 border-border bg-card transition-colors hover:border-foreground/50 h-full">
              <div className="flex items-center justify-between">
                <Mic className="size-4 text-muted-foreground" />
                <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-mono text-muted-foreground border border-border">
                  Milestone M2
                </span>
              </div>
              <h4 className="mt-3 text-sm font-semibold text-foreground">
                Shadowing Practice
              </h4>
              <p className="mt-1 text-xs text-muted-foreground">
                Native audio mimicry and user recording comparisons.
              </p>
            </Card>
          </Link>

          <Link href="/quiz" className="group">
            <Card className="p-4 border-border bg-card transition-colors hover:border-foreground/50 h-full">
              <div className="flex items-center justify-between">
                <HelpCircle className="size-4 text-muted-foreground" />
                <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-mono text-muted-foreground border border-border">
                  Milestone M2
                </span>
              </div>
              <h4 className="mt-3 text-sm font-semibold text-foreground">
                Diagnostic Quiz
              </h4>
              <p className="mt-1 text-xs text-muted-foreground">
                Multiple choice, cloze tests, and retention diagnostics.
              </p>
            </Card>
          </Link>
        </div>
      </div>
    </div>
  );
}
