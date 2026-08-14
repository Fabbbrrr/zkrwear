"""Generate golden crypto test vectors from the REAL zeekr_ev_api signing code.

Runs the actual library functions (zeekr_hmac.generateHMAC and
zeekr_app_sig.calculate_sig) against fixed inputs with frozen date/nonce/
timestamp, and dumps the exact outputs to golden_vectors.json. The Kotlin
port's JUnit tests assert byte-for-byte equality against this file.

Reproducibility only — the vectors are already committed. To regenerate, first
copy `zeekr_hmac.py` and `zeekr_app_sig.py` from Fryyyy's MIT-licensed library
into this folder (they are intentionally not redistributed here):
    https://github.com/Fryyyyy/zeekr_ev_api  (src/zeekr_ev_api/)
Then: pip install requests && python gen_vectors.py
"""
import base64
import json
import types

import requests

import zeekr_hmac

# --- Load zeekr_app_sig without pycryptodome (aes_encrypt is unused here) ---
_src = open("zeekr_app_sig.py", encoding="utf-8").read()
_src = _src.replace("from Crypto.Cipher import AES\n", "")
_src = _src.replace("from Crypto.Util.Padding import pad\n", "")
appsig = types.ModuleType("appsig")
exec(compile(_src, "zeekr_app_sig.py", "exec"), appsig.__dict__)

# Freeze the GMT date the HMAC signer uses.
FIXED_GMT = "Mon, 10 Aug 2026 06:30:00 GMT"
zeekr_hmac._get_gmt_date = lambda: FIXED_GMT

vectors = {"hmac": [], "appsig": []}


def hmac_case(name, method, url, body, access_key, secret_key):
    req = requests.Request(method=method, url=url, data=body)
    zeekr_hmac.generateHMAC(req, access_key, secret_key)
    vectors["hmac"].append({
        "name": name,
        "method": method,
        "url": url,
        "body": body if body is not None else "",
        "access_key": access_key,
        "secret_key": secret_key,
        "gmt_date": FIXED_GMT,
        "expect": {
            "X-HMAC-ALGORITHM": req.headers["X-HMAC-ALGORITHM"],
            "X-HMAC-SIGNATURE": req.headers["X-HMAC-SIGNATURE"],
            "X-HMAC-ACCESS-KEY": req.headers["X-HMAC-ACCESS-KEY"],
            "X-HMAC-DIGEST": req.headers["X-HMAC-DIGEST"],
            "X-DATE": req.headers["X-DATE"],
        },
    })


def appsig_case(name, method, url, headers, body, secret):
    prepared = requests.Request(method=method, url=url, headers=dict(headers),
                                data=body).prepare()
    # requests may drop Content-Type if no body; force our headers back on.
    for k, v in headers.items():
        prepared.headers[k] = v
    if body is not None:
        prepared.body = body
    sig = appsig.calculate_sig(prepared, secret)
    vectors["appsig"].append({
        "name": name,
        "method": method,
        "url": url,
        "headers": dict(headers),
        "body": body if body is not None else "",
        "secret": secret,
        "expect": {"X-SIGNATURE": sig},
    })


ACCESS = "TESTACCESSKEY0000000000000000000"
SECRET = "TESTSECRETKEY0000000000000000000"
PROD = "TESTPRODSECRET000000000000000000"

# ---- HMAC vectors ----
hmac_case("post_json_no_query", "POST",
          "https://gateway-pub-hw-em-sg.zeekrlife.com/overseas-app/ms-vehicle-status/api/v1.0/vehicle/status/latest",
          '{"vin":"TESTVIN1234567890"}', ACCESS, SECRET)
hmac_case("get_no_body_no_query", "GET",
          "https://gateway-pub-hw-em-sg.zeekrlife.com/overseas-app/ms-app-bff/api/v4.0/veh/vehicle-list",
          None, ACCESS, SECRET)
hmac_case("get_with_query", "GET",
          "https://gateway-pub-hw-em-sg.zeekrlife.com/overseas-app/ms-vehicle-trail/v1.0/journalLog/trip/listForPage?pageSize=50&Page=1",
          None, ACCESS, SECRET)
hmac_case("post_empty_body", "POST",
          "https://gateway-pub-hw-em-sg.zeekrlife.com/overseas-app/x/y",
          "", ACCESS, SECRET)

# ---- App-signature vectors ----
appsig_case("auth_post_json", "POST",
            "https://sea-snc-tsp-api-gw.zeekrlife.com/auth/loginByEmailEncrypt",
            {
                "Content-Type": "application/json",
                "x-app-id": "app",
                "x-project-id": "ZEEKR_SEA",
                "authorization": "Bearer TESTBEARER",
                "x-timestamp": "1754807400000",
                "x-api-signature-nonce": "11111111-2222-3333-4444-555555555555",
                "x-api-signature-version": "1.0",
                "accept-language": "en",
                "x-vin": "ENCRYPTEDVINBASE64==",
                "x-device-id": "device-123",
                "x-platform": "app",
            },
            '{"email":"a@b.com","password":"cipher","zzz":1,"aaa":2}', PROD)
appsig_case("auth_get_with_query", "GET",
            "https://sea-snc-tsp-api-gw.zeekrlife.com/user/tspCode?b=2&A=1",
            {
                "Content-Type": "application/json",
                "x-app-id": "app",
                "x-project-id": "ZEEKR_SEA",
                "authorization": "Bearer TESTBEARER",
                "x-timestamp": "1754807400000",
                "x-api-signature-nonce": "11111111-2222-3333-4444-555555555555",
            },
            None, PROD)

with open("golden_vectors.json", "w", encoding="utf-8") as f:
    json.dump(vectors, f, indent=2, ensure_ascii=False)

print("Wrote golden_vectors.json")
print(json.dumps(vectors, indent=2, ensure_ascii=False))
