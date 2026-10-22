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

---

## 10. M3-T01B Runtime Volume Delivery & Staging Security

### 10.1 Storage Root Configuration (`englishflow.media.storage-root`)
Production media delivery decouples speech binaries from the executable JAR. The backend reads media strictly from an external runtime filesystem volume:
- Property: `englishflow.media.storage-root` (env var `ENGLISHFLOW_MEDIA_STORAGE_ROOT`).
- Target Directory: Refers to the directory containing `curated/`.
- Public Endpoint Contract: Kept invariant as `/api/v1/media/audio/curated/<filename>`.
- Path Resolution: The resource handler maps `/api/v1/media/audio/**` directly to `file:${storage-root}/` without duplicating the `curated/` folder segment.
- Anti-Escape Protection: `AudioMediaSecurityInterceptor` resolves canonical paths and immediately blocks symlinks escaping the configured storage root (`403 Forbidden` with `SYMLINK_ESCAPE_DETECTED`). Path traversals (`..`, `%2e%2e`) are blocked with `400 Bad Request`.

### 10.2 Read-Only Volume Container Mount Procedure
In containerized Kubernetes or Docker deployments:
```yaml
# Kubernetes Pod volume mount specification
spec:
  containers:
    - name: englishflow-backend
      image: englishflow-backend:latest
      env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: ENGLISHFLOW_MEDIA_STORAGE_ROOT
          value: "/var/englishflow/media/audio"
      volumeMounts:
        - name: media-volume
          mountPath: /var/englishflow/media/audio
          readOnly: true
  volumes:
    - name: media-volume
      persistentVolumeClaim:
        claimName: englishflow-curated-media-pvc
        readOnly: true
```
A read-only volume prevents unauthorized runtime modification, but does not self-authorize media licensing. The backend independently verifies every requested asset against the authoritative manifest and licensing gates.

### 10.3 Licensing Dossier Contract (`docs/licensing/dossiers/`)
Per-asset licensing dossiers are maintained in `docs/licensing/dossiers/<contentId>.json` according to `docs/licensing/dossier.schema.json`.
- Default state is strictly unapproved:
  ```json
  {
    "distributionApproved": false,
    "humanQaVerified": false,
    "licenseReviewStatus": "PENDING",
    "audioReadinessStatus": "PENDING_LICENSE",
    "reviewedAt": null
  }
  ```
- All 10 curated catalog items have committed unapproved dossiers.
- Git commit provenance and peer review form the organizational trust boundary for legal and linguistic QA signoffs.

### 10.4 Versioned Media Promotion & Rollback Workflow
Offline promotions are managed via `scripts/promote-audio-release.py`:
1. **Validation**: Enforces approved dossier flags, verifies audio byte SHA-256 and transcript SHA-256 against the canonical catalog.
2. **Staging**: Stages approved bytes into `<storage-root>/releases/<release-tag>/curated/` and creates release metadata and deployment manifest.
3. **Dry-Run**: Supported via `--dry-run` to simulate validation without modifying disk state.
4. **Atomic Activation**: Supported via `--activate-release <tag>`, updating `<storage-root>/curated` symlink atomically and appending to `release-history.json`.
5. **Rollback**: Supported via `--rollback`, atomically pointing `<storage-root>/curated` back to the prior coherent release without mixing versions.

### 10.6 Release Consistency, Permissions & Recovery Architecture (M3-T01B-1 Audit)

1. **Release Manager vs. Runtime Permissions**:
   - **Offline Release Manager**: Requires write permissions to the underlying storage volume repository (`/var/englishflow/media/audio/releases/`) to stage versioned assets and atomically update symlinks (`curated -> releases/<tag>/curated`).
   - **Runtime Backend Application**: Must mount the volume as strictly **read-only** (`:ro` in Docker / `readOnly: true` in Kubernetes / `chmod 555`). The runtime process has zero write permissions to disk; any attempted file creation or alteration by application code fails with `Read-only file system` (`EACCES`).
2. **Authoritative Manifest Location & Identity**:
   - The authoritative deployment manifest is located at `<storage-root>/curated/audio-manifest.json` (resolving directly to `<storage-root>/releases/<active-release>/curated/audio-manifest.json`).
   - Release identity is cryptographically bound at backend startup: `releaseTag`, `manifestSha256`, `boundCuratedDir`, and `loadedAt`.
