#!/usr/bin/env python3
"""Update GitHub traffic badges. Requires a dedicated read-only traffic token.

Run from the repository root. No credentials or raw API payloads are written.
GitHub traffic covers a *rolling 14-day period*, never lifetime totals.
"""

import datetime as dt
import html
import json
import os
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

REPOSITORY = os.environ.get("GITHUB_REPOSITORY", "YoungJibbit95/Neutron-Client")
OUTPUT = Path("assets/readme/metrics")


def get_traffic(kind, token):
    url = "https://api.github.com/repos/{}/{}/traffic/{}".format(*REPOSITORY.split("/"), kind)
    req = Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": "Bearer " + token,
            "X-GitHub-Api-Version": "2026-03-10",
            "User-Agent": "neutron-repository-traffic-badges",
        },
    )
    with urlopen(req, timeout=25) as response:
        data = json.load(response)
    count, uniques = data.get("count"), data.get("uniques")
    if (not isinstance(count, int) or isinstance(count, bool) or count < 0
            or not isinstance(uniques, int) or isinstance(uniques, bool) or uniques < 0):
        raise ValueError("Missing or invalid GitHub traffic counts for " + kind)
    return count, uniques


def make_svg(kind, count, uniques, utc_day):
    label = "REPO VIEWS" if kind == "views" else "REPO CLONES"
    # Source-derived metrics only; no fabricated/approximate values.
    number = f"{count:,}"
    label = html.escape(label)
    unique_label = html.escape(f"{uniques:,} unique · 14 days · updated {utc_day} UTC")
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="280" height="88" viewBox="0 0 280 88" role="img" aria-labelledby="title desc">
<title id="title">Neutron GitHub {kind} in the last 14 days</title>
<desc id="desc">{number} {kind}; {unique_label}</desc>
<defs><linearGradient id="accent"><stop stop-color="#FF00EA"/><stop offset="1" stop-color="#FF6200"/></linearGradient></defs>
<rect x="0.75" y="0.75" width="278.5" height="86.5" rx="13" fill="#10111B" stroke="#60405D" stroke-width="1.5"/>
<rect x="1" y="1" width="5" height="86" rx="2.5" fill="url(#accent)"/>
<g font-family="Inter,Segoe UI,Arial,sans-serif">
<text x="18" y="21" font-size="12" font-weight="700" letter-spacing="1.5" fill="#CCA6C8">{label} · 14D</text>
<text x="18" y="48" font-size="24" font-weight="800" fill="#FCF6FF">{number}</text>
<text x="18" y="65" font-size="11" fill="#B8A1B3">{uniques:,} unique</text>
<text x="18" y="79" font-size="9" fill="#B8A1B3">Updated {html.escape(utc_day)} UTC</text>
</g></svg>'''



def main():
    token = os.environ.get("NEUTRON_TRAFFIC_TOKEN", "").strip()
    if not token:
        message = "NEUTRON_TRAFFIC_TOKEN is missing. Existing badges are kept; see docs/README_ASSETS.md."
        print("::warning::" + message)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
                summary.write(message + "\n")
        return
    # Obtain and validate both *before* writing either file.
    data = {kind: get_traffic(kind, token) for kind in ("views", "clones")}
    utc_day = dt.datetime.now(dt.timezone.utc).date().isoformat()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for kind, (count, uniques) in data.items():
        (OUTPUT / (kind + "-14d.svg")).write_text(
            make_svg(kind, count, uniques, utc_day), encoding="utf-8"
        )
        print("Updated {} 14-day count".format(kind))


if __name__ == "__main__":
    try:
        main()
    except (HTTPError, URLError, ValueError) as exc:
        raise SystemExit("GitHub traffic refresh failed; badges were not overwritten: " + str(exc))
