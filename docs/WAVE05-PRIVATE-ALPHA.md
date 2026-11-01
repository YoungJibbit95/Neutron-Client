# Wave 05 private alpha

Use the existing checkout `F:\Puslar Backup - Copy` on local `main`. This local
integration includes unmerged draft work; it is not GitHub `main` or a release.
The delivery receipt records the exact tested commit and open gates.

## Start in IntelliJ

1. Open the existing project in that directory. Use Temurin 8u482 at
   `C:\Users\Administrator\.jdks\temurin-1.8.0_482` for the project and Maven runner.
2. Finish any Maven test process before starting an IntelliJ build.
3. Select **Neutron Client (Java 8)** and Run. The checked-in run configuration
   starts `Start`, uses the existing Windows natives and `test_run`, and supplies
   the offline name `NeutronTest` at 1280x720. **Launch Pulsar** is also available.
4. Choose Singleplayer and the explicitly authorized local world **New Worl**.
   Keep the other worlds intact. No login or server connection is part of this run.
5. Right Shift (LWJGL key 54) opens ClickGUI. The ClickGUI module offers Panel,
   iOS and Dropdown. Escape closes the screen. Aura and AimAssist independently
   offer **Speed Curve: Off / Bezier**; Off is the default. See
   [the speed contract](BEZIER-ROTATION-SPEED.md) and [Dropdown controls](DROPDOWN-GUI.md).

Back up the existing options and `pulsar/configs` files before changing settings.
Restore those exact files only after the client has exited, so its shutdown save
cannot overwrite the restored settings. Do not copy the full client or worlds.

## Local playtest

Use a clear, disposable area in New Worl. Record the exact config, selected slot,
camera/packet owner, target and before/after block positions. Start with all combat
and movement modules off, then enable only the module under test.

- Verify all three GUI modes, Right Shift, Escape, scrolling, dropdown reveal and
  slider cancellation on close. Check the diagnostic overlay together with the
  default Watermark, SessionInfo and ArrayList.
- For Scaffold, test an actual placement with rotations Off at a valid block face
  in both PRE and POST timing. Then test sustained W and W+D bridging, slot restore,
  disable/re-enable and world lifecycle. One successful block is not sustained bridging.
- For Aura/AimAssist, use local mobs with the matching target setting. Check loss,
  death, range/visibility filtering, target switching, manual attack, module priority
  and camera behavior. Compare Speed Curve Off with Bezier without changing other settings.
- F9 toggles CombatDiagnostics and F8 inserts a client-side marker. The trace is
  observational: local accepted attacks do not prove server hits, and S08 packets
  do not identify or prove an anticheat flag.

The diagnostic panel preserves its full history but shows fewer rows in smaller
windows. Its default-HUD regression covers logical heights 224, 320 and 360;
arbitrary dragged or densely populated HUD layouts are not guaranteed to avoid overlap.

## Separate user-only gates

An authorized multiplayer/account session is a separate user decision. It is
needed for real player-team filtering, server hit confirmation, multiplayer
corrections and any server-specific observation. Never infer a server check name
from S08 or claim Intave/Polar compatibility from source or local tests.

Automated ordinary-walking/controller/transport tests, offscreen native Pbuffer
captures and actual gameplay are separate evidence levels. A key tap does not
prove sustained input or a sub-frame lifecycle deadline. Unobserved gates remain
BLOCKED or NOT_RUN in the final receipt. All PRs remain open until an explicit merge
instruction; no direct push to GitHub main is part of this alpha.
