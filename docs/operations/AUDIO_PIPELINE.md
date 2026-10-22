# EnglishFlow Production Audio Safety, Provenance & Offline Ingestion Pipeline (M3-T01A)

**Document Version**: 1.0.0  
**Effective Date**: Milestone M3-T01A  
**Authority**: Principal Spring Boot Engineer, Media Pipeline Architect, Security Reviewer  
**Audience**: Audio Content Operations, Release Engineers, Autonomous Coding Agents  

---

## 1. Executive Summary & Purpose

EnglishFlow requires spoken English audio exercises for **Daily Dictation** and **Speech Shadowing**. In private local development, synthetic tones and macOS system speech (`say -v Samantha`) provide immediate developer ergonomics. However, distributing speech assets in production requires verifiable copyright licensing, canonical transcript integrity, and auditable human speech quality assurance.

The **M3-T01A pipeline** provides:
1. **Contract-First Audio Manifest v2.0.0** establishing 16 auditable metadata attributes per asset.
2. **Provider-Independent Offline Ingestion Workflow** (`scripts/ingest-audio.py`) that stages assets and computes byte-level SHA-256 and transcript digests without requiring external network access or paid cloud services.
3. **Deterministic Offline Validation Command** (`scripts/validate-audio-manifest.py`) enforcing integrity checks across dev and production modes.
4. **Dual Independent Logical Approval Gates**: Licensing Gate and Human Speech QA Gate. Neither gate can self-authorize or automatically approve the other.
5. **Runtime Production Media Security Guard**: Enforced at the resource serving layer (`AudioMediaSecurityInterceptor`), rejecting unapproved assets, synthetic fixtures, and local development audio with `403 Forbidden` (`AUDIO_UNAPPROVED_FOR_PRODUCTION`).
6. **Elimination of Static URL Bypass**: Complete closure of direct classpath static and Next.js `/public` paths via `spring.web.resources.add-mappings: false` and Next.js Edge Middleware blocking `/media/*`.
7. **Production Startup Readiness & Graceful Feature Degradation**: Configurable failure modes ensuring learning records remain protected even when individual audio lessons are offline.

---

## 2. Contract-First Audio Manifest v2.0.0

The authoritative manifest is maintained at:
- Backend: `backend/src/main/resources/static/media/audio/curated/audio-manifest.json`
- Frontend: `frontend/public/media/audio/curated/audio-manifest.json`

### 2.1 Schema Definition

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "manifestVersion": "2.0.0",
  "mode": "LOCAL_SPOKEN",
  "updatedAt": "2026-10-17T09:00:00Z",
  "catalogSource": "content/canonical-curated-catalog.json",
  "assets": [
    {
      "contentId": "b2000000-0000-0000-0000-000000000042",
      "storageKey": "/api/v1/media/audio/curated/dictation-b2-042.wav",
      "sha256": "80ff899d453db4fe59325f190ea472e391caee13083321650b284e36d4f9f600",
      "mimeType": "audio/wav",
      "fileSizeBytes": 155668,
      "durationSeconds": 3.44,
      "transcriptSha256": "43015fb291077ea7dd052d94cf213db8a109a25b3cf13ba6f5f92ce3709b4344",
      "accent": "en-US",
      "provider": "LOCAL_MACOS_TTS",
      "voiceId": "Samantha",
      "provenance": "Local macOS Samantha Speech Synthesis (Apple SLA Section 2M Private Dev Only)",
      "licenseEvidenceRef": "docs/licensing/LOCAL_DEV_RESTRICTIONS.md#apple-sla-section-2m",
      "distributionApproved": false,
      "humanQaVerified": false,
      "reviewedAt": null,
      "readinessStatus": "LOCAL_SPOKEN"
    }
  ]
}
```

### 2.2 Asset Readiness States

| State | Spoken Speech | License Cleared | QA Approved | Distributable in Production |
| :--- | :---: | :---: | :---: | :---: |
| `MISSING` | No | No | No | **No** (404 Not Found) |
| `SYNTHETIC_FIXTURE` | No (Tone) | Yes (Generated) | No | **No** (Dev/CI Only) |
| `LOCAL_SPOKEN` | Yes | Private Only | No | **No** (Dev Only) |
| `PENDING_LICENSE` | Yes | In Review | Yes | **No** (Blocked at Licensing Gate) |
| `PENDING_HUMAN_QA` | Yes | Cleared | In Review | **No** (Blocked at QA Gate) |
| `LICENSED_DISTRIBUTABLE` | Yes | Cleared | Verified | **YES** |

**Zero Self-Authorization Invariant**: Under no circumstances does a valid file format, nonempty byte stream, or valid duration imply approval. `distributionApproved` and `humanQaVerified` default to `false`.

---

## 3. Offline Ingestion Workflow (`scripts/ingest-audio.py`)

The offline ingestion tool enables staging externally provided speech assets without network dependencies or paid provider APIs:

```bash
python3 scripts/ingest-audio.py \
  --content-id <UUID> \
  --source-file <PATH_TO_AUDIO> \
  --provider <PROVIDER_NAME> \
  --voice-id <VOICE_IDENTIFIER> \
  --accent <ACCENT_CODE> \
  --license-evidence <REFERENCE_DOC_OR_PATH>
