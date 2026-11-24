import os
import sys
import json
import time
import urllib.request
import urllib.parse
from http.server import HTTPServer, SimpleHTTPRequestHandler

PORT = 3000
DASHBOARD_DIR = os.path.dirname(os.path.abspath(__file__))
WORKSPACE_DIR = os.path.dirname(DASHBOARD_DIR)
PHRASEBOOK_PATH = os.path.join(WORKSPACE_DIR, "app", "src", "main", "assets", "phrasebook.json")
APK_PATH = os.path.join(WORKSPACE_DIR, "app", "build", "outputs", "apk", "debug", "app-debug.apk")

# Local High-Accuracy Translation Dictionary for Essential Conversation
LOCAL_DICTIONARY = {
    # Greetings & Common Expressions
    "how are you": {
        "hi": "आप कैसे हैं?",
        "te": "మీరు ఎలా ఉన్నారు?",
        "ta": "நீங்கள் எப்படி இருக்கிறீர்கள்?",
        "en": "How are you?"
    },
    "how are you?": {
        "hi": "आप कैसे हैं?",
        "te": "మీరు ఎలా ఉన్నారు?",
        "ta": "நீங்கள் எப்படி இருக்கிறீர்கள்?",
        "en": "How are you?"
    },
    "i am fine": {
        "hi": "मैं ठीक हूँ।",
        "te": "నేను బాగున్నాను.",
        "ta": "நான் நன்றாக இருக்கிறேன்.",
        "en": "I am fine."
    },
    "i am good": {
        "hi": "मैं अच्छा हूँ।",
        "te": "నేను బాగున్నాను.",
        "ta": "நான் நலமாக இருக்கிறேன்.",
        "en": "I am good."
    },
    "hello": {
        "hi": "नमस्ते",
        "te": "నమస్కారం",
        "ta": "வணக்கம்",
        "en": "Hello"
    },
    "good morning": {
        "hi": "शुभ प्रभात",
        "te": "శుభోదయం",
        "ta": "காலை வணக்கம்",
        "en": "Good morning"
    },
    "what is your name": {
        "hi": "आपका नाम क्या है?",
        "te": "మీ పేరు ఏమిటి?",
        "ta": "உங்கள் பெயர் என்ன?",
        "en": "What is your name?"
    },
    "what is your name?": {
        "hi": "आपका नाम क्या है?",
        "te": "మీ పేరు ఏమిటి?",
        "ta": "உங்கள் பெயர் என்ன?",
        "en": "What is your name?"
    },
    "my name is": {
        "hi": "मेरा नाम ... है।",
        "te": "నా పేరు ...",
        "ta": "என் பெயர் ...",
        "en": "My name is"
    },
    "thank you": {
        "hi": "धन्यवाद",
        "te": "ధన్యవాదాలు",
        "ta": "நன்றி",
        "en": "Thank you"
    },
    "thank you very much": {
        "hi": "बहुत बहुत धन्यवाद।",
        "te": "చాలా ధన్యవాదాలు.",
        "ta": "மிக்க நன்றி.",
        "en": "Thank you very much."
    },
    "yes": {
        "hi": "हाँ",
        "te": "అవును",
        "ta": "ஆம்",
        "en": "Yes"
    },
    "no": {
        "hi": "नहीं",
        "te": "కాదు",
        "ta": "இல்லை",
        "en": "No"
    },
    "please": {
        "hi": "कृपया",
        "te": "దయచేసి",
        "ta": "தயவுசெய்து",
        "en": "Please"
    },
    "help me": {
        "hi": "मेरी मदद कीजिए।",
        "te": "నాకు సహాయం చేయండి.",
        "ta": "எனக்கு உதவுங்கள்.",
        "en": "Help me."
    },
    "where is the bathroom": {
        "hi": "शौचालय कहाँ है?",
        "te": "వాష్ రూమ్ ఎక్కడ ఉంది?",
        "ta": "கழிப்பறை எங்கே உள்ளது?",
        "en": "Where is the bathroom?"
    },
    "where is the railway station": {
        "hi": "रेलवे स्टेशन कहाँ है?",
        "te": "రైల్వే స్టేషన్ ఎక్కడ ఉంది?",
        "ta": "ரயில் நிலையம் எங்கே உள்ளது?",
        "en": "Where is the railway station?"
    },
    "how much": {
        "hi": "कितना हुआ?",
        "te": "ఎంత అవుతుంది?",
        "ta": "எவ்வளவு?",
        "en": "How much?"
    },
    "how much is this": {
        "hi": "यह कितने का है?",
        "te": "దీని ధర ఎంత?",
        "ta": "இதன் விலை என்ன?",
        "en": "How much is this?"
    },
    # Reverse Lookups (Hindi -> English / Telugu / Tamil)
    "आप कैसे हैं?": {
        "en": "How are you?",
        "te": "మీరు ఎలా ఉన్నారు?",
        "ta": "நீங்கள் எப்படி இருக்கிறீர்கள்?",
        "hi": "आप कैसे हैं?"
    },
    "आप कैसे हैं": {
        "en": "How are you?",
        "te": "మీరు ఎలా ఉన్నారు?",
        "ta": "நீங்கள் எப்படி இருக்கிறீர்கள்?",
        "hi": "आप कैसे हैं?"
    },
    "नमस्ते": {
        "en": "Hello / Greetings",
        "te": "నమస్కారం",
        "ta": "வணக்கம்",
        "hi": "नमस्ते"
    },
    "मैं ठीक हूँ": {
        "en": "I am fine.",
        "te": "నేను బాగున్నాను.",
        "ta": "நான் நன்றாக இருக்கிறேன்.",
        "hi": "मैं ठीक हूँ।"
    },
    "धन्यवाद": {
        "en": "Thank you.",
        "te": "ధన్యవాదాలు.",
        "ta": "நன்றி.",
        "hi": "धन्यवाद"
    }
}

