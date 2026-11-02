# Project direction

Neutron is a Minecraft 1.8.9 / Java 8 client. The priorities are Intave/Polar bypass
research, consistent visuals and client systems that remain maintainable as server
checks change. Other anti-cheats can be added through the same test and review process.

## Compatibility that can be checked

A compatibility claim applies to a recorded environment: client commit, anti-cheat
version and configuration, server/game version, enabled modes and test date.
Classify each tested combination as **verified**, **failing** or **untested**, with
evidence and remaining limits. Unknown server versions stay unknown.

Intave and Polar are research targets, not blanket compatibility promises. No
future-proof or universal bypass is guaranteed. New server checks can invalidate
old results without a client update; repeated regression tests make that visible.

## Reduce the cost of future changes

- Keep shared movement, rotation, attack, packet and world-lifecycle contracts
  separate from anti-cheat-specific profiles. Avoid duplicated core fixes per mode.
- Preserve a known-good baseline and frozen regression cases. Test target switches,
  correction packets, world transitions, cancellation and module combinations.
- Treat anti-cheat updates as a test-matrix change first: reproduce the difference,
  isolate its cause, then change the smallest relevant behavior.
- Keep changes optional until verified. Record mode migrations and rollback paths
  so configuration updates do not silently change existing setups.

## Visual quality

Use one shared theme/material/font vocabulary across menus, ClickGUIs, HUDs,
accounts, settings and notifications. New layouts must reuse those foundations.

A visual change needs rendered evidence at multiple sizes/scales, readable long
labels, correct hover/focus/disabled states, reliable keyboard/mouse input and
consistent clipping. Check reduced motion, animation interruption, frame time,
world unload and GPU resource cleanup. Screenshots alone do not establish input
correctness or performance.

## Release milestones

1. Reproducible Java 8 build, safe bundle assembly and green repository checks.
2. Clean official-launcher installation, sign-in, native loading and game lifecycle.
3. Recorded Intave/Polar baselines and visual/input acceptance for the release scope.
4. Additional anti-cheat/platform coverage backed by the same evidence.

Publish what was tested, what failed and what remains untested. Keep unfinished
milestones in draft/pre-release status. Imported dependencies and assets need
documented provenance and distribution notices before release.
