# BMS protocol: status and evidence log

**Status: UNKNOWN. No decoder is verified.** The app ships with `UnknownProtocolDecoder`, which subscribes to
FFF1 notifications, logs raw bytes and sends nothing.

## Known (from nRF Connect, not from packet captures)

| Item | Value |
|---|---|
| Service | `FFF0` |
| `FFF1` | READ + NOTIFY (read returned 20 zero bytes with no request) |
| `FFF2` | READ + WRITE WITHOUT RESPONSE |
| Other service | `00010203-0405-0607-0809-0A0B0C0D1912` |

Everything else (request frames, response layout, checksums, byte order, scaling) is **not known**.

## PackDial reference values (from one screenshot, while charging)

SOC 27 %, 49.65 V, +5.79 A, 287 W, 8.1/30 Ah, 3 temps (33.6 / 32.4 / 31.7 °C), 15 cells
(3.303 to 3.314 V), cycles 2, charge/discharge ON, no protection. A decoded packet must reproduce values like
these from the same moment before a field is marked verified.

## How to prove the protocol (no PC needed)

1. Settings > Developer options > *Enable Bluetooth HCI snoop log* (Enabled/Filtered off).
2. Toggle Bluetooth off and on.
3. Open PackDial, connect, let live data show for about a minute. Screenshot PackDial at a known time.
4. Take a bug report (Developer options > *Take bug report* > Full report) and share the ZIP.
5. Look for `btsnoop_hci.log` / `.cfa` inside it. Writes to FFF2 are PackDial's requests; notifications on FFF1
   are the replies.

## Evidence table (fill in; one row per field)

| Field | Request frame | Response offset / length | Byte order | Scale | Signed | Checksum | PackDial value | Decoded value | Confidence |
|---|---|---|---|---|---|---|---|---|---|
| (none verified yet) | | | | | | | | | |

## Plugging in a verified decoder

1. Implement `BmsDecoder` in `app/src/main/java/com/komaki/rangepro/bms/`.
2. Set `isVerified = true` only when every emitted field is in the table above.
3. Fill `pollRequests` only with frames seen in the capture (the app then writes them to
   `writeCharacteristic` with write-without-response, at `pollIntervalMs`).
4. Normalise current so **positive = charging**.
5. Add unit tests that feed the *captured* bytes and assert the PackDial values.
6. Point `BmsDecoders.active` at it.

Do not send commands that were not observed in the capture, and never touch charge/discharge, protection or
balancing controls.

## PackDial reference set 2 (screenshot, lower part of the screen; time not recorded)

Charge ON, Discharge ON, no protection active. Cell spread 83 mV.
Cells (V): 1 3.509, 2 3.480, 3 3.497, 4 3.503, 5 3.502, 6 3.505, 7 3.526, 8 3.485, 9 3.503, 10 3.513,
11 3.563, 12 3.500, 13 3.519, 14 3.487, 15 3.505. Lowest = #2, highest = #11 (3.563 - 3.480 = 0.083 V).
A balancing symbol is shown on cells 1, 3, 5, 7, 9, 11, 13, 15 (odd cells).
Details: BMS model DB20SA02L20S40A, manufactured 2026-09-22, BMS software 1.13.
Missing for this moment: SOC, pack voltage, current, temperatures (top of the screen was not captured).
Note: PackDial reads model, manufacture date and software version from the BMS, so the real protocol has a
request that returns them. PackDial itself says it is read-only.
