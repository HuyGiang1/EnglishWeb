import Link from "next/link";
import { ArrowRight, Sparkles, BookOpen, Layers } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

export default function HomePage() {
  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col justify-between">
      {/* Top Navigation Bar */}
      <header className="border-b border-border px-6 py-4 flex items-center justify-between max-w-7xl mx-auto w-full">
        <div className="flex items-center gap-3">
          <div className="size-7 rounded bg-foreground text-background font-bold text-xs flex items-center justify-center">
            EF
          </div>
          <span className="font-semibold text-sm tracking-tight">EnglishFlow</span>
          <span className="rounded border border-border bg-muted px-2 py-0.5 text-[10px] font-mono text-muted-foreground">
            M1 Vertical Slice
          </span>
        </div>
        <div className="flex items-center gap-3">
          <Link href="/dashboard">
            <Button variant="outline" size="sm" className="text-xs">
              Open Workspace
            </Button>
          </Link>
          <Link href="/review">
            <Button size="sm" className="text-xs gap-1.5">
              Focus Shell <ArrowRight className="size-3" />
            </Button>
          </Link>
        </div>
      </header>

      {/* Hero Section */}
      <main className="max-w-4xl mx-auto px-6 py-16 sm:py-24 flex flex-col items-center text-center">
        <div className="inline-flex items-center gap-2 rounded-full border border-border bg-muted/60 px-3 py-1 text-xs text-muted-foreground font-mono mb-6">
          <span>Monochrome SaaS Architecture</span>
          <span>·</span>
          <span>Zero Gamification Clutter</span>
        </div>

        <h1 className="text-4xl sm:text-6xl font-bold tracking-tight text-foreground max-w-2xl">
          Serious English Learning Built for Deep Retention.
        </h1>

        <p className="mt-5 text-base sm:text-lg text-muted-foreground max-w-xl leading-relaxed">
          EnglishFlow couples spaced repetition review, active dictation, and shadowing with a distraction-free monochrome interface engineered for sustained focus.
        </p>

        <div className="mt-8 flex flex-col sm:flex-row items-center gap-3 w-full sm:w-auto">
          <Link href="/dashboard" className="w-full sm:w-auto">
            <Button className="w-full sm:w-auto px-6 py-2.5 h-10 text-sm font-medium gap-2">
              Launch Workspace Dashboard <ArrowRight className="size-4" />
            </Button>
          </Link>
          <Link href="/review" className="w-full sm:w-auto">
            <Button
              variant="outline"
              className="w-full sm:w-auto px-6 py-2.5 h-10 text-sm font-medium"
            >
              Preview Focus Learning Shell
            </Button>
          </Link>
        </div>

        {/* Architectural Pillars */}
        <div className="mt-16 grid grid-cols-1 sm:grid-cols-3 gap-4 w-full text-left">
          <Card className="p-5 border-border bg-card">
            <BookOpen className="size-5 mb-3 text-foreground" />
            <h2 className="text-sm font-semibold tracking-tight">SRS & Vocabulary</h2>
            <p className="mt-1.5 text-xs text-muted-foreground leading-relaxed">
              SuperMemo-2 algorithmic spacing, custom collections, and zero-friction quick word entry.
            </p>
          </Card>

          <Card className="p-5 border-border bg-card">
            <Sparkles className="size-5 mb-3 text-foreground" />
            <h2 className="text-sm font-semibold tracking-tight">Active Speech Modalities</h2>
            <p className="mt-1.5 text-xs text-muted-foreground leading-relaxed">
              Sentence dictation transcription with character diffing, audio shadowing, and diagnostic quizzes.
            </p>
          </Card>

          <Card className="p-5 border-border bg-card">
            <Layers className="size-5 mb-3 text-foreground" />
            <h2 className="text-sm font-semibold tracking-tight">Modular Monolith</h2>
            <p className="mt-1.5 text-xs text-muted-foreground leading-relaxed">
              Spring Boot 3 + Java 21 backend with session-based security and Next.js 15 App Router proxy.
            </p>
          </Card>
        </div>
      </main>

      {/* Footer System Info */}
      <footer className="border-t border-border px-6 py-6 max-w-7xl mx-auto w-full">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-muted-foreground">
          <div className="flex items-center gap-2">
            <span className="size-2 rounded-full bg-foreground" />
            <span>EnglishFlow Architecture: Milestone M1 Vertical Slice</span>
          </div>
          <div className="flex items-center gap-4 font-mono text-[11px]">
            <span>Next.js 15</span>
            <Separator orientation="vertical" className="h-3" />
            <span>React 19</span>
            <Separator orientation="vertical" className="h-3" />
            <span>Tailwind v4</span>
            <Separator orientation="vertical" className="h-3" />
            <span>Spring Boot 3</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
