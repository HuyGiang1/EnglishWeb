# Web Design Guidelines provenance — M3-EVO-2B

## Reviewed source

- Repository: https://github.com/vercel-labs/web-interface-guidelines
- Pinned commit: `434b7f91364665f2f733b310ec54809bf8f37937`
- Retrieval date: 2026-11-09 (Asia/Ho_Chi_Minh).
- Transport: read-only GitHub connector; no installer or upstream script executed.
- Source paths: `command.md` and `LICENSE` at the pinned commit.
- Immutable source URLs:
  - https://github.com/vercel-labs/web-interface-guidelines/blob/434b7f91364665f2f733b310ec54809bf8f37937/command.md
  - https://github.com/vercel-labs/web-interface-guidelines/blob/434b7f91364665f2f733b310ec54809bf8f37937/LICENSE

## Local files and integrity

All content is UTF-8. The two upstream files are copied unchanged, including
their trailing newlines; their Git blob IDs match the M3-EVO-2A reviewed revision.

| Local path | Origin | Git blob ID | SHA-256 |
|---|---|---|---|
| `references/command.md` | Upstream `command.md`, verbatim | `3eb3a212b7b89493922ea418489d4c6f67ae8bd5` | `d246b026f4f29b5823a9cc857f9edf3d2507002e055e32040cadeaf3b38e0234` |
| `LICENSE` | Upstream `LICENSE`, verbatim | `b3575a3c1358eac4b9ee36a4c851872d81417760` | `6cd1609c9c12233507cdd2ce0d32e9a721e3c27494951be06b90090deeeb7af2` |
| `SKILL.md` | EnglishFlow-authored wrapper | Not an upstream file | `dba3e9cf634148b453233e2da20ce2c7cbb2510c581c6fba7352d36d10ff338e` |

`UPSTREAM.md` is an EnglishFlow-authored provenance record. Its own hash is
reported externally during verification rather than embedded in itself.

## License

The copied reference is MIT-licensed. The complete upstream license, including
`Copyright (c) 2025 Vercel Labs`, is preserved in [LICENSE](LICENSE).
Retain that notice and permission text with copies or substantial portions.
The wrapper and this provenance record are locally authored; neither is claimed
to be an upstream Vercel skill. No copyright holder or year has been invented.

## Local restrictions

The wrapper uses [references/command.md](references/command.md) locally and never
fetches fresh instructions on activation. AGENTS.md, current task restrictions,
approved ADRs and EnglishFlow design/localization documents govern applicability.
Keep Next.js 15/React 19.1, existing components/tokens, monochrome design, reduced
motion, vi-VN/en cookie/account precedence, SSR and session/CSRF behavior.

Generic language detection, URL-state, branding and library suggestions must
not replace project decisions. No private learning state or answers in URLs,
automatic packages, routing redesign, Spring-to-Next business-logic migration,
paid calls, uploads, shell execution or unrelated edits are authorized by the skill.

## Reviewed snapshot and updates

This snapshot is reviewed reference material, not a permanent safety guarantee.
Future adoption of a newer revision requires a new security and compatibility
review and explicit task authorization:

1. Identify an exact upstream commit and retrieve only its required files.
2. Review the complete diff, references, license, network instructions, embedded
   commands and conflicts with the current project architecture.
3. Recheck licensing/copyright and preserve the actual upstream notice.
4. Approve a specific file allowlist; do not run `install.sh` or a package installer.
5. Replace the two references only with the approved pinned content, then update
   this record and its Git blob IDs/SHA-256 hashes; document wrapper adaptations.
6. Validate YAML, links, scope, non-executable files and absence of remote
   instruction retrieval. Runtime discovery/activation remains a separate check.

No moving `main` branch is used as the instruction source. No React Best
Practices or EnglishFlow custom skills are included. Global configuration is
unchanged. Runtime activation is unverified and deferred to M3-EVO-2D.
