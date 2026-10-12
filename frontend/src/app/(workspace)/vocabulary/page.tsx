import { ModulePlaceholder } from "@/components/layout/module-placeholder";

export default function VocabularyPage() {
  return (
    <ModulePlaceholder
      title="Vocabulary Management"
      milestone="Milestone M1"
      task="M1-T05"
      description="Central vocabulary catalog, full-text search, phonetic transcriptions, and quick add."
      scopeSummary="Scheduled for implementation following M1-T04 (Session Authentication). Connects to Spring Boot Vocabulary Service (/api/v1/vocabulary) with multi-tenant data isolation and pagination."
    />
  );
}
