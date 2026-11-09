# Privacy Policy for AppData Blocker

**Effective Date:** November 9, 2026  
**Developer:** Deepak Kumar  
**Contact Email:** deepakkumar1115@gmail.com  
**Application ID:** `com.appdatablocker.android`

---

## 1. Overview & Core Privacy Commitment

AppData Blocker ("we", "our", or "the app") is designed from the ground up with a **privacy-first, zero-knowledge architecture**. 

We believe your network activity and device usage belong solely to you. **AppData Blocker does not collect, record, store, transmit, or share any personal data, network traffic payloads, browsing history, or analytics.**

---

## 2. Android `VpnService` Prominent Disclosure

To provide local firewall and per-app network traffic blocking capabilities without requiring root privileges, AppData Blocker utilizes the standard Android `android.net.VpnService` API.

### How `VpnService` Operates in AppData Blocker:
- **100% Local Loopback**: The VPN tunnel created by AppData Blocker terminates strictly on your local device (virtual interface `10.1.10.1` and `fd00::1`).
- **No Remote Servers**: Your internet connections are **never** forwarded, proxied, or routed through any external proxy or remote VPN servers.
- **On-Device Blackhole / Firewall**: Blocked applications have their outgoing packets dropped locally on your device before they ever hit the physical network interface. Unblocked/exempted applications bypass the virtual interface and connect directly to your mobile carrier or Wi-Fi network.
- **Zero Inspection or Decryption**: AppData Blocker does not decrypt SSL/TLS traffic, inspect HTTP payloads, analyze DNS queries, or monitor your online communications.

---

## 3. Data Collection and Usage

| Data Category | Collected? | Stored? | Transmitted? |
| :--- | :--- | :--- | :--- |
| **Personal Identifiers** (Name, Phone, Email, IP) | **NO** | **NO** | **NO** |
| **Network Traffic & Web Activity** (URLs, Packets, Payloads) | **NO** | **NO** | **NO** |
| **Installed Apps List** | Processed in-memory on-device only | Local SharedPreferences only (exempt list) | **NO** |
| **Device Hardware Identifiers** (IMEI, MAC, Advertising ID) | **NO** | **NO** | **NO** |
| **Location Data** (GPS or Cell Tower) | **NO** | **NO** | **NO** |
| **Financial / Payment Information** | **NO** | **NO** | **NO** |

---

## 4. Permissions Requested & Purpose

AppData Blocker requests only the absolute minimum permissions necessary to perform its firewall functionality:

1. **`android.permission.INTERNET`**  
   Required by the Android operating system to initialize the network interface adapter and query network connection state.

2. **`android.permission.QUERY_ALL_PACKAGES`**  
   Required to display the list of installed applications and system packages on your device so you can view, choose, and configure which apps to shield or exempt from data blocking. This list is processed exclusively in-memory on your device and is never uploaded anywhere.

3. **`android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`**  
   Required to maintain continuous, stable firewall protection while the device screen is off or while other apps are active. Android requires foreground services to display an active status notification.

4. **`android.permission.POST_NOTIFICATIONS`**  
   Used strictly to display the ongoing firewall status notification in your system tray, providing transparent visibility that the firewall is active and enabling one-tap stop controls.

5. **`android.permission.ACCESS_NETWORK_STATE`**  
   Required to detect active Wi-Fi and Cellular connectivity states for the dashboard network speedometer.

---

## 5. Third-Party Libraries & Tracking

- AppData Blocker contains **zero** third-party advertising SDKs, **zero** crash analytics trackers (e.g., no Firebase Analytics, no Facebook SDK, no AppsFlyer), and **zero** behavioral tracking cookies.
- Support voluntary UPI donations open directly in external standard UPI payment apps (such as Google Pay, PhonePe, Paytm, or BHIM) via the system Android intent protocol (`upi://pay`). No payment credentials, bank details, or card numbers are collected, handled, or stored by AppData Blocker.

---

## 6. Children's Privacy

AppData Blocker does not collect any personal information from anyone, including children under the age of 13 (or under 16 in certain jurisdictions).

---

## 7. Security of Your Device

Because AppData Blocker requires no root access and runs entirely in the standard Android application sandbox, it cannot compromise your device's security, invalidate banking app integrity, or interfere with system security patches.

---

## 8. Changes to This Privacy Policy

If we update this Privacy Policy, the revised version will be published within the application and at this repository with an updated effective date.

---

## 9. Contact Us

If you have any questions, suggestions, or concerns regarding this Privacy Policy or the security practices of AppData Blocker, please contact:

* **Developer:** Deepak Kumar  
* **Email:** deepakkumar1115@gmail.com  
* **GitHub:** https://github.com/deepakkumar1115
