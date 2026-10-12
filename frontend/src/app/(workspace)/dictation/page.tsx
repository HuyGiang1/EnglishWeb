import { ModulePlaceholder } from "@/components/layout/module-placeholder";

export default function DictationPage() {
  return (
    <ModulePlaceholder
      title="Listening Dictation"
      milestone="Milestone M2"
      task="M2-T02"
      description="Active listening transcription, sentence playback, and diff analysis."
      scopeSummary="Scheduled for Milestone M2. Integrates native audio playback, user typing input with keyboard safety guards, and idempotency key attempt submission."
    />
  );
}
