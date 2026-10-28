# Desktop hot-reload via Compose Multiplatform — migration plan

**Goal:** run AquaLog's UI in a phone-sized desktop window on macOS with Compose
Hot Reload, to iterate on the UI far faster than the Android emulator cycle
(as the sibling Yams app already does with `:app:hotRunJvm`).

**Scope framing (deliberately lazy):** we want a *dev harness* for UI iteration,
not a shippable desktop product. On desktop the gateway interfaces already
declared in `:shared` (`AuthGateway`, `AquariumRepository`, `ProBillingGateway`)
are wired to **fakes**, so there is no need to port Supabase, RevenueCat,
WorkManager or secure storage to the JVM.

## Why this is a migration, not a config tweak

Yams was Compose Multiplatform from the start (shared UI in `feature/*` and
`core:ui`, a `jvm()` target, a desktop entry point). AquaLog is Android-only on
the UI side: the whole UI lives in `:androidApp` (a `com.android.application`
module using **Android** Compose, `androidx.compose.*`), and it is wired to
`Activity`, runtime permissions, native `DatePickerDialog`/`TimePickerDialog`,
Android `ViewModel`/`SavedStateHandle`, and `collectAsStateWithLifecycle`. None
of that exists on Compose Desktop, so the UI has to move into a multiplatform
module and be decoupled from those APIs first.

## Measured cost drivers (as of this writing)

| Item | Volume | Difficulty |
|---|---|---|
| **Strings** (`R.string` / `stringResource`) → Compose Resources | **742 call sites** + localized `strings.xml` | the bulk, mechanical |
| UI to move into a common module | ~6 000 lines (`OnboardingUi` 1514, `HomeViewModel` 2220, `TrendsUi` 697, `TimelineUi` 446, …) | moderate |
| `ViewModel` / `SavedStateHandle` | 11 sites → multiplatform `androidx.lifecycle` | mechanical |
| Platform seam (date/time pickers, file import/export, notification permission, `Uri`/`Intent`, `Context`) | ~35 sites → `expect`/`actual` + Material3 picker composables | moderate |
| Data/domain layer on JVM | `:shared` already has a `jvm()` target | done |

## Stages (each keeps Android green and is shippable on its own)

### Stage 0 — Feasibility spike (½–1 day)
Add Compose Multiplatform + Compose Hot Reload to the version catalog. Create a
`:ui` module (KMP, `jvm` + `android`). Add a desktop `main()` with a phone-sized
window (~412×915) showing `AquaLogTheme { … }`, plus a `.run` config mirroring
Yams (`hotRunJvm`, `--auto`). **This already gives a running hot-reload window on
the Mac** (even if empty) — the real quick win, and it validates the toolchain
version alignment (Compose MP ↔ Kotlin 2.4.20, Room3 on JVM) before investing in
the rest.

### Stage 1 — Strings migration (1–2 days)
Move `strings.xml` (and locales) into `commonMain/composeResources/`, and replace
the 742 `R.string` / `stringResource(R.string.x)` sites with
`stringResource(Res.string.x)`. Done while the UI still lives in `:androidApp`
(Compose Resources work on Android too), so the diff is isolated and Android
stays green.

### Stage 2 — Platform seam (½–1 day)
`expect`/`actual` for file import/export (PortableData) and notification
permission; replace native date/time dialogs with Material3 `DatePicker` /
`TimePicker` composables. Android `actual` keeps current behavior; JVM `actual`
uses a file dialog / no-op permission.

### Stage 3 — ViewModels on KMP (½ day)
Swap Android `ViewModel` / `SavedStateHandle` / `viewModels()` for multiplatform
`androidx.lifecycle` + `lifecycle-viewmodel-compose`, and the common variant of
`collectAsStateWithLifecycle`.

### Stage 4 — Move the UI into `:ui` (1 day)
Relocate the `*Ui.kt` files, theme and `HomeViewModel` into `commonMain`; switch
`androidx.compose.*` imports to `org.jetbrains.compose.*`. `:androidApp` becomes
a thin Activity host.

### Stage 5 — Desktop entry + fakes (½ day)
Desktop `main()` wires the fake gateways and launches the real navigation at
phone size with hot reload. The actual screens now iterate on macOS.

## Estimate & risk

Rough total **~4–6 focused days**, strings ≈ ⅓. Main risk is version alignment
(Compose Resources, Room3 on desktop, KMP ViewModel with Kotlin 2.4.20) — which
is exactly what Stage 0 exists to surface before the large stages start.
