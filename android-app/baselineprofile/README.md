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
a conservative manual fallback for API 33 OEM devices where ART profile capture
is blocked; generated rules should replace it when an API 34+ device is available.

Run the comparisons:

```powershell
.\gradlew.bat :baselineprofile:connectedReleaseAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=ua.rytm.app.baselineprofile.FinanceMacrobenchmark' --quiet
```
