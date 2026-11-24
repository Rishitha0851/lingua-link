import fs from 'fs';
import path from 'path';

// --- In-Memory Offline Translation Dictionary (Matches server.py & app.js) ---
const LOCAL_DICTIONARY = {
  "how are you": {
    hi: "आप कैसे हैं?",
    te: "మీరు ఎలా ఉన్నారు?",
    ta: "நீங்கள் எப்படி இருக்கிறீர்கள்?",
    en: "How are you?"
  },
  "how are you?": {
    hi: "आप कैसे हैं?",
    te: "మీరు ఎలా ఉన్నారు?",
    ta: "நீங்கள் எப்படி இருக்கிறீர்கள்?",
    en: "How are you?"
  },
  "i am fine": {
    hi: "मैं ठीक हूँ।",
    te: "నేను బాగున్నాను.",
    ta: "நான் நன்றாக இருக்கிறேன்.",
    en: "I am fine."
  },
  "i am good": {
    hi: "मैं अच्छा हूँ।",
    te: "నేను బాగున్నాను.",
    ta: "நான் நலமாக இருக்கிறேன்.",
    en: "I am good."
  },
  "hello": {
    hi: "नमस्ते",
    te: "నమస్కారం",
    ta: "வணக்கம்",
    en: "Hello"
  },
  "good morning": {
    hi: "शुभ प्रभात",
    te: "శుభోదయం",
    ta: "காலை வணக்கம்",
    en: "Good morning"
  },
  "what is your name": {
    hi: "आपका नाम क्या है?",
    te: "మీ పేరు ఏమిటి?",
    ta: "உங்கள் பெயர் என்ன?",
    en: "What is your name?"
  },
  "what is your name?": {
    hi: "आपका नाम क्या है?",
    te: "మీ పేరు ఏమిటి?",
    ta: "உங்கள் பெயர் என்ன?",
    en: "What is your name?"
  },
  "my name is rahul": {
    hi: "मेरा नाम राहुल है।",
    te: "నా పేరు రాహుల్.",
    ta: "என் பெயர் ராகுல்.",
    en: "My name is Rahul."
  },
  "thank you": {
    hi: "धन्यवाद",
    te: "ధన్యవాదాలు",
    ta: "நன்றி",
    en: "Thank you"
  },
  "thank you very much": {
    hi: "बहुत बहुत धन्यवाद।",
    te: "చాలా ధన్యవాదాలు.",
    ta: "மிக்க நன்றி.",
    en: "Thank you very much."
  },
  "yes": {
    hi: "हाँ",
    te: "అవును",
    ta: "ஆம்",
    en: "Yes"
  },
  "no": {
    hi: "नहीं",
    te: "కాదు",
    ta: "இல்லை",
    en: "No"
  },
  "please": {
    hi: "कृपया",
    te: "దయచేసి",
    ta: "தயவுசெய்து",
    en: "Please"
  },
  "help me": {
    hi: "मेरी मदद कीजिए।",
    te: "నాకు సహాయం చేయండి.",
    ta: "எனக்கு உதவுங்கள்.",
    en: "Help me."
  },
  "where is the bathroom": {
    hi: "शौचालय कहाँ है?",
    te: "వాష్ రూమ్ ఎక్కడ ఉంది?",
    ta: "கழிப்பறை எங்கே உள்ளது?",
    en: "Where is the bathroom?"
  },
  "where is the railway station": {
    hi: "रेलवे स्टेशन कहाँ है?",
    te: "రైల్వే स्टेशन ఎక్కడ ఉంది?",
    ta: "ரயில் நிலையம் எங்கே உள்ளது?",
    en: "Where is the railway station?"
  },
  "how much": {
    hi: "कितना हुआ?",
    te: "ఎంత అవుతుంది?",
    ta: "எவ்வளவு?",
    en: "How much?"
  },
  "how much is this": {
    hi: "यह कितने का है?",
    te: "దీని ధర ఎంత?",
    ta: "இதன் விலை என்ன?",
    en: "How much is this?"
  },
  // Reverse Lookups (Hindi -> English / Telugu / Tamil)
  "आप कैसे हैं?": {
    en: "How are you?",
    te: "మీరు ఎలా ఉన్నారు?",
    ta: "நீங்கள் எப்படி இருக்கிறீர்கள்?",
    hi: "आप कैसे हैं?"
  },
  "आप कैसे हैं": {
    en: "How are you?",
    te: "మీరు ఎలా ఉన్నారు?",
    ta: "நீங்கள் எப்படி இருக்கிறீர்கள்?",
    hi: "आप कैसे हैं?"
  },
  "नमस्ते": {
    en: "Hello / Greetings",
    te: "నమస్కారం",
    ta: "வணக்கம்",
    hi: "नमस्ते"
  },
  "मैं ठीक हूँ": {
    en: "I am fine.",
    te: "నేను బాగున్నాను.",
    ta: "நான் நன்றாக இருக்கிறேன்.",
    hi: "मैं ठीक हूँ।"
  },
  "धन्यवाद": {
    en: "Thank you.",
    te: "ధన్యవాదాలు.",
    ta: "நன்றி.",
    hi: "धन्यवाद"
  }
};

