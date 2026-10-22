# Licensing Restrictions & Legal Scope: Local Development Audio

**Document Reference**: `docs/licensing/LOCAL_DEV_RESTRICTIONS.md`  
**Classification**: Legal Governance & Audio Provenance Control  
**Effective**: Milestone M2 / M3  
**Status**: ACTIVE  

---

## 1. Purpose

This document provides formal legal and technical provenance evidence for audio assets generated during local development of EnglishFlow. It defines the strict boundary between development-only fixtures and production-cleared media assets.

---

## 2. macOS Speech Synthesis (`say -v Samantha`)

### 2.1 Asset Identification
- Audio assets prefixed with `dictation-*.wav` and `shadowing-*.wav` generated via the macOS command-line utility `say` using system voice profiles (e.g., `Samantha`).

### 2.2 Applicable License Agreement
- Apple Software License Agreement for macOS (Section 2M: Voices and Speech Synthesis).
- URL: `https://www.apple.com/legal/sla/`

### 2.3 Permitted Scope of Use
- **Permitted**: Personal, non-commercial use on Apple hardware; private local development and debugging by engineers holding legitimate macOS licenses.
- **Strictly Prohibited**: Redistribution, embedding into commercial software, public web serving, third-party distribution via CDN, or SaaS learners receiving synthetic speech without Apple device-level execution.

### 2.4 Governance Classification
- Manifest Readiness Status: `LOCAL_SPOKEN`
- `distributionApproved`: `false`
- `humanQaVerified`: `false`
- **Safety Enforcement**: Prohibited from inclusion in production builds; rejected by Spring Boot media guard in `production` profile.

---

## 3. Synthetic PCM Sine Wave Fixtures (440 Hz)

### 3.1 Asset Identification
- Sine wave audio files generated algorithmically via `scripts/setup-dev-audio.sh --synthetic` using standard Python `wave` and `math.sin`.

### 3.2 Applicable License Agreement
- Project-generated algorithmic artifact (Public Domain / MIT Equivalent).

### 3.3 Permitted Scope of Use
- **Permitted**: Automated CI testing, HTTP 206 Partial Content range request testing, browser audio decoder lifecycle verification, headless pipeline verification.
- **Strictly Prohibited**: Presentation to learners as genuine English speech; earning learning activity credit for listening or speech practice.

### 3.4 Governance Classification
- Manifest Readiness Status: `SYNTHETIC_FIXTURE`
- `isSpokenSpeech`: `false`
- `distributionApproved`: `false`
- `humanQaVerified`: `false`
- **Safety Enforcement**: Cannot earn `SHADOWING_TAKE` or `DICTATION_SENTENCE` activity events; rejected by production media serving endpoint.

---

## 4. Production Release Requirement

Before any audio exercise is made available in a public or production environment:
1. The audio must be synthesized by an authorized commercial provider (e.g., AWS Polly Neural under AWS Customer Content terms) or recorded by human voice artists with signed release agreements.
2. The asset must undergo the dual-gate approval process:
   - **Licensing Gate**: Signed agreement reference, commercial redistribution rights confirmed.
   - **Human Speech QA Gate**: Spoken English clarity, accurate transcript alignment, natural cadence verified by a designated human reviewer.
3. The asset record in `audio-manifest.json` must be updated with `distributionApproved: true`, `humanQaVerified: true`, non-empty byte SHA-256, transcript SHA-256, reviewer ID, and timestamp, setting status to `LICENSED_DISTRIBUTABLE`.
