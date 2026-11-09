# EnglishFlow localization — M3-EVO-1A / 1B / 1C-1

## Scope and resolution

Localized scope now includes Login/Register, authenticated navigation/header,
Dashboard and its activity heatmap, Settings, session-loading copy and the shared
Focus header. Individual Vocabulary/Collections/Review/Dictation/Shadowing/Quiz
feature pages, public landing page and document metadata are not translated in
M3-EVO-1C-1. User content is never automatically translated. M3-EVO-1B account
preference behavior and V11 remain unchanged by this frontend-only increment.

`frontend/src/lib/i18n/config.ts` owns the supported values `vi-VN` and `en`.
The root server layout reads `englishflow_locale` using Next.js `cookies()`.
A valid cookie is preserved; absent, empty or unsupported values resolve to
`vi-VN`, the Product Owner's approved default. Browser Accept-Language does not
override this rule. Resolution is exact: `en-US` is unsupported in this task.

The server renders `<html lang={locale}>` and passes that same value to
`LocaleProvider`. No client cookie detection changes the initial render. Changing
the native language select updates context, document language and the cookie
without remounting the form, refreshing the route or submitting credentials.
The root layout remains a server component; only the locale context, switcher
and translated auth chrome are client components in addition to the existing forms.
Reading cookies makes routes using this root layout request-dependent.

The preference cookie is non-sensitive, host-only, `Path=/`, `Max-Age=31536000`,
`SameSite=Lax`, with `Secure` when the page uses HTTPS. It is intentionally
JavaScript-readable/writable and is separate from `JSESSIONID` and `XSRF-TOKEN`.
Persistence depends on browser cookie support and ends when users clear cookies.
Explicit account preferences synchronize on a new authenticated session/device.
There is no cross-tab live synchronization.

The root document language follows the preference globally; remaining feature
screens still contain English pending later tasks. This is partial application
localization, not a claim that every screen has been translated.

## Using translations

- Add canonical English keys in `dictionaries.ts`; Vietnamese must satisfy the
  same `Record<TranslationKey, string>` type.
- Use `const { t, locale } = useLocale()` and `t("loginTitle")` for UI text.
- Missing localized entries fall back to English. Unknown runtime keys produce
  a safe generic message, never an internal key or arbitrary backend text.
- Zod auth messages store stable translation keys; render them with
  `translate(locale, message)`. Existing errors retranslate without resetting
  validation, touched fields, password visibility or entered values.
- Auth error alerts store keys from `authErrorKey(error)`, mapped by API code.
  Unknown codes/exceptions use a generic localized fallback. Never display raw
  backend messages, rejected values or exceptions in the form.
- `noValidate` delegates validation presentation to the existing Zod/RHF rules
  so browser-language tooltips do not bypass translated validation. Constraints
  and backend validation remain unchanged.
- Do not translate vocabulary, reference transcripts, quiz answers or user input.

English and Vietnamese dictionaries currently ship together because the auth
catalog is small. No runtime translation service or new dependency is needed.
Inter includes the Vietnamese subset. Routes remain unchanged. M3-EVO-1B adds optional registration/settings locale
fields and nullable locale response fields; existing fields remain compatible.

## Automated verification

Run from `frontend/`:

```sh
node --test src/lib/__tests__/auth-localization.test.mjs
npm test
npm run typecheck
npm run lint
```

`auth-localization.test.mjs` renders actual production Login/Register, AuthLayout,
LocaleProvider, AuthProvider, Zod/RHF and API client. The test-only TSX loader uses
the installed TypeScript compiler and Node `registerHooks` (verified on Node
26.6.0). Only Next router/link/cookie/font adapters and fetch responses are
substituted. Existing regression tests are not replaced or disabled.

Coverage includes both locales, preference remount/reload simulation, native
select semantics/focus, form/visibility/validation preservation, loading text,
known/unknown errors, CSRF bootstrap/header, unchanged successful auth payloads
and redirects, dictionary fallback, root SSR language and JSDOM hydration.
These tests do not establish live backend authentication or real browser behavior.

## Manual browser smoke procedure

With the normal local frontend/backend running, use an isolated browser profile:

