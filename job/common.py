"""Shared helpers for the digest job (stdlib only)."""
import hashlib, re, html
from urllib.parse import urlsplit, urlunsplit, parse_qsl, urlencode

TRACKING = re.compile(r"^(utm_.*|fbclid|gclid|mc_cid|mc_eid|ref|ref_src|source|share|igshid|_hsenc|_hsmi|mkt_tok|s|si)$", re.I)

def normalize_url(url: str) -> str:
    p = urlsplit(url.strip())
    q = [(k, v) for k, v in parse_qsl(p.query, keep_blank_values=True) if not TRACKING.match(k)]
    host = p.netloc.lower()
    if host.startswith("www."):
        host = host[4:]
    path = p.path.rstrip("/") or "/"
    scheme = "https" if p.scheme in ("http", "https") else p.scheme
    return urlunsplit((scheme, host, path, urlencode(q), ""))

def item_id(url: str) -> str:
    return hashlib.sha256(normalize_url(url).encode()).hexdigest()[:16]

def clean_text(s: str) -> str:
    s = re.sub(r"(?is)<(script|style)[^>]*>.*?</\1>", " ", s or "")
    s = re.sub(r"(?s)<[^>]+>", " ", s)
    s = html.unescape(s)
    s = re.sub(r"\s+", " ", s).strip()
    return re.sub(r"\s*submitted by /u/\S+ \[link\] \[comments\]$", "", s)

def excerpt(s: str, limit: int = 280) -> str:
    s = clean_text(s)
    if len(s) <= limit:
        return s
    cut = s[: limit - 1]
    cut = cut[: cut.rfind(" ")] if " " in cut[-40:] else cut
    return cut.rstrip(",;:.- ") + "…"
