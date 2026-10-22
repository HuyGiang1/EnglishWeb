# EnglishFlow Audio Licensing Dossiers & Review Protocol

**Authority**: AI Technical Lead, Release Security Engineer, Legal & QA Lead
**Specification Version**: 1.0.0
**Schema Definition**: [`docs/licensing/dossier.schema.json`](../dossier.schema.json)

---

## 1. Overview & Trust Boundary

In EnglishFlow, media assets are strictly separated into two realms:
1. **Local Development Audio**: Permitted solely for developer workstations under the `dev` profile (`docs/licensing/LOCAL_DEV_RESTRICTIONS.md`).
2. **Production Distributable Audio**: Permitted for staging, public testing, and production runtime volumes only when backed by an explicitly approved, verifiable Licensing Dossier.

### The Trust Boundary
A read-only filesystem volume alone does **not** establish licensing authorization. The backend independent verification layer (`AudioManifestService` and `AudioMediaSecurityInterceptor`) gates every audio request against cryptographic hashes and approved manifest entries.

The authoritative source of authorization is the **Per-Asset Licensing Dossier** committed under Git review. Git commit history and signed peer review constitute the organizational trust boundary unless an external cryptographic PKI notary is introduced.

---

## 2. Dossier Lifecycle & States

Every curated exercise in `content/canonical-curated-catalog.json` must possess a corresponding dossier named `<contentId>.json`.

### Default Baseline State (Unapproved)
```json
{
  "distributionApproved": false,
  "humanQaVerified": false,
  "licenseReviewStatus": "PENDING",
  "audioReadinessStatus": "PENDING_LICENSE",
  "reviewedAt": null,
  "reviewerIdentity": null,
  "humanReviewerIdentity": null
}
```

### Verification Requirements for Production Promotion
Before an audio asset can be promoted to a production release via `scripts/promote-audio-release.py`, the dossier must be updated with verifiable evidence:
1. **Audio SHA-256**: Real, cryptographic digest calculated from the approved master recording.
2. **Transcript SHA-256**: Must match the authoritative canonical catalog transcript hash bit-for-bit.
3. **Legal License Review**:
   - `licenseReviewStatus`: `"APPROVED"`
   - `distributionApproved`: `true`
   - `reviewerIdentity`: Legal/compliance officer handle
   - `licenseEvidenceRef`: Verifiable contract reference or commercial invoice
   - `authorizedDistributionScope`: `"PUBLIC_PRODUCTION"`
4. **Human Speech QA**:
   - `humanSpeechQaDecision`: `"PASSED"`
   - `humanQaVerified`: `true`
   - `humanReviewerIdentity`: Qualified native-speaker linguist handle
   - Verification covers: natural cadence, lack of unnatural robotic artifacts, correct pronunciation of domain terms, and CEFR-appropriate speech rate.
5. **Readiness Status**: Transition to `"LICENSED_DISTRIBUTABLE"`.

---

## 3. Tooling & Enforcement

- **Schema Validation**: `python3 scripts/validate-audio-manifest.py --dossiers` validates all dossiers against `dossier.schema.json`.
- **Promotion Pipeline**: `scripts/promote-audio-release.py` enforces all fields before staging bytes into the release directory.
- **Fail-Closed Guarantee**: Any asset lacking an approved dossier or presenting mismatched digests will be rejected with `403 Forbidden` / `AUDIO_UNAPPROVED_FOR_PRODUCTION`.
