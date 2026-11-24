import json
import urllib.request
import urllib.parse

with open("app/src/main/assets/phrasebook.json", "r", encoding="utf-8") as f:
    phrases = json.load(f)

print(f"Total phrases evaluated: {len(phrases)}\n")
passed_hi = 0
passed_te = 0
passed_ta = 0

def get_trans(text, to_lang):
    url = f"http://localhost:3000/api/translate?text=" + urllib.parse.quote(text) + f"&from=en&to={to_lang}"
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, timeout=5) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        return data.get("translated", "").strip()

for i, p in enumerate(phrases, 1):
    en = p["english"]
    exp_hi = p["hindi"].split("(")[0].strip()
    exp_te = p["telugu"].split("(")[0].strip()
    exp_ta = p["tamil"].split("(")[0].strip()

    res_hi = get_trans(en, "hi")
    res_te = get_trans(en, "te")
    res_ta = get_trans(en, "ta")

    ok_hi = (res_hi == exp_hi)
    ok_te = (res_te == exp_te)
    ok_ta = (res_ta == exp_ta)

    if ok_hi: passed_hi += 1
    if ok_te: passed_te += 1
    if ok_ta: passed_ta += 1

    status_hi = "PASS" if ok_hi else "DIFF"
    status_te = "PASS" if ok_te else "DIFF"
    status_ta = "PASS" if ok_ta else "DIFF"
    print(f"[{i:02d}/17] {en[:32]:<32} | HI: {status_hi:<4} | TE: {status_te:<4} | TA: {status_ta:<4}")

print("\n" + "="*50)
print("ACCURACY BENCHMARK ON 17 VERIFIED PHRASES:")
print(f"  • English -> Hindi:  {passed_hi}/17 ({passed_hi/17*100:.1f}%) exact match")
print(f"  • English -> Telugu: {passed_te}/17 ({passed_te/17*100:.1f}%) exact match")
print(f"  • English -> Tamil:  {passed_ta}/17 ({passed_ta/17*100:.1f}%) exact match")
print("="*50)
