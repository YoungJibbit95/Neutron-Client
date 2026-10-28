# Neutron Client · technical source reference & module inventory

**Prepared:** 2026-10-28  · **Repository:** [`YoungJibbit95/Pulsar-Client`](https://github.com/YoungJibbit95/Pulsar-Client)  · **Source:** Java 8 / Minecraft 1.8.9.

This file separates the *observed source surface* from *release validation*. It is an engineering reference accompanying the shorter public README; not a runtime certification, performance benchmark, nor a promise that specific servers accept a module.

## Evidence / freshness

- **Live GitHub:** `README.md`, `pom.xml`, `Pulsar.java`, bootstrap, module registry, categories, core UI and theme classes, and the concrete files for all **42** inventoried module classes were inspected on `main` during preparation.
- **Audit baseline:** module observations and preliminary ratings in this table are from the historical audit on commit `c19312037dc76e7d4d02fa34fe5f28760bbd6e90`, collected on **2026-10-27**. They are not new test results. A GitHub comparison during preparation found that `main` is four commits ahead of that audit baseline, changing authentication, MoveFix, tests and design docs, **not the 42 module class paths**. Do not interpret the baseline rating as a verified current-release grade.
- **Tests:** the integration report dated 2026-10-26 records 387 passing tests on its then-integrated source, including synthetic/offscreen checks. Tests were **not re-run** for this README and do not establish clean-machine launch, native window, shaderpack or live-server operation.

## Runtime architecture

| Surface | Observed implementation and boundaries |
| :-- | :-- |
| Game integration | Modified Minecraft Java **1.8.9** / OptiFine paths; hooks span `net.minecraft.*` and the client `me.youngjibbit.*` tree |
| Core language | **Java 8**; MavenMCP, `pom.xml` Maven compiler source/target `1.8` |
| Rendering | LWJGL **2.9.4-nightly** + OpenGL; shader programs, FBO, blur/bloom, font/render utilities and GL state handling |
| Events | Alpine **3.1.0**, `Pulsar.BUS`, listeners and module lifecycle subscriptions |
| Discovery | Reflections **0.10.2**, `@ModuleInfo`, `ModuleManager` indexes by class, id, name and category |
| Shared services | `ClientRuntime`: `TargetingService`, `RotationManager`, `RaycastService`, `AttackScheduler`, `ScaffoldPlacementService`; packet and input coordination elsewhere |
| Settings / themes | Slider, boolean, mode, color and theme-selection values; `ThemeCatalog`, theme persistence and `Client Settings` |
| Build artifacts | Maven Assembly (`jar-with-dependencies`) + Shade (`Pulsar b1.0` final name), manifest entry `net.minecraft.launchwrapper.Launch`; no verified official-launcher JSON pair |
| Dependencies | JUnit 4, Gson, Netty 4, Log4j 2, JNA / OSHI, OkHttp and legacy Minecraft/LWJGL libraries; clean bootstrap remains a release task |

### Branding consistency

The runtime `Pulsar.java` reports **`Neutron` / `Dev 2.2`**, while the Java entry package, repository identity and some archive paths retain **Pulsar**. `pom.xml` uses `<artifactId>Neutron</artifactId>` but its Shade final name remains **`Pulsar b1.0`**. User-visible release naming, packaging outputs and migration require coordination before any published stable release.

## UI, HUD, visual and account surfaces

| Surface | Concrete current-source features | Release/UX review |
| :-- | :-- | :-- |
| Main menu | Animated shader backdrop, responsive panel, singleplayer, multiplayer, account manager, settings, exit, keyboard navigation, motion/blur/glow controls | Native-window scaling, low-resolution layouts, keyboard/mouse focus, shaders and shutdown |
| Panel ClickGUI | Category sidebar, search field, theme control, module controls, dragging/resizing, scrolling, animations, gradients | Shared style tokens, input arbitration, focus and all setting types |
| iOS ClickGUI | Alternative category/module interface, scaled/animated panels, particles, common themed surface | UI consistency vs Panel, reduced motion, smaller screens and state restore |
| Global UI settings | `Standard`, `Clear`, `Gradient`, `Blur`, `Liquid Glass`; border source, Gaussian/Kawase blur, glow, opacity, animations, reduced motion and notification presentation | Render cost and effects fallback across GPUs |
| Theme system | 19 built-in themes; runtime theme selection; custom theme storage/preview; current default **PinkLemonade** (`#FF00EA` / `#FF6200`) | Palette consistency, persistence and migrations |
| Client HUD | ArrayList (6 styles, font/filter/scale options), watermark (9 modes), generic HUD guide/snap/bounds controls | User-editable widget interactions differ; verify anchor/screen edges |
| Render HUD | ArmorHUD, TargetHUD, Session Info, nametags, ESP and GlowESP | Native screenshots, depth/GL state, blending, FPS and resource lifetimes |
| Accounts | Offline account library, filters/search, saved profile UI; browser Microsoft OAuth/PKCE with local callback, account selection guards | Own Microsoft client registration and Minecraft Services access; real personal-account tests not established |

## All 42 modules — audit evidence and source paths

The **Historical assessment** is an initial audit label. The **Audit note** is a concise observation from the earlier module register and should be treated as a test hypothesis/known check area, not a claim that the same problem was reproduced today.

| Category | Module | Historical assessment | Audit note / follow-up |
| :-- | :-- | :-- | :-- |
| Combat | [AimAssist](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/combat/AimAssist.java) | Gut | Shared Targeting/Rotation; Settings und Interaktion mit Aura im Live-Client prüfen |
| Combat | [AntiBot](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/combat/AntiBot.java) | Unvollständig | Leerer EventUpdate-Listener, kein Bot-Filter in gemeinsamem Targeting |
| Combat | [Aura](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/combat/Aura.java) | Gut | Shared Targeting, Rotation-Manager, akzeptierter Attack Scheduler; Visibilität, Debug UX und Kampfinteraktion prüfen |
| Combat | [MoreKB](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/combat/MoreKB.java) | Risiko | Sprint-Paketfolgen und Target-Retention; Weltwechsel und Konflikte prüfen |
| Combat | [Velocity](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/combat/Velocity.java) | Risiko | Timer-Eigentum, direkte Weltänderung und unnötiger parallelStream; Modi isoliert durchtesten |
| Exploit | [Backtrack](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/exploit/Backtrack.java) | Gut | Zentrale verzögerte Packet-Queue, Kontexthygiene und Diagnose; Live-Queue-Zustand prüfen |
| Exploit | [LagRange](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/exploit/LagRange.java) | Gut | PacketDelays-Paketvertrag, Aktivierungsfenster und Weltwechsel; Event-Reihenfolge prüfen |
| Exploit | [MinibloxFix](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/exploit/MinibloxFix.java) | Solide | Outbound Packet-Replacement; serverbezogene Gültigkeit und Vertragsprüfung |
| Exploit | [Tickbase](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/exploit/Tickbase.java) | Solide | Budgeted Player-Steps statt Timer-Speed; Reconnect und TPS/Render-Fallprüfung |
| Ghost | [AutoHeal](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/player/AutoHeal.java) | Solide | Hotbar-Heal und vorsichtiger Inventartransfer mit Kontext-/Ack-Gates; Konflikte mit InvManager testen |
| Ghost | [AutoBridger](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/ghost/AutoBridger.java) | Risiko | Setzt Sneak-Key direkt und deaktiviert mit false; Input-Besitzkonflikte |
| Ghost | [AutoClicker](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/ghost/AutoClicker2.java) | Risiko | Noise-CPS ohne gültigen Bound, Block Frequency Randfehler, Overlap mit Aura und manuellen Clicks |
| Ghost | [HitDelayFix](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/ghost/HitDelayFix.java) | Mittel | Vanilla clickMouse Hook vorhanden; Konflikte mit Scheduler/AutoClicker testen |
| Ghost | [JumpReset](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/ghost/JumpReset.java) | Mittel | Velocity-getriggerter Jump, Wechselwirkung mit Velocity/Movement prüfen |
| Ghost | [Reach](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/ghost/Reach.java) | Unvollständig | hitReach/blockReach werden gesetzt, Vanilla EntityRenderer/PlayerControllerPicking konsumieren sie nicht |
| Ghost | [WTap (source: WTap2)](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/ghost/WTap2.java) | Risiko | Manipuliert KeyBindings und Sprint, wall-clock Zeit; Input-Koordinierung notwendig |
| Movement | [Speed](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/movement/Speed.java) | Risiko | Direkte Timer- und KeyBinding-Writes; Base-Speed Formel gewährt Bonus ohne Potion |
| Movement | [Sprint](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/movement/Sprint.java) | Solide | Auto-Sprint einfacher EventUpdate-Listener; Guards und Koexistenz prüfen |
| Player | [AutoTool](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/player/AutoTool.java) | Solide | Selektions-Fix und World-Reset vorhanden; InventoryManager-/Scaffold-Konflikte prüfen |
| Player | [FastPlace](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/player/FastPlace.java) | Einfach | Kleiner Delay-Patch; Settings und Ownership-Reset prüfen |
| Player | [InvManager](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/player/InventoryManager.java) | Risiko | 534 LOC, viele verzweigte Inventory-Writes, teils leere catches; atomare Operationsplanung prüfen |
| Player | [NoSlow](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/player/NoSlow.java) | Mittel | 6 Modi, Slowdown-Event Hook vorhanden; direkte Silent-Packet-Flows können mit Aura/Sprint kollidieren |
| Player | [SlotHandler](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/player/SlotHandler.java) | Mittel | Statischer Silent-Slot; Event-Methode von Alpine unterstützter Subscribe-Mechanismus; Reconnect/Reset prüfen |
| Player | [Stealer](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/player/Stealer.java) | Risiko | Container-Klicks, Slot-Randomisierung und starre Empty-Size; Session-/Window-Checks erweitern |
| World | [BridgeAssist](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/world/BridgeAssist.java) | Mittel | Richtet Spielerrotation direkt aus statt RotationManager; Winkel-Wrap und PRE/POST prüfen |
| World | [Scaffold](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/world/Scaffold.java) | Gut | PlacementService, planbasierte Rotation und Diagnostics; TimerControl nur lokal statt global |
| Client | [ArrayListHUD](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/client/ArrayListHUDModule.java) | Gut | Viele Stile, Filter, Fonts, Animationen und ein globaler Effektpfad; einheitliche Maße/Perf prüfen |
| Client | [Client Settings](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/client/ClientSettings.java) | Gut | 33+ UI-Einstellungen inkl. Blur/Glass/Reduced Motion/Notifications; Konzept entwirren |
| Client | [HUD](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/client/HUD.java) | Mittel | HUD Guide und Snap/Bounds vorhanden; einzelne Widgets noch nicht konsistent editierbar |
| Client | [Theme](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/client/ThemeModule.java) | Gut | ThemeCatalog/Runtime und Auswahl vorhanden; Persistenz und Palette testen |
| Client | [Watermark](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/client/WatermarkHUDModule.java) | Gut | Mehrere Themes, Font/Content-Optionen und UI-Effects; Editor/Anchors prüfen |
| Client | [ClickGui](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/ClickGUI.java) | Mittel | Panel/iOS Umschalter funktionsfähig, zwei UI-Implementierungen verursachen Divergenz |
| Render | [Animations](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/Animations.java) | Solide | Settings werden von Vanilla ItemRenderer konsumiert; Bewegungsprofile visuell testen |
| Render | [ArmorHUD](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/ArmorHUD.java) | Solide | Position, Skalierung, Layout, Haltbarkeit, Drag, GL State; visuelle Konsistenz prüfen |
| Render | [2D ESP](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/ESP.java) | Mittel | 3D Capture und 2D Overlay implementiert; häufige Matrix-Reads und Ausrichtungsfälle |
| Render | [Fullbright](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/FullBright.java) | Einfach | Schreibt Gamma in EventUpdate, restauriert altes Gamma; externe Setting-Änderung überschreibbar |
| Render | [GlowESP](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/GlowESP.java) | Mittel | Mehrpass-FBO Glow, Besitzer-Cleanup, TODO gradient; State/Perf und Qualität messen |
| Render | [Nametags](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/Nametags.java) | Gut | Theme-integrierte Panels und Vanilla-Name-Guard; Clipping/Tiefenreihenfolge prüfen |
| Render | [NoFovChange](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/NoFovChange.java) | Einfach | Vanilla dynamischer FOV Toggle; State-Restore gegenüber Menüeinstellungen prüfen |
| Render | [Session Info](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/SessionInfo.java) | Gut | Mehrere Designs, SessionStats/Lifecycle, Blur/Glow; RenderBatch und Layout prüfen |
| Render | [TargetHUD](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/TargetHUD.java) | Mittel | Mehrere Designs, Gesundheit-Interpolation, aber fixe Position und im Chat ausgeblendet |
| Render | [ViewBobbing](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/src/main/java/me/youngjibbit/module/impl/render/ViewBobbing.java) | Einfach | Vanilla ViewBobbing Flag, alte Einstellung bei Disable restauriert |

## Most important cross-module contracts to certify

1. **Combat + input:** Aura/AimAssist share targets, rotations and attack scheduling; movement correction must consume confirmed local rotation ownership rather than a pending request. AutoClicker, WTap, Speed, Velocity and Scaffold can compete over keys, timer, strafe or sprint state. Establish explicit owner priority, release and scene-change reset tests.
2. **Placement + inventory:** Scaffold/BridgeAssist/AutoBridger/AutoTool/SlotHandler/InvManager/Stealer/AutoHeal touch overlapping inventory or placement contracts. Tests should exercise selected-slot restoration, inventory acknowledgements, aborts and transitions, not only single-module happy paths.
3. **Network + world:** BackTrack, LagRange, Tickbase and any delayed traffic must clear queues on connection/world transitions and distinguish local packet acceptance from server acknowledgment. Never market named modes as anti-cheat bypass guarantees.
4. **Rendering:** UI blur, bloom, GlowESP, 2D ESP, nametags and HUDs share GPU state/targets. Cover framebuffer lifecycle, resizing, fullscreen, shader packs and older hardware with real frames and automated regression where practical.
5. **Release:** The official-launcher `JAR + JSON` pair needs to be **produced and started from a fresh installation**, with assets, libraries, native paths and licensing verified. Source build success is a weaker condition.

## Sources

- [Live source root](https://github.com/YoungJibbit95/Pulsar-Client/tree/main/src/main/java/me/youngjibbit)
- [Maven build file](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/pom.xml)
- [Unified integration report](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/design/integration-final.md)
- [MoveFix behavior verification](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/design/movefix-verification.md)
- [Microsoft OAuth setup](https://github.com/YoungJibbit95/Pulsar-Client/blob/main/design/microsoft-browser-login.md)
- Historical project attachment: `03_Modulregister.md`, `audit_01/Pulsar_Revived_Audit_01_Daten.json`.

**Limit:** Source inspection and audit documents cannot substitute for launching the client with a real display, real user interaction, resource packs, official launcher and supported test hardware.
