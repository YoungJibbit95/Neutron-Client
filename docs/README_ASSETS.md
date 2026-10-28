# Neutron README · branding and traffic-badge notes

## Brand direction

The SVG and GIF assets were authored specifically for this README using the source-level default `PinkLemonade` theme (`#FF00EA` / `#FF6200`) and the client UI's dark-glass presentation. The hero's miniature Panel interface is **illustrative**, not an actual game capture. The GIF is an animated **branding concept**, not a demonstrated game session, anti-cheat test or measured performance result. Release screenshots should replace or supplement these graphics once native-window QA is complete.

| Asset | Purpose |
| :-- | :-- |
| `assets/readme/neutron-hero.svg` | Repository hero and brand identity |
| `assets/readme/install-flow.svg` | At-a-glance launcher installation steps |
| `assets/readme/neutron-experience.svg` | Four feature pillars |
| `assets/readme/neutron-motion.gif` | Lightweight animated brand visualization |
| `assets/readme/neutron-footer.svg` | Closing brand mark |
| `assets/readme/metrics/{views,clones}-14d.svg` | GitHub traffic in a **rolling 14-day window** |

SVGs use only local shapes, text and gradients; no external scripts, linked imagery or redistributed font files. The GIF is original and low-size; all image references in the README are repository-relative for display in a private repository.

## Enable real GitHub repository views and clones

GitHub's repository traffic API provides the last **14 days** of views/clones (and unique visitors/cloners), not the complete lifetime number. These endpoints require **Administration: read** repository permissions, including for fine-grained tokens. [GitHub traffic API documentation](https://docs.github.com/en/rest/metrics/traffic).

1. Create a **fine-grained personal access token** restricted to **this repository only**, granting **Administration: read**. Avoid additional permissions for the traffic-reading token. Store it as a GitHub Actions repository secret named `NEUTRON_TRAFFIC_TOKEN` (Settings → Secrets and variables → Actions). Never paste the token into README or commit it.
2. Ensure the repository's Actions settings permit workflows to write **contents**, and that the main branch allows commits from the workflow identity (or replace direct push with a reviewed PR if branch protection requires it).
3. Merge `.github/workflows/traffic-metrics.yml`, `.github/scripts/update_traffic_badges.py` and the two placeholder SVGs into the default branch.
4. Run **Actions → Neutron · GitHub traffic badges → Run workflow**. If configured, it fetches the actual GitHub API totals and commits new image contents, thereafter refreshing daily. If no token is configured, the workflow leaves placeholders unchanged.
5. Metrics are private to users with repository access while the repository is private. Do **not** replace them with public README-hit counters labeled “GitHub views”; those measure a different event.

If a token expires or API permissions change, the workflow fails without replacing the previous valid numbers. They may become stale; check the update date and workflow status. Avoid publishing private analytics if that is not intended.

## Release launch contract

The README's two-file instructions become applicable **only after** a verified, matching `Neutron-1.8.9.jar` and `Neutron-1.8.9.json` are attached to a versioned Release. Before publishing, check the JSON `id`, launcher main class/arguments, libraries, assets, game directory, resource handling, and the actual JAR class/resource layout. `mvn package` alone does not prove the release artifact is launcher-ready.

## Publishing

Keep `README.md` in the repository root and preserve the paths above. The `design/`, `src/` and `LICENSE` links in the README refer to existing repository content and are **not** duplicated in this README-only package. Replace the placeholder traffic badges only with actual API data; change the release-status badge only after testing the full installation flow.
