# AntiSplit-X ⚡

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)

**AntiSplit-X** is an advanced, fully on-device Android utility designed to seamlessly merge Split APK bundles (`.apks`, `.xapk`, `.apkm`, `.zip`) into a single, standalone, and universally installable `.apk` file. 

Built entirely from scratch using modern **Kotlin** and **Jetpack Compose (Material Design 3)**, AntiSplit-X provides a blazing-fast, beautiful, and highly customizable experience for Android power users, modders, and developers.

---

## ✨ Key Features

* 📦 **Universal Format Support:** Easily process and merge `.apks`, `.xapk`, `.apkm`, and `.zip` split application formats.
* 🤖 **Smart Device-Matching:** Automatically detects and pre-selects the correct architecture (ABI) and screen density splits tailored specifically for your currently running device.
* 📱 **Installed App Extraction:** Don't have the original file? Directly select any installed split-app from your device, and AntiSplit-X will extract, merge, and export it as a single APK.
* ✍️ **Built-in APK Signer (V1 + V2):** Instantly sign your merged APKs. Supports:
  * Default Debug Keystore.
  * Custom Keystore (`.jks`, `.p12`).
  * Custom Key Files (`.pk8`, `.pem`, `.key`).
* 🛡️ **DRM & License Stripping:** Automatically scans the `AndroidManifest.xml` and completely removes annoying Google Play Store license checks and App Bundle DRM elements.
* 💻 **Terminal-Style Live Log:** Watch the magic happen with an integrated, color-coded, real-time terminal output view.
* 🗄️ **Smart Backup System:** Automatically backs up existing installed apps to `.apks` format before uninstalling them during signature conflict resolutions.

---

## 📸 Screenshots

*(Add your screenshots here by replacing the placeholder links!)*

|<img src="https://github.com/Hiaashuu/AntiSplit-X/blob/main/Screenshots/AppList.jpg" width="250">|<img src="[https://via.placeholder.com/250x500.png?text=Merge+Terminal](https://github.com/Hiaashuu/AntiSplit-X/blob/main/Screenshots/Settings2.jpg)" width="250">|<img src="[https://via.placeholder.com/250x500.png?text=Settings](https://github.com/Hiaashuu/AntiSplit-X/blob/main/Screenshots/Settings1.jpg)" width="250">|
|:---:|:---:|:---:|
| **Home Screen** | **Live Terminal Merging** | **Advanced Settings** |

---

## 🚀 How to Use

1. **Select a File:** Tap the drop-zone to browse for a split bundle, or share a file directly to AntiSplit-X from your file manager.
2. **Filter Splits (Optional):** Choose specific splits to include, or let the app auto-select the best ones for your device.
3. **Merge & Sign:** Hit "Merge". The app will combine the splits, remove license restrictions, and sign the final APK.
4. **Install or Share:** Once complete, you can instantly install the merged APK or share it with others.

---

## 🏆 Credits & Acknowledgements

AntiSplit-X was built by **Aashuu (Hiaashuu)**, but it stands on the shoulders of incredible open-source projects. A massive thank you to the following developers and their libraries:

* **Developed by:** [Hiaashuu (Aashuu)](https://github.com/hiaashuu) - UI, UX, App Logic, Jetpack Compose implementation, and core integration.
* **[APKEditor](https://github.com/REAndroid/APKEditor) by REAndroid:** The absolute core of this application. APKEditor's powerful merging engine and structural XML parsing API make the split-merging and DRM-removal processes possible.
* **[apksig-android](https://github.com/MuntashirAkon/apksig-android) by MuntashirAkon:** This brilliant port of the AOSP `apksig` library allows AntiSplit-X to sign the final merged APKs with V1 and V2 signature schemes completely on-device without needing a PC.

---

## ⚖️ License

This project is licensed under the **MIT License**.

> **Note:** The upstream libraries used in this project (APKEditor and apksig-android) may be governed by their own respective open-source licenses (such as GPLv3 or Apache License 2.0). Please refer to their repositories for specific licensing details regarding their core code.

```text
MIT License

Copyright (c) 2026 Hiaashuu (Aashuu)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