let cachedPhrasebook = null;
function getPhrasebook() {
  if (cachedPhrasebook) return cachedPhrasebook;
  const possiblePaths = [
    path.join(process.cwd(), 'dashboard', 'phrasebook.json'),
    path.join(process.cwd(), 'phrasebook.json'),
    path.join(process.cwd(), 'app', 'src', 'main', 'assets', 'phrasebook.json')
  ];

  for (const p of possiblePaths) {
    if (fs.existsSync(p)) {
      try {
        cachedPhrasebook = JSON.parse(fs.readFileSync(p, 'utf-8'));
        return cachedPhrasebook;
      } catch (e) {}
    }
  }
  return [];
}

async function performUpstreamTranslation(text, fromLang, toLang) {
  let saw429 = false;

  // Provider 1: Google Clients5 (Chrome Extension endpoint - reliable from datacenter IPs)
  try {
    const c1 = new AbortController();
    const t1 = setTimeout(() => c1.abort(), 3500);
    const u1 = `https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=${fromLang}&tl=${toLang}&q=${encodeURIComponent(text)}`;
    const r1 = await fetch(u1, {
      signal: c1.signal,
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }
    });
    clearTimeout(t1);

    if (r1.status === 429) saw429 = true;
    if (r1.ok) {
      const data = await r1.json();
      if (Array.isArray(data) && data[0]) {
        const trans = Array.isArray(data[0]) ? data[0][0] : data[0];
        if (trans && typeof trans === 'string') {
          return { translated: trans.trim(), provider: 'google_clients5' };
        }
      }
    }
  } catch (e) {}

  // Provider 2: MyMemory Translation API (Free tier fallback)
  try {
    const c2 = new AbortController();
    const t2 = setTimeout(() => c2.abort(), 3500);
    const u2 = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=${fromLang}|${toLang}`;
    const r2 = await fetch(u2, {
      signal: c2.signal,
      headers: { 'User-Agent': 'Mozilla/5.0' }
    });
    clearTimeout(t2);

    if (r2.status === 429) saw429 = true;
    if (r2.ok) {
      const data = await r2.json();
      const trans = data?.responseData?.translatedText;
      if (trans && typeof trans === 'string' && !trans.toUpperCase().includes('QUERY LENGTH LIMIT EXCEEDED')) {
        return { translated: trans.trim(), provider: 'mymemory' };
      }
    }
  } catch (e) {}

  // Provider 3: Google GTX
  try {
    const c3 = new AbortController();
    const t3 = setTimeout(() => c3.abort(), 3000);
    const u3 = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=${fromLang}&tl=${toLang}&dt=t&q=${encodeURIComponent(text)}`;
    const r3 = await fetch(u3, {
      signal: c3.signal,
      headers: { 'User-Agent': 'Mozilla/5.0' }
    });
    clearTimeout(t3);

    if (r3.status === 429) saw429 = true;
    if (r3.ok) {
      const data = await r3.json();
      if (Array.isArray(data?.[0])) {
        const trans = data[0].map(part => part[0] || '').join('').trim();
        if (trans) {
          return { translated: trans, provider: 'google_gtx' };
        }
      }
    }
  } catch (e) {}

  if (saw429) {
    return { error: 'rate_limited', message: 'Translation service busy or rate-limited. Please wait a moment or use the Phrasebook.' };
  }

  return { error: 'service_unavailable', message: 'Translation service unavailable right now, try again or use the Phrasebook.' };
}

