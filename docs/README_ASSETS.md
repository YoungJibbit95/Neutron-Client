# README assets

The README keeps the dark PinkLemonade SVGs (`#FF00EA` / `#FF6200`). Graphics are
brand illustrations, not game captures. All displayed assets use repository-relative
paths, which also work for viewers of the private repository.

The header, feature panel and footer remain visible. The installation illustration
and animated branding stay in `assets/readme/` for reuse; the shorter README omits
them to keep downloads and setup easy to find.

## Traffic badges

Views and clones sit directly below the header and link to the repository traffic
page. Each SVG shows the total, unique count and last update date. The cards have
explicit dimensions and separate text rows so large totals do not overlap.

The numbers cover GitHub's **rolling 14-day window**; they are not lifetime counts
or README pixel hits. [GitHub traffic API](https://docs.github.com/en/rest/metrics/traffic).

To refresh them daily:

1. Create a fine-grained token restricted to `YoungJibbit95/Neutron-Client` with
   **Administration: read**. Save it as the Actions secret `NEUTRON_TRAFFIC_TOKEN`.
2. In **Settings → Actions → General → Workflow permissions**, allow Actions to
   create pull requests. The workflow requests contents and pull-request write
   permissions for its built-in token; the dedicated traffic token only reads stats.
3. After the workflow is on `main`, run **Actions → Neutron · GitHub traffic badges**.

The workflow reads from `GITHUB_REPOSITORY`, updates both SVGs and opens or updates
the `codex/readme-traffic` PR. It does not push directly to `main` or force-push.
Merge the badge PR when desired. Missing secrets produce a visible warning; API
errors fail the run and preserve the last valid files. Check the printed UTC date
for freshness. Traffic remains visible only to people who can view this repository.

The current SVGs can also be refreshed locally with the same script and a dedicated
token in the process environment. Never commit tokens or copy a broad personal
credential into a repository secret.

## Release files

See [Release bundles](RELEASES.md) for the JAR/JSON contract, generated changelog,
checksums and publication steps. README download links point to `Neutron-Client`.
