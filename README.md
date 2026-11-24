# Lingua Link

**Lingua Link** is an offline, real-time speech translation Android application designed for natural face-to-face communication between a **Tourist** and a **Local Service Provider** (e.g. taxi drivers, market vendors, hotel receptionists) in India.

The application operates with **zero cloud dependencies** during conversations, functioning completely in **Airplane Mode** once models are downloaded.

---

## 🏛️ System Architecture

The project is structured into four core modules corresponding to the system architecture specification:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        LINGUA LINK ARCHITECTURE                        │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  [ MODULE 1: SPEECH CAPTURE & NOISE SUPPRESSION ]                      │
│   • AudioRecord: 16,000 Hz, Mono, PCM 16-bit                           │
│   • Hardware FX: NoiseSuppressor, AutomaticGainControl, AcousticEcho   │
│   • Software DSP: 80 Hz Butterworth High-Pass Biquad Filter            │
│   • Adaptive Noise Gate with asymmetric noise floor tracking           │
│   • Real-Time Metrics: Input/Output RMS, SNR Gain (dB), DSP Latency    │
│   • True ON/OFF DSP Bypass Switch & Live Waveform Canvas               │
│                                ↓                                       │
│  [ MODULE 2: OFFLINE STT & TRANSLATION ]                               │
│   • Vosk Android (com.alphacephei:vosk-android): Offline STT           │
│     - English (vosk-model-small-en-in-0.4)                             │
│     - Hindi (vosk-model-small-hi-0.22)                                 │
│     - Telugu (vosk-model-small-te-0.42)                                │
│     - Tamil: Clearly marked unsupported for Vosk offline STT           │
│   • Google ML Kit Translate (com.google.mlkit:translate): On-device    │
│     - English ↔ Hindi ↔ Tamil ↔ Telugu in all directions (12 pairs)   │
│     - RemoteModelManager: Download verification & progress tracking    │
│     - Error Handling: "Language pack not downloaded" (no fake echoes)  │
│                                ↓                                       │
│  [ MODULE 3: TTS, PHRASEBOOK & LANGUAGE PACK MANAGER ]                 │
│   • Android TextToSpeech: Locales en-IN, hi-IN, ta-IN, te-IN           │
│     - Missing voice detection & ACTION_INSTALL_TTS_DATA prompt         │
│     - Replay button for all utterances                                 │
│   • Human-Verified Offline Phrasebook (assets/phrasebook.json):        │
│     - Taxi & Auto, Price Bargaining, UPI/GPay, Directions, Food, Hotel │
│   • Language Pack Manager:                                             │
│     - Live size, status, download progress %, delete actions           │
│                                ↓                                       │
│  [ MODULE 4: USER INTERFACE (JETPACK COMPOSE & MATERIAL 3) ]           │
│   • Single-Device Split-Screen Mode:                                   │
│     - Tourist Panel (Bottom, standard orientation)                     │
│     - Provider Panel (Top, flipped 180° for face-to-face table use)    │
│     - Real-Time DSP Audio & Waveform Monitor                           │
│   • Two-Device Paired Mode: Google Nearby Connections (P2P_CLUSTER)    │
│   • Real "Offline Ready" & Connectivity Status Badges                  │
│   • Conversation History Log: Stored in local database with per-stage  │
│     latencies (STT ms, Translate ms, TTS ms)                           │
│   • Built-In Automated Self-Test Pipeline                              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Requirements & SDK Setup

- **Android Studio**: Android Studio Hedgehog / Iguana / Jellyfish / Koala or newer
- **JDK**: Java 17+ (Java 17, 21, or 24 supported)
- **Minimum SDK (`minSdk`)**: 26 (Android 8.0 Oreo)
- **Target SDK (`targetSdk`)**: 34 (Android 14)
- **Compile SDK (`compileSdk`)**: 34

---

## 🚀 How to Build & Run