1. Clear only `englishflow_locale`; open `/login` and `/register`. Confirm
   Vietnamese labels, placeholders, page heading and `html.lang === "vi-VN"`.
2. Use Tab to reach the language select, arrows to choose English and Enter to
   confirm. Check labels, loading/error messages, focus visibility and no submit.
3. Enter form values, reveal the password, trigger validation and switch locale.
   Confirm all values, visibility and error state remain; errors retranslate.
4. Reload, follow Login/Register links and use browser back/forward. Confirm
   language persistence and correct server HTML on a fresh document request.
5. Set `englishflow_locale=fr`; reload. Confirm Vietnamese fallback. Restore en/vi.
6. Try invalid credentials and duplicate registration with authorized test data.
   Confirm safe localized errors. With valid test credentials confirm the normal
   dashboard redirect and unchanged session/CSRF behavior.
7. Inspect at 320 px, 375 px and desktop widths, including 200% text zoom. Check
   no horizontal scrolling, clipped Vietnamese text or inaccessible controls.
8. Keep DevTools console open through load/switch/reload; check for hydration
   warnings. Check cookie scope/expiry and Secure on HTTPS.

No Playwright test infrastructure is installed in the repository. An ad hoc
Chrome headless smoke can supplement these steps but is not a full backend E2E,
screen-reader audit, Safari test or physical mobile-device certification.

## Verification record — 2026-11-07

- `npm test`: exit 0; 137 tests, 61 suites, 137 pass, 0 fail, 0 skipped.
  Includes 15 production-component localization tests, all passing.
- `npm run typecheck`: exit 0. `npm run lint`: exit 0.
- `git diff --check`: exit 0.
- HTTP GET smoke against Next.js on port 3000: 8/8 returned 200 with matching
  HTML language and translated description (Login/Register × no cookie/en/vi-VN/fr).
- Ad hoc Chrome headless smoke: exit 0. Desktop Tab navigation, select change,
  Login field/password visibility preservation, localized auth error masking,
  reload persistence, Register validation/value preservation, 320/375 px Register
  and mobile Login overflow checks passed. No hydration/runtime exception found.
  Auth endpoints were intercepted with deterministic responses; no account was
  created and no live backend authentication was claimed.
- Local smoke driver: `/tmp/englishflow-locale-browser-smoke.mjs`; isolated Chrome
  profile under `/tmp`, process stopped by the driver. The initial native picker
  arrow/Enter automation timed out on headless macOS; final smoke verifies Tab
  navigation and dispatches the select change in-page. Full native picker and
  screen-reader operation still have the manual procedure above.
- Frontend regression log: `/tmp/englishflow-m3-evo-1a-tests.log` (local temporary
  evidence, not a tracked artifact). No backend regression or production build
  was run for this frontend-only task.


## M3-EVO-1B — Account preference semantics

1. Initial SSR and the initial client render use the same validated guest cookie
   (`vi-VN` when missing/unsupported). Server rendering does not call authenticated
   profile APIs or make preference writes.
2. AuthProvider uses the existing `/auth/me`, Login or Register response to
   reconcile locale before navigation. A valid explicit account preference wins;
   null/absent/invalid account preference preserves the current guest locale.
   Account bootstrap is asynchronous, so a differing account preference can cause
   one predictable post-hydration language transition. There is no forced reload,
   locale-based redirect or hydration suppression.
3. The current UI sends the selected locale with registration. Older clients may
   omit it and receive null; no non-null DB default/backfill is used. Login and
   bootstrap do not persist a null legacy preference automatically. The first
   explicit authenticated change in Settings is the persistence point.
4. The same LanguageSwitcher appears at the top of Settings. It changes UI/cookie
   optimistically and sends a locale-only PUT to `/api/v1/settings/preferences`
   using the existing API client and CSRF bootstrap/header. Other Settings forms
   do not resend stale locale values.
5. Writes are serialized in selection order. Only the latest selection may alter
   saving/error UI. Older success records the last confirmed value for rollback
   but cannot replace a newer displayed choice. Older failure cannot show a stale
   error. A latest failure rolls UI and cookie back to the last confirmed value
   and displays a localized message inviting the user to select again.