3. **Immutable Release Selection & Snapshot Isolation (Model A)**:
   - At startup, `AudioManifestService` resolves the canonical curated directory `boundCuratedDir` and locks to that specific release.
   - Resource serving in `WebMvcConfig` resolves static audio bytes strictly from `boundCuratedDir`.
   - **Immunity to Release Drift & TOCTOU**: If an external release manager switches the `curated` symlink to a newer release while the backend is running, the running backend continues to serve audio strictly from its startup release snapshot. No request can receive bytes from an asset version different from the manifest version it was authorized against.
   - **Release Transition / Rolling Restart**: To transition traffic to a new release, deployment operators execute an atomic symlink switch followed by a backend container restart (e.g. Kubernetes rolling update or blue/green switch).
4. **Activation & Rollback Commands**:
   - Stage release: `python3 scripts/promote-audio-release.py --release-tag <tag> --source-dir <dir> --storage-root <root>`
   - Dry-run validation: `python3 scripts/promote-audio-release.py --release-tag <tag> --source-dir <dir> --storage-root <root> --dry-run`
   - Atomic activation: `python3 scripts/promote-audio-release.py --storage-root <root> --activate-release <tag>`
   - Atomic rollback: `python3 scripts/promote-audio-release.py --storage-root <root> --rollback`
   - Release status: `python3 scripts/promote-audio-release.py --storage-root <root> --status`
5. **Failure Recovery & Pre-Activation Verification**:
   - Staging occurs in a temporary PID-isolated directory (`.tmp.<tag>.<pid>`) and is atomically renamed only after all audio files, manifest SHA-256, and metadata pass validation. Interrupted promotions leave no partial releases.
   - `activate_release` and `rollback_release` perform strict pre-activation integrity verification: validating `release-metadata.json`, comparing `manifestSha256`, and verifying the physical existence and non-zero size of every staged audio file. Corrupted or incomplete releases are rejected from activation.
6. **Known Test Limitations & Boundaries**:
   - Staging and test rehearsal use disposable test fixtures labeled with `provenance: TEST_CONTAINER_FIXTURE` and `READONLY_CONTAINER_FIXTURE`. These stubs test delivery and authorization mechanics only and do NOT constitute commercial licensing rights.
   - Commercial production speech remains **UNACQUIRED**.
   - ADR-012 remains strictly **PROPOSED**.

---

## 11. MP3 Production Compatibility & Audio Preflight (M3-T01B-2A)

### 11.1 Pure Python Deterministic Media Inspector (`scripts/media_inspector.py`)
To eliminate inaccurate 128 kbps duration heuristics without adding external binary dependencies (`ffprobe`/`ffmpeg` may not be present on all target hosts or minimalist production containers), EnglishFlow utilizes a deterministic, pure Python audio parser:
- **MPEG Audio Frame Parser**: Resolves MPEG versions (MPEG-1, MPEG-2, MPEG-2.5) and Layer III framing tables.
- **Variable Bitrate (VBR) & Fixed Bitrate (CBR) Support**: Parses Xing, Info, and VBRI header extensions to extract exact total frame counts and duration. In the absence of VBR headers, it performs deterministic frame-walking across the entire bitstream, accounting for frame padding and byte sync.
- **ID3 Metadata Traversal**: Accurately parses ID3v2 syncsafe integer headers and skips ID3 tags without corrupting audio sync words; strips ID3v1 128-byte trailers at EOF.
- **Fail-Closed Integrity**: Rejects malformed headers, corrupt sync words, truncated frames at EOF, zero-duration files, or empty files with explicit `ValueError`. Walks every audio frame across the entire byte stream to verify full container integrity.
- **WAV Regression**: Leverages standard library `wave` module for RIFF WAV container parsing.
- **Synthetic Frame Fixture Tests**: Validated by 12 automated unit tests in `scripts/tests/test_media_inspector.py` covering synthetic CBR, VBR, Polly-style 24 kHz MP3, ID3 tags, corrupt headers, truncated files, and WAV regression.
- **Real Encoded MP3 Verification (`scripts/tests/test_real_encoded_mp3.py`)**:
  - Validates the parser against real audio files produced by a standards-compliant LAME / FFmpeg encoder:
    1. Real LAME-encoded CBR MP3 (128 kbps, 44.1 kHz).
    2. Real LAME-encoded VBR MP3 with Xing/Info header.
    3. Real MP3 with ID3v2 metadata frames (`title`, `artist`).
    4. Real 24 kHz mono speech-compatible MP3 (matching AWS Polly Neural profile).
    5. Truncated real MP3 (fails closed with `ValueError`).
    6. Corrupted real MP3 (corrupted sync header fails closed with `ValueError`).
    7. Exact duration agreement with Apple CoreAudio reference decoder (`/usr/bin/afinfo`), matching down to the hundredth of a second.
  - Ephemeral Test Fixtures: Real audio files are generated dynamically in temporary directories (`tempfile.mkdtemp`) during test execution and completely removed upon completion. Zero test audio binaries are committed to version control.

