# Pair

Two people, one room, one line of live text. Whatever one of you writes is the only thing the
other one sees — in the app and in the status bar, as an Android **Live Update**. Separately,
Pair can change your wallpaper when you arrive somewhere.

There is no account, no email, no password, no chat history, no feed and no model behind it.
You open it, it gives you an anonymous identity, you make a room, you read the code out loud.

---

## What is here

| | |
|---|---|
| **Language** | Kotlin, Jetpack Compose, Material 3 Expressive |
| **Backend** | Firebase — anonymous Auth, Realtime Database, Cloud Messaging, Cloud Functions |
| **Min / target SDK** | 26 / 37 |
| **Build** | AGP 9.4.1 (built-in Kotlin), Gradle 9.6.0, JDK 25 |
| **Size** | 8 MB release bundle |

---

## Running it

```bash
# A debug APK on a connected device or a running emulator
./gradlew :app:installDebug

# Everything, including the JVM tests and the rendered screens
./gradlew :app:testDebugUnitTest

# A release bundle
./gradlew :app:bundleRelease
```

`JAVA_HOME` must point at a JDK 25 (Android Studio's bundled JBR is the easy answer):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

`local.properties` needs `sdk.dir` set to your Android SDK.

---

## The Realtime Database is secured

The rules are deployed. An unauthenticated read or write of any part of the tree now returns
`401`, verified from a plain `curl` against the live instance:

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  "https://pair-4791e-default-rtdb.asia-southeast1.firebasedatabase.app/.json"
```

Two things are worth knowing about how that state was reached.

**The rules file was not valid JSON.** The rule expressions are written across several
lines for legibility, and the newlines inside those quoted strings were raw control
characters. Firebase's own parser is lenient and reads it; a strict JSON parser does not.
The file has been escaped, which changes no rule — the parsed rule tree is identical
character for character — and it now parses strictly.

**The rules were never enforced anywhere before this, so nothing about them had actually
been tested.** The database was in public test mode, where every read and write succeeds
no matter what the rules say. The app working proved nothing, because the app worked
*because* nothing was being checked. `database-rules-test/` is what closes that gap:

```bash
firebase emulators:start --only database
cd database-rules-test && npm install && npm test
```

25 assertions, each security property paired with its "must be refused" counterpart —
a member cannot publish a status under their partner's key, a stranger cannot read a room
or join it, a token cannot be rewritten by anyone else. Run against the emulator, which
loads the real rules file, so what passes is what is deployed.

---

## The Cloud Function is written but not deployed

`functions/src/index.ts` pushes a status to the partner's device over FCM. **It is not
deployed, and it cannot be on the current project plan.** `pair-4791e` has
`billingEnabled: false` — the Spark plan — and Cloud Functions, Cloud Run, Eventarc, Cloud Build
and Artifact Registry all require the Blaze plan. The APIs are disabled, so
`firebase deploy --only functions` fails with `SERVICE_DISABLED`.

The function stays in the repository because it is the right answer, and because the trigger bug
it fixed was real: it watched `/rooms/{roomId}/live` with `onValueCreated`, which only fires when
the parent node is created, so it pushed once per room, ever. It is now `onValueWritten` on the
per-uid child, with the sender read from the path rather than the payload.

## How the live status stays live without it

Since a push cannot be sent, the wake-up is done by the app. `LiveUpdateService` is *already*
running for as long as there is a chip — owning a foreground service is what Live Update
promotion requires — so all that was missing was a listener. While the Live Update is up, the
service now watches the room and re-posts the notification whenever the status it should be
showing changes. The partner sets a status, and the chip updates on a closed app.

Three things make that correct rather than merely working:

- **One rule decides what the chip says.** `decideLiveUpdate` in
  `notifications/LiveUpdateDecision.kt` is used by both the room screen and the service. The
  displayed text comes from `LiveTexts.forViewer`, the same expression everywhere, so the chip
  cannot disagree with the room screen. The decision is a pure function precisely so it could be
  tested — 11 assertions cover it.
- **The service is sticky, and recovers.** Android reclaims background processes routinely; on
  restart it recovers the room from preferences and re-reads it, rather than re-posting a stale
  string or dropping the chip.
- **It stops when there is nothing to show.** A blank status, a room that can no longer be read,
  or Live Updates switched off all withdraw the chip and stop the service, so a silent room costs
  nothing.

The cost, stated plainly: one database connection held open for as long as there is a status to
show. It is not polling — it is a single listener on a single node, idle when nothing changes.
It does not survive a force-stop from Android recents, which no app can survive.

---

## How it is put together

```
com.wood.pair
├── data
│   ├── model/        Room, RoomId, LiveText, LocationRule, TimeWindow — pure, and unit tested
│   ├── remote/       Firebase providers and the RTDB→Flow adapters
│   ├── repository/   Auth, Room, FCM token
│   └── local/        DataStore-backed preferences and location rules
├── location/         Geofence registration, the broadcast receiver, boot re-arm
├── notifications/    Live Update building, the FCM service, the foreground service
├── wallpaper/        Import, preview decode, WallpaperManager application
└── ui
    ├── theme/        Material You resolution, transcribed M3 tokens, the motion scheme
    ├── shape/        The custom artwork: blob, starburst, petals, wavy rules, indicators
    ├── components/   Shared chrome — top bar, wordmark, nav bar, avatars, cards
    ├── home/ room/ rules/ settings/ onboarding/
    └── motion/       Press feedback and the reduced-motion-aware rotation
```

### The few decisions worth knowing about

**There is no brand colour.** Every colour resolves from Material You, with a monochrome
black-and-white fallback for devices without it. `ui/theme/Color.kt` names the two places a raw
role would be easy to get wrong — the decor wash and the navigation bar's surface — because both
depend on which way "away from the background" is in the current scheme.

**Cards are `secondaryContainer`, not `primaryContainer`.** In the palette the design was drawn
on, `primaryContainer` *is* the page colour, so a card painted with it disappears completely
rather than looking slightly wrong. `PairSurfaces` names this so the choice is made once.

**The room's input is never hidden.** Paused shows the indicator *above* the field, not instead
of it. A paused room that removed its own field would have no way to be unpaused.

**`isPaused` is driven by the published text, never the draft.** The Live Update shows what the
database holds, so an unsaved draft cannot un-pause anything — and the indicator has to agree
with the notification, or the two contradict each other on screen.

**The wordmark is artwork, not live type.** `pair.svg` is converted to a vector drawable, so the
star is welded to the "i" rather than positioned next to it. The two lines under it are indented
by fractions of the wordmark's known width, which is how the design places them and what keeps
the lockup intact at any density or font scale.

**Room IDs drop `I`, `O`, `0` and `1`.** Those are the four characters people mistype when
reading a code aloud; removing them removes the whole class of "room not found" mistakes. What
is left is 32 symbols, so six characters is a 30-bit space.

**Rules never leave the device.** Location rules are personal behaviour, not shared state, so
they live in DataStore and are never uploaded.

---

## Verification

### Rendered screens

There is a rendering harness in `app/src/test/` that rasterises every screen with **Robolectric
in native graphics mode** and writes real PNGs to `app/build/screenshots/`:

```bash
./gradlew :app:testDebugUnitTest --tests "*RenderTest"
```

This exists because the Android emulator on this machine cannot run: it needs the hypervisor,
the hypervisor is not installed, and it cannot be installed without elevation. Native-graphics
Robolectric rasterises the real Compose tree with the real Android graphics stack on the JVM, at
the same 1080×2400 the comps are drawn at, so a rendered screen can be laid straight over
`Designs/*.png` and compared.

**What it verifies:** layout, geometry, colour, type and artwork, by running the actual
composables.

**What it does not verify:** anything the platform owns — geofence registration, wallpaper
application, Live Update promotion, notification behaviour. Those need a device or a booted
emulator.

The screens are rendered on `BaselinePalette` in the test source set: the palette the comps were
actually drawn on, sampled from the frames. It is a test fixture and never referenced by the app,
which has no brand colour.

### Unit tests

`RoomId`, `TimeWindow`, `LocationRule`, `Room`'s membership rules, `LiveTexts` and
`decideLiveUpdate` — the pure logic, where a mistake is silent and a wrong answer is a user's lost
room. 56 tests, plus 25 rules assertions in `database-rules-test/`.

Two of them found real bugs while being written, both now fixed: a `TimeWindow` whose end equalled
its start matched *every* minute of the day, and a card painted with `primaryContainer` was
invisible against the design's own background.

`decideLiveUpdate` exists because the Live Update is decided in two places — the room screen while
the app is open, the notification service while it is closed — and two copies of a rule is how
they stop agreeing. Extracting it as a pure function is what made it testable at all; the
`Service` around it is full of notification-manager calls, none of which run on the JVM. One of
its tests protects something manual testing would miss: the service posts the chip and *then*
subscribes, so the subscription's first emission arrives immediately after a successful post, and
reading it as "nothing to show" would withdraw the chip the instant it appeared.

### Not yet verified

- **Geofencing end to end.** The registrar, the receiver and `WallpaperStore.applySystem` are
  written and the pure parts are tested, but no geofence has actually fired. Needs a device:
  grant location, add a rule, `adb emu geo fix <lon> <lat>`, confirm the wallpaper changes.
- **The Live Update while the app is closed.** The service's watch, its sticky restart and the
  decision logic are all unit-tested, and the database listener it depends on is the same one the
  app already used. But it has never actually run on a device: this host has no emulator, so
  whether the system lets the chip update from a background service — rather than silently
  dropping the update, which is what happened to the earlier `Withdrawing a Live Update` problem
  below — is unconfirmed. Needs two devices: close both, set a status on one, read the other.
- **Withdrawing a Live Update.** The notification posts correctly and is withheld while paused,
  but a Live Update that was posted and *then* cleared has been seen to persist. The cause is not
  diagnosed. The watch now withdraws on a blank status, but whether the platform honours the
  withdrawal is the same open question.
- **The typeface.** The comps' "Partner" is a Didone. Pair uses Roboto Serif Italic, so the name
  is wider than the design at the same cap height; the size was chosen to fit rather than to match
  the width.

---

## The emulator, if you want to try

Hardware acceleration is required and is not currently available on this host. Enabling it needs
one elevated command and a reboot:

```powershell
dism /online /enable-feature /featurename:HypervisorPlatform /all /norestart
```

After that, an `x86_64` API 37 AVD will boot and `./gradlew :app:installDebug` will work
normally.
