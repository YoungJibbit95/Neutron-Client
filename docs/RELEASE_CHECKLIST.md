# Neutron Client · first public-release gate

This checklist is a **proposal**, not evidence of completed validation. It translates the current repository reports into concrete publication criteria.

## P0 — Before claiming “download and play”

- [ ] Freeze one release commit and decide the public product/version names. Source says **Neutron Dev 2.2**; Maven still emits the legacy name `Pulsar b1.0` and repository is `Pulsar-Client`.
- [ ] Implement and test a real launcher release packaging task that produces a matching **`Neutron-1.8.9.jar` + `Neutron-1.8.9.json`**, including required Minecraft/client classes, resources, libraries, assets and native-loading contract.
- [ ] Validate the JSON `id`, main class, arguments, game assets, Java 8 requirement and launcher version discovery. A `jar-with-dependencies` or shaded JAR is not sufficient evidence.
- [ ] Install the pair on a **clean machine / clean Minecraft directory** and start from the **official Minecraft Launcher**. Test at least Windows, and document unsupported operating systems explicitly.
- [ ] Verify native-window rendering: Panel/iOS, main menu, HUD widgets, blur/GlowESP, resolution scaling, fullscreen/windowed changes and shutdown resource cleanup.
- [ ] Verify safe session and world handling: offline, disconnect/reconnect, multiple world loads, settings persistence, no personal data in releases.
- [ ] Resolve or clearly disable known unfinished modules (especially **AntiBot** and **Reach**); test representative combinations of Aura, Scaffold, Speed, Velocity, WTap/AutoClicker, InvManager and packet delay.
- [ ] Review dependency provenance, all bundled notices and Minecraft distribution/license compliance; verify GPL-3.0 obligations and third-party asset rights.
- [ ] Perform reproducible clean dependency resolution and publish a SHA-256 checksum for every release asset.

## P1 — Strong project page

- [x] Accurate introduction, full module catalog, UI/tech-stack summary and beginner-friendly future JAR+JSON installation guide in this README package.
- [x] Original dark-glass / PinkLemonade SVG + GIF branding assets.
- [x] Optional GitHub API views/clones badges with honest **NOT ENABLED** defaults.
- [ ] Replace illustrated panels with real, approved in-game screenshots after testing.
- [ ] Add a verified Java 8 CI workflow and only then display a green CI status badge.
- [ ] Add a release notes template, downloadable asset checksums, GitHub release checklist and a support channel.
- [ ] Decide whether to rename the repository, artifact and package-level user-facing identifiers for Neutron without breaking config migration.
- [ ] Establish support scope, known limitations, privacy/security notices and permitted-use documentation.

## P2 — Website / long-term release program

- [ ] Responsive landing page with hero, feature panels, real screenshots, installation FAQ, changelog and linked GitHub Releases.
- [ ] Versioned release channel definitions (alpha/beta/stable) and publish gates.
- [ ] Regression checks across supported GPUs, shader packs, window sizes, Java 8 distributions and account flows.
- [ ] Public roadmap based on tested, prioritized module and UI findings rather than promises of undetectability.

**Status as of this documentation draft:** Good source-level product foundations, but standalone launcher certification is not established. Do not publish a “stable download” badge before P0 is met.
