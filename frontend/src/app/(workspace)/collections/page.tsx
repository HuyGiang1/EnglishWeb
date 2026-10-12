import { ModulePlaceholder } from "@/components/layout/module-placeholder";

export default function CollectionsPage() {
  return (
    <ModulePlaceholder
      title="Vocabulary Collections"
      milestone="Milestone M2"
      task="M2-T01"
      description="Deck organization, custom collections, and targeted study deck assignments."
      scopeSummary="Scheduled for Milestone M2. Enforces cross-tenant composite foreign keys (collection_id, user_id) with collection CRUD and review launching."
    />
  );
}
