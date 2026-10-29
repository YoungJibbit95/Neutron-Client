# VIS-QA01 Gradient-Vertrag

Basis VIS-02 `534b8c4db8d3a6633eea6e663a74be17d18aadda`. QA01 repariert ausschließlich reproduzierte Consumerfehler; die VIS-02-Formeln und Zeitintegration bleiben bytegleich.

## Globale Quelle

ThemeCatalog liefert beim Draw die aktuelle immutable Palette in unveränderter Reihenfolge Primary → Secondary. UiEffects bleibt alleiniger Phasenowner. `beginFrame` integriert reale monotone Differenzen mit High-water und maximal 100 ms pro Reading. Mehrere UI-/HUD-Eintritte bei identischem Timestamp addieren nichts. Ressourcenrelease, Themewechsel und Resize setzen die Phase nicht zurück.

`Animated` verwendet ClientSettings-Speed, `Theme` weiterhin den Speed und Animationsstatus des Themes. Statische Presets können ausdrücklich mit Animated bewegt werden. Sync ON teilt Direction/Method/Speed/Phase zwischen TEXT/BORDER/GLOW; GLOBAL-Fill folgt BORDER. Sync OFF behält die vorhandenen Channelwerte. Shared Gradients OFF transportiert Solid. Ambient OFF, Reduced ON oder Animations OFF frieren den globalen Takt ein; kurze Interaktionen behalten die VIS-01-Regeln.

CPU-GUI-Koordinaten und GLSL-Fragmentkoordinaten bezeichnen denselben Viewport-Samplepunkt. Die Position ist kein lokaler Stringindex. Flow/Ping-Pong/Pulse, Codes 0–4 und GLSL 1.20 bleiben unverändert. Konkrete Pixelabweichungen und effektive Scales stehen im Evidenzbericht.

## Reparable Ausnahmen

Hotbar: Die Selected-Slot-Dekoration sampelt nun UiEffects.GLOW an ihrer bestehenden Position. Der eigene 2000-ms-Wallclock-Sinus und sein ausschließlich dekorativ genutzter Helfer entfallen. Der konfigurierte Bloomshader war bereits GLOBAL; repariert wird insbesondere der echte unkonfigurierte statische Fallback. Surface, Items, neun Slotpositionen, Renderreihenfolge, Eingaben und Event2D bleiben unverändert. Änderungen in GuiIngame sind ausschließlich dekorative Farbwerte.

iOS: Die Header-Glyphe `l` verwendet TextEffects.direct und damit den tatsächlichen per-Pixel-TEXT-Sampler statt des direkt zugewiesenen 3000-ms-Sinus. Ihre bestehende 95%-Deckkraft bleibt. Das Outline-Farbargument stammt aus dem bestehenden projizierten BORDER-Sampler. Root-Surface, Sidebaricons, Komponenten, Model-/Projection-Transform und inverse Mauskoordinaten bleiben erhalten. Kein neuer beginFrame-Aufruf: EntityRenderer besitzt bereits den Welt-unabhängigen Screen-Eintritt.

ArmorHUD: Dekorative Surface/Border/Glow bleiben global beziehungsweise explizit lokal. Die Durability-Bar bildet ausschließlich das verbleibende Verhältnis ab: 0% ERROR, 50% WARNING, 100% SUCCESS, linear dazwischen. Semantic Color Priority ON verwendet die vorhandenen semantischen Farben `FF667F`, `FFC969`, `65E0AD`. OFF erlaubt die vorhandene begrenzte 35%-Themetönung über UiEffects.semanticColor; kritisches ERROR bleibt rot. Kein Phase-/Method-/Direction-Sampling dieser Informationsgröße. Bestehende Theme-Stop-Alpha bleibt erhalten, Werte werden auf [0,1] begrenzt; nicht endliche Werte werden als 0 behandelt. Labels, Itemdamage, Barbreite und Layout bleiben unverändert.

## Bewusste Local-/Informationsrollen

ArrayList Global nutzt bereits TextEffects/Scene- und Surfacekanäle. Seine Wallclock-Layoutdifferenz und Exitanimation werden nicht angefasst; der übergebene gradientOffset ist im Label unbenutzt und keine aktive zweite GLOBAL-Farbuhr. Explizites Local/Legacy bleibt kompatibel.

Watermark Theme-Stil und Panel-Titel verwenden bereits `useShared`; lokale Theme-isAnimated-/Speed-Argumente bestimmen dort bei konfiguriertem Owner nicht den globalen Shader. Watermark Horizontal/Vertical/Solid sind bewusst lokale Textstile mit erhaltenen Animated-Text-/Text-Speed-Werten. Wortmarke, Status, Glow und Surface-Overrides bleiben erhalten. Die statische lokale Status-Unterstreichung ist separat ausgewiesen, kein neuer globaler Consumervertrag.

Custom-Textfarben für sachliche Labels, Haltbarkeitszahlen, iOS-Traffic-Lights, Notification-Statusformen/-Farben und feste semantische Weltfarben werden nicht durch Dekoration ersetzt. TargetHUD/SessionInfo, ThemeEditor/Settings-Vorschau und andere Oberflächen sind nur lesend inventarisiert; QA01 erteilt keine universelle Produktsynchronitätsfreigabe.

## Fallback und Abnahmegrenze

Vorhandene NoShader-/NoFBO-Surface-/Textpfade müssen lesbar bleiben, versprechen aber keine identische Shaderanimation. Der iOS-Menübackdrop braucht weiterhin Shader und ist kein vollständiger NoShader-Renderer. Pbuffer beweist reale GPU-Draws und Readbacks, keine gestartete Spielwelt, kein natives Display, kein Shaderpack und keine 144-FPS-Abnahme. Dafür bleibt der Status NOT_RUN.

Keine neuen Settings, IDs, Optionsreihenfolgen, Persistenzschichten, Themes oder Animationfamilien. UiEffects, ClientSettings, Theme-/Configowner, Shader/Assets, VIS-01-Spring und Notifications sind produktiv unverändert. STOPP nach QA01.