def load_phrasebook_entries():
    entries = []
    if os.path.exists(PHRASEBOOK_PATH):
        try:
            with open(PHRASEBOOK_PATH, "r", encoding="utf-8") as f:
                entries = json.load(f)
        except Exception as e:
            print(f"[WARN] Failed to load phrasebook: {e}")
    return entries

PHRASEBOOK_DATA = load_phrasebook_entries()

def check_upstream_internet():
    try:
        url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=hi&dt=t&q=test"
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=2.5) as resp:
            return resp.status == 200
    except Exception:
        return False

def perform_translation(text, from_lang="en", to_lang="hi"):
    if not text or from_lang == to_lang:
        return text

    t_clean = text.strip()
    t_lower = t_clean.lower().rstrip(".?!")

    # Tier 1: Check Exact Local Dictionary (100% Offline)
    if t_lower in LOCAL_DICTIONARY:
        mapping = LOCAL_DICTIONARY[t_lower]
        if to_lang in mapping:
            return mapping[to_lang]

    if t_clean in LOCAL_DICTIONARY:
        mapping = LOCAL_DICTIONARY[t_clean]
        if to_lang in mapping:
            return mapping[to_lang]

    # Tier 2: Check Phrasebook (100% Offline)
    for p in PHRASEBOOK_DATA:
        en = p.get("english", "").lower().rstrip(".?!")
        if en == t_lower or t_lower in en:
            if to_lang == "hi" and p.get("hindi"):
                return p["hindi"].split("(")[0].strip()
            elif to_lang == "te" and p.get("telugu"):
                return p["telugu"].split("(")[0].strip()
            elif to_lang == "ta" and p.get("tamil"):
                return p["tamil"].split("(")[0].strip()
            elif to_lang == "en" and p.get("english"):
                return p["english"]

    # Tier 3: Neural Translation via HTTP (Requires Internet)
    # Provider 1: Google Clients5
    try:
        url = f"https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl={from_lang}&tl={to_lang}&q=" + urllib.parse.quote(t_clean)
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
        with urllib.request.urlopen(req, timeout=3.5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            if isinstance(data, list) and len(data) > 0:
                res = data[0][0] if isinstance(data[0], list) else data[0]
                if res and isinstance(res, str):
                    return res.strip()
    except Exception as e:
        pass

    # Provider 2: MyMemory API Fallback
    try:
        url = f"https://api.mymemory.translated.net/get?q={urllib.parse.quote(t_clean)}&langpair={from_lang}|{to_lang}"
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=3.5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            res = data.get("responseData", {}).get("translatedText")
            if res and isinstance(res, str) and "QUERY LENGTH" not in res.upper():
                return res.strip()
    except Exception as e:
        pass

    # Provider 3: Google GTX
    try:
        url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl={from_lang}&tl={to_lang}&dt=t&q=" + urllib.parse.quote(t_clean)
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=3.0) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            result = "".join([part[0] for part in data[0] if part[0]])
            if result:
                return result.strip()
    except Exception as e:
        pass

    return "Translation service unavailable right now, try again or use the Phrasebook."

class LinguaLinkHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DASHBOARD_DIR, **kwargs)

    def do_HEAD(self):
        if self.path == "/download/apk":
            if os.path.exists(APK_PATH):
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="LinguaLink-debug.apk"')
                self.send_header("Content-Length", str(os.path.getsize(APK_PATH)))
                self.end_headers()
                return
            else:
                self.send_response(404)
                self.end_headers()
                return
        return super().do_HEAD()

    def do_GET(self):
        if self.path == "/api/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            is_online = check_upstream_internet()
            resp = {
                "status": "healthy",
                "service": "Lingua Link Companion Dashboard",
                "version": "1.0.0",
                "internet_online": is_online,
                "apk_present": os.path.exists(APK_PATH),
                "apk_size_bytes": os.path.getsize(APK_PATH) if os.path.exists(APK_PATH) else 0
            }
            self.wfile.write(json.dumps(resp).encode("utf-8"))
            return

        if self.path == "/api/phrasebook":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            if os.path.exists(PHRASEBOOK_PATH):
                with open(PHRASEBOOK_PATH, "r", encoding="utf-8") as f:
                    self.wfile.write(f.read().encode("utf-8"))
            else:
                self.wfile.write(b"[]")
            return

        if self.path.startswith("/api/translate"):
            # Parse query parameters: ?text=...&from=en&to=hi
            parsed = urllib.parse.urlparse(self.path)
            params = urllib.parse.parse_qs(parsed.query)
            text = params.get("text", [""])[0]
            from_lang = params.get("from", ["en"])[0]
            to_lang = params.get("to", ["hi"])[0]

            t0 = time.time()
            translated = perform_translation(text, from_lang, to_lang)
            latency_ms = round((time.time() - t0) * 1000)

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            res = {
                "original": text,
                "translated": translated,
                "from": from_lang,
                "to": to_lang,
                "latency_ms": latency_ms
            }
            self.wfile.write(json.dumps(res, ensure_ascii=False).encode("utf-8"))
            return

        if self.path == "/download/apk":
            if os.path.exists(APK_PATH):
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="LinguaLink-debug.apk"')
                self.send_header("Content-Length", str(os.path.getsize(APK_PATH)))
                self.end_headers()
                with open(APK_PATH, "rb") as f:
                    self.wfile.write(f.read())
                return
            else:
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b"APK not found. Run gradle assembleDebug first.")
                return

        return super().do_GET()

    def do_POST(self):
        if self.path == "/api/translate":
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            try:
                data = json.loads(body)
                text = data.get("text", "")
                from_lang = data.get("from", "en")
                to_lang = data.get("to", "hi")
            except Exception:
                text = ""
                from_lang = "en"
                to_lang = "hi"

            t0 = time.time()
            translated = perform_translation(text, from_lang, to_lang)
            latency_ms = round((time.time() - t0) * 1000)

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            res = {
                "original": text,
                "translated": translated,
                "from": from_lang,
                "to": to_lang,
                "latency_ms": latency_ms
            }
            self.wfile.write(json.dumps(res, ensure_ascii=False).encode("utf-8"))
            return

        self.send_response(404)
        self.end_headers()

    def log_message(self, format, *args):
        sys.stdout.write(f"[DASHBOARD SERVER] {self.address_string()} - {format % args}\n")
        sys.stdout.flush()

def run():
    server = HTTPServer(("0.0.0.0", PORT), LinguaLinkHandler)
    print(f"[DASHBOARD SERVER] Lingua Link Dashboard running at http://localhost:{PORT}")
    print(f"[DASHBOARD SERVER] Serving from {DASHBOARD_DIR}")
    sys.stdout.flush()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    run()
