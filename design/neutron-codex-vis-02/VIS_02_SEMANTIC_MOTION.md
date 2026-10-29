# VIS-02 Semantik und Motion

## Drei neue vorhandene ClientSettings-Values

| Name | Persistenz-ID | Werte / Default | Gruppe |
|---|---|---|---|
| Gradient Method | gradient_method | Flow, Ping-Pong, Pulse; Index 0 Flow | Gradients / Motion |
| Ambient Motion | ambient_motion | true | Gradients / Motion |
| Semantic Color Priority | semantic_color_priority | true | Gradients / Motion |

Es gibt weiterhin genau ein Settingsmodul und denselben Configcodec. ClientSettingsVis02Test enthält das vollständige, mit Java 8 vor der Änderung ausgelesene Inventar der 46 alten Values. Der Test vergleicht IDs, Namen, Defaults, Value-Reihenfolge und alle Enumoptionen exakt. Insgesamt nun 49 Values. Alte Direction/Motion/Speed-Visibility-Prädikate bleiben erhalten; die drei neuen Values sind sichtbar. Ein Legacyprofil ohne neue Schlüssel lädt mit Flow/true/true; alle drei Methoden und beide Schalterzustände werden mit echtem ConfigManager gespeichert und geladen. ui_motion_intensity bleibt Calm/Balanced/Expressive, Default Balanced, Index 1. Keine persönliche Config wird geladen oder geändert.

## Motion-Priorität

| UI Animations | Reduced Motion | Ambient Motion | MainMenu-Interaktion | Fortlaufender UiEffects-Takt |
|---|---|---|---|---|
| OFF | beliebig | beliebig | bisheriger Snap | eingefroren |
| ON | ON | beliebig | bestehende 120-ms-Gerade ohne Overshoot | eingefroren |
| ON | OFF | OFF | Calm/Balanced/Expressive unverändert | eingefroren |
| ON | OFF | ON | Calm/Balanced/Expressive unverändert | aktiv, wenn Gradient dynamisch |

Options.motion bleibt UI Animations, motion()/response() behalten ihre bisherige interaktive Legacybedeutung (Animations && !Reduced). Nur beginFrame berücksichtigt zusätzlich Options.ambient. Dadurch stoppt Ambient OFF weder Hover noch Notification-Entry-/Exitmotion oder Lifetime. MenuSpring, MenuMotion, MainMenu-Input, SettingsMenu, Bounds, MouseCapture, Key-Fokus und die acht Aktionen werden nicht geändert. MainMenuVis02Test ruft advanceMotion, Press, Escape und Release am echten Screen auf; bestehende Interaktions- und FPS-Tests laufen unverändert weiter.

NotificationManager.update und Timeline.advance behalten echte Monotonic-Lifetime, begrenzte Inbox/Queue, Coalesce, sichtbare Limits, Filter und Promotion. Vis02NotificationTest führt bei Ambient OFF eine reale Timeline mit wiederholter Fehlermeldung, Ablauf, Exit und Promotion aus. Vorhandene NotificationTimelineTest-Assertions bleiben unverändert.

## Semantische Farben

UiEffects.semanticColor ist ein reiner Resolver mit expliziter SemanticRole, dekorativer Farbe und Prioritätsflag. Kein Interceptor und keine Umfärbung fremder Module. Einziger neuer Produktionsconsumer ist NotificationManager.iconColor, verwendet vom bestehenden icon-Zeichenpfad.

| Rolle / bestehende Typen | Priorität ON | Priorität OFF | Unveränderte Form |
|---|---|---|---|
| ERROR | #FF667F | immer #FF667F | Kreis mit Kreuz |
| WARNING | #FFC969 | 65% bestehend + 35% Theme-Accent | Dreieck mit Ausrufezeichen |
| SUCCESS / ENABLED | #65E0AD | 65% bestehend + 35% Theme-Accent | Kreis mit Haken |
| INFO / DISABLED | #71CAFF | 65% bestehend + 35% Theme-Accent | Kreis mit i |

Default ON bewahrt exakt die bisherigen Iconfarben. OFF erlaubt begrenzte dekorative Annäherung, ohne Error zu entkernen. Kritische Meldungen behalten zusätzlich ihre bestehende Kreuzform, die auch bei Graustufen von Haken, i und Warndreieck unterscheidbar ist. Titel und Originalnachricht bleiben unverändert hell, unabhängig von Border/Glow und Theme-Akzent. NotificationType-Reihenfolge/IDs INFO,WARNING,ERROR,ENABLED,DISABLED,SUCCESS bleiben bestehen. Keine Queue-, ACK-, Timeout- oder Gameplayänderung; Teamfarben werden nicht angefasst.

Native Symbole werden über die echte private NotificationManager.icon-Methode gerendert, für ON/OFF und Reduced ON/OFF auf stark gesättigtem synthetischem Custom Theme bei Border/Glow OFF. Readbacks prüfen sichtbare Geometrien und unterschiedliche Helligkeitsmasken aller vier Rollen. Zusätzliche reale Notificationkarten zeigen Inhalt und Form. Bei 480x320 GUI begrenzt der bestehende sichtbare Limitcode die Karten auf drei; INFO verbleibt in der Queue, wird aber zusätzlich im realen Iconpfad geprüft. Das ist kein geänderter Notificationdefault.

## Abnahmegrenzen

Die geprüfte dunkle Standard-/Fixtureoberfläche zeigt lesbare Symbole und Meldungen. Das ist keine universelle Kontrastfreigabe aller Custom Themes und Sehbedingungen. Farbmessung für beliebige Hintergründe, Produktreview und vollständige Shaderpack-/Fullscreen-/Low/Balanced/High-Matrix bleiben OPEN. Es wird kein neuer Status oder Gameplay-Consumer beansprucht. Detaillierte Ausführung, Vorfälle und offene Fälle stehen in RESULTS.md. STOPP nach VIS-02.
