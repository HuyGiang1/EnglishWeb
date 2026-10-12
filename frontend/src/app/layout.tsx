import type { Metadata } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/lib/auth/auth-context";
import "./globals.css";

const inter = Inter({
  variable: "--font-sans",
  subsets: ["latin"],
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

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} ${jetbrainsMono.variable}`}>
      <body className="min-h-screen bg-background font-sans text-foreground antialiased selection:bg-foreground selection:text-background">
        <AuthProvider>
          <TooltipProvider delay={200}>
            {children}
          </TooltipProvider>
        </AuthProvider>
      </body>
    </html>
  );
}

