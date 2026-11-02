# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Tiny Fast-Food Story** is a Vietnamese-language fast-food restaurant management game (landscape, 3D chibi diorama). The whole game is one self-contained `index.html`: CSS, body markup and a single inline `<script>`. There is no build step, no `package.json` and no test suite in the repo. An Android APK is produced by CI.

Standing requirements from the owner:
- All player-facing text is Vietnamese with diacritics.
- Use rounded fonts (Nunito/Quicksand) and a warm pastel "Tiny Cafe Shop Story" look.
- Draw all art in code (inline SVG, CSS, Three.js primitives). Don't add image files.
- Save progress in LocalStorage and keep a Reset button.
- Write complete code with no "…rest of code" placeholders.

## Commands

- **Run:** open `index.html` directly, or serve it with `python3 -m http.server 8765` and browse to http://localhost:8765/.
- **Syntax-check the game script.** This is the only static check available:
  ```bash
  python3 -c "import re;s=open('index.html',encoding='utf-8').read();open('/tmp/game.js','w').write(re.findall(r'<script>(.*?)</script>',s,re.S)[-1])" && node --check /tmp/game.js
  ```
- **Browser checks** are ad hoc with Playwright and Chromium; nothing is committed.
  - Headless WebGL needs `--use-angle=swiftshader --enable-unsafe-swiftshader`.
  - If the CDNs are unreachable, `page.route` them to local copies: `npm i three@0.149.0`, plus a Tailwind 3.4 CLI build that scans `index.html`.
  - Game time advances per rendered frame (dt is capped at 0.25 s), and SwiftShader frames are slow. Wait on conditions instead of fixed timeouts.

## Versioning and update announcements

- **For each release:**
  1. Bump `GAME_VERSION`.
  2. Prepend an entry to `CHANGELOG` in the section headed `PHIÊN BẢN & THÔNG CÁO CẬP NHẬT`.
  3. Update `version.json` to the same version and notes. CI fails when the two versions differ.
- **Returning players** see the "Có gì mới" modal once per version. The last seen version is stored in `localStorage` under `tinyFastFoodStory_seenVersion`.
- **New-version banner:** the loading screen fetches `version.json` from the `main` branch raw URL and shows a banner when that version is newer than the running build.
- **Save data** lives in `tinyFastFoodStory_save_v2`. `loadGame()` merges and sanitizes it, so new fields need defaults in `createDefaultState()`. Changing the key wipes every player's progress.

## CI: APK build (`.github/workflows/build-apk.yml`)

- **Trigger:** pushes to `main` (and the dev branch listed in the workflow) that touch `index.html`, `version.json` or the workflow.
- **Build steps:**
  - Wraps `index.html` in a Capacitor 6 Android app.
  - Inlines Tailwind and Three.js so the app works offline.
  - Forces `sensorLandscape`.
  - Replaces Capacitor's `MainActivity` with `android-native/MainActivity.java` (immersive full screen, draws into the camera cutout, pushes the cutout insets to the page as `window.__setSafeArea(l, t, r, b)` and exposes `TiemDoanSafeArea.get()`), and adds `android:windowFullscreen` to both app themes.
  - Sets `versionCode` to `run_number` and `versionName` from `version.json`.
  - Builds a debug APK and republishes the `apk-latest` pre-release, with notes taken from `version.json`.
  - Signs with the committed key `android-signing/debug.keystore` (standard debug credentials: store/key password `android`, alias `androiddebugkey`), so a new APK installs over the old one and keeps player data. Don't regenerate or replace this key, or every player must uninstall and loses progress.
- **The inlining string-matches the exact `<head>` tags.** Change these together with the workflow:
  - `<script src="https://cdn.tailwindcss.com"></script>`
  - `<script src="https://cdn.jsdelivr.net/npm/three@0.149.0/build/three.min.js"></script>`
- **Tailwind is compiled by scanning `index.html`.** The browser Play CDN generates classes at runtime, but the APK build only knows class names that appear literally in the file. Don't assemble Tailwind class names from string pieces.

## Architecture (`index.html`)

