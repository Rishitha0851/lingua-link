import fs from 'fs';
import path from 'path';

export default function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  // Attempt to read from dashboard or app assets
  const possiblePaths = [
    path.join(process.cwd(), 'dashboard', 'phrasebook.json'),
    path.join(process.cwd(), 'phrasebook.json'),
    path.join(process.cwd(), 'app', 'src', 'main', 'assets', 'phrasebook.json')
  ];

  for (const p of possiblePaths) {
    if (fs.existsSync(p)) {
      const data = fs.readFileSync(p, 'utf-8');
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      return res.status(200).send(data);
    }
  }

  // Bundled fallback
  return res.status(200).json([
    { id: "taxi_01", category: "Taxi & Auto", english: "How much to go to the Railway Station?", hindi: "रेलवे स्टेशन जाने का कितना लेंगे?", tamil: "ரயில் நிலையத்திற்கு செல்ல எவ்வளவு ஆகும்?", telugu: "రైల్వే స్టేషన్ వెళ్ళడానికి ఎంత అవుతుంది?" },
    { id: "taxi_02", category: "Taxi & Auto", english: "Please turn on the meter.", hindi: "कृपया मीटर चालू कीजिए।", tamil: "தயவுசெய்து மீட்டர் போடுங்கள்.", telugu: "దయచేసి మీటర్ వేయండి." },
    { id: "price_01", category: "Price & Shopping", english: "How much does this cost?", hindi: "यह कितने का है?", tamil: "இதன் விலை என்ன?", telugu: "దీని ధర ఎంత?" },
    { id: "price_02", category: "Price & Shopping", english: "This is too expensive. Can you reduce the price?", hindi: "यह बहुत महंगा है। थोड़ा कम कर सकते हैं?", tamil: "இது மிகவும் விலை அதிகம். கொஞ்சம் குறைக்க முடியுமா?", telugu: "ఇది చాలా ఎక్కువ ధర. కొంచెం తగ్గిస్తారా?" },
    { id: "pay_01", category: "Payment & UPI", english: "Do you accept Google Pay or PhonePe / UPI?", hindi: "क्या आप गूगल पे या फोनपे / यूपीआई लेते हैं?", tamil: "கூகிள் பே அல்லது ஃபோன்பே / யுபிஐ ஏற்கிறீர்களா?", telugu: "మీరు Google Pay లేదా PhonePe / UPI తీసుకుంటారా?" },
    { id: "dir_01", category: "Directions", english: "Which way is the bus stand?", hindi: "बस स्टैंड किस तरफ है?", tamil: "பஸ் நிலையம் எந்த வழி?", telugu: "బస్ స్టాండ్ ఏ వైపు?" },
    { id: "food_01", category: "Food & Water", english: "Please give me a bottle of drinking water.", hindi: "कृपया मुझे पीने के पानी की एक बोतल दीजिए।", tamil: "தயவுசெய்து எனக்கு ஒரு குடிநீர் பாட்டில் கொடுங்கள்.", telugu: "దయచేసి నాకు ఒక మంచి నీళ్ల బాటిల్ ఇవ్వండి." },
    { id: "hotel_01", category: "Hotel & Stay", english: "Do you have a room available for tonight?", hindi: "क्या आज रात के लिए कमरा उपलब्ध है?", tamil: "இன்று இரவுக்கு அறை உள்ளதா?", telugu: "ఈ రాత్రికి గది అందుబాటులో ఉందా?" }
  ]);
}