```

### Pipeline Steps:
1. **Catalog Integrity Check**: Validates `contentId` against the authoritative catalog `content/canonical-curated-catalog.json`.
2. **Media Inspection**: Inspects binary header, codec, sample rate, duration, and file size using Python standard library `wave` and standard tools.
3. **Cryptographic Hashes**: Computes SHA-256 digest of source audio and verifies canonical transcript SHA-256.
4. **Isolated Staging**: Copies asset into staging directory `storage/audio/staging/<contentId>.<ext>`.
5. **Manifest Metadata Registration**: Creates or updates manifest asset entry, marking state as `PENDING_HUMAN_QA`.
6. **Strict Approval Lockout**: Ingestion strictly enforces:
   - `distributionApproved: false`
   - `humanQaVerified: false`
   - `readinessStatus: PENDING_HUMAN_QA` (or `PENDING_LICENSE`)
7. **Production Overwrite Protection**: If an existing asset in the target location is already marked `LICENSED_DISTRIBUTABLE`, ingestion refuses to overwrite it unless explicit force flags and replacement audits are provided.

---

## 4. Independent Logical Approval Gates

```mermaid
flowchart TD
    IngestedAsset[Ingested Staged Audio Asset] --> LicensingGate{Gate 1: Licensing Verification}
    LicensingGate -- Missing or Private SLA --> BarredProd[REJECTED: 403 Forbidden in Production]
    LicensingGate -- Cleared Commercial Rights --> QA Gate{Gate 2: Human Speech QA}
    QA Gate -- Accent/Pronunciation/Tone Flaw --> BarredProd
    QA Gate -- Audio Quality Verified --> Ready[LICENSED_DISTRIBUTABLE]
    Ready --> DeployCheck{Production Startup Guard}
    DeployCheck -- All Valid --> PublicServing[Public Serving at /api/v1/media/audio/**]
```

### 4.1 Gate 1: Licensing Clearance
- Requires verified commercial redistribution agreement or provider SLA (e.g., commercial rights for synthesized or voice-actor speech).
- Reference documented in `licenseEvidenceRef` (e.g., license agreement path, purchase order, or public domain declaration).
- Requires named legal reviewer and recorded review timestamp.

### 4.2 Gate 2: Human Speech Quality Assurance
Signal processing checks cannot replace human perception of natural English speech. Human speech QA requires:
1. Audible, clear native or near-native English speech.
2. 100% transcript word alignment (no missing, inserted, or slurred words).
3. Correct pronunciation matching the declared accent (`en-US`, `en-GB`, `en-AU`).
4. Natural cadence, appropriate speaking rate (140–165 WPM for intermediate learners).
5. Clean acoustics: no synthetic carrier tones, harsh clipping, digital artifacts, or excessive background noise.
6. Recording signed off by a human QA reviewer with timestamp.

---

## 5. Production Media Access Enforcement

### 5.1 Serving Layer Architecture
All audio is served through Spring MVC:
`GET /api/v1/media/audio/curated/{filename}`

Protected by `AudioMediaSecurityInterceptor`:
1. **Path Traversal Protection**: Rejects `..`, `%2e%2e`, and backslashes with `400 Bad Request`.
2. **File Extension Whitelist**: Only `.wav` and `.mp3` are allowed. Requests for `.json`, `.yml`, etc., return `404 Not Found`.
3. **Production Mode Check**:
   - In Development (`englishflow.media.production-mode: false`): Legitimate development assets (`LOCAL_SPOKEN`, `SYNTHETIC_FIXTURE`) are served with HTTP 200/206.
   - In Production (`englishflow.media.production-mode: true`): Unapproved assets, synthetic fixtures, and local macOS TTS assets are strictly rejected with `403 Forbidden` (`AUDIO_UNAPPROVED_FOR_PRODUCTION`).
4. **HTTP Method Parity**: `HEAD` requests execute identical authorization checks as `GET`.

### 5.2 Closure of Alternate Static Paths
To prevent unapproved media from being served outside the security interceptor:
- **Backend Classpath Direct Access**: `spring.web.resources.add-mappings: false` disables Spring Boot default static serving at `/**`. Direct requests to `http://host:8080/media/audio/**` return `404 Not Found` or `401 Unauthorized`.
- **Frontend Direct Access**: Generated audio binaries are excluded from `frontend/public/media/audio/`. Furthermore, Next.js Edge Middleware intercepts `/media/:path*` and returns `404 Not Found`.

---

## 6. Production Profile Activation & Fail-Closed Safety Configuration

Configured in `application.yml` and profile configurations (`application-prod.yml`, `application-production.yml`):
```yaml
englishflow:
  media:
    production-mode: ${ENGLISHFLOW_PRODUCTION_MODE:false}
    strict-startup-check: ${ENGLISHFLOW_STRICT_AUDIO_CHECK:false}
```

### 6.1 Profile Activation Invariants:
1. **Case A (Development)**: Development profile (`dev`) permits explicitly designated local development speech assets and synthetic fixtures.
2. **Case B (Production Profile Auto-Enforcement)**: When running under `prod` or `production` profiles, `AudioManifestService` automatically forces `productionMode = true` without requiring any extra environment flag.
3. **Case C (Invalid Security Configuration Rejection)**: If a production profile (`prod`/`production`) is active and `englishflow.media.production-mode` is explicitly configured to `false`, `AudioManifestService.init()` throws `IllegalStateException` during application startup, preventing any silent bypass of audio safety policies.
4. **Case D & E (Missing/Malformed Manifest)**: If the manifest is missing or contains malformed JSON, the parser fails closed (`topLevelMode = MISSING`, 0 registered assets), returning `isAudioAvailable = false` and denying all media requests.
5. **Case F (Missing/Dev License Ref)**: Missing `licenseEvidenceRef` or references containing `LOCAL_DEV_RESTRICTIONS` are rejected in production (`isApprovedForProduction() == false`).
6. **Case G (Canonical Transcript Hash Drift)**: If an asset's `transcriptSha256` does not match the canonical database/catalog transcript, `isAudioAvailable(storageKey, transcriptText)` evaluates to `false`.
7. **Case H (Non-Distributable Readiness Status)**: Any status other than `LICENSED_DISTRIBUTABLE` denies production serving.
8. **Case I (Method Parity)**: HTTP `HEAD` and `GET` execute identical security pipeline filters.
9. **Case J (Graceful Feature Degradation)**: When audio lessons are unavailable or unapproved, unrelated learning modules (**Vocabulary, Collections, Flashcard SRS, Diagnostic Quizzes, Heatmap, Settings**) remain 100% operational.

### 6.2 Proxy / CDN Configuration Directives
- **Direct S3 / Storage Bucket Exposure Forbidden**: Audio assets must never be publicly accessible directly from cloud object storage buckets.
- **Edge Reverse Proxy Parity**: Any external CDN or reverse proxy (e.g. Cloudflare, AWS CloudFront, Nginx) deployed in front of EnglishFlow must route `/api/v1/media/audio/**` requests to the application layer to enforce interceptor validation, or must replicate identical token-based signed URL authorization. Direct bypass routes to origin static files must be strictly blocked at the proxy layer.

---

## 7. License and QA Trust Boundaries

1. **Maintainer Trust Boundary**: Manifest metadata is authored and reviewed by repository maintainers within Git version control. The system does not currently utilize asymmetric digital PKI signatures for metadata records; authenticity relies on Git commit provenance and strict code review gates.
2. **Deterministic Cryptographic Checks**: File byte streams and canonical transcript texts are validated with SHA-256 hashes against immutable catalog specifications.
3. **Evidence Validation Rules**:
   - A non-empty `licenseEvidenceRef` string alone cannot authorize distribution; it must point to an existing authorized commercial agreement and must not reference development restriction documents (`LOCAL_DEV_RESTRICTIONS.md`).
   - A human QA timestamp (`reviewedAt`) alone cannot authorize distribution; it requires explicit `humanQaVerified: true` signoff.
   - Default state for all newly ingested assets is strictly unapproved (`distributionApproved: false`).

---

## 8. Verification Commands & Quality Gates

```bash
# 1. Canonical Transcript & Manifest Consistency (10/10 exercises 100% hash agreement)
python3 scripts/verify-transcript-integrity.py

# 2. Offline Manifest Validation (Dev Mode)
python3 scripts/validate-audio-manifest.py --manifest backend/src/main/resources/static/media/audio/curated/audio-manifest.json

# 3. Offline Manifest Validation (Production Mode — Expected failure for unapproved assets)
python3 scripts/validate-audio-manifest.py --manifest backend/src/main/resources/static/media/audio/curated/audio-manifest.json --production

# 4. Offline Pipeline Unit Tests (10/10 passing)
python3 -m unittest discover -s scripts/tests

# 5. Backend Production Audio Safety Integration Tests (21/21 passing, Cases A through J)
cd backend && ./mvnw test -Dtest=ProductionAudioSafetyIntegrationTest

# 6. Full Backend Clean Verify Suite (299/299 passing)
cd backend && ./mvnw clean verify

# 7. Frontend Verification (122/122 passing, typecheck, lint, build)
cd frontend && npm test && npm run typecheck && npm run lint && npm run build

# 8. Full-Stack HTTP End-to-End Verification (16/16 scenarios passing)
scratch/e2e-m3-t01a-audio-pipeline.sh
```

---

## 9. Current Deployment Status & Release Governance

| Dimension | Current Verification Status | Operational Reality |
| :--- | :--- | :--- |
| **Local Pipeline Code** | **VERIFIED** | Unit and integration tests passing in local development. |
| **Production-Like Environment** | **VERIFIED** | Docker PostgreSQL, Spring Boot prod profile, Next.js build verified. |
| **Actual Production Deployment** | **NOT PERFORMED** | Zero staging/production cloud instances deployed. |
| **Commercial Speech Rights** | **PENDING** | No commercial TTS subscription or contract activated; AWS Polly Neural is a candidate only. |
| **Human Speech QA** | **PENDING** | Assets in repo are development placeholders; physical ear QA pending. |
| **ADR-012 Architectural Status** | **PROPOSED** | Architecture Decision Record remains PROPOSED pending rights acquisition. |

