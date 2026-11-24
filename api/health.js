export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  let isOnline = false;
  let activeProvider = null;

  // Test Provider 1: Google Clients5
  try {
    const c1 = new AbortController();
    const t1 = setTimeout(() => c1.abort(), 2500);
    const r1 = await fetch('https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl=hi&q=test', {
      signal: c1.signal,
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }
    });
    clearTimeout(t1);
    if (r1.ok) {
      isOnline = true;
      activeProvider = 'google_clients5';
    }
  } catch (e) {}

  // Test Provider 2: MyMemory API
  if (!isOnline) {
    try {
      const c2 = new AbortController();
      const t2 = setTimeout(() => c2.abort(), 2500);
      const r2 = await fetch('https://api.mymemory.translated.net/get?q=test&langpair=en|hi', {
        signal: c2.signal,
        headers: { 'User-Agent': 'Mozilla/5.0' }
      });
      clearTimeout(t2);
      if (r2.ok) {
        isOnline = true;
        activeProvider = 'mymemory';
      }
    } catch (e) {}
  }

  return res.status(200).json({
    status: 'healthy',
    service: 'Lingua Link Vercel Serverless',
    version: '1.0.2',
    internet_online: isOnline,
    active_provider: activeProvider,
    deployment: 'Vercel Serverless'
  });
}
