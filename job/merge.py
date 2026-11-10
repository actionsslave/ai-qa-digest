#!/usr/bin/env python3
"""Merge newly selected items into docs/digest.json.
Usage: merge.py selected.json [--digest docs/digest.json] [--now ISO]
selected.json: list of {title,url,source,excerpt,published_at}. Adds id and added_at,
drops duplicates (normalized URL), keeps the last 14 days (by added_at), sorts by added_at desc."""
import argparse, json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo
from common import normalize_url, item_id

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("selected")
    ap.add_argument("--digest", default="docs/digest.json")
    ap.add_argument("--now", default=None)
    a = ap.parse_args()
    now = datetime.fromisoformat(a.now) if a.now else datetime.now(ZoneInfo("Europe/Berlin"))
    p = Path(a.digest)
    cur = json.loads(p.read_text()) if p.exists() else {"items": []}
    items = {i["id"]: i for i in cur["items"]}
    added = 0
    for s in json.loads(Path(a.selected).read_text())[:8]:          # at most 8 new per run
        url = normalize_url(s["url"]); iid = item_id(url)
        if iid in items: continue
        assert len(s["excerpt"]) <= 280, s["title"]
        items[iid] = dict(id=iid, title=s["title"], url=url, source=s["source"], excerpt=s["excerpt"],
                          published_at=s["published_at"], added_at=now.isoformat(timespec="seconds"))
        added += 1
    cutoff = now - timedelta(days=14)
    kept = [i for i in items.values() if datetime.fromisoformat(i["added_at"]) >= cutoff]
    kept.sort(key=lambda i: (i["added_at"], i["published_at"]), reverse=True)
    out = {"generated_at": now.isoformat(timespec="seconds"), "items": kept}
    p.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n")
    print(f"added {added}, total {len(kept)}")

if __name__ == "__main__":
    main()