The file is ordered CSS → body markup → one `'use strict'` IIFE, with `init()` at the very end. Sections are marked by `/* ===== … ===== */` banners with Vietnamese titles; grep the banners to navigate. Function declarations are hoisted, but `const` objects (data tables, `ART`, `ZONES`, `World`, `Tutorial`) must be defined above `init()`.

The script runs top to bottom in this order:

1. **Data.** Constants, `INGREDIENTS`, `COOKABLES`, `LAYERS`, `RECIPES` (`unlockLv` gates the menu), `FLAVORS`, `UPGRADES`, `LEVELS` (XP thresholds, max customers in the shop, spawn interval, level-up reward), `STAFF`/`STAFF_SKILL`, character `LOOKS`/`CHEF_LOOKS`, dialogue pools, and the version/`CHANGELOG`.
2. **`ART`.** Generators that return SVG strings (2D chibi characters, food, props, icons) for panels, popups and the loading/profile screens. Results are cached with `cached(key, fn)`. Shared gradients live once in the hidden `<svg id="svg-defs">` and are referenced as `url(#g…)`; never make that element `display:none`.
3. **Core.**
   - A single mutable `state` object (version 2), persisted by `saveGame()`.
   - Inventory stored as batches `{qty, age}`, with FIFO `invTake()` and overnight spoilage.
   - `Sound` (WebAudio).
   - A `Modal` queue. Any open modal pauses the simulation through `isPaused()`.
   - Toasts, popups, floating text and tap effects.
4. **Screens and panels.** The game moves loading → profile → game through `showScreen()`. `switchScene(name)` opens the right-side zone panel for `counter|grill|assembly|drinks|serve|storage|shop|staff` (`'world'` closes it) and tells `World` to focus that zone. Each panel is a `<section id="scene-…">` inside `#zone-panel`. `currentScene` holds the open panel. The panel width is the CSS variable `--zp-w`; `storage|shop|staff` (`WIDE_SCENES`) widen it via `#screen-game.panel-wide`.
5. **Gameplay.**
   - **Customers** move from `state.queue` through `takeOrder()` into `state.waiting`, where they get a ticket and a food-wait patience bar. `moodOf()` derives their mood from patience.
   - **Kitchen:** `state.kitchen.cook` has 5 slots (0–2 grill, 3–4 fryer, per `SLOT_STATION`), plus holding, plate, tray, and a `drink` filled by press-and-hold pouring.
   - **Grading:** `evaluateBurger/Side/Drink` return issues. A major issue makes the customer angry and they don't pay; minor issues cut the score. `resolveCustomer()` applies the outcome.
   - **Day cycle:** `prep → open → closed`. `endDay()` applies spoilage and rent, then `startNextDay()` runs.
   - **Random events** (power outage, spoilage) are scheduled per day in `state.events`.
   - **Levels:** `state.xp` is total XP; `curLevel()` derives the level from `LEVELS`. `state.dayLv` is locked in `beginPrep()` and drives the menu, spawn rate, max customers (`maxCustomers()`), order size, patience, critics and events for that day. `gainXP()` adds XP (serving via `serveXP()`, end of day `DAY_XP`/`PERFECT_DAY_XP`); `announceLevels()` shows each level-up once (`state.levelSeen`) and pays the reward. Old saves without `xp` get the level matching their day (max 5).
   - **Staff:** `state.staff[k] = {hired, skill, days}`; `updateStaff(dt)` runs the cashier, griller, drinker, assembler and waiter ticks. They work from `openNeeds()` (items the waiting orders still lack). Drinks, burgers and the waiter's delivery are timed jobs in `state.kitchen.jobs` (saved). Wages are paid in `endDay()` by `payWages()`; staff quit if the shop can't pay.
