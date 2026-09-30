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

## Before it works against a real database

**The Realtime Database is in public test mode.** `database.rules.json` is written and correct,
but it has not been deployed, which means anyone who knows the URL can currently read and write
every room. Deploy it before anything else:

```bash
firebase deploy --only database
```

Then verify from a client that an unauthenticated read is refused.

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

`RoomId`, `TimeWindow`, `LocationRule` and `Room`'s membership rules — the pure logic, where a
mistake is silent and a wrong answer is a user's lost room. 33 tests.

Two of them found real bugs while being written, both now fixed: a `TimeWindow` whose end equalled
its start matched *every* minute of the day, and a card painted with `primaryContainer` was
invisible against the design's own background.

### Not yet verified

- **Geofencing end to end.** The registrar, the receiver and `WallpaperStore.applySystem` are
  written and the pure parts are tested, but no geofence has actually fired. Needs a device:
  grant location, add a rule, `adb emu geo fix <lon> <lat>`, confirm the wallpaper changes.
- **Withdrawing a Live Update.** The notification posts correctly and is withheld while paused,
  but a Live Update that was posted and *then* cleared has been seen to persist. The cause is not
  diagnosed.
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
