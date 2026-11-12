# Komaki Range Pro

Android app (Kotlin, Jetpack Compose, Room) for a Komaki X-One scooter: BMS monitor, charging log, GPS ride
tracking and a range estimate from your own ride history. Offline and local only (no network permission).

## Honest status

| Area | State |
|---|---|
| Project, UI, Room, GPS service, charge tracker, range model, BLE connect/retry | Written. **Not compiled or run yet** (see below). |
| Unit tests (range, charge tracker, decoder safety) | Written, **not run yet**. CI runs them on every build. |
| **BMS telemetry decoding** | **Not implemented: the protocol is unknown.** No battery values appear until it is decoded from a PackDial capture. |

**Works today without the BMS:** type the battery % from the scooter's display (Home tab) and when starting/stopping a ride (Rides tab).
Rides, ride history and the range estimate then use those typed numbers, clearly marked as entered by you. Charging detection and the Battery tab still need the BMS.

Until a verified decoder exists the app connects, enables notifications on FFF1, logs raw frames (Diagnostics)
and **writes nothing to the BMS**. Dashboard, Battery, Charging and range show "no data". Rides still record
GPS distance and speed, but without SOC they do not feed range prediction. Demo mode shows clearly labelled
simulated data so you can look at the UI.

## Build the APK with only a phone (GitHub Actions)

1. Create a free GitHub account and a new empty repository (private is fine).
2. Upload this project's contents: on the repo page choose *Add file > Upload files*; unzip the project first
   and upload the folders (keep `.github/workflows/build-apk.yml` at that exact path). The GitHub mobile site in
   "Desktop site" mode works.
3. Open the *Actions* tab > *Build debug APK* > *Run workflow* (it also runs on every push to `main`).
4. When it finishes, open the run, download the `komaki-range-pro-debug-apk` artifact (a zip containing
   `app-debug.apk`), extract it, and install it. Android will ask to allow installs from your browser or file manager.
5. If the build fails, open the failed step's log and send it to me; first builds of a new project often need a fix.

The debug APK is signed with a throwaway key generated on each build, so updating over an older build may require
uninstalling first (your ride history is deleted with it).

## First run on a Realme P3

1. Install, open, and allow Bluetooth ("Nearby devices"). Location and notifications are requested when you start a ride.
2. In Realme settings, set the app's battery usage to *Unrestricted* so rides are not killed in the background.
3. Close PackDial, switch the scooter on, then Home > *Connect to BMS* (MAC prefilled: `A5:C2:3A:4C:1D:57`).
4. Open Settings > Diagnostics. You should see the GATT services, then `STATE CONNECTED Notifications on`.
   Raw frames (if the BMS sends any unprompted) appear as `RX` lines. The earlier nRF test suggests it will stay
   silent until a request is sent, which this app deliberately does not do yet.

## Finding the protocol

See `docs/PROTOCOL.md`. Short version: capture a PackDial session in a Bluetooth HCI snoop log, share the bug
report, decode requests on FFF2 and replies on FFF1, check against PackDial's screen, then add a verified decoder.

## Layout

`ble/` GATT client, retry/backoff, raw log | `bms/` decoder interface (only place for protocol bytes) |
`model/` telemetry types | `data/` repository + demo | `db/` Room | `gps/` ride recorder + foreground service |
`charge/` session tracker | `range/` predictor | `settings/` | `ui/` Compose screens.

## Known limitations

- No decoded telemetry yet (see above); capacity is not assumed to be 30 Ah and "verified capacity" is empty unless you set it.
- Connects by saved MAC address; there is no scan screen. If the MAC changes, edit it in Settings.
- Reconnect: backoff 2 s up to 60 s, 8 attempts, then it stops until you tap Connect. It lives only while the app
  process lives; Android may stop it in the background. It does not auto-start after reboot.
- A ride in progress is lost if Android kills the process before you tap Stop.
- Route points are stored but there is no map view (maps need an API key/network).
- Range is a simple %/km model seeded with your 61 km test (100% to 21%, one passenger); LiFePO4 SOC is non-linear,
  so treat it as an estimate. Confidence stays "low" until you have several real rides.
- Targets SDK 35 (the Android Gradle plugin used does not yet target 36); it runs on Android 16.
- Current sign must be normalised by the future decoder (positive = charging); charging detection depends on it.
