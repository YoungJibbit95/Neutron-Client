# Fenster- und Sessiongates

| Gate | Status | Tatsächlicher Beleg / Grenze |
|---|---|---|
| SOURCE_REUSED | PASS | Exakter cd200f41-Pin, eigener Sparse-Worktree ohne test_run/logs |
| CMD_DEV_EXECUTES_JAVA | PASS | Echte Java8-PIDs und weitergereichte Exitcodes |
| RUNTIME_INPUTS | PASS lokal | Assemblyhash, Java8 x64, 4 AMD64-Natives, 734 Asseteinträge |
| LOCAL_NATIVE_WINDOW | PASS | OS-Handle und sichtbare Screenshots; kein Pbuffer |
| MAINMENU | PASS | Neutron-Menü im nativen Fenster; Laufzeitbeobachtung |
| SINGLEPLAYER_MENU | PASS | Sichtbare Weltwahl und Welterstellung nach Shaderpfad-Fix |
| SINGLEPLAYER_WORLD | NOT_RETESTED | Reeller Selector-Pipe-Crash; Guard-Fix positiv geprüft, Welt danach nicht erneut abgenommen |
| GAMEPLAY_CLICKGUI | NOT_RUN | Kein beobachteter Welt-/ClickGUI-Smoke |
| QUIT | PASS beobachtet | Letzter Gameprozess: Stopping/Shutdown und Exit 0 |
| QUIT_RESTART_PAIR | PARTIAL | Mehrere echte Starts; kein vollständiges normales Quit/Restart-Abnahmepaar |
| EGRESS_GUARD | PASS im geprüften Java-Pfad | 17 Negativproben, positiver JDK-Selector; OptiFine-DNS wurde abgefangen |
| HOSTILE_NATIVE_SANDBOX | NOT_CLAIMED | JNI und Assembly sind gepinnte vertrauenswürdige Eingaben; keine Kernel-Sandbox |
| OFFLINE_DEV_ONLY | PASS | Fester Testname, Token 0, keine fremden Accountdateien |
| JAVA8_TEST_PACKAGE | PASS | 678/0/0/3, 111 XMLs je finalem Lauf |
| ABI | PARTIAL | macOS/GLES/Patchy und umfassende Laufzeitqualifikation nicht neu freigegeben |
| SCAFFOLD_FREEZE | PASS | Quellen unverändert; Scaffold=false im Client |
| FRIEND_AUTH | BLOCKED | Keine eigene App-ID/API-Livefreigabe; keine reguläre Sitzung |
| FRIEND_ZIP | NOT_CREATED | Kein als Multiplayer-fähig ausgegebenes Paket |

Erste tatsächliche Spielstartversuche und Fehler sind unverändert extern
gesichert. Der Renderer-Crash im Singleplayer-Menü lautete:

```text
java.lang.IllegalStateException: Shader resource: Tenacity/shaders/vertex.vsh
  at me.youngjibbit.utils.render.ShaderUtil.resource(ShaderUtil.java:51)
  at me.youngjibbit.gui.MenuShader.shader(MenuShader.java:23)
Caused by: java.io.FileNotFoundException: minecraft:Tenacity/shaders/vertex.vsh
```

Die vollständigen Stacktraces liegen in den Runtime-Logs. Minimale Behebung:
Ressourcenpfad auf `Tenacity/Shaders/vertex.vsh` korrigieren. Der danach erreichte
Weltstart zeigte separat:

```text
java.lang.IllegalStateException: failed to create a child event loop
Caused by: java.lang.SecurityException: NEUTRON_DEV_OFFLINE: LISTEN
  at sun.nio.ch.PipeImpl$Initializer$LoopbackConnector.run(PipeImpl.java:121)
```

Die minimale Harness-Ausnahme ist auf diesen Bootstrap-JDK-Stack und lokale
Loopback-Verbindungen begrenzt. Der vollständige Welt-Retest bleibt offen.
