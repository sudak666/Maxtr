# Rytm Macrobenchmark and Baseline Profile

The module measures cold startup and Finance history scrolling with and without
profile compilation. It also contains the repeatable critical-user-journey
generator.

Build the benchmark APK:

```powershell
.\gradlew.bat :baselineprofile:assembleRelease --quiet
```

Run only profile generation on an API 34+ physical device or rooted AOSP image:

```powershell
.\gradlew.bat :baselineprofile:connectedReleaseAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=ua.rytm.app.baselineprofile.BaselineProfileGenerator' `
  '-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile' --quiet
```

Copy the generated `*-baseline-prof.txt` from
`baselineprofile/build/outputs/connected_android_test_additional_output/` to
`app/src/main/baselineProfiles/rytm-baseline-prof.txt`, then rebuild release. The checked-in profile is
generated from a signed-in QA run (see "Recording the profile" below).

Run the comparisons:

```powershell
.\gradlew.bat :baselineprofile:connectedReleaseAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=ua.rytm.app.baselineprofile.FinanceMacrobenchmark' --quiet
```

## Recording the profile (the way that works, 2026-11-15)

The Finance journeys need a signed-in app, and signing in can't be automated
(no credentials in tests). So the profile is recorded on a **non-minified QA
install** that a person signs in to once, and the generator runs against it
**without reinstalling** (a Gradle connected-test run reinstalls/uninstalls the
app and signs it out). A profile from an R8 build has obfuscated names and is
useless for the next build; a non-minified one has real names, and R8 rewrites
them into release.

1. Emulator only (API 34+; Samsung's Android 13 blocks profile capture). Close
   the emulator while Gradle builds — together they ran out of RAM.
2. `./gradlew :app:assembleQa :baselineprofile:assembleRelease -Prytm.qaNoMinify`
3. Start the emulator; `adb -s emulator-5554 install -r` both APKs (always pass
   `-s`: with the phone on wireless adb, a bare install can land on the phone).
4. Sign in to **Rytm QA** on the emulator (Google account added in system
   Settings first), skip onboarding, leave it on Finance.
5. Run without Gradle:
   ```
   adb -s emulator-5554 shell am instrument -w -r      -e class ua.rytm.app.baselineprofile.BaselineProfileGenerator      -e androidx.benchmark.enabledRules BaselineProfile      -e targetPackage ua.rytm.app.qa      ua.rytm.app.baselineprofile/androidx.test.runner.AndroidJUnitRunner
   ```
6. Pull `/storage/emulated/0/Android/media/ua.rytm.app.baselineprofile/BaselineProfileGenerator_criticalUserJourneys-startup-prof.txt`
   to `app/src/main/baselineProfiles/rytm-baseline-prof.txt`. Check it has the
   UI (`grep -c FinanceScreen` > 0, `LoginScreen` 0) before committing.
