import { cookies } from "next/headers";
import { localeCookieName, resolveLocale } from "@/lib/i18n/config";
import { LocaleProvider } from "@/lib/i18n/locale-provider";
import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/lib/auth/auth-context";
import "./globals.css";

const inter = Inter({
  variable: "--font-sans",
  subsets: ["latin", "vietnamese"],
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "EnglishFlow — Focus-Driven English Learning",
  description:
    "A distraction-free English learning platform for spaced repetition, active listening, and pronunciation practice.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const locale = resolveLocale((await cookies()).get(localeCookieName)?.value);
  return (
    <html lang={locale} className={`${inter.variable} ${jetbrainsMono.variable}`}>
      <body className="min-h-screen bg-background font-sans text-foreground antialiased selection:bg-foreground selection:text-background">
        <LocaleProvider initialLocale={locale}>
          <AuthProvider>
            <TooltipProvider delay={200}>
              {children}
            </TooltipProvider>
          </AuthProvider>
        </LocaleProvider>
      </body>
    </html>
  );
}

