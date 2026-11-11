#!/usr/bin/env python3
"""Nostr publishing for the digest (standard library only).

  nostr.py keygen                      print a new key (hex, nsec, npub); nothing is stored
  nostr.py pubkey                      print the public key for NOSTR_SECRET_KEY
  nostr.py publish [--digest FILE]     sign and publish every item of digest.json to the relays
  nostr.py read    [--since SECONDS]   read the digest events back from the relays (for checking)

Secret: env NOSTR_SECRET_KEY (64 hex characters or an nsec1... string). It is never printed or logged.
Relays and the account's public key: job/nostr.json.

Each digest item becomes one NIP-23 long-form event (kind 30023, addressable, d tag = item id):
  content    excerpt + blank line + url          (readable in any Nostr client)
  tags       d, title, summary, published_at, r (url), source, t
  created_at the item's added_at
Publishing is idempotent: signing is deterministic, so the same item always yields the same event.
Signatures: BIP340 (Schnorr, secp256k1), pure Python, only used for this low-stakes account.
"""
import argparse, base64, hashlib, json, os, secrets, socket, ssl, struct, sys, time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit, unquote

KIND_ARTICLE = 30023
CONFIG = Path(__file__).with_name("nostr.json")

# ---------------------------------------------------------------- BIP340 (reference algorithm)
P = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
G = (0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798,
     0x483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8)

def _add(a, b):
    if a is None: return b
    if b is None: return a
    if a[0] == b[0] and (a[1] + b[1]) % P == 0: return None
    if a == b: lam = 3 * a[0] * a[0] * pow(2 * a[1], -1, P) % P
    else: lam = (b[1] - a[1]) * pow(b[0] - a[0], -1, P) % P
    x = (lam * lam - a[0] - b[0]) % P
    return (x, (lam * (a[0] - x) - a[1]) % P)

def _mul(pt, k):
    r = None
    while k:
        if k & 1: r = _add(r, pt)
        pt = _add(pt, pt); k >>= 1
    return r

def _th(tag, msg):
    t = hashlib.sha256(tag.encode()).digest()
    return hashlib.sha256(t + t + msg).digest()

