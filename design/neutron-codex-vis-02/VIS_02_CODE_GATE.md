# VIS-02 Code-Gate

Stand 28.10.2026, vor Produktionsänderung. Freigabe ausschließlich Masterprompt VIS-02; historische Roadmap erteilt keinen weiteren Auftrag. Keine Subagents.

## Basis und Quellen

Sauberer lokaler Branch codex/neutron-vis-01: `7f79895f4033ba7e2e58af95bec2a3775d082ab0`; `e3492be7a0a415491e87fda23b2cb7f282fa2a4c` ist Vorfahr (Git Exit 0). Neuer Branch codex/neutron-vis-02, F:/neutron-vis-02. Quellen extern F:/neutron-vis-02-sources; START zuerst, alle 16 SHA-256-/Längeneinträge geprüft. Receipts extern F:/neutron-vis-02-results. 32 fremde Worktrees read-only erfasst. Kein Merge mit CMB/SCF. Keine AGENTS.md im Basisbaum gefunden.

## Tatsächlicher Callgraph / Ownership

`GuiIngame.renderGameOverlay` ruft beginFrame, `EntityRenderer` vor Screen.drawScreen ebenfalls; MainMenu.drawScreen und SettingsMenu.drawScreen rufen erneut. Es gibt keinen gemeinsamen Frame-Token innerhalb der Allowlist. Deshalb reale monotone Zeitdifferenzen statt fester Frameschritte; gleiches Timestamp ist No-op. Keine Minecraft-/Rendereränderung. Zeitdifferenz maximal 100 ms pro Reading, rückläufiges Reading verändert den High-water-Timestamp nicht. Lange Pause wird abgeschnitten, nicht nachgeholt. UiEffects.time bleibt Ambient-Zeit. motion()/response() bleiben interaktive Legacy-Policy (Animations && !Reduced), Ambient OFF beeinflusst diese nicht. MainMenu.advanceMotion verwendet Options.motion/reduced und unveränderte MenuMotion/MenuSpring mit 120-ms-Gerade. SceneContext besitzt keine Uhr.

`UiEffects.gradient -> Gradient.position` ist CPU-Vertrag; `gradientColor` interpoliert Theme-Stopps an skalierten Viewportkoordinaten. `upload` sendet `uiText/uiBorder/uiGlow = (signed axis, mode, phase)` und uiResolution in physischen Pixeln. ShaderUtil ersetzt den vorhandenen Marker UI_GRADIENT vor UI_SURFACE und UI_GLASS. Keine Include-/Compileränderung erforderlich.

Include-Consumer: Ressourcen textDirect.frag, textEffect.frag, arraylistSurface.frag; eingebettete ShaderUtil-Quellen roundRectOutline, kawaseUpBloom, roundedRectGradient, roundedRect. uiSurface.glsl sampelt Border für Fill/Glass, uiBorderPalette respektiert Accent. Alle erhalten neue Modi über denselben Include. textEffect lokale Overrides bleiben in ihrem bisherigen expliziten lokalen Shaderpfad.

MainMenu.drawActions -> TextEffects.direct -> upload -> textDirect: echter GUI-Titel. MainMenu.render/hit nutzen actionBounds; Gradient wirkt nur auf Farbe, verändert keine Bounds, Capture oder Aktionen. ArrayListHUDModule.renderArrayList -> ArrayListSurface.render -> upload(policy.gradient) -> arraylistSurface: GLOBAL-HUD/Border/Fill. Die dortige gradientOffset ist im Labelpfad unbenutzt; sie wird nicht umgebaut. LocalSurface besitzt bestehende Legacy-Speedintegration aus UiEffects.time, keine Wallclock; lokale Paletten und Overrides bleiben. TextEffects lokale Gradienten sind ausdrücklich Advanced/Legacy und bleiben kompatibel.

NotificationManager.drawCards -> icon: expliziter Typ bestimmt Triangle/Exclamation, Circle/Cross, Circle/Check, Circle/i. Genau hier wird der reine UiEffects.SemanticRole-Resolver benutzt. ERROR bleibt immer bisheriges Rot. Andere Rollen behalten bei Priorität ON ihre bisherigen Farben; OFF darf dekorative Theme-Anteile enthalten. Form, Titel, Body und Information werden nicht ersetzt. Timeline nutzt weiter echte monotone Lifetime/Inbox/Queue/Coalesce; Ambient OFF ändert motion() nicht. NotificationType-Reihenfolge bleibt INFO,WARNING,ERROR,ENABLED,DISABLED,SUCCESS. ENABLED=Success, DISABLED=Info.

Theme/ThemeCatalog vollständig als read-only Vertrag geprüft: zwei unvertauschbare Stopps, Custom-Textfarbe/Background/Accent, unveränderte Defaultauswahl PinkLemonade; kein Themewechsel oder Cyan-Migration. Lokale Theme-Speed wird weiterhin aus kanonischem elapsed abgeleitet, Global-Channel-Speeds integriert. Reload-/FBO-release setzen weder elapsed noch phases zurück.

## Mathematik vor Code

Koordinate t = x/max(1,w) oder y/max(1,h), bei negativer Achse 1-t. C(t)=clamp(t,0,1), p modulo 1, q(p)=0.5-0.5*cos(2*pi*p).

| Mode | Vertrag |
|---|---|
| 0 | Solid: 0, unverändert |
| 1 | Static: C(t), unverändert |
| 2 | Flow: 0.5-0.5*cos(2*pi*(t-p)), exakt bisheriger Cosinus |
| 3 | Ping-Pong: 0.5-0.5*cos(2*pi*(t-q(p))); glatte Vor-/Rückverschiebung q in [0,1], Geschwindigkeit null an beiden Umkehrpunkten |
| 4 | Pulse: 0.5+(C(t)-0.5)*cos(2*pi*p); gemeinsamer Farbgewichtstakt, Werte [0,1], Alpha weiter aus unveränderten Stopps |