6. Session/selection generations invalidate pending UI results and queued writes
   on logout/account replacement/unmount. A stale `/auth/me` response cannot
   overwrite a later successful login. Logout retains the current guest cookie.
7. Another browser/session reads the stored account preference during bootstrap.
   Independent devices use last committed backend value; there is no live device
   broadcast or global ordering of simultaneous edits across devices.
8. A response lost after server commit may cause a client rollback to its last
   confirmation even though the server saved; next bootstrap reconciles it.
   No successful-save claim is made on failure. Generic existing fetch behavior
   applies; this task does not add global timeout/retry infrastructure.

Deploy migration/backend before the new frontend when rolling out. The old
frontend remains compatible with the additive backend fields. A new frontend
against an old backend that ignores locale updates treats an unconfirmed response
as a failed save instead of claiming persistence. Java compatibility constructors
preserve existing test/client construction of request records.

### Verification — M3-EVO-1B (2026-11-07)

Commands from backend/:

```sh
./mvnw -B -Dtest=LocaleIntegrationTest test
./mvnw -B test
```

Commands from frontend/:

```sh
node --test src/lib/__tests__/account-locale.test.mjs src/lib/__tests__/auth-localization.test.mjs
npm test
npm run typecheck
npm run lint
```

LocaleIntegrationTest has 12 PostgreSQL Testcontainers tests: guest registration
locale; legacy null/login no write; both allowlisted updates; invalid update
rollback; invalid registration; anonymous/CSRF/cross-user checks; old payload
compatibility; new session/logout; database CHECK; separate-schema V10→V11 upgrade
with existing row preservation and nullable/no-default inspection. Existing
regression covers session rotation, account deletion and learning-history rules.
No existing test was removed or disabled. The final locale/auth component
suites contain 30 tests (15 account-specific and 15 auth-foundation cases).

account-locale.test.mjs tests actual Settings/Login/Register, Sidebar logout,
AuthProvider, LocaleProvider and API client with mocked HTTP responses. Coverage
includes legacy/explicit precedence, registration inheritance, CSRF, reload,
rollback/retry, serialized rapid changes, stale success/failure/bootstrap/session
responses, invalid server values and SSR hydration with a differing account
preference. These component tests are distinct from browser/backend E2E.

Real browser E2E driver: `python3 /tmp/englishflow-1b-e2e-env.py` (local temporary
verification artifact), using `/tmp/englishflow-1b-browser-real.mjs`. It starts
an ephemeral PostgreSQL container and isolated Spring Boot on port 8080, routes
through the running Next.js on port 3000 and uses two independent Chrome profiles.
Eight scenarios passed with real APIs: English registration inheritance,
Vietnamese Settings save/reload, logout/cookie preservation, a second browser
receiving stored locale, subsequent new-session synchronization, CSRF/invalid
locale rejection, legacy null login without a write, and no hydration/runtime
exception. Exit 0. The driver stops its backend and removes its temporary
container; no production database is used. This is an ad hoc Chrome test, not a
committed Playwright suite or Safari/physical-device certification.

**Regression isolation correction:** the first full backend regression passed
328 tests but three pre-existing smoke classes used the dev datasource on port
5432. Flyway therefore applied the additive V11 to the local dev database. It
contains no UPDATE/DELETE/backfill; a read-only check returned migration `11|t`
and 85 accounts with 0 non-null locale preferences. No before/after row-count
baseline was captured, so no broader row-count claim is made. All three smoke classes
now use Testcontainers (`EnglishFlowApplicationTests`,
`PostgreSQLConnectivityTest`, `SecuritySmokeTest`); this necessary test-fixture correction prevents
future regression from depending on the dev database. Do not roll back/drop V11.
V1–V10 were verified byte-identical to HEAD.

Backend final verification: `./mvnw -B test` exit 0, 328 tests, 0 failures,
0 errors, 0 skipped (3:27 min). All SpringBootTest classes have ServiceConnection;
final Flyway log has zero dev-datasource references. Targeted locale tests: 12/12.

