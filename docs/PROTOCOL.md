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