6. **Tutorial.** `TUTORIAL_STEPS` are condition-driven, with `target`, `done` and `requires`/`backTo` fields. `Tutorial.update()` runs every frame to place the spotlight shades, ring and hand. While `state.tutorial.active`, the clock, patience, spawning and events are frozen.
7. **3D world (`World`, Three.js r149).**
   - Built from primitives with `MeshToonMaterial`. Three.js runs in legacy color management, so hex colors render as written. Keep light intensities low or the pastels wash out.
   - **Pull model:** `World.update()` reads `state` every frame. It creates and moves customer avatars (door → `QUEUE_SPOTS` → `SEATS`); avatars leave with the mood passed to `World.customerExit()`. It rebuilds grill, plate, tray and cup meshes when their signature string changes, and drives lighting from the clock and power state.
   - HTML labels (`#zone-tags`, `#cust-tags`) are projected from 3D each frame.
   - Taps raycast the zone hitboxes and avatars.
   - The camera uses `setViewOffset` so the focused zone stays visible beside the panel.
   - Layout positions live in `ZONES`, `QUEUE_SPOTS`, `SEATS` and `SLOT_POS`; staff stand at `STAFF[k].spot`. `syncStaff()` walks hired staff in through the door and fired staff out; the waiter walks to the seat (`waiterGo/waiterBack`).
8. **Loop and input.**
   - `frame()` calls `update(dt)`, then `World.update/render`, then the HUD and per-frame UI, then `Tutorial.update()`. All of it sits in one try/catch, so an exception silently skips the rest of that frame; check the console.
   - Buttons use `data-action` and are handled by one document click listener (the `switch` in the event-delegation section). Add a `case` there for new actions.
   - Pour-button hold, tutorial buttons, the profile form and modal buttons have their own listeners.

### Conventions

- **Rendering:** structural changes re-render with `innerHTML` (`refreshKitchen`, `renderCounter`, `renderServe`, `refreshAll`). Per-frame changes update existing nodes: `[data-pat]` patience bars, `[data-mood]` faces, cook markers and the drink level. Don't rebuild clickable DOM every frame, or clicks get lost. Renders caused by staff go through `renderLater()`, which waits while a finger is held down.
- **Panels on phones:** the station panels are laid out so the main controls fit in ~310px of height on a landscape phone (two columns, small icons). `ico()` sets icon sizes inline, so CSS overrides of `.ico` sizes need `!important`. Keep long hints in `.panel-hint` (hidden on short screens).
- **Safe area:** `--sal`/`--sar` combine `env(safe-area-inset-*)` with the APK's `--nsa-*` values; edge-anchored UI (HUD, scene nav, panel padding, power panel) adds them.
- **`.hidden`** is `display:none !important`, because Tailwind and the custom display rules would otherwise override it.
- **Layout** is landscape-first. `@media (max-height: 520px)` compacts the UI for phones held sideways, and a rotate hint covers the game in portrait.

## Progress (as of 2026-11-02)

Shipped, newest first (each version is a `CHANGELOG` entry):
- **3.2.0:** shop levels Lv 1–10 (Lv 1–2 allow at most 2 customers at once, then the shop gets busier), XP and level-up rewards, menu unlocked by level; hireable staff (cashier, griller, drinks, assembler, waiter) with wages, training and 3D avatars plus a staff corner; compact two-column zone panels for landscape phones; the APK now fills the camera-cutout strip with safe-area padding.
- **3.1.1:** immersive full screen (APK hides status/navigation bars via `android-native/MainActivity.java`, which CI copies over Capacitor's; mobile browsers call `requestFullscreen` on entering the game), and fixed floor z-fighting (the floor base box top sat exactly at the floor plane's y=0).
- **Fixed APK signing:** all builds now share `android-signing/debug.keystore`. Players still on an older APK must uninstall once; after that, updates install in place.
- **3.1.0:** in-game update announcements, the `version.json` new-version banner, versioned APKs and release notes, compact loading/profile screens on short landscape screens.
- **3.0.0:** landscape Three.js restaurant with tappable zones, walking 3D customers, state-mirrored food, time-of-day lighting, and upgrades shown in the room.
- **2.0.0:** SVG chibi art, per-station panels, tutorial, loading and profile screens, tap effects, one-by-one customer queue, 12-item menu.
- **1.0.0:** core loop (cook, assemble, serve, grade), inventory with spoilage, power-outage and spoilage events, 4 upgrades.
- **CI:** APK workflow publishing the `apk-latest` pre-release.

Open items:
- PR #1 (dev branch into `main`) is not merged yet. Until it is, the new-version banner can't find `version.json` on `main`.
- Untested so far: frame rate on real phones and loading from the live CDNs (development tests used local copies under SwiftShader). A low-graphics option (no shadows, lower pixel ratio) was offered if phones stutter.
