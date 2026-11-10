#!/usr/bin/env python3
"""Fetch all sources, print candidates (new since `since`) as JSON.
Usage: collect.py --since ISO [--out candidates.json] [--known digest.json]
The curation step (judgement) happens afterwards; this script only collects."""
import argparse, json, sys, time, urllib.request, urllib.error
import xml.etree.ElementTree as ET
from datetime import datetime, timezone, timedelta
from email.utils import parsedate_to_datetime
from pathlib import Path
from common import normalize_url, item_id, excerpt, clean_text

UA = "Mozilla/5.0 (compatible; ai-qa-digest/1.0; personal feed reader)"
NS = {"a": "http://www.w3.org/2005/Atom", "c": "http://purl.org/rss/1.0/modules/content/",
      "dc": "http://purl.org/dc/elements/1.1/"}

def fetch(url, tries=3):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/xml,text/xml,*/*"})
            with urllib.request.urlopen(req, timeout=25) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            last = f"HTTP {e.code}"
            if e.code == 429:
                time.sleep(5 * (i + 1)); continue
            if e.code in (404, 403, 410):
                break
        except Exception as e:
            last = type(e).__name__ + ": " + str(e)[:80]
            time.sleep(2)
    raise RuntimeError(last)

def parse_date(s):
    if not s: return None
    s = s.strip()
    try:
        d = datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        try: d = parsedate_to_datetime(s)
        except Exception: return None
    if d.tzinfo is None: d = d.replace(tzinfo=timezone.utc)
    return d

def txt(el, path):
    x = el.find(path, NS)
    return (x.text or "").strip() if x is not None and x.text else ""

def parse_feed(data):
    root = ET.fromstring(data)
    out = []
    if root.tag.endswith("feed"):  # Atom
        for e in root.findall("a:entry", NS):
            link = ""
            for l in e.findall("a:link", NS):
                if l.get("rel", "alternate") == "alternate": link = l.get("href", ""); break
            body = txt(e, "a:summary") or txt(e, "a:content")
            out.append(dict(title=clean_text(txt(e, "a:title")), url=link,
                            date=parse_date(txt(e, "a:published") or txt(e, "a:updated")), body=body))
    else:  # RSS
        for e in root.iter("item"):
            body = txt(e, "description") or txt(e, "c:encoded")
            if len(clean_text(body)) < 40:
                body = txt(e, "c:encoded") or body
            out.append(dict(title=clean_text(txt(e, "title")), url=txt(e, "link"),
                            date=parse_date(txt(e, "pubDate") or txt(e, "dc:date")), body=body))
    return out

def hn_items(s, since):
    """Hacker News via the official Algolia API (hnrss.org search answered 502)."""
    import urllib.parse
    out, seen = [], set()
    for q in s["queries"]:
        url = "https://hn.algolia.com/api/v1/search_by_date?" + urllib.parse.urlencode(
            {"query": q, "tags": "story", "numericFilters": f"points>={s['min_points']},created_at_i>{int(since.timestamp())}"})
        for h in json.loads(fetch(url)).get("hits", []):
            if not h.get("url") or h["objectID"] in seen: continue
            seen.add(h["objectID"])
            out.append(dict(title=h["title"], url=h["url"], date=parse_date(h["created_at"]),
                            body=f"{h['points']} points on Hacker News: https://news.ycombinator.com/item?id={h['objectID']}"))
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--since", required=True)
    ap.add_argument("--out", default="candidates.json")
    ap.add_argument("--known", default=None, help="existing digest.json (for dedup)")
    ap.add_argument("--sources", default=str(Path(__file__).with_name("sources.json")))
    a = ap.parse_args()
    since = parse_date(a.since)
    known = set()
    if a.known and Path(a.known).exists():
        known = {i["id"] for i in json.loads(Path(a.known).read_text()).get("items", [])}
    report, cands, seen = [], [], set()
    for s in json.loads(Path(a.sources).read_text())["sources"]:
        try:
            items = hn_items(s, since) if s.get("type") == "hn_algolia" else parse_feed(fetch(s["url"]))
            n_new = 0
            for it in items:
                if not it["url"] or not it["date"] or it["date"] < since: continue
                iid = item_id(it["url"])
                if iid in known or iid in seen: continue
                seen.add(iid); n_new += 1
                cands.append(dict(id=iid, title=it["title"], url=normalize_url(it["url"]) if False else it["url"],
                                  source=s["name"], group=s["group"], published_at=it["date"].isoformat(),
                                  excerpt=excerpt(it["body"]),
                                  length=len(clean_text(it["body"]))))
            report.append(dict(source=s["name"], url=s.get("url", s.get("type")), ok=True, entries=len(items), new=n_new))
        except Exception as e:
            report.append(dict(source=s["name"], url=s.get("url", s.get("type")), ok=False, error=str(e)))
        time.sleep(1)
    cands.sort(key=lambda c: c["published_at"], reverse=True)
    Path(a.out).write_text(json.dumps(dict(since=since.isoformat(), report=report, candidates=cands), indent=1, ensure_ascii=False))
    for r in report:
        print(("OK  " if r["ok"] else "FAIL"), r["source"], r.get("entries", ""), r.get("new", ""), r.get("error", ""), file=sys.stderr)
    print(f"{len(cands)} candidates", file=sys.stderr)

if __name__ == "__main__":
    main()