Frontend final verification: `npm test` exit 0, 152/152 passed, 62 suites,
0 failed, 0 skipped; typecheck and lint exit 0.

Local logs: `/tmp/englishflow-1b-backend-targeted.log`,
`/tmp/englishflow-1b-backend-isolated-regression.log`,
`/tmp/englishflow-1b-frontend-regression.log`, and
`/tmp/englishflow-1b-e2e-backend.log`. The initial backend locale test run had two
incorrect expected error identifiers; they were corrected to the existing
`VALIDATION_FAILED` envelope. The initial browser driver required a boolean CDP
wait fix; the successful final E2E rerun is the evidence above.

Manual follow-up: repeat the authentication smoke procedure above, use Settings
language control with a test account, then log in in a separate browser profile.
Verify offline/failed saves show rollback and never raw errors. At the 1B checkpoint, full Settings translation was reserved for M3-EVO-1C;
it is now delivered by M3-EVO-1C-1 below.


## M3-EVO-1C-1 — Application shell, Dashboard and Settings

### Coverage and usage

- Navigation uses Overview/Tổng quan, Vocabulary/Từ vựng, Study sets/Bộ từ,
  Review/Ôn tập, Listening/Luyện nghe, Speaking/Luyện nói, Quiz/Kiểm tra and
  Settings/Cài đặt. The routes, entities and API names are unchanged.
- The shell has localized section names, current-location labels, mobile drawer,
  sign-out, skip-to-content and session checking state. Milestone badges, static
  backend-port/session diagnostics and the non-functional Palette badge were
  removed from learner navigation; no health endpoint or operational feature
  was removed. The shared Focus header is translated; its unsupported Esc badge
  was removed without changing any learning module keyboard handlers.
- `shell-dictionaries.ts` contains the typed English/Vietnamese shell catalog,
  merged into the existing dictionaries. Use `t(key, { count, name, ... })` for
  named parameters; key parity and parameter parity are checked in tests. Missing
  locale entries still fall back to English. Data interpolates as escaped text.
- Dashboard presents one filled primary action: due review when due count is
  positive, add first words for an empty library, otherwise explore vocabulary.
  Secondary links retain Vocabulary, Dictation, Shadowing and Quiz routes.
  Daily goal, all four counts, streak state, recent activities and the full
  52-week heatmap remain based on existing GET responses. No recommendations,
  activity events or progress are synthesized. Unknown activity types use a
  generic translated label rather than exposing an enum identifier.
- Numbers and dates format with the selected locale. Dashboard's calendar date
  and heatmap dates remain the backend-provided date strings; no timezone
  rebucketing occurs. Recent times use the account/heatmap timezone.
- Heatmap labels, legend, states, hover text, modality breakdown and day details
  are localized. Existing intensity values/counts are not recalculated. Keyboard
  entry uses the final non-future date, rather than a disabled future Sunday.
  Home/arrow navigation stays within the visible view. The compact view is
  honestly labelled “Recent 14 weeks” (98 cells), replacing the former 90d label.
  Details remain inline as a labelled region, rather than claiming modal focus
  behavior; closing them returns focus to the selected calendar cell.
- Settings preserves profile, timezone, goal, speed, password and deletion API
  payloads. Feedback/validation store translation keys so switching languages
  preserves drafts and retranslates existing messages. `shellErrorKey` uses
  stable codes and a safe fallback; no raw server message is rendered.
- Settings tabs support arrows/Home/End and roving tabindex with labelled panels.
  Confirmation uses the existing Base UI Dialog with focus containment/return.
  Password confirmation and submission guards remain. Successful deletion calls
  the existing session refresh before redirecting to Login, avoiding stale local
  auth state redirecting an erased account back into the workspace.
- Shared focus visibility and prefers-reduced-motion support do not change design
  tokens. Settings status/warning presentation uses the neutral palette and
  explicit wording. Dangerous account actions remain in a separate section.

### Verification and isolation

Only frontend tests and browser smoke with API mocks are needed for this task.
Do not launch the prior live-database scripts or backend dev setup for these
checks. Backend/infra/migrations/package files are compared to the task-start
hash manifest; the existing uncommitted 1A/1B work is preserved.

