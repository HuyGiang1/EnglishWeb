import Link from "next/link";
import { ArrowLeft, Clock } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";

interface ModulePlaceholderProps {
  title: string;
  milestone: string;
  task: string;
  description: string;
  scopeSummary: string;
}

export function ModulePlaceholder({
  title,
  milestone,
  task,
  description,
  scopeSummary,
}: ModulePlaceholderProps) {
  return (
    <div className="max-w-2xl mx-auto py-8">
      <Card className="border-border bg-card">
        <CardHeader>
          <div className="flex items-center gap-2 mb-2">
            <span className="inline-flex items-center gap-1 rounded bg-muted px-2 py-0.5 text-[11px] font-mono text-muted-foreground border border-border">
              <Clock className="size-3" />
              <span>{milestone} · {task}</span>
            </span>
            <span className="rounded bg-muted px-2 py-0.5 text-[11px] font-mono text-foreground font-medium border border-border">
              Upcoming
            </span>
          </div>
          <CardTitle className="text-xl font-bold tracking-tight text-foreground">
            {title}
          </CardTitle>
          <CardDescription className="text-xs text-muted-foreground">
            {description}
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-6">
          <div className="rounded-md border border-border bg-muted/40 p-4 text-xs text-muted-foreground leading-relaxed">
            <p className="font-semibold text-foreground mb-1">Architecture Directive:</p>
            {scopeSummary}
          </div>

          <div>
            <Link href="/dashboard">
              <Button variant="outline" size="sm" className="gap-2 text-xs">
                <ArrowLeft className="size-3.5" />
                <span>Return to Dashboard</span>
              </Button>
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