export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  let text = '';
  let fromLang = 'en';
  let toLang = 'hi';

  if (req.method === 'POST') {
    let body = req.body;
    if (typeof body === 'string') {
      try {
        body = JSON.parse(body);
      } catch (e) {
        body = {};
      }
    }
    text = (body && body.text) || '';
    fromLang = (body && body.from) || 'en';
    toLang = (body && body.to) || 'hi';
  } else {
    text = req.query.text || '';
    fromLang = req.query.from || 'en';
    toLang = req.query.to || 'hi';
  }

  const startTime = Date.now();
  const tClean = (text || '').trim();
  const tLower = tClean.toLowerCase().replace(/[.?!]+$/, '');

  // Empty string
  if (!tClean) {
    return res.status(200).json({
      original: '',
      translated: '',
      from: fromLang,
      to: toLang,
      latency_ms: 0
    });
  }

  // Same language passthrough
  if (fromLang === toLang) {
    return res.status(200).json({
      original: text,
      translated: tClean,
      from: fromLang,
      to: toLang,
      latency_ms: 0
    });
  }

  // Tier 1: Check Exact Local Dictionary (0ms, 100% Offline)
  if (LOCAL_DICTIONARY[tLower] && LOCAL_DICTIONARY[tLower][toLang]) {
    return res.status(200).json({
      original: text,
      translated: LOCAL_DICTIONARY[tLower][toLang],
      from: fromLang,
      to: toLang,
      provider: 'local_dictionary',
      latency_ms: Date.now() - startTime
    });
  }

  if (LOCAL_DICTIONARY[tClean] && LOCAL_DICTIONARY[tClean][toLang]) {
    return res.status(200).json({
      original: text,
      translated: LOCAL_DICTIONARY[tClean][toLang],
      from: fromLang,
      to: toLang,
      provider: 'local_dictionary',
      latency_ms: Date.now() - startTime
    });
  }

  // Tier 2: Check Phrasebook (0ms, 100% Offline)
  const phrasebook = getPhrasebook();
  for (const item of phrasebook) {
    const en = (item.english || '').toLowerCase().replace(/[.?!]+$/, '');
    if (en === tLower || (tLower.length > 5 && en.includes(tLower))) {
      let candidate = '';
      if (toLang === 'hi' && item.hindi) candidate = item.hindi.split('(')[0].trim();
      else if (toLang === 'te' && item.telugu) candidate = item.telugu.split('(')[0].trim();
      else if (toLang === 'ta' && item.tamil) candidate = item.tamil.split('(')[0].trim();
      else if (toLang === 'en' && item.english) candidate = item.english;

      if (candidate) {
        return res.status(200).json({
          original: text,
          translated: candidate,
          from: fromLang,
          to: toLang,
          provider: 'phrasebook',
          latency_ms: Date.now() - startTime
        });
      }
    }
  }

  // Tier 3: Upstream Translation (Multi-provider resilient engine)
  const result = await performUpstreamTranslation(tClean, fromLang, toLang);
  const latency = Date.now() - startTime;

  if (result.translated) {
    return res.status(200).json({
      original: text,
      translated: result.translated,
      from: fromLang,
      to: toLang,
      provider: result.provider,
      latency_ms: latency
    });
  }

  if (result.error === 'rate_limited') {
    return res.status(429).json({
      original: text,
      translated: result.message,
      status: 'rate_limited',
      from: fromLang,
      to: toLang,
      latency_ms: latency
    });
  }

  return res.status(503).json({
    original: text,
    translated: result.message,
    status: 'service_unavailable',
    from: fromLang,
    to: toLang,
    latency_ms: latency
  });
}