### 11.2 Verified Promotion CLI Contract (`scripts/promote-audio-release.py`)
The promotion CLI utilizes top-level command-line flags rather than positional subcommands. Operators and release automation must execute the verified flag interface:
```bash
# 1. Inspect active release status and activation history:
python3 scripts/promote-audio-release.py --storage-root /var/englishflow/media/audio --status

# 2. Stage and validate a candidate versioned release (offline dry-run):
python3 scripts/promote-audio-release.py \
  --release-tag v1.0.0 \
  --source-dir storage/audio/source \
  --storage-root /var/englishflow/media/audio \
  --dry-run

# 3. Stage candidate versioned release:
python3 scripts/promote-audio-release.py \
  --release-tag v1.0.0 \
  --source-dir storage/audio/source \
  --storage-root /var/englishflow/media/audio

# 4. Atomically activate an already-staged release:
python3 scripts/promote-audio-release.py \
  --storage-root /var/englishflow/media/audio \
  --activate-release v1.0.0

# 5. Atomically rollback active release to previous coherent release:
python3 scripts/promote-audio-release.py \
  --storage-root /var/englishflow/media/audio \
  --rollback
```

### 11.3 Audio URL Invariant & Byte-Level Delivery Contract
1. **URL-to-Byte Agreement Invariant**:
   - A media URL ending in `.mp3` strictly serves MP3 bytes with `Content-Type: audio/mpeg`.
   - A media URL ending in `.wav` strictly serves WAV bytes with `Content-Type: audio/wav`.
   - The Spring MVC `ResourceResolver` does NOT silently substitute file bytes or mask extensions behind transparent aliasing.
2. **Explicit HTTP 307 Temporary Redirect for Legacy URLs**:
   - When a client requests a legacy `.wav` URL for an exercise whose active production release provides an approved `.mp3` file, `AudioMediaSecurityInterceptor` detects that the requested `.wav` file is absent but an approved `.mp3` equivalent exists.
   - It issues an explicit `HTTP 307 Temporary Redirect` with `Location: /api/v1/media/audio/curated/<name>.mp3`.
   - This eliminates cache confusion, keeps HTTP headers and payload bytes strictly aligned, and provides safe, backward-compatible URL migration.
3. **Fail-Closed Security**:
   - If the alternate asset is unapproved in production mode, the interceptor immediately rejects the request with `403 Forbidden` (`AUDIO_UNAPPROVED_FOR_PRODUCTION`).
   - GET, HEAD, and Range requests are enforced uniformly across all media endpoints.

### 11.4 Database Migration & Deployment Strategy
1. **Dynamic Manifest URL Derivation**:
   - Audio playback URLs in Dictation and Shadowing APIs are resolved at runtime from the active, immutable manifest via `AudioManifestService.resolveActiveAudioUrl(contentId, defaultStorageKey)`.
   - Persistent database columns (`learning_contents.audio_storage_key`) do not need to be mutated prematurely; local developer WAV environments and production MP3 releases coexist without schema changes.
2. **Manual Deployment SQL Scripts (NOT Flyway Migrations)**:
   - Location: `scripts/deployment/manual_update_audio_storage_keys_to_mp3.sql`
   - Rollback: `scripts/deployment/manual_rollback_audio_storage_keys_to_wav.sql`
   - Classification: Optional manual procedures for DBA maintenance during major production milestones.
   - Automated Flyway migrations (`V1` through `V10`) remain untouched and immutable.
   - Idempotency & Preflight Verification: Both scripts execute within transactional `DO $$ ... $$` blocks that assert all 10 canonical UUIDs exist before applying changes.
   - Zero Learning Data Impact: Foreign keys in `dictation_attempts` and `shadowing_takes` reference UUID `content_id`, guaranteeing zero disruption to historical learning records, streaks, or heatmaps.

### 11.5 Provider Voice Support Preflight (AWS Polly Neural)
Public documentation for AWS Polly Neural verifies the proposed voice assignments:
- `Joanna`: US English (`en-US`), Female, Neural engine supported, native MP3 24 kHz output.
- `Matthew`: US English (`en-US`), Male, Neural engine supported, native MP3 24 kHz output.
- Output formats: Native `audio/mpeg` (MP3) at 24000 Hz eliminates the need for PCM WAV container wrappers.
- All provider assignments remain proposals in unapproved dossiers. No AWS API calls (`DescribeVoices` or `SynthesizeSpeech`) have been executed, and zero cloud expenses have been incurred.
