# WAVE07 VIS-C4 — Session statistics and measured diagnostics (C4a)

**Task scope:** VIS-only, isolated draft PR. **Base main:** `756ee105d2716eab06c71db88e1bdeb17ea3b302` after independent TECH #116 merge. No TECH ownership overlap. GitHub connector only, no local Windows checkout/worktree or main writes.

## Design references / actual changes

Checked the WAVE07 four-image revision archive: current HUD and GUI screenshots are real user captures, generated target HUD/GUI pictures are conceptual. Reviewed Approach-2 crops `07_Session_Info`, `17_Session_Info_originalnah`, `18_Statistics`, `08_CPS_Anzeige`, `13_Performance_FPS_Ping`, `09_Flag_Detector`, `20_CPS_Performance_Flag_Detector`. No reference bitmap imported.

| Existing UI | C4a actual 2.0 source changes | Data contract |
| --- | --- | --- |
| `SessionInfo` default Outline | New 250–330 wide / 100 high structured dark-glass Statistics card. Left: heading, player, K/D, elapsed session time. Right: existing real elapsed-time ring. Neutral white/muted hierarchy and hairline theme gradient; no large glow. Legacy designs unchanged. | Only `SessionStatistics.Snapshot` via `SessionInfoLifecycle`; never inferred kills/deaths from scoreboard or unconfirmed events |
| `Diagnostics` widget panels | Opt-in graphite ink plate, restrained theme underline, legible neutral titles/status, 10 radius and suppressed excessive panel glow. Existing real performance/rotation/network/correction plot drawers retained, plus true-missing sample gaps in frame-history plot. | Only existing `RenderTimings`, ping history, rotation snapshots and `CorrectionHistory` data |
| Missing graph samples | `Diagnostics.graphSampleValid` rejects negative/NaN/infinity. Splits OpenGL line strips at gaps instead of displaying zero latency/frame time. Raw telemetry arrays remain unchanged. | Missing means unknown, never zero, never generated sine data |

### Mode and palette compatibility
- The existing `Session Info / Mode` remains `Basic, Outline, Tenacity, Rue (Rise)`, with **Outline as saved default index 1**. A global 2.0 look upgrades the **display only** of the historic Outline default through `SessionInfo.modernFor`, without changing the Value; explicit old selections retain legacy rendering.
- The existing Diagnostics `Display Mode` `Text/Simple` and all four distinct informational widgets, config flags, channel filters, X/Y/scale, chat drag and editor scroll survive. This mode can change **which data view is displayed**, so it must not be blindly coerced to a cosmetic two-option selector.
- The two-visible-`Legacy/Neutron 2.0` migration for saved legacy visual names remains separately pending until the WAVE07 migration matrix and config-by-index tests are complete; C4a keeps all values readable.
- The chosen ThemeCatalog palette is still used for the restrained accent/ring. C2's palette-ownership correction is in independent [Draft PR #115](https://github.com/YoungJibbit95/Neutron-Client/pull/115), not merged or claimed green at initial C4a creation.

### Test matrix and data protection
- `Wave07SessionCardTest`: historic Mode/default index and position, actual SessionStatistics verified chat parsing and time, no synthetic flags/counts, modern selection no Value writes.
- `Wave07DiagnosticsCardTest`: no synthetic samples, raw array unchanged, display mode Text/Simple retained.
- `Wave07SessionDiagnosticsConfigTest`: real ConfigManager roundtrip of SessionInfo mode/positions and Diagnostics display/widget settings.
- Existing SessionStatisticsEventIntegrationTest, SessionInfoLifecycleTest, DiagnosticsTest/ModeTest/GraphMotionTest and Java8 full regression must pass on the exact PR merge tree.
- **C4a deliberately does not claim a new CPS event provider:** the current Render-module inventory does not expose a certified global CPS history. A placeholder `CPS: 12` would be fabricated. Further C4b CPS/Performance widgets require existing real mouse-event sample ownership and separate tests.
- **RUNTIME_PENDING:** Native LWJGL Pbuffer, actual Minecraft window, shader/GL state resource lifetime, ring shader rendering, fullscreen/UI scale 640×360/854×480/1280×720/1920×1080, chat HUD overlap, FPS p95/p99 and exact screenshots. Headless tests do not validate generated ZIEL image pixels.

### Phase boundaries
- C3 [Draft PR #117](https://github.com/YoungJibbit95/Neutron-Client/pull/117) is disjoint and passed its **first** Java8 PR merge test, then advanced to a newer TECH-including base; final head must be verified separately. No C3 paths edited by C4.
- C2 #115 remains active and not green (test fixture invalid `Theme.custom` UUID); owner notified in PR conversation. C4's independence is source-only, not an overall release gate.
- C5 TargetHUD/Armor/Hotbar/Keystrokes and C6 other Render-module consumers are **not** implemented in this C4a PR. No game/network/packet logic, new shaders/fonts, POM or workflow changes.

**No auto-merge, release, force push, direct main change, world/account asset or local copy.**
