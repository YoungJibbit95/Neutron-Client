# VIS-02 Gradient-Vertrag

Basis `7f79895f4033ba7e2e58af95bec2a3775d082ab0`, ausschließlich VIS-02. UiEffects bleibt Eigentümer der bestehenden Monotonic Clock, von elapsed und den drei bereits vorhandenen Channel-Phasen. Keine konkurrierende Produktionsklasse, kein Timer pro Methode oder Consumer.

## Zeit und Phasen

beginFrame liest System.nanoTime über den vorhandenen LongSupplier. Erstes Reading: dt=0. Danach gilt dt=max(0,min(0.1,(now-previous)/1e9)). Ein rückläufiges Reading lässt den bisherigen High-water-Zeitpunkt stehen. Mehrere Call-Sites teilen dieselbe Uhr; bei gleichem Timestamp passiert nichts, bei späterem Timestamp wird nur die echte Differenz integriert. Es gibt keinen neu erfundenen Framecount und keinen pauschalen 1/FPS-Schritt. Die Grenze begrenzt eine lange Pause auf 100 ms Bewegung und verwirft ihre restliche Dauer. Nachfolgende Readings holen nichts nach. Das begrenzt die größte Phaseänderung auf 0.1*Speed (bei üblichen .18 auf .018 Zyklen). Nachteil: Bei dauerhaft weniger als 10 Readings/s läuft Ambient langsamer; das ist die bewusste Pausenbegrenzung.

Fortschritt erfolgt nur bei UI Animations ON, Reduced Motion OFF und Ambient Motion ON. Die Uhr wird auch während Freeze gelesen, damit Resume keine deaktivierte Zeit integriert. method-, shader-, scene-, theme- oder surface-state wird nicht als Uhr verwendet. Ein Shader/FBO-Release oder neue Surface setzt weder elapsed noch phases zurück. Die package-private timeSource bleibt ausschließlich die vorhandene Testnaht und setzt nur in Fixtures den Testepoch zurück.

Synchronize ON wählt für Text/Border/Glow Index 0 und dieselben globalen Direction/Motion/Speed/Method. Fill-Shader sampeln den vorhandenen Borderkanal. OFF erhält Text/Border/Glow-Overrides und ihre drei bestehenden integrierten Phasen. Theme-Motion nimmt Theme-Speed; Animated nimmt eingestellten Speed. Ein animiertes lokales Custom Theme darf weiterhin seinen eigenen Speed über elapsed*speed nutzen. LocalSurface.Legacy und TextEffects lokale Advanced-Overrides werden ausdrücklich nicht migriert: lokale Gradienten und deren bisheriger Cosinus bleiben erhalten. LocalSurface dt stammt weiter ausschließlich aus UiEffects.time. Bestehendes Verhalten bei expliziten Speed-/Sync-/Themewechseln bleibt erhalten; garantiert wird kein Reset durch Grafikressourcen.

## CPU / GLSL 1.20

Transport unverändert: uiText/uiBorder/uiGlow sind vec3(signed axis, mode, phase); uiResolution enthält physische Viewportgröße. CPU gradientColor nimmt GUI-Koordinaten und ScaledResolution, GPU nimmt gl_FragCoord mit umgedrehter Y-Achse. Verglichen werden Pixelzentren (x+.5,y+.5), bei GUI-Scale 2 jeweils durch 2 geteilt. X/Left sind +1/-1, Y/Up +2/-2.

Es sei t die entsprechend gerichtete Viewportkoordinate, C(t)=clamp(t,0,1), p die kanonische Channelphase und q(p)=.5-.5*cos(2*pi*p).

| Mode | Methode / Formel |
|---|---|
| 0 | Solid = 0 |
| 1 | Static = C(t) |
| 2 | Flow = .5-.5*cos(2*pi*(t-p)) |
| 3 | Ping-Pong = .5-.5*cos(2*pi*(t-q(p))) |
| 4 | Pulse = .5+(C(t)-.5)*cos(2*pi*p) |

0/1/2 sind unverändert. Mode 3/4 erweitern ausschließlich den bisherigen Transport. Flow reproduziert die bisherige Float-Cosinusberechnung exakt. Ping-Pong fährt mit q in [0,1] glatt vor und zurück; Ableitung null bei p=0,.5,1. Pulse verändert das Farbgewicht mit gemeinsamem Cosinustakt; bei Viertelperiode ist der Mix überall .5, bei Halbperiode sind die Gewichte gespiegelt. Es verändert keine Geometrie oder separate Alphauniform. Alle Formeln liefern bei endlichen Normalproben Werte in [0,1]. Custom Stoppreihenfolge bleibt Primary->Secondary. Der vorhandene Integer-Themeinterpolator kann bei Alpha 255 um eine Einheit abwärts quantisieren; VIS-02 verändert ihn nicht.

Neue Methode wird nur aus Mode 2 abgeleitet. Solid und Static und statische Theme-Motion bleiben unabhängig von Gradient Method. Shared Gradients OFF sendet weiterhin Solid. Border Color Source Accent bleibt eigenständig wirksam; es wird nicht in einen Gradient gezwungen.

ShaderUtil und Include-Mechanik bleiben unverändert. Der neue Include wird in textDirect, textEffect, arraylistSurface und den vier eingebetteten Shape-/Bloom-Shadern kompiliert. TextEffects lokale Overrides behalten ihren expliziten alten lokalen Shaderpfad. Keine Assets außer uiGradient.glsl geändert.

## Sichtbare Consumer / Grenzen

MainMenu.drawActions -> TextEffects.direct zeichnet den Titel aus echten Fontglyphen mit dem globalen Textkanal. ArrayListHUDModule -> ArrayListSurface.render nutzt globale Border-, Fill- und Textwerte. Diese Produktionsdateien bleiben unverändert. Tests treiben die echte zentrale Testclock, nicht individuelle Consumerphasen. Die Nativeprobe erzeugt je drei Phasen (.07/.21/.41) für jede Methode in beiden Consumern, prüft sichtbare Pixelunterschiede und Titel-CPU-Parität und vergleicht Methoden zusätzlich bei identischer Phase. Roh-GLSL-Referenzen decken alle fünf Modecodes, vier Achsen, vier Phasen und Viewportedgen ab; Toleranz ein 8-Bit-Level. Kein Mock-/Uniform-only-Nachweis.

NoShader und NoFBO verwenden die vorhandene ArrayListSurface-Tessellierung und lesbare Glyphen. Der Fallback bleibt statisch getönt; identische animierte GPU-Effekte werden nicht zugesagt. MainMenus eigenständiger Backdropshader ist kein vollständig shaderfreier Renderer. Die Prüfung beweist den vorhandenen Surface/Text-Fallback, keinen totalen Ausfall aller GL-APIs. Native Display, Shaderpack und Monitorfarbkalibrierung bleiben eigene Abnahmen. Ausführungsstatus und Logs: RESULTS.md.