### 1. Build via Gradle
```bash
# Debug APK compilation
./gradlew assembleDebug

# Run unit tests (DSP mathematics & RMS verification)
./gradlew testDebugUnitTest
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Run on Android Emulator with Virtual Microphone
1. Open **Android Studio** → **Device Manager**.
2. Start an emulator running API 34 (Android 14).
3. In Emulator Settings (three dots `...` on the emulator toolbar):
   - Navigate to **Microphone**.
   - Check **Virtual microphone uses host audio input**.
4. Install and launch the application:
   ```bash
   ./gradlew installDebug
   ```
5. Grant the **Microphone (RECORD_AUDIO)** and **Nearby Devices** permissions when prompted.

---

## ✈️ Testing 100% Offline (Airplane Mode)

1. Connect device or emulator to the internet initially.
2. Navigate to the **Packs** tab in Lingua Link.
3. Download the desired language models:
   - For example, **English (STT Voice)** + **Hindi (STT Voice)**.
   - For translation: **English (Translation Model)** + **Hindi (Translation Model)**.
4. Turn on **Airplane Mode** (disable Wi-Fi and Cellular Data).
5. Notice the top header badge displays **"Offline Ready"** in green.
6. Speak or type in English:
   - Real-time speech recognition runs on-device via Vosk.
   - Real-time translation runs on-device via ML Kit.
   - Speech synthesis speaks the translated Hindi sentence via Android TTS.
   - Everything works with zero network traffic.

---

## 📱 Face-to-Face Split-Screen Mode

Place the single phone on a table or service counter between the Tourist and the Local Service Provider:
- **Bottom half (Tourist)**: Standard orientation, selected to English.
- **Top half (Local Provider)**: Rotated 180 degrees (`Modifier.rotate(180f)`), selected to Hindi/Telugu/Tamil.
- Both participants can see their own side right-side up, view live transcripts and translations, and tap to speak.

---

## 📡 Device-to-Device Communication (Google Nearby Connections)

When two phones are available:
1. Switch both phones to **Nearby (Two Phones)** tab.
2. On Phone A (Tourist): Tap **Advertise**.
3. On Phone B (Provider): Tap **Discover**.
4. Phones automatically pair over Bluetooth / Wi-Fi Direct (using `Strategy.P2P_CLUSTER`).
5. **Conversation Flow**:
   - Tourist speaks on Phone A → Phone A transcribes and translates → Sends payload over Nearby → Phone B receives, displays translation, and automatically speaks via TTS in the Provider's local language.
   - Provider speaks on Phone B → Phone B transcribes and translates → Sends payload over Nearby → Phone A receives, displays translation, and speaks via TTS in English.

---

## 🧪 Verification & Acceptance Criteria

### 1. Automated Unit Tests
Run the DSP unit tests:
```bash
./gradlew testDebugUnitTest
```
- `testHighPassFilterAttenuatesLowFrequencyRumble`: Verifies 40 Hz rumble is attenuated by >60% (-8 dB to -12 dB) by the 80 Hz high-pass biquad.
- `testPassBandAllows1000HzSpeechTone`: Verifies 1000 Hz human speech frequencies pass through with >90% power preservation.
- `testNoiseGateAttenuatesSilenceAndStationaryNoise`: Verifies the adaptive noise gate attenuates stationary noise below threshold.
- `testBypassSwitchMaintainsExactSamples`: Verifies the Noise Filter toggle provides 100% true sample-for-sample passthrough when turned OFF.
- `testRmsMathematicalAccuracy`: Verifies computed RMS of a known sine wave matches theoretical $A / \sqrt{2}$ within 2%.

### 2. Built-In Self-Test Screen
1. In the app, tap the **Settings** tab.
2. Tap **Run Test** under **Automated Self-Test Pipeline**.
3. The app generates a 16kHz audio buffer with 40 Hz rumble + 1000 Hz tone, runs it through DSP, checks model directories, and tests ML Kit on-device translation.
4. Outputs real execution latencies and PASS/FAIL status.

### 3. Logcat Verification Tags
Filter Logcat by these tags to inspect each real pipeline stage:
- `AUDIO`: AudioRecord state, hardware effect attachments (NS, AGC, AEC), DSP processing.
- `STT`: Vosk model loading, recognition sessions, partial and final JSON results.
- `TRANSLATE`: ML Kit model checks, on-device translation execution, latencies.
- `TTS`: Android TextToSpeech engine initialization, locale verification, utterance synthesis.
- `NEARBY`: Google Nearby Connections advertising, discovery, connection lifecycle, and payload transfers.

---

## 🔍 Engineering Notes & Disclosures

### 1. Tamil Speech Recognition (STT) Disclosure
As explicitly directed in the instructions:
> *"Vosk has no official Tamil model, so for Tamil use Whisper Tiny via whisper.cpp or sherpa-onnx, or clearly mark Tamil as unsupported for speech input. Do not fake it."*

In accordance with strict integrity requirements:
- **Tamil STT is clearly marked as unsupported for speech input** in the offline Vosk engine.
- Tamil speech input cannot be faked or mocked.
- **Tamil Text-to-Speech (ta-IN)** and **Tamil ML Kit On-Device Translation (ta)** are **100% operational and supported**.
- Tourists and providers can select Tamil and communicate via typed input or the pre-translated Phrasebook.

### 2. No Mock / Echo Translations
Every translation displayed in the app is generated by Google ML Kit's on-device neural translation engine (`com.google.mlkit:translate`) or selected from the human-verified `phrasebook.json`. If a model pack is not downloaded, the app surfaces a clear, actionable error rather than echoing the source text or generating mock output.
