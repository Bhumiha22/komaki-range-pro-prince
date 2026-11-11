# Komaki Range Pro (Komaki X-One BMS Dashboard & APK Builder)

Native-grade Progressive Web App & Android build repository for the **Komaki X-One (X1)** electric scooter equipped with the **DB20SA02L20S40A** 15S LiFePO4 Smart BMS (BLE MAC `A5:C2:3A:4C:1D:57`).

## Contents of this Repository:
* `index.html`: Fully self-contained dashboard with direct Web Bluetooth driver (`0xFFF0`, `0xFFF1`, `0xFFF2`), 15S cell voltage matrix, real-time dynamic range calculator (Eco/Normal/Sport), and live telemetry debugger.
* `.github/workflows/build-apk.yml`: Automated GitHub Actions workflow that converts this web app into a native installable Android APK with Android 16 Bluetooth permissions.

## Quick Start on GitHub:
1. Extract and upload these files directly into your GitHub repository (`bhumiha22/komaki-range`).
2. GitHub Pages will instantly host the live dashboard at:
   `https://bhumiha22.github.io/komaki-range/`
3. Go to the **Actions** tab in your repository to automatically build and download your Android `.apk`!
