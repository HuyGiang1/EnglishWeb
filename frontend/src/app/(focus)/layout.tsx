import { FocusHeader } from "@/components/layout/focus-header";

export default function FocusLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="app-shell learning-module flex min-h-screen flex-col bg-background">
      <FocusHeader />
      <main className="flex flex-1 flex-col items-center justify-center p-4 sm:p-6 lg:p-10 max-w-3xl w-full mx-auto">
        {children}
      </main>
    </div>
  );
}
