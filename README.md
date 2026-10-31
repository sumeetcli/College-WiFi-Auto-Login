# Campus WiFi ⚡

A native, ultra-lightweight Android app built with Kotlin, Jetpack Compose, and Material 3 that automatically authenticates with Sophos captive portals (`172.16.16.16:8090`) with near-zero battery usage and zero location permissions.

---

## 🚀 Key Features

* **Zero-Location Architecture:** Unlike traditional campus Wi-Fi apps that require `ACCESS_FINE_LOCATION` and active GPS just to read the SSID string, Campus WiFi detects the network by probing portal socket reachability on `172.16.16.16:8090`.
* **Zero Idle Battery Consumption:** No polling background services, no persistent wake locks, and no timers. Uses `ConnectivityManager.registerNetworkCallback(NetworkRequest, PendingIntent)` so the Android OS wakes the app only when connecting to a Wi-Fi network.
* **Direct Network Interface Routing:** Captive portals start without internet access, which often causes Android to route traffic to cellular data instead. Campus WiFi binds HTTP requests directly to the physical Wi-Fi `Network` handle.
* **Hardware-Backed Security:**
  * Credentials encrypted on-device via **Android Keystore (AES-256 GCM)**.
  * Cleartext HTTP traffic is strictly restricted to `172.16.16.16` via `network_security_config.xml`.
  * TOFU (Trust-On-First-Use) SHA-256 certificate pinning for the HTTPS user portal (`172.16.16.16:4443`).
* **Daily Wi-Fi Data Tracking:** Lazily queries `NetworkStatsManager` only when you open the app (never in the background). Visualizes today's used vs. remaining quota against a customizable daily cap (default: 13 GB) with a 7-day mini history.
* **Minimal Dark Design:** Styled in near-black (`#0B0B0C`) with subtle surfaces (`#151517`) and soft accents.
* **Ultra-Lean APK:** Written using pure `java.net.HttpURLConnection` and built-in XML parsing. No heavy third-party dependencies (no OkHttp, Retrofit, Gson, or Room).

---

## 🔒 Permissions Used

Campus WiFi requests only the bare minimum permissions needed to function:

| Permission | Purpose |
| :--- | :--- |
| `INTERNET` | Communicates with the captive portal endpoint (`172.16.16.16:8090`). |
| `ACCESS_NETWORK_STATE` | Inspects Wi-Fi network capabilities and connectivity. |
| `ACCESS_WIFI_STATE` | Monitors Wi-Fi connection events. |
| `RECEIVE_BOOT_COMPLETED` | Re-registers the passive network callback after device reboot. |
| `POST_NOTIFICATIONS` | Displays optional, quiet notifications on login results. |
| `FOREGROUND_SERVICE` | Optional: Used only if "Reliability Mode" is enabled for aggressive OEM task killers. |
| `PACKAGE_USAGE_STATS` | Optional: Used to compute daily Wi-Fi data usage when enabled by the user. |

*Zero Location Permissions. Zero Storage Permissions. Zero Contacts Permissions.*

---

## 📱 How It Works

1. **Association:** When your phone associates with a Wi-Fi access point, the OS triggers `NetworkChangeReceiver`.
2. **Detection:** The app checks if the network reports `NET_CAPABILITY_CAPTIVE_PORTAL` or lacks `NET_CAPABILITY_VALIDATED`, and verifies that `172.16.16.16:8090` is reachable within 3 seconds.
3. **Login:** A `POST` request is sent to `http://172.16.16.16:8090/login.xml` with `mode=191`.
4. **Verification:** The XML response is parsed. Active sessions (`"You are signed in as..."` or `LIVE`) and successful logins update the status to **Logged In**.
5. **Sleep:** The background thread terminates immediately.

---

## 🛠️ Building from Source

```bash
# Clone the repository
git clone https://github.com/your-username/campus-wifi.git
cd campus-wifi

# Build debug APK
gradle :app:assembleDebug

# Run unit tests
gradle :app:testDebugUnitTest
```

The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📄 License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.