def _lift_x(x):
    if x >= P: return None
    y = pow((x ** 3 + 7) % P, (P + 1) // 4, P)
    if y * y % P != (x ** 3 + 7) % P: return None
    return (x, y if y % 2 == 0 else P - y)

def pubkey(sk: bytes) -> bytes:
    d = int.from_bytes(sk, "big")
    if not 1 <= d < N: raise ValueError("invalid secret key")
    return _mul(G, d)[0].to_bytes(32, "big")

def schnorr_sign(msg: bytes, sk: bytes, aux: bytes) -> bytes:
    d0 = int.from_bytes(sk, "big")
    pt = _mul(G, d0)
    d = d0 if pt[1] % 2 == 0 else N - d0
    t = (d ^ int.from_bytes(_th("BIP0340/aux", aux), "big")).to_bytes(32, "big")
    k0 = int.from_bytes(_th("BIP0340/nonce", t + pt[0].to_bytes(32, "big") + msg), "big") % N
    if k0 == 0: raise RuntimeError("bad nonce")
    r = _mul(G, k0)
    k = k0 if r[1] % 2 == 0 else N - k0
    e = int.from_bytes(_th("BIP0340/challenge", r[0].to_bytes(32, "big") + pt[0].to_bytes(32, "big") + msg), "big") % N
    sig = r[0].to_bytes(32, "big") + ((k + e * d) % N).to_bytes(32, "big")
    if not schnorr_verify(msg, pt[0].to_bytes(32, "big"), sig): raise RuntimeError("self-check failed")
    return sig

def schnorr_verify(msg: bytes, pub: bytes, sig: bytes) -> bool:
    pt = _lift_x(int.from_bytes(pub, "big"))
    r, s = int.from_bytes(sig[:32], "big"), int.from_bytes(sig[32:], "big")
    if pt is None or r >= P or s >= N: return False
    e = int.from_bytes(_th("BIP0340/challenge", sig[:32] + pub + msg), "big") % N
    R = _add(_mul(G, s), _mul(pt, N - e))
    return R is not None and R[1] % 2 == 0 and R[0] == r

# ---------------------------------------------------------------- bech32 (npub / nsec)
_B32 = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
def _polymod(values):
    gen = [0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3]; chk = 1
    for v in values:
        b = chk >> 25; chk = (chk & 0x1ffffff) << 5 ^ v
        for i in range(5): chk ^= gen[i] if (b >> i) & 1 else 0
    return chk
def _hrp_expand(h): return [ord(c) >> 5 for c in h] + [0] + [ord(c) & 31 for c in h]
def _convert(data, frm, to, pad=True):
    acc = bits = 0; out = []; maxv = (1 << to) - 1
    for v in data:
        acc = (acc << frm) | v; bits += frm
        while bits >= to: bits -= to; out.append((acc >> bits) & maxv)
    if pad and bits: out.append((acc << (to - bits)) & maxv)
    elif not pad and (bits >= frm or ((acc << (to - bits)) & maxv)): raise ValueError("bad padding")
    return out
def bech32_encode(hrp, raw: bytes) -> str:
    d = _convert(raw, 8, 5)
    c = _polymod(_hrp_expand(hrp) + d + [0] * 6) ^ 1
    return hrp + "1" + "".join(_B32[x] for x in d + [(c >> 5 * (5 - i)) & 31 for i in range(6)])
def bech32_decode(s: str):
    s = s.lower(); hrp, _, data = s.rpartition("1")
    d = [_B32.index(c) for c in data]
    if _polymod(_hrp_expand(hrp) + d) != 1: raise ValueError("bad checksum")
    return hrp, bytes(_convert(d[:-6], 5, 8, False))

def load_secret() -> bytes:
    v = (os.environ.get("NOSTR_SECRET_KEY") or "").strip()
    if not v: sys.exit("NOSTR_SECRET_KEY is not set")
    if v.startswith("nsec1"):
        hrp, raw = bech32_decode(v)
        if hrp != "nsec": sys.exit("not an nsec")
        return raw
    return bytes.fromhex(v)

# ---------------------------------------------------------------- events
def _clean(s: str) -> str:
    """Only characters NIP-01 serialization defines an escape for (plus printable text)."""
    return "".join(c for c in s if c in "\n\t" or (ord(c) >= 32 and ord(c) != 127))

def event_id(ev) -> str:
    ser = json.dumps([0, ev["pubkey"], ev["created_at"], ev["kind"], ev["tags"], ev["content"]],
                     separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(ser.encode("utf-8")).hexdigest()

def make_event(sk: bytes, kind, tags, content, created_at) -> dict:
    ev = {"pubkey": pubkey(sk).hex(), "created_at": int(created_at), "kind": kind,
          "tags": [[_clean(x) for x in t] for t in tags], "content": _clean(content)}
    ev["id"] = event_id(ev)
    ev["sig"] = schnorr_sign(bytes.fromhex(ev["id"]), sk, hashlib.sha256(b"aiqa-digest" + bytes.fromhex(ev["id"])).digest()).hex()
    return ev

def epoch(iso: str) -> int:
    return int(datetime.fromisoformat(iso.replace("Z", "+00:00")).astimezone(timezone.utc).timestamp())

def item_event(sk: bytes, it) -> dict:
    tags = [["d", it["id"]], ["title", it["title"]], ["summary", it["excerpt"]],
            ["published_at", str(epoch(it["published_at"]))], ["r", it["url"]],
            ["source", it["source"]], ["t", "ai-qa-digest"]]
    return make_event(sk, KIND_ARTICLE, tags, f'{it["excerpt"]}\n\n{it["url"]}', epoch(it["added_at"]))

def profile_event(sk: bytes, cfg) -> dict:
    prof = {"name": "aiqadigest", "display_name": "AI & QA Digest",
            "about": "Curated English-language resources on AI for software testing and on testing AI systems. "
                     "Automated account of a private digest; each entry is a long-form note with the link and the source's own first lines."}
    prof.update(cfg.get("profile", {}))
    return make_event(sk, 0, [], json.dumps(prof, ensure_ascii=False), 1700000000)  # fixed time: identical event on every run

# ---------------------------------------------------------------- minimal WebSocket client (RFC 6455)
class WS:
    def __init__(self, url, timeout=20):
        u = urlsplit(url); assert u.scheme == "wss", "wss only"
        host, port = u.hostname, u.port or 443
        path = (u.path or "/") + (("?" + u.query) if u.query else "")
        self.timeout = timeout
        raw = self._tcp(host, port)
        self.sock = ssl.create_default_context().wrap_socket(raw, server_hostname=host)
        self.sock.settimeout(timeout)
        key = base64.b64encode(secrets.token_bytes(16)).decode()
        self.sock.sendall((f"GET {path} HTTP/1.1\r\nHost: {host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                           f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n"
                           f"User-Agent: ai-qa-digest/1.0\r\n\r\n").encode())
        self.f = self.sock.makefile("rb")
        status = self.f.readline().decode("latin1")
        if " 101 " not in status: raise ConnectionError("handshake failed: " + status.strip()[:80])
        while self.f.readline() not in (b"\r\n", b""): pass

    def _tcp(self, host, port):
        proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
        if not proxy or os.environ.get("GITHUB_ACTIONS"):
            return socket.create_connection((host, port), timeout=self.timeout)
        p = urlsplit(proxy)
        s = socket.create_connection((p.hostname, p.port), timeout=self.timeout)
        auth = ""
        if p.username:
            auth = "Proxy-Authorization: Basic " + base64.b64encode(f"{unquote(p.username)}:{unquote(p.password or '')}".encode()).decode() + "\r\n"
        s.sendall(f"CONNECT {host}:{port} HTTP/1.1\r\nHost: {host}:{port}\r\n{auth}\r\n".encode())
        buf = b""
        while b"\r\n\r\n" not in buf:
            chunk = s.recv(4096)
            if not chunk: raise ConnectionError("proxy closed")
            buf += chunk
        if b" 200 " not in buf.split(b"\r\n")[0]: raise ConnectionError("proxy: " + buf.split(b"\r\n")[0].decode()[:60])
        return s

    def send(self, text: str, opcode=1):
        data = text.encode(); n = len(data); mask = secrets.token_bytes(4)
        hdr = bytes([0x80 | opcode]) + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack(">H", n) if n < 65536 else bytes([0x80 | 127]) + struct.pack(">Q", n))
        self.sock.sendall(hdr + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

    def _frame(self):
        b1, b2 = self.f.read(2)
        n = b2 & 0x7F
        if n == 126: n = struct.unpack(">H", self.f.read(2))[0]
        elif n == 127: n = struct.unpack(">Q", self.f.read(8))[0]
        mask = self.f.read(4) if b2 & 0x80 else None
        data = self.f.read(n)
        if mask: data = bytes(b ^ mask[i % 4] for i, b in enumerate(data))
        return b1 & 0x0F, bool(b1 & 0x80), data

    def recv(self):
        buf = b""
        while True:
            op, fin, data = self._frame()
            if op == 9: self.send(data.decode("latin1"), 10); continue
            if op == 10: continue
            if op == 8: raise ConnectionError("closed by relay")
            buf += data
            if fin: return buf.decode("utf-8", "replace")

    def close(self):
        try: self.sock.close()
        except Exception: pass

# ---------------------------------------------------------------- relay operations
def publish_to(relay, events, timeout=25):
    """Send all events, return {event_id: (accepted, message)}."""
    res = {}
    ws = WS(relay, timeout=timeout)
    try:
        for ev in events: ws.send(json.dumps(["EVENT", ev], separators=(",", ":"), ensure_ascii=False))
        end = time.time() + timeout
        while len(res) < len(events) and time.time() < end:
            m = json.loads(ws.recv())
            if m[0] == "OK": res[m[1]] = (bool(m[2]), str(m[3])[:120] if len(m) > 3 else "")
    except (socket.timeout, TimeoutError):
        pass
    finally:
        ws.close()
    return res

def read_from(relay, pub, since, timeout=20):
    ws = WS(relay, timeout=timeout); out = []
    try:
        ws.send(json.dumps(["REQ", "d", {"kinds": [KIND_ARTICLE], "authors": [pub], "since": since, "limit": 500}]))
        end = time.time() + timeout
        while time.time() < end:
            m = json.loads(ws.recv())
            if m[0] == "EVENT": out.append(m[2])
            elif m[0] in ("EOSE", "CLOSED"): break
    except (socket.timeout, TimeoutError):
        pass
    finally:
        ws.close()
    return out

def valid(ev, pub) -> bool:
    try:
        return (ev["pubkey"] == pub and event_id(ev) == ev["id"]
                and schnorr_verify(bytes.fromhex(ev["id"]), bytes.fromhex(pub), bytes.fromhex(ev["sig"])))
    except Exception:
        return False

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("cmd", choices=["keygen", "pubkey", "publish", "read"])
    ap.add_argument("--digest", default="docs/digest.json"); ap.add_argument("--since", type=int, default=14 * 86400)
    a = ap.parse_args()
    cfg = json.loads(CONFIG.read_text()) if CONFIG.exists() else {}
    if a.cmd == "keygen":
        sk = secrets.token_bytes(32)
        while not 1 <= int.from_bytes(sk, "big") < N: sk = secrets.token_bytes(32)
        print("hex ", sk.hex()); print("nsec", bech32_encode("nsec", sk)); print("npub", bech32_encode("npub", pubkey(sk)))
        print("pub ", pubkey(sk).hex()); return
    if a.cmd == "pubkey":
        pk = pubkey(load_secret()); print(pk.hex()); print(bech32_encode("npub", pk)); return
    if a.cmd == "read":
        pub = cfg["pubkey"]; seen = {}
        for r in cfg["relays"]:
            try:
                evs = [e for e in read_from(r, pub, int(time.time()) - a.since) if valid(e, pub)]
                print(f"{r}: {len(evs)} valid events")
                for e in evs: seen[e["id"]] = e
            except Exception as ex: print(f"{r}: FAILED {type(ex).__name__}: {ex}")
        for e in sorted(seen.values(), key=lambda e: -e["created_at"]):
            t = {x[0]: x[1] for x in e["tags"] if len(x) > 1}; print(" -", t.get("title", "")[:80], "|", t.get("source"))
        return
    # publish
    sk = load_secret()
    if cfg.get("pubkey") and pubkey(sk).hex() != cfg["pubkey"]:
        sys.exit("NOSTR_SECRET_KEY does not match the pubkey in job/nostr.json")
    items = json.loads(Path(a.digest).read_text())["items"]
    events = [profile_event(sk, cfg)] + [item_event(sk, it) for it in items]
    ok_relays = 0
    for r in cfg["relays"]:
        try:
            res = publish_to(r, events)
            acc = sum(1 for v in res.values() if v[0]); rej = [v[1] for v in res.values() if not v[0]]
            print(f"{r}: {acc}/{len(events)} accepted" + (f", rejected: {sorted(set(rej))[:2]}" if rej else "") + (f", no answer for {len(events) - len(res)}" if len(res) < len(events) else ""))
            ok_relays += acc == len(events) or (acc > 0 and all("duplicate" in m or "replaced" in m for m in rej))
        except Exception as ex:
            print(f"{r}: FAILED {type(ex).__name__}: {ex}")
    print(f"{ok_relays}/{len(cfg['relays'])} relays fully up to date")
    sys.exit(0 if ok_relays else 1)

if __name__ == "__main__":
    main()
