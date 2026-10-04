"""Dev stand-in for external messaging gateways (OTP SMS + FCM).

Endpoints mirror the pluggable provider contract (master spec Section 3.2):

    POST /otp/request   {"e164": "+9198..."}   -> prints + stores a 6-digit code
    POST /otp/verify    {"e164": "...", "code": "..."}
    POST /fcm/send      {"token": "...", "data": {...}}
    GET  /health

Codes are printed to stdout and served at GET /otp/last for convenience.
NEVER point production at this service.
"""

import json
import random
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = 8090
LAST_CODES = {}


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self):
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {}

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"ok": True})
        elif self.path == "/otp/last":
            self._send(200, {"codes": LAST_CODES})
        else:
            self._send(404, {"ok": False})

    def do_POST(self):
        body = self._read_body()

        if self.path == "/otp/request":
            e164 = str(body.get("e164", ""))
            code = f"{random.SystemRandom().randint(0, 999999):06d}"
            LAST_CODES[e164] = code
            print(f"[fake-otp] code for {e164}: {code}", flush=True)
            self._send(200, {"ok": True, "expiresInSeconds": 300})

        elif self.path == "/otp/verify":
            e164 = str(body.get("e164", ""))
            ok = LAST_CODES.get(e164) == str(body.get("code", ""))
            print(f"[fake-otp] verify {e164}: {'ok' if ok else 'mismatch'}", flush=True)
            self._send(200 if ok else 401, {"ok": ok})

        elif self.path == "/fcm/send":
            print(f"[fake-fcm] push payload: {json.dumps(body)}", flush=True)
            self._send(200, {"ok": True})

        else:
            self._send(404, {"ok": False})

    def log_message(self, fmt, *args):
        # Keep request noise out; business lines are printed above.
        pass


if __name__ == "__main__":
    print(f"[fake-gateway] listening on :{PORT}", flush=True)
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
