# README assets

The README keeps the dark PinkLemonade SVGs (`#FF00EA` / `#FF6200`). Graphics are
brand illustrations, not game captures. Decorative assets use repository-relative
paths; live traffic cards read the dedicated `readme-metrics` branch.

The header, feature panel and footer remain visible. Subtle header motion and two
compact separators add animation without moving the text. The SVGs are
self-contained, remain readable as still images, and disable animation when the
viewer requests reduced motion. The new animation markup is authored in this
repository; it requires no external scripts, fonts or animation service.

A compact row of dynamic badges links to the corresponding release, activity or
workflow pages. These badges report live GitHub metadata through Shields.io;
they do not make a gameplay compatibility or release-readiness claim.

## Traffic badges

Views and clones sit directly below the header and link to the repository traffic
page. Each SVG shows the total, unique count and last update date. The cards have
explicit dimensions and separate text rows so large totals do not overlap.

The numbers cover GitHub's **rolling 14-day window**; they are not lifetime counts
or README pixel hits. [GitHub traffic API](https://docs.github.com/en/rest/metrics/traffic).

The workflow runs **every day at 04:23 UTC** (`23 04 * * *`), also supports
`workflow_dispatch`, and refreshes after its workflow or traffic scripts change
on `main`. Scheduled Actions runs can be delayed by GitHub; the SVG's printed UTC
date is the source of truth for the last successful update.

To enable daily refreshes:

1. Create a fine-grained token restricted to `YoungJibbit95/Neutron-Client` with
   **Administration: read**. Save it as the Actions secret `NEUTRON_TRAFFIC_TOKEN`.
   This is required even though the repository is public: traffic access is
   authenticated. The token only reads statistics.
2. Allow this workflow's built-in `GITHUB_TOKEN` to use **Contents: write** for
   the generated-data branch. No pull-request or Actions write permissions are
   required, and the protections on `main` remain in place.
3. After the workflow is on `main`, run **Actions → Neutron · GitHub traffic badges**
   once, or wait for the next daily run. Check that the update and publication
   steps succeeded and that both SVG dates advanced.

The workflow reads from `GITHUB_REPOSITORY`, validates both API responses, and
publishes only `assets/readme/metrics/views-14d.svg` and
`assets/readme/metrics/clones-14d.svg` on `readme-metrics`. The README embeds the
raw SVGs from that branch, so fresh data becomes visible without a daily PR or
manual merge. A small temporary Git repository holds only the generated files;
updates are ordinary fast-forward pushes. Unexpected files on the metrics branch
or a rejected push stop publication instead of overwriting unrelated work. The
workflow never pushes to `main` and does not dispatch client builds for stats.

A missing `NEUTRON_TRAFFIC_TOKEN` now **fails visibly** rather than reporting a
successful run with all update steps skipped. Missing credentials, API errors and
failed publication leave the last successfully published pair intact. Existing
SVGs can seed the data branch with their original dates; seeding does not claim a
new traffic measurement. GitHub's image proxy and browser caches may briefly show
an older image after publication; compare its visible date with the raw branch
file when checking freshness.

These aggregate cards are public because this repository and its README are
public. Raw traffic responses and credentials are never committed. The source
branch copies remain historical fallbacks; the README's live cards use
`readme-metrics`. The former `codex/readme-traffic` PR is no longer used for daily
updates.

The SVGs can also be generated locally with `update_traffic_badges.py` and the same
dedicated token in the process environment. Never commit tokens or copy a broad
personal credential into a repository secret.

## Release files

See [Release bundles](RELEASES.md) for the JAR/JSON contract, generated changelog,
checksums and publication steps. README download links point to `Neutron-Client`.
