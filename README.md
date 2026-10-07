# Carlos AI - Voice Activated Phone Assistant 🚀

**DEVELOPER - CYBER_ANXHU**

Carlos AI is a hands-free voice assistant that controls your Android phone on your command. It listens in the background for your custom wake word (**"Hey Carlos"**) and executes device actions instantly, whether the Carlos app is open or closed.

---

## ⚡ Key Features

1. **24/7 Voice Activation ("Hey Carlos")**
   - Runs in a continuous background foreground service (`CarlosWakeWordService`).
   - Responds to customizable wake words (`"Hey Carlos"`, `"Carlos"`, `"Ok Carlos"`, or custom phrases).
   - **Compact Bottom HUD Box:** When activated in the background, a compact cybernetic bottom HUD pops up over whatever app you are using, listens to your command, executes it, and dismisses automatically!

2. **Full Phone Automation & Controls**
   - **WhatsApp Automation:** `"Open WhatsApp"` or `"Send message to [number] saying [message]"`
   - **Phone Calls:** Hands-free dialing (`"Make a call to [number]"`)
   - **Launch Any App:** Scans installed phone apps and launches any app by name (`"Open YouTube"`, `"Open Spotify"`, `"Open Chrome"`, etc.)
   - **Camera & Gallery:** `"Open camera"`, `"Open my gallery"`
   - **Flashlight Control:** `"Turn on flashlight"`, `"Turn off my flashlight"` (with auto hardware detection & screen torch fallback)
   - **Battery & Device Settings:** `"Check battery"`, `"Open Wi-Fi settings"`, `"Open Bluetooth"`

3. **xAI Grok AI Integration Slot**
   - Dedicated slot to input your xAI Grok API key (`xai-...`)
   - Model selection (`grok-2-latest`, `grok-beta`, etc.)
   - Live API ping test & latency check
   - Customizable personality prompt for Carlos
   - Smart offline rule engine fallback if no API key is provided

---

## 📥 Downloading the APK from GitHub Actions

This repository includes an automated GitHub Actions CI pipeline that builds the APK automatically on push:

1. Click on the **Actions** tab at the top of this repository.
2. Select the latest workflow run: **Build Carlos AI APK**.
3. Under the **Artifacts** section at the bottom of the page, download **`Carlos-AI-v1.0-Debug`**.
4. Unzip and install the APK on your Android phone!

---

**Built with Kotlin, Jetpack Compose, and Material Design 3 by CYBER_ANXHU.**