From `frontend/`:

```sh
node --test src/lib/__tests__/shell-localization.test.mjs
npm test
npm run typecheck
npm run lint
```

The new suite renders production WorkspaceLayout, Sidebar/Header, Dashboard,
HeatmapCalendar, Settings and shared dialogs with the real locale/auth/API code.
Only Next routing adapters and HTTP responses are mocked. Cases include both
languages, populated/empty/caught-up/error Dashboard states, unchanged counts and
364 cells, compact-grid keyboard bounds, Settings persistence and CSRF headers,
safe errors, feedback/draft preservation, password/deletion guards, keyboard
navigation, mobile menu semantics, dictionary/parameter parity and hydration.
The existing account-locale test now expects the translated sign-out label;
no test is disabled or replaced.

Chrome smoke driver (local temporary artifact):

```sh
node /tmp/englishflow-1c1-browser.mjs
```

It uses isolated Chrome profiles and intercepts all `/api/v1/*` requests with
fixtures. It must not be described as real backend E2E. It checks vi/en at 320,
375, 768 and 1280 CSS pixels, retains Dashboard fixture counts and 364 grid cells,
checks all Settings sections, mobile drawer Escape/focus return, confirmation
Tab containment/Escape/focus return, locale save/reload with the CSRF header,
and reduced-motion behavior. A 200% root-font-size test supplements narrow-width
checks; it is not native browser-UI zoom or a physical-device test. Safari and
screen-reader certification remain manual follow-up.

Manual review: open Dashboard/Settings in both locales; navigate via Tab and
arrows, use skip-to-content, inspect current-page labels and close dialogs with
Escape. Try profile and preferences using an authorized test account only;
confirm errors are localized and drafts survive language changes. Never perform
account deletion or learning submissions against dev/production as a smoke test.

Logs: `/tmp/englishflow-1c1-shell-tests.log`,
`/tmp/englishflow-1c1-frontend-regression.log`,
`/tmp/englishflow-1c1-browser.log`. Task-start snapshot:
`/tmp/englishflow-1c1-baseline.json`. M3-EVO-1C-2 is not implemented by this task.


### Final verification record — M3-EVO-1C-1

- `npm test` from frontend/: exit 0, 176 tests / 63 suites, 176 passed,
  0 failed, 0 skipped. This includes 24 new production shell/Dashboard/Settings
  cases and all 152 existing frontend cases.
- `npm run typecheck`: exit 0. `npm run lint`: exit 0, no warnings.
- `git diff --check`: exit 0.
- `node /tmp/englishflow-1c1-browser.mjs`: exit 0, 10 grouped Chrome smoke
  scenarios (vi/en × four widths, plus zoom/locale/reduced-motion per locale).
  All API requests were intercepted; no real backend E2E is claimed for 1C-1.
- The 200% root-font-size pass covers all four Settings tabs and the dialog's
  internal content width. An overflow found in the long confirmation button was
  fixed by wrapping action text, stacking mobile buttons and removing nested
  dialog padding. Dialog may scroll vertically; it does not require horizontal
  scrolling. Focus containment checks allow focus-guard updates to settle.
- No browser hydration/runtime exception was detected. Menu Escape returns
  focus to its trigger; confirmation Escape returns focus to the delete action.
- Screenshot evidence: `/tmp/englishflow-1c1-dashboard-320.png`,
  `/tmp/englishflow-1c1-settings-zoom-vi-VN.png` and
  `/tmp/englishflow-1c1-settings-zoom-en.png` (fixtures only).
- No backend tests, database scripts, Flyway migrations or paid calls were run
  in 1C-1. The historical 328-test backend result belongs to 1B. Backend, infra,
  API client contracts and dependency manifests were verified unchanged against
  the 1C-1 task-start hash snapshot.

Known limits: ad hoc Chrome smoke is not a committed Playwright/CI suite; native
browser UI zoom, Safari, physical devices and a full screen-reader audit are not
certified. Feature-page translations, Folder/Study Sets, media providers and AI
assessment remain outside this task. Original roadmap header metadata is still
historical; the latest-delivery notes in PROJECT_STATUS track these increments.
