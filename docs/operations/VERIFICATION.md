# Reproducible verification — M3-T03A-1

This is local/CI preparation, not a deployment pipeline. Hosted CI execution is
**UNVERIFIED** until a completed GitHub Actions run exists. No staging, monitoring,
backup system, production release or audio approval is delivered by this task.

## Toolchain and installation

- Node **26.6.0**, declared in root `.nvmrc`, with bundled npm **11.18.0**.
  The supported Node 26 major matches the existing `node:module.registerHooks`
  tests and the verified local runtime. Do not substitute older Node test loaders.
- Java **21**; hosted job selects Temurin 21. Local evidence uses Oracle 21.0.5.
- Maven **3.10.0** via the committed wrapper, Spring Boot parent/plugin management.
  The distribution ZIP SHA-256 is pinned; bytes were checked against Maven
  Central's published SHA-512 before recording this checksum.
- Docker with a working daemon, PostgreSQL Testcontainers (`postgres:16-alpine`).
- Python 3 standard library for report/package auditing. No pip dependency required.

From the root, select Node using your installed version manager (`nvm use` if
available). In `frontend/`, use `npm ci --no-audit --no-fund`. This replaces only
installed dependencies from the existing lockfile; it does not update dependencies.
Do not copy production credentials or `.env.local` into a verification workspace.

Node distribution metadata: <https://nodejs.org/dist/index.json>.
Support schedule: <https://github.com/nodejs/Release>.

## Frontend local verification

Run in `frontend/`, stopping at the first nonzero exit:

```sh
npm ci --no-audit --no-fund
npm test
npm run typecheck -- --incremental false
npm run lint
BACKEND_INTERNAL_URL=http://127.0.0.1:1 NEXT_TELEMETRY_DISABLED=1 npm run build
test -s .next/BUILD_ID
node -e 'const fs=require("node:fs"); for(const p of [".next/routes-manifest.json",".next/build-manifest.json"]) JSON.parse(fs.readFileSync(p,"utf8"));'
```

No backend credentials are required. The configured rewrite target is deliberately
unreachable; build must not require a live database/backend. Root cookie-based
locale resolution makes app pages request-dependent. Auth bootstrap/API reads run
at runtime; `next/font/google` can download public fonts during build, so an offline
font-download failure is not proof of a source defect. No audio/provider operation
is part of build. A successful build does not approve deployment.

## Backend local verification and isolation

Inspect datasource wiring before adding/running database tests. Every current
`@SpringBootTest` class declares `@Container` PostgreSQL + `@ServiceConnection`.
Never substitute H2 or a developer database. Docker must already be available;
these commands do not start Docker Desktop or provision remote infrastructure.

Run in `backend/`, stopping at the first failure:

```sh
java -version
docker info --format '{{.ServerVersion}}'
python3 -m unittest discover -s ../scripts/tests -p test_verify_backend_reports.py -v
./mvnw --batch-mode --no-transfer-progress -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:1/englishflow_test_fallback_disabled -Dspring.jpa.show-sql=false clean verify
python3 ../scripts/verify-backend-reports.py
```

Surefire forces test profile plus impossible datasource and Flyway fallback URLs.
It also disables Spring Boot debug mode so a workstation `DEBUG` environment flag
does not enable credential-bearing HTTP DTO debug output during tests.
The test-profile resource contains no developer password or environment credential
fallback. Spring Boot service connection details override these inert defaults for
both application and Flyway. `PostgreSQLConnectivityTest` compares actual JDBC URLs
for both connections with the running container URL, checks database identity and
the V12 schema, and prints a credential-free `ISOLATION PASS` target.
Do not override Surefire properties with real JDBC URLs or add real credentials.
Never run `spring-boot:run` or local dev scripts as verification substitutes.

Some legacy tests have `disabledWithoutDocker=true`. Docker prerequisite failure
stops the workflow, and the report gate rejects **any skipped test** after Maven,
preventing a partial regression from becoming a successful verification job.
`clean verify` removes Maven build outputs only, applies migrations inside test
containers, and packages the application. No Docker-wide cleanup is performed.

The read-only report helper requires Surefire reports, executed tests, zero
failures/errors/skips, one valid Spring Boot JAR, and no loose audio binaries in
the JAR. It does not execute or publish artifact contents. Its 17 fixture-based
tests retain the original six checks and add root/counter validation, mixed-report
failure masking, malformed XML, DTD/entity rejection and linked-report rejection.
Frontend manifests are checked separately.

Report parsing accepts only the unqualified `<testsuite>` document root emitted
by the project's 26 existing Surefire XML reports. `<testsuites>` wrappers and
namespaced roots are unsupported and rejected. All four counters are mandatory
ASCII decimal integers, validated per report before aggregation; negatives and
outcome totals exceeding the suite's test count fail. Each suite must have zero
failures/errors/skips, and the final total must execute at least one test. Python
integers aggregate without fixed-width overflow. DTDs (including entity
declarations) and linked/non-file report entries are rejected. Reports are read
without modification; XML content is never executed or resolved as external data.

## Workflow and supply-chain policy

`.github/workflows/verify.yml` defines independent frontend/backend jobs on
`ubuntu-24.04` (Docker available), with 20/30-minute timeouts. Events are main
push, ordinary pull requests and manual dispatch. Writing the file does not
authorize this task to push or dispatch it.

Official action release/tag commit SHAs were verified through the GitHub API:

| Action | Release | Immutable commit |
| --- | --- | --- |
| actions/checkout | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| actions/setup-node | v7.1.0 | `949feb2413d6458794dcd2491c4babbbce0c15c1` |
| actions/setup-java | v6.2.0 | `77dd5b2ce225ea11469928ac317850245a74ae78` |

Release sources: <https://github.com/actions/checkout/releases/tag/v7.0.1>,
<https://github.com/actions/setup-node/releases/tag/v7.1.0>,
<https://github.com/actions/setup-java/releases/tag/v6.2.0>.
Runner inventory: <https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md>.
These actions use Node 24 internally, separately from the application Node 26 runtime.

Permissions are `contents: read`; checkout credential persistence is disabled.
There is no `pull_request_target`, deployment environment, secret reference,
artifact upload, package publication or cloud/provider step. npm download cache
is keyed by lockfile and Maven dependency cache by pom/toolchain using official
action cache mechanisms; node_modules, `.next`, database volumes and credentials
are not cached. Cache changes must never replace lockfile-based installation.

Update action SHAs/runtime pins through a reviewed change: verify the official
release/tag SHA, inspect release notes/action inputs/runner compatibility, rerun
local checks, and obtain actual hosted-run evidence. Do not silently follow tags.
Java patch, hosted image, Maven transitive dependencies and PostgreSQL image tag
are not fully immutable; this foundation is not a byte-identical/hermetic build.

## Failure interpretation and media restrictions

Each required command has its own job step. A failure stops subsequent normal
steps; the backend report gate also runs after Maven failure and cannot turn the
job green. Read the first failed step and original command log; do not add retries,
skip tests or change handlers merely to force a pass. Missing Docker, registry
access or public-font access is an environment blocker to report explicitly.

The historical vi-VN Review language-select test observation remains unresolved.
Preserve exact failure logs/context if it recurs; do not call it fixed or flaky.

Audio licensing and human QA gates remain unchanged. CI never synthesizes,
downloads, approves or promotes production media. Existing audio integration tests
use local test fixtures, not licensing evidence. No paid API key is required.
No production deploy permission is granted by passing this workflow.
