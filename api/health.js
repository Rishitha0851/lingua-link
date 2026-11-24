export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  let isOnline = false;
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2000);
    const testResp = await fetch('https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=hi&dt=t&q=test', {
      signal: controller.signal,
      headers: { 'User-Agent': 'Mozilla/5.0' }
    });
    clearTimeout(timer);
    isOnline = testResp.ok;
  } catch (e) {
    isOnline = false;
  }

  return res.status(200).json({
    status: 'healthy',
    service: 'Lingua Link Vercel Serverless',
    version: '1.0.0',
    internet_online: isOnline,
    deployment: 'Vercel Serverless'
  });
}
