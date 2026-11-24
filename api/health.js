export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const results = {};

  // Test 1: Google GTX
  try {
    const r1 = await fetch('https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=hi&dt=t&q=test', {
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }
    });
    results.google_gtx = { status: r1.status, ok: r1.ok };
  } catch (e) {
    results.google_gtx = { error: e.message };
  }

  // Test 2: Google Clients5
  try {
    const r2 = await fetch('https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl=hi&q=test', {
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }
    });
    results.google_clients5 = { status: r2.status, ok: r2.ok };
  } catch (e) {
    results.google_clients5 = { error: e.message };
  }

  // Test 3: MyMemory API
  try {
    const r3 = await fetch('https://api.mymemory.translated.net/get?q=test&langpair=en|hi', {
      headers: { 'User-Agent': 'Mozilla/5.0' }
    });
    const d3 = await r3.json();
    results.mymemory = { status: r3.status, ok: r3.ok, translation: d3?.responseData?.translatedText };
  } catch (e) {
    results.mymemory = { error: e.message };
  }

  const anyOnline = Object.values(results).some(r => r.ok);

  return res.status(200).json({
    status: 'healthy',
    service: 'Lingua Link Vercel Serverless',
    version: '1.0.1',
    internet_online: anyOnline,
    upstream_diagnostics: results,
    deployment: 'Vercel Serverless'
  });
}
