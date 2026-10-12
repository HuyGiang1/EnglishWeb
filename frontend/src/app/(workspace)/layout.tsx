import { Sidebar } from "@/components/layout/sidebar";
import { Header } from "@/components/layout/header";
import { WorkspaceAuthGuard } from "@/components/auth/workspace-auth-guard";

export default function WorkspaceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <WorkspaceAuthGuard>
      <div className="flex min-h-screen bg-background">
        {/* Persistent Left Sidebar for Desktop */}
        <Sidebar />

        {/* Main Content Area */}
        <div className="flex flex-1 flex-col min-w-0">
          <Header />
          <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto">
            {children}
          </main>
        </div>
      </div>
    </WorkspaceAuthGuard>
  );
}