Methodenwahl nur für tatsächlich dynamischen Modus. Neue Codes erweitern den vorhandenen vec3-Transport; 0/1/2 behalten Bedeutung und Uniformnamen. GLSL 1.20 unterstützt cos/clamp/Verzweigungen; echte Kompilierung/Readback ist Pflicht, kein Mock-Pass. Synchronize ON verwendet Index 0 für alle Kanäle; OFF behält drei vorhandene Phasen/Direction/Speed-Regeln an einer Uhr. Keine neue Clock/Config-/Themeklasse.

## Settings: 1:1 Altvergleich

Unverändert: direction-Werte `Theme,Vertical,Horizontal,Up,Left`; motion-Werte `Theme,Static,Animated,Solid`; direction-ID gradient_direction/text_direction/border_direction/glow_direction; gradient_motion/text_motion/border_motion/glow_motion; gradient_speed/text_speed/border_speed/glow_speed; synchronize_text_border_glow; shared_gradients. Theme-Motion nutzt Theme-Speed, Animated expliziten Channel-Speed. Solid/Static bleiben unabhängig neuer Methode. Local Effects Source `Global,Local,Clear,Gradient,Blur,Liquid Glass`, Local Rectangle Substance `Legacy,Clear,Gradient,Blur,Liquid Glass` bleiben unverändert.

VIS-01 `ui_motion_intensity`: Calm,Balanced,Expressive, Index 1 Balanced; ui_animations=true, reduced_motion=false. Alle bisherigen Values, Defaults, Visibility-Predicates und Combobox-Indizes bleiben erhalten. Vollständiger maschineller Altvergleich wird als neuer Configtest mit eingefrorenem Basisinventar ergänzt.

Genau drei neue Values im vorhandenen Modul und Gruppenmodell: `Gradient Method` / gradient_method / Flow,Ping-Pong,Pulse / Index 0 Flow; `Ambient Motion` / ambient_motion / true; `Semantic Color Priority` / semantic_color_priority / true. Vorhandener ConfigManager lädt fehlende Values über Defaults; keine Migration oder Coreänderung. Tests nutzen echten Codec und Legacy-HUD-Import.

## Test-/Native-Gate und erlaubter Bedarf

Vor Code: UiEffectsTest, ThemeUiConsumerTest, ClientSettingsConfigTest (tatsächlich im Paket config), MainMenuMotionTest, MainMenuInteractionTest, ClientSettingsMotionTest, NotificationTimelineTest. Nach Baseline neue Tests, zunächst Compile-RED für neue API/Values; echte Failure-RED für Pause/Backwards und später gegebenenfalls Adapterbeleg. Bestehende Uhrtests springen 1 bzw. 3 Sekunden pro beginFrame: bei 100-ms-Pausenschutz ist ein ausschließlich zeitlicher Fixtureadapter nötig. Erst RED protokollieren, dann normale 100-ms-Readings an dieselben Zielzeiten, alle bisherigen Assertions und Erwartungswerte unverändert. Keine anderen bestehenden Tests ändern.

Native Entry Points verifiziert: gui.MainMenuVisualCheck/MainMenuCheck, clickgui.ClickGuiRenderCheck/ArrayListRenderCheck, rendercheck.PbufferTestContext. Neuer ausführbarer Vis02RenderCheck im erlaubten utils/render-Testpaket nutzt echten Pbuffer, Fonts, GLSL und FBO-Readback. CPU/GLSL-Rasterproben für 0..4 und vier Achsen, drei reale Consumerphasen für Methoden, Ambient/Reduced/Animations Freeze, echte Notification-Symbole ON/OFF, explizites shadersSupported=false und FBO-disabled-Fallback. Keine Änderungen an vorhandenen Nativefixtures. Fallback muss lesbar bleiben, nicht identisch dynamisch aussehen. Kein Liveclient-/Shaderpack-/144-FPS-Pass aus Offscreen ableiten.

Produktionsbedarf: ausschließlich UiEffects.java, ClientSettings.java, uiGradient.glsl und minimale NotificationManager.java-Resolveranbindung. MainMenu/TextEffects/ArrayListSurface/ArrayListHUDModule benötigen nach aktuellem Befund keine Änderung. ShaderUtil/Theme/Core/FBO/POM bleiben unberührt. Innerhalb Allowlist lösbar; kein Ownership-STOPP aktuell. Tatsächliche Ausführung und verbleibende Gates folgen in RESULTS.

## Ausgeführter Adapter-RED vor Fixtureänderung

`first-green-and-adapter-red.log`: 29/3/0/0. UiEffectsTest.waveTravelsDownAndWrapsWithoutJump erwartet .25, erhält .025; localAnimatedThemeUsesItsOwnSpeedAndThePausedUiClock erwartet .3, erhält .03. Grund ist ausschließlich der neue 100-ms-Pausenvertrag. Minimaladapter: positive kontinuierliche Zielzeiten dieser Fixtures über advanceTo in 100-ms-Readings treiben. Alle Assertions, Toleranzen und Erwartungswerte bleiben bytegleich. Pausenprüfungen liegen zusätzlich ausdrücklich in Vis02ClockTest (vor Code 3/2/0/0, danach 3/0/0/0). Dritter Fehler betrifft eine neue Pulse-Assertion: bestehendes Theme.interpolateRGB rundet mitunter Alpha 255 zu 254 ab; neue Prüfung akzeptiert genau diese vorhandene Einheitenquantisierung. Theme/Interpolator wird nicht geändert.
