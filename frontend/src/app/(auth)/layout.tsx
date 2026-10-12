import Link from "next/link";

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 py-12 sm:px-6 lg:px-8">
      {/* Brand Header */}
      <div className="mb-8 flex flex-col items-center">
        <Link href="/" className="flex items-center gap-2.5 group">
          <div className="flex size-9 items-center justify-center rounded-lg bg-foreground text-background font-bold text-sm tracking-tighter">
            EF
          </div>
          <span className="font-semibold text-lg tracking-tight text-foreground">
            EnglishFlow
          </span>
        </Link>
        <p className="mt-2 text-xs font-mono text-muted-foreground">
          Focus-driven English acquisition
        </p>
      </div>

      {/* Auth Card Container */}
      <div className="w-full max-w-sm">
        {children}
      </div>

      {/* Footer Info */}
      <div className="mt-8 text-center text-xs text-muted-foreground">
        <span>Protected by Spring Security session cookies & CSRF verification</span>
      </div>
    </div>
  );
}
