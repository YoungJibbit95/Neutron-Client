# Testserver-Quellenfragment

Dieser Ordner enthält rekonstruierte, **unvollständige Quellen** für lokale Neutron-Testbots. Der veröffentlichte Stand ist **kein baubares oder ladbares Plugin**. Die Dateien werden zur nachvollziehbaren Wiederherstellung erhalten; sie belegen keinen erfolgreichen Serverbetrieb.

## Vorhandener Umfang

Unter `plugin/src/main` liegen vier Java-Dateien (`BotDefinition`, `BotType`, `FakePlayer`, `FakePlayerConnection`) sowie `config.yml` und `plugin.yml`.

Es fehlen die Builddefinition mit festgelegter Serverabhängigkeit und die in `plugin.yml` deklarierte Hauptklasse `me.neutron.testserver.NeutronTestBotsPlugin`. Auch die in `BotDefinition` erwähnte Manager-Implementierung `ManagedBot` ist nicht enthalten. Deshalb sind Bot-Verwaltung, Plugin-Lifecycle und die beschriebenen Serveraktionen mit diesem Fragment nicht ausführbar.

Die [Root-POM](../../pom.xml) baut den Neutron-Client. Sie bindet diesen `tools/neutron-testserver`-Unterbaum weder als Maven-Modul noch als zusätzliche Java-Quelle ein. Ein erfolgreicher Client-Build oder Client-Testlauf prüft dieses Fragment daher nicht.

## Bestätigte API-Abweichungen

Die unveränderten Quellen wurden gegen tatsächliche `net.minecraft.server.v1_8_R3`-Bytecode-Klassen eines ausdrücklich identifizierten Spigot-1.8.8-Artefakts geprüft. Dabei sind drei Abweichungen in `FakePlayer` bestätigt:

| Eingereichte Methode | Geprüfte v1_8_R3-Signatur |
|---|---|
| `void damageEntity(DamageSource, float)` | `boolean damageEntity(DamageSource, float)` |
| `knockBack(Entity, float, double, double)` | `a(Entity, float, double, double)` |
| `isEntityInvulnerable(DamageSource)` | `isInvulnerable(DamageSource)` |

Die tatsächliche Kompilierung der vier Java-Dateien ergibt vier Fehlerdiagnosen: den inkompatiblen Rückgabetyp und drei fehlgeschlagene `@Override`-Prüfungen. Ein bloßer Wechsel zu `boolean` mit einem konstanten Rückgabewert belegt weder den beschriebenen Hurt-Effekt noch die Unsterblichkeit des Bots; diese Semantik muss am vorgesehenen Serverpfad gesondert geprüft werden.

Prüfstand: Repository-Commit `53dac36da48990408581f961b6607038a02c385d`, Plugin-Tree `277746771c5c9e26b4081ab54cefa864b57ac0bc`. Der unabhängige API-Vergleich verwendete die Maven-Central-Republication `com.github.azbh111:spigot-1.8.8:R` mit interner Versionsangabe `org.spigotmc:spigot:1.8.8-R0.1-SNAPSHOT`. Verifizierte SHA-256: `ef2db0e787cc84ebc3f79711dae0d40fcfb3846c96092d3f6030b9aedd78a0ed`; veröffentlichte und verifizierte SHA-1: `729a79a1ef2da9c30e816b8a4913f5344a73c617`.

Dieses Artefakt diente ausschließlich als Daten- und Compiler-Classpath für den Vergleich. Es wird durch diese Dokumentation **nicht als Projektabhängigkeit eingeführt** und legt den beabsichtigten exakten Serverbuild nicht nachträglich fest. Verwendet wurden `javap` und der OpenJDK-21-Compiler mit `-source 8 -target 8 -proc:none`; ein vollständiger Temurin-8-Build und ein Serverstart wurden nicht ausgeführt.

## Grenze der Wiederherstellung

Vor einer Aussage zur Lauffähigkeit müssen die fehlenden Originalbestandteile und die vorgesehene Build-/Serverversion wiederhergestellt sowie die API-Anbindung fachlich korrigiert werden. Danach sind Plugin-Laden, Bot-Lifecycle, Hurt-/Health-Verhalten und die tatsächlichen Knockback-Pfade am vorgesehenen Testserver zu verifizieren. Die hier vorhandenen Quellen und Konfigurationswerte ersetzen diese Prüfung nicht.
