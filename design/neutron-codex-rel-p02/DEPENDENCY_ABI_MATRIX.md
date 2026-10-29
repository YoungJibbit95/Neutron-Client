# REL-P02 — Dependency-/ABI-Matrix

28.10.2026, Europe/Berlin. **ABI_PARTIAL / UNRESOLVED**, lokal auf Windows x64
mit Oracle JDK 1.8.0_391. **LOCAL_ONLY / DISTRIBUTION_BLOCKED.**
Die geprüften Anwendungsreferenzen und fokussierten Headless-Proben bestehen.
Ein vollständiger Client-/Display-/Audiopfad und alle optionalen Plattformen
sind nicht qualifiziert. Es gibt keinen POM-/Dependency-/Versionswechsel.

## Neu ermittelter Classpath und Auswahl

`mvn -o dependency:build-classpath -DincludeScope=runtime` und
`dependency:tree -Dscope=runtime` reproduzieren 63 Artefakte, 621 mehrfach
gelieferte Klassennamen und 199 verschiedene Byteversionen. JUnit und Hamcrest
sind Testinfrastruktur und fehlen in dieser Runtime-Liste. Die gewählte Ausgabe
bleibt die unminimierte `Neutron-dev-jar-with-dependencies.jar` mit direktem
`net.minecraft.client.main.Main`. Thin und Shade sind nicht startfreigegeben.

| Gruppe | Überlappende Namen | Verschiedene Bytes | Ausgewählte verschiedene Byteversion |
|---|---:|---:|---|
| LWJGL | 551 | 152 | org.lwjgl:lwjgl:2.9.4-nightly |
| Vecmath | 44 | 44 | Javax.vecmath:vecmath:1.5.2 |
| Netty/Patchy | 3 | 3 | io.netty:netty-all:4.0.25.Final |
| SoundSystem | 23 | 0 | Bytes beider unterschiedlich gruppierten 20120107-Artefakte identisch |

Assembly enthält jeden dieser 621 ZIP-Klasseneinträge genau einmal.
Der eigene Java8-Post-Package-Check lädt alle 621 Klassen ohne Initialisierung,
prüft ihre CodeSource gegen genau das Assembly und vergleicht Resourcebytes
und deklarierte Owner-SHA256. Bei identischen Bytes lässt sich der ursprüngliche
Lieferant nicht aus den Bytes unterscheiden; die Tabelle nennt alle passenden
Owner und erfindet keine eindeutige Herkunft.

Der Runner-Classpath enthält ausschließlich externe Prüfklassen, die Assembly,
JUnit und Hamcrest. Die 63 Artefakte werden dabei als ZIP-Eingaben gelesen,
nicht zusätzlich in den Java-Classpath gelegt. Alle Anwendungsklassen und
Bibliotheken kommen damit aus derselben tatsächlich gewählten Assembly.

## Aussage der ABI-Prüfung

Der unabhängige Classfile-Parser inventarisiert 15060 Member-Konstanten mit
Ownern aus den 621 Überlappungen. Er löst Feld-/Methodennamen plus vollständigen
JVM-Descriptor gegen die effektive gepackte Klassenhierarchie auf; geerbte
JDK-Signaturen werden aus genau dem JDK8-rt.jar gelesen. Eine Member-Konstante
ist eine statische Überapproximation, keine ausgeführte Codeabdeckung.

Ein separater Java8-Check löst die **1198 Anwendungsreferenzen** aus
`me/youngjibbit`, `net/minecraft`, `net/optifine`, `net/minecraftforge` zusätzlich
über die tatsächliche Assembly-ClassLoader-Hierarchie auf. Die fokussierten
Proben führen Vecmath, LWJGL-Utilities, Codec-Registry, Gson/Authlib-Adapter,
Log4j-Plugin-Discovery und synthetische Netty-Callbacks tatsächlich aus.
JNI und OSHI werden separat mit den unveränderten bisherigen Paketchecks
ausgeführt. Kein Socket, Server, Account, Display oder Audiogerät wird durch
die neuen Tests erzeugt. Die vorhandene Gesamtsuite enthält eigene lokale
synthetische HTTP-/Authfixtures; deren Erfolg ist keine echte Anmeldung.

`COMPATIBLE` bezeichnet hier identische Redundanz oder erfüllte erfasste
Member-Deskriptoren, nicht ein vollständig kompatibles Spieleverhalten.
`VIOLATION` benennt einen konkreten fehlenden Member. `UNKNOWN` umfasst
abweichende Klassen ohne erfassten Membercaller oder eine unqualifizierte
Reflexions-/Nativefläche. Auch bei COMPATIBLE bleiben nicht ausgeführte
Display-/Audio-/Treiberpfade offen. Zugriffsschutz, sämtliche Opcode-/Verifier-
Regeln, alle dynamisch konstruierten Klassennamen und JNI-Callbacks werden
durch den statischen Resolver nicht vollständig bewiesen.

## Tatsächliche Callgraphs und offene Befunde

### Netty / Patchy

`NetworkManager.func_181124_a` verwendet Bootstrap.<init>, group(EventLoopGroup),
handler(ChannelHandler), channel(Class), connect(InetAddress,int) und
syncUninterruptibly. `provideLocalClient` verwendet connect(SocketAddress).
Die fluent Methoden erben JVM-erased Rückgabetypen aus AbstractBootstrap;
die exakten Descriptoren stehen unten und im externen Inventar.

Die gewählte Bootstrap-Klasse samt `$1` und `$2` stammt bytegenau aus Netty.
Der Java8-Callbacktest konstruiert den tatsächlichen paketprivaten `$1`-Listener
reflektiv und durchläuft über access$000/doConnect0 den tatsächlichen `$2`-Runnable.
Direkter Dispatcher bzw. nachgebildeter Listener-Callback, Erfolg/Fehler und
beide LocalAddress-Zweige werden in acht Kombinationen mit Proxy-Channels und
Inline-EventLoop geprüft. Der Listener wird nach synthetischem Abschluss direkt
aufgerufen; Thread-Scheduling und die echte asynchrone Registrierung sind offen.
Die Channel.connect-Aufrufe landen ausschließlich im synthetischen Proxy.
Das ist ein Link-/Callbacktest, keine Netzwerkverbindung.

Patchys alternative Bootstrap-Bytes enthalten zusätzlich
`checkAddress(SocketAddress):ChannelFuture`,
`BlockedServers.isBlockedServer(String):boolean` und im Klasseninitializer
`BlockingICFB.install():void`. Die gewählte Netty-Bootstrap besitzt diese
Zusätze nicht. Deshalb ist Patchys Bootstrap-Blockierlogik in dieser Assembly
kein nachgewiesen aktiver Schutz. Das wird ausdrücklich **UNRESOLVED** gemeldet;
es wurde weder eine externe Blockliste geladen noch Patchy initialisiert oder
eine Netzwerk-/Serverprobe gemacht. Eine Anforderung an dieses Verhalten und
die geeignete Auswahl müssten separat geklärt werden. Bloße Abwesenheit einer
beabsichtigten fremden Zusatzfunktion ist hier kein nachgewiesener
NoSuchMethodError am aktuellen Windows-Anwendungscallsite. Es wurde keine
Abhängigkeit entfernt, um diesen Befund zu verdecken.

### LWJGL / JNI

Die ältere transitive Gruppe `org.lwjgl.lwjgl:lwjgl:2.8.3` kollidiert mit der
direkten `org.lwjgl:lwjgl:2.9.4-nightly`; Maven vermittelt diese verschiedenen
groupIds nicht als eine Version. Alle 152 verschiedenen überlappenden Bytes
stammen im Assembly aus nightly. 399 weitere Überlappungen sind byteidentisch.

Zwei nur in der alten JAR vorhandene macOS-Innenklassen bleiben zusätzlich
gepackt und referenzieren fehlende synthetische Methoden der nightly-Hostklasse:

* `MacOSXDisplay$1$1 -> MacOSXDisplay.access$000(MacOSXDisplay):void`
* `MacOSXDisplay$2 -> MacOSXDisplay.access$100(MacOSXDisplay):MacOSXFrame`

Dies sind echte statische Memberlücken, keine durchgeführten macOS-Starts.
Display.createDisplayImplementation wählt unter Windows den WindowsDisplay-
Zweig (LWJGLUtil-Platform 3), nicht MacOSXDisplay. Es gibt keine macOS-Freigabe.

Eine anfangs zu breite Reflexionsprobe scheitert außerdem mit echter
`NoClassDefFoundError: org/lwjgl/opengles/PowerManagementEventException`.
Der ungekürzte Diagnosebefund bleibt in `04-draft-abi.log`. Eine separate Probe
erfasst drei betroffene Flächen: ContextGLES, DrawableGLES und Display$6.
Class.forName ohne Initialisierung funktioniert für diese Klassen; vollständige
Memberreflexion funktioniert nicht. Die Desktop-GL-Aufrufe des tatsächlichen
Minecraft/Config/GlDebugHandler verwenden PixelFormat bzw. opengl.ContextAttribs;
kein Anwendungscallsite verwendet opengles.ContextAttribs. Der spätere grüne
Anwendungscheck ist ausdrücklich auf Anwendungsreferenzen begrenzt und ist
**kein GREEN derselben umfassenden Reflexionsprobe**. Diese Lücke bleibt UNKNOWN.

Die unveränderte Windows64-Probe ruft LWJGL-Sys-Timer-JNI auf und lädt OpenAL64,
JInput-64 und Twitch samt dessen vorab geladenen Abhängigkeiten. Alle 14 lokalen
DLLs sind gehasht und PE-architekturgeprüft; 32-bit-Dateien werden inventarisiert,
nicht in einen 64-bit-Prozess geladen. Kein OpenGL-Kontext oder Audiogerät,
keine Twitch-Session und keine komplette JNI-Signaturmatrix werden behauptet.

### Vecmath / Sound / weitere Bibliotheken

ESP/Nametags verwenden `Vector3d(DDD)V`, `Vector4d(DDDD)V` sowie die geerbten
öffentlichen Tuple-Felder x/y/z/w:D. Die Java8-Probe führt diese Zugriffe aus.
Matrix4f-Konstruktion/Identität/Transformation und die Matrix4f-Signatur des
Forge-Dummys werden geprüft; der absichtlich nicht implementierte Dummy wird
nicht konstruiert. OptiFine referenziert Matrix4f außerdem in der optionalen
ForgeHooksClient.applyTransform-Reflection. Eine Forge-Gesamtlaufzeit ist offen.

SoundSystem liefert 23 identische redundante Klassen aus zwei groupIds.
SoundManager registriert LibraryLWJGLOpenAL und CodecJOrbis. Die neuen Proben
registrieren Ogg/Wav unter eigenen Fixture-Erweiterungen und lassen die reale
SoundSystemConfig-Registry die Codecs reflektiv konstruieren; Cleanup läuft.
Assembly enthält zusätzlich CodecWav/Pbuffer; Shade entfernt sie weiterhin
und bleibt unfreigegeben. Kein beobachteter Audiofehler wurde erfunden.

Gson serialisiert/deserialisiert eine leere synthetische Authlib-PropertyMap.
Authlib-Signaturen werden ohne Account gelesen. Log4j findet reale Console-/
PatternLayout-Plugins und die SLF4J-Bindung. Bestehende JNA/OSHI-CPU-Discovery,
Service-/Lombok-Shadowloader-/Resource-/OptiFine-Prüfungen bleiben erhalten.

## Änderungsentscheidung

**NO_POM_CHANGE_JUSTIFIED** für den getesteten Windows-Anwendungspfad.
Es gibt keinen produktrelevanten reproduzierten Dependency-RED, der eine
POM-Änderung rechtfertigt. Die dokumentierten optionalen macOS-/GLES-/Patchy-
Grenzen werden nicht durch Exclusions, neue Versionen oder Gamecode-Writes
entfernt. Für Gesamtfreigabe bleibt ABI_PARTIAL bestehen. Ein zusätzlicher
Scaffold-Suitefehler und alle vorläufigen Runnerfehler stehen in RESULTS;
sie werden nicht zu einer Dependencyreparatur umgedeutet.

## Vollständige Eingaben und klassenweise Verträge

Die folgenden Artefaktpfade sind relativ zum lokalen Maven-Repository;
Axx bezeichnet die frisch ermittelte Classpathposition. Die Artefakthashes
stehen in `runtime-classpath-hashes.json`. Darunter folgt für **jeden der 621
Namen** der SHA256 jeder gelieferten Klasse, der tatsächlichen Paketklasse,
alle dazu bytegleichen Owner sowie die Bewertung. Der Loader ist in jeder
Zeile derselbe oben nachgewiesene JDK8-AppClassLoader der Assembly.

Die eingehenden Caller und **sämtliche vollständigen erwarteten Member-
Descriptoren je FQN** stehen zusätzlich unter `duplicates[].incoming` in
[dependency-abi-audit.json](F:/neutron-rel-p02-evidence/dependency-abi-audit.json).
Die Spalte Vertrag zählt die deduplizierten Owner/Name/Descriptor-Tripel;
der anschließende Signaturanhang führt sie ohne Callerwiederholungen auf.
Statische String-Reflectiontreffer stehen separat in `reflection_literals`;
sie werden nicht als vollständige dynamische Discovery ausgegeben.

| ID | Runtimeartefakt | Artefakt-SHA256 |
|---|---|---|
| A00 | `org/apache/logging/log4j/log4j-slf4j-impl/2.17.1/log4j-slf4j-impl-2.17.1.jar` | `e9a03720e5d5076009c2530635da9d08485e28a0b0ec20708dadc51afb78e41e` |
| A01 | `net/jodah/typetools/0.6.3/typetools-0.6.3.jar` | `408300867804be05d1083cc0287dd3a21295a9e76090e288013ba5a4182dc96d` |
| A02 | `org/slf4j/slf4j-api/1.7.32/slf4j-api-1.7.32.jar` | `3624f8474c1af46d75f98bc097d7864a323c81b3808aa43689a6e1c601c027be` |
| A03 | `org/reflections/reflections/0.10.2/reflections-0.10.2.jar` | `938a2d08fe54050d7610b944d8ddc3a09355710d9e6be0aac838dbc04e9a2825` |
| A04 | `java3d/vecmath/1.3.1/vecmath-1.3.1.jar` | `dbaa088690a23a954eff669a99d2452a24dd9f10d866725ff5d29ff4740a897e` |
| A05 | `net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-windows.jar` | `24afbd5e1fab17da57d16a4d3f19d53f36155ef46a9976484201a4bb9722287f` |
| A06 | `org/apache/httpcomponents/httpmime/4.5.2/httpmime-4.5.2.jar` | `231a3f7e4962053db2be8461d5422e68fc458a3a7dd7d8ada803a348e21f8f07` |
| A07 | `org/lwjgl/lwjgl/lwjgl-platform/2.8.3/lwjgl-platform-2.8.3-natives-windows.jar` | `5f6900b1a9899cb68af807ccf7c38608ddce36aa4c3b5c2c7d290fe91620482e` |
| A08 | `org/jetbrains/kotlin/kotlin-stdlib/1.6.20/kotlin-stdlib-1.6.20.jar` | `eeb51c2b67b26233fd81d0bc4f8044ec849718890905763ceffd84a31e2cb799` |
| A09 | `com/github/oshi/oshi-core/6.4.5/oshi-core-6.4.5.jar` | `7e634fb57b8763b7803d5f9caaed46d19c3bdbe81ddd8a93e61528c700cdc09e` |
| A10 | `io/netty/netty-all/4.0.25.Final/netty-all-4.0.25.Final.jar` | `974789d86cece0b8ffbf6a2d7d782c1239db518ec5158919b159d88cd48138a5` |
| A11 | `com/paulscode/soundsystem/20120107/soundsystem-20120107.jar` | `2882d64550240dd0c026724da664d9f97ef205c91d6a85273d10790d88608f34` |
| A12 | `com/mojang/authlib/1.5.21/authlib-1.5.21.jar` | `ce7a9c86ebebf30b89929026f4a0a3c58ce0f4d47937d6dc086ae4fd0c77544a` |
| A13 | `com/paulscode/librarylwjglopenal/20100824/librarylwjglopenal-20100824.jar` | `107d11509f6af59b8c2ba23df828bc5d3c4e067b58f3d858c7deb90ca5308f84` |
| A14 | `com/google/guava/guava/17.0/guava-17.0.jar` | `8c36a80ea613d0b6b8040a17cf837c5bbe3677bc1b06a058a6c174fdb787ebbc` |
| A15 | `org/lwjgl/lwjgl/2.9.4-nightly/lwjgl-2.9.4-nightly.jar` | `833e721817f70d1445eec13d8ce5a86e12af70efac5014356302e2bbc68b3fe2` |
| A16 | `com/paulscode/sound/libraryjavasound/20101123/libraryjavasound-20101123.jar` | `333bbefeff5eb1722dc9b8d2e26c38bc69c1efa07d0ca870480f29d8858aa336` |
| A17 | `org/lwjgl/lwjgl/lwjgl-platform/2.8.3/lwjgl-platform-2.8.3-natives-osx.jar` | `b8e4aa3315e25b718a5ff47f97fc6f6868c2fe0850a5cf4d5e3c63e6a5424245` |
| A18 | `com/github/zeromemes/alpine/3.1.0/alpine-3.1.0.jar` | `b70daebd604ab28b7256559e3f6a44d440859890a18aaf6f542c96d734f57526` |
| A19 | `org/projectlombok/lombok/1.18.26/lombok-1.18.26.jar` | `1ea5ad6c6afcff902d75072b2aaa6695585aebee9f12127e15c0036ba95d2918` |
| A20 | `net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar` | `66d4f819a062a51a1d5627bffc23fac55d1677f0e0a1feba144aabdd670a64bb` |
| A21 | `org/apache/httpcomponents/httpcore/4.3.2/httpcore-4.3.2.jar` | `abd02320e2356f89d054dae4cf02306bef20a9cf7865b3ac94ec7552b4f1528b` |
| A22 | `tv/twitch/twitch/6.5/twitch-6.5.jar` | `3b90f9f7ad968b9588482e3a09b6e7d0d81c6cf7c214c5cecd8120e3ee509ae9` |
| A23 | `commons-io/commons-io/2.4/commons-io-2.4.jar` | `cc6a41dc3eaacc9e440a6bd0d2890b20d36b4ee408fe2d67122f328bb6e01581` |
| A24 | `com/paulscode/sound/soundsystem/20120107/soundsystem-20120107.jar` | `2882d64550240dd0c026724da664d9f97ef205c91d6a85273d10790d88608f34` |
| A25 | `Javax/vecmath/vecmath/1.5.2/vecmath-1.5.2.jar` | `3558e81ca74c60dc01baca7ef05f48594bbaed43af45d4f1b9196e479734e675` |
| A26 | `commons-codec/commons-codec/1.9/commons-codec-1.9.jar` | `ad19d2601c3abf0b946b5c3a4113e226a8c1e3305e395b90013b78dd94a723ce` |
| A27 | `com/paulscode/sound/codecjorbis/20101023/codecjorbis-20101023.jar` | `6c4b4e50e608763564afa1bde2d25ece9dd715e7c9129540faa1faded4896506` |
| A28 | `org/apache/logging/log4j/log4j-core/2.17.1/log4j-core-2.17.1.jar` | `c967f223487980b9364e94a7c7f9a8a01fd3ee7c19bdbf0b0f9f8cb8511f3d41` |
| A29 | `net/java/jutils/jutils/1.0.0/jutils-1.0.0.jar` | `c3334ff39cf0ab3b54925619101054c90098b7c733b1e7834c7b75e4c41e84a5` |
| A30 | `com/paulscode/sound/codecwav/20101023/codecwav-20101023.jar` | `bb7d17b340afe6abdfbfdaa03683bce4aef39a64887dbab0636eaff3cf2d59ba` |
| A31 | `org/apache/httpcomponents/httpcore-nio/4.4.4/httpcore-nio-4.4.4.jar` | `f21be11ed00a7c655204c03d3ff38c2e8ac88db0913da3598ce5f9ffd686ae1f` |
| A32 | `com/mojang/realms/1.8.19/realms-1.8.19.jar` | `c5bbcbcfba1c7444af30b94f7c3eb2ff215ec3cc17c57a9be6b2c815f9a93a0f` |
| A33 | `org/apache/commons/commons-lang3/3.3.2/commons-lang3-3.3.2.jar` | `6b81d10754dadf184d386011486e6509c2cc0c3d33565ced4fb4402b9413d47d` |
| A34 | `fr/litarvan/openauth/1.1.6/openauth-1.1.6.jar` | `444915d2c88b053a7c51a6e4451e22d0a21811321c15872f105febe2ab9e4b50` |
| A35 | `org/json/json/20230618/json-20230618.jar` | `7004007a9fd87fe1d86f673b472c0372e5bc50bacb724c029a6112d7c4b4a640` |
| A36 | `com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar` | `766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7` |
| A37 | `org/apache/commons/commons-compress/1.21/commons-compress-1.21.jar` | `6aecfd5459728a595601cfa07258d131972ffc39b492eb48bdd596577a2f244a` |
| A38 | `com/mojang/patchy/1.7.7/patchy-1.7.7.jar` | `8922278caf146e6cb6bf956c6bff102496e0d72d89afb1a994818ca119cae97a` |
| A39 | `org/apache/httpcomponents/httpclient/4.5.13/httpclient-4.5.13.jar` | `6fe9026a566c6a5001608cf3fc32196641f6c1e5e1986d1037ccdbd5f31ef743` |
| A40 | `com/mojang/icu4j-core-mojang/51.2/icu4j-core-mojang-51.2.jar` | `147592c5eb8e11fc8394125954f877acf25918bae13aa210c2825aefc3030ed8` |
| A41 | `com/squareup/okio/okio-jvm/3.0.0/okio-jvm-3.0.0.jar` | `be64a0cc1f28ea9cd5c970dd7e7557af72c808d738c495b397bf897c9921e907` |
| A42 | `org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.9.0-RC/kotlin-stdlib-jdk7-1.9.0-RC.jar` | `d4e173f6d13a2658d3cebcbd5dcb1d81836a0ef9d6e0dd8c23b2fd8d6eb8fe18` |
| A43 | `org/jetbrains/annotations/24.1.0/annotations-24.1.0.jar` | `27a770dc7ce50500918bb8c3c0660c98290630ec796b5e3cf6b90f403b3033c6` |
| A44 | `net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-linux.jar` | `1ab4d194277816b6105253d224c8c69f1b6d720690efe7c85f3ce884f0ea273d` |
| A45 | `com/mashape/unirest/unirest-java/1.4.9/unirest-java-1.4.9.jar` | `88d9d28faa03d05deab9b4813597aee231c4ea5279edbb13d2ef35db118127f9` |
| A46 | `org/apache/logging/log4j/log4j-api/2.17.1/log4j-api-2.17.1.jar` | `b0d8a4c8ab4fb8b1888d0095822703b0e6d4793c419550203da9e69196161de4` |
| A47 | `it/unimi/dsi/fastutil/8.5.12/fastutil-8.5.12.jar` | `b5543ee08d062d551cf0a5c9bc0fb70588b0382079029ba48941fa9c9be8a5d4` |
| A48 | `com/google/code/gson/gson/2.10.1/gson-2.10.1.jar` | `4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593` |
| A49 | `org/l33tlabs/twl/pngdecoder/1.0/pngdecoder-1.0.jar` | `0fb460812aecef0ba734f7cb4e6f91c221f467c71b2e94c41a0d56115a667989` |
| A50 | `org/lwjgl/util/2.9.4-nightly/util-2.9.4-nightly.jar` | `034456b132a336b72757325bb7aaf99e075681ee5650d2bea21717bd485de42f` |
| A51 | `net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar` | `79b5a4f5829e1a49a415711f7fb8eb5b9ad22defa72929fda2da96ec30d3f018` |
| A52 | `org/apache/httpcomponents/httpasyncclient/4.1.1/httpasyncclient-4.1.1.jar` | `27cb2c5eba8e85799f38b7cb0341406ef967e061f91018c68733ec6856ff7633` |
| A53 | `net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-osx.jar` | `171024b00f9a743b92388b48733dc30fd3f6d3a7da4f66f9821531fbcf1aa074` |
| A54 | `commons-logging/commons-logging/1.1.3/commons-logging-1.1.3.jar` | `70903f6fc82e9908c8da9f20443f61d90f0870a312642991fe8462a0b9391784` |
| A55 | `org/jetbrains/kotlin/kotlin-stdlib-common/1.5.31/kotlin-stdlib-common-1.5.31.jar` | `dfa2a18e26b028388ee1968d199bf6f166f737ab7049c25a5e2da614404e22ad` |
| A56 | `net/java/dev/jna/jna-platform/5.13.0/jna-platform-5.13.0.jar` | `474d7b88f6e97009b6ec1d98c3024dd95c23187c65dabfbc35331bcac3d173dd` |
| A57 | `org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.9.0-RC/kotlin-stdlib-jdk8-1.9.0-RC.jar` | `572e14a5970f3b297ade8a09bd4789a8eaa1006f513b197733e8d5a093d5493a` |
| A58 | `com/squareup/okhttp3/okhttp/4.10.0/okhttp-4.10.0.jar` | `7580f14fa1691206e37081ad3f92063b1603b328da0bb316f2fef02e0562e7ec` |
| A59 | `org/lwjgl/lwjgl/lwjgl-platform/2.8.3/lwjgl-platform-2.8.3-natives-linux.jar` | `50916d35a3d7975031536a1e17e781627b2f81b7bec44008e37ae18114b90cfd` |
| A60 | `net/sf/jopt-simple/jopt-simple/4.6/jopt-simple-4.6.jar` | `3fcfbe3203c2ea521bf7640484fd35d6303186ea2e08e72f032d640ca067ffda` |
| A61 | `org/lwjgl/lwjgl/lwjgl/2.8.3/lwjgl-2.8.3.jar` | `99b6c31de8c799ed386863806047e36238f6125870aacc6d12abe462c15c175f` |
| A62 | `org/javassist/javassist/3.28.0-GA/javassist-3.28.0-GA.jar` | `57d0a9e9286f82f4eaa851125186997f811befce0e2060ff0a15a77f5a9dd9a7` |

### Alle 621 überlappenden FQNs

| FQN | Klassen-SHA256 je Quelle | Paket-SHA256 / passende Owner | Bewertung | Vertrag |
|---|---|---|---|---|
| `io.netty.bootstrap.Bootstrap$1` | A10 `2509dc1b99f5e3a200379ec578f7cea1d8fe68f6645e01b4b7be061541353211`<br>A38 `52820710cdf9f3da90cae3d0ed32502de49f0896746bfa705bed9cb2f29e6169` | `2509dc1b99f5e3a200379ec578f7cea1d8fe68f6645e01b4b7be061541353211` / A10 | COMPATIBLE | 8 Signaturen; verschiedene Bytes |
| `io.netty.bootstrap.Bootstrap$2` | A10 `4dad684dd4b30dd79e26b4bc0ea578dbc9ede06c6211401fc3cf649e2c25440f`<br>A38 `c7bdb8c96503c230ef719d61ef76b078b3ec08d069fc2ea70fce71074c4a7314` | `4dad684dd4b30dd79e26b4bc0ea578dbc9ede06c6211401fc3cf649e2c25440f` / A10 | COMPATIBLE | 6 Signaturen; verschiedene Bytes |
| `io.netty.bootstrap.Bootstrap` | A10 `62ebcb30f0f99a1611b1275dd2a3ca64c98c08228c5f38731dc1a9095075ca91`<br>A38 `d4a7a88df20d060b5daf270d680f4b068866f12153e079ab61f8e37920b439d6` | `62ebcb30f0f99a1611b1275dd2a3ca64c98c08228c5f38731dc1a9095075ca91` / A10 | COMPATIBLE | 20 Signaturen; verschiedene Bytes |
| `javax.vecmath.AxisAngle4d` | A04 `436f999a37d64b21c29f1dc89efa81a6546bfb889c3d820abf7c5ac39115ef6d`<br>A25 `4b84bad335c522d1dea7e4c0b73b2d151a104407e5d49ef040a78a272b16383a` | `4b84bad335c522d1dea7e4c0b73b2d151a104407e5d49ef040a78a272b16383a` / A25 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `javax.vecmath.AxisAngle4f` | A04 `95c971cc0446b668c0aab444d9ad3690a0bfb348f6977319373a2d959bda307e`<br>A25 `8063c164f80225a3cdd8b376b4a9022260e652af4b1d61e770f7113fdb9f9e04` | `8063c164f80225a3cdd8b376b4a9022260e652af4b1d61e770f7113fdb9f9e04` / A25 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `javax.vecmath.Color3b` | A04 `ad72a83edda82bd49af1d0d531c6510369b2a944c9fab62fc6cb99f89fbf31ba`<br>A25 `7d5f5b61528f298e2c4fcdaceab7f222edde3a1f8d40f86eaf2c77ea9e145f0c` | `7d5f5b61528f298e2c4fcdaceab7f222edde3a1f8d40f86eaf2c77ea9e145f0c` / A25 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `javax.vecmath.Color3f` | A04 `4754026185f8169a98d7de3aff72ce8b80ae2d7f8c8871ebd965e67e9cd2ab79`<br>A25 `7b6de97a5d2416ec46a9861f54da617002fe2c39844431fbb9da6e294869157b` | `7b6de97a5d2416ec46a9861f54da617002fe2c39844431fbb9da6e294869157b` / A25 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `javax.vecmath.Color4b` | A04 `5712189fb7c38e685a3fa0e9a1fac11c825fa8ec9a9613f4f63552a8d1eee0c1`<br>A25 `b99b0c15b712352775a962b1951e71e671e970258a2251f9fc07b3eaf2cecde0` | `b99b0c15b712352775a962b1951e71e671e970258a2251f9fc07b3eaf2cecde0` / A25 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `javax.vecmath.Color4f` | A04 `255fb406eaee59ba74253bb9c5a7e5d74183cd3f79ac1b2970fb06b9d6488ddb`<br>A25 `05cd7a82d97ac90f7c2a7f7b4ad311fa15865015720e82854f5229148ca5c757` | `05cd7a82d97ac90f7c2a7f7b4ad311fa15865015720e82854f5229148ca5c757` / A25 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `javax.vecmath.GMatrix` | A04 `06947ccd01d9e24cbcf63968dc3b12849174b4b96cd818548c80addf2318a2ab`<br>A25 `a6fe1d227eef438b8024f1c5aad913bf013253600eaee3c2d8ba2dcee002477c` | `a6fe1d227eef438b8024f1c5aad913bf013253600eaee3c2d8ba2dcee002477c` / A25 | COMPATIBLE | 32 Signaturen; verschiedene Bytes |
| `javax.vecmath.GVector` | A04 `5639cdb13a25c99fbefc849f8b94a2e6358a5e1a9a2ef74c68baa069663894f4`<br>A25 `ade01c1293bf8730b72655b9f6d1a3aca3523c286ef9934fc154d3a98fc160ec` | `ade01c1293bf8730b72655b9f6d1a3aca3523c286ef9934fc154d3a98fc160ec` / A25 | COMPATIBLE | 9 Signaturen; verschiedene Bytes |
| `javax.vecmath.Matrix3d` | A04 `82e0b0e44d4499ba9e676c4a328dcbc8af59fb2c92941280866e47a3ef86d80a`<br>A25 `5b9bed056378314052519d0a5188a0cccec6b902b15a475c25906490c3016cd4` | `5b9bed056378314052519d0a5188a0cccec6b902b15a475c25906490c3016cd4` / A25 | COMPATIBLE | 29 Signaturen; verschiedene Bytes |
| `javax.vecmath.Matrix3f` | A04 `8ba0f30276afe75c6efdaf78797f97b3831e5440f8d01f94f6be0c98c4b587ca`<br>A25 `709d7821161a8c10c0ca514596a2a12e35a11525afc568d339702de0db2bf376` | `709d7821161a8c10c0ca514596a2a12e35a11525afc568d339702de0db2bf376` / A25 | COMPATIBLE | 16 Signaturen; verschiedene Bytes |
| `javax.vecmath.Matrix4d` | A04 `5d4005eee02999beb498653c620041ffcbf5613a32e01009be2d201d21a6dfba`<br>A25 `e453f090dd9cfbcad0e9cc98508dbd65f2a6608294913529a7d843f97e02a3b2` | `e453f090dd9cfbcad0e9cc98508dbd65f2a6608294913529a7d843f97e02a3b2` / A25 | COMPATIBLE | 24 Signaturen; verschiedene Bytes |
| `javax.vecmath.Matrix4f` | A04 `e4dcacf137d24d40db179cb74f277c9b7271b2608171e714e2aa02e3bae8e71e`<br>A25 `7416959c45577d898cb9d8efc5e7f2000719d0142990649e822a0a89ad0779fa` | `7416959c45577d898cb9d8efc5e7f2000719d0142990649e822a0a89ad0779fa` / A25 | COMPATIBLE | 25 Signaturen; verschiedene Bytes |
| `javax.vecmath.MismatchedSizeException` | A04 `345f9ed7da30fb412de334631859b948820dce802ef79a3e4f485b4e93768a20`<br>A25 `3e397ca2ae2ae832d9d793f98ae964645a951e54973113ecf16428d89565c590` | `3e397ca2ae2ae832d9d793f98ae964645a951e54973113ecf16428d89565c590` / A25 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `javax.vecmath.Point2d` | A04 `3df23836d732c97503fac2e8f2bf3546eb9ea1a65c1a08b0dd1036bef7632584`<br>A25 `1549cae375a85525efc3c7f58da77deb307584ef3c2460554f74e1e3c14460ca` | `1549cae375a85525efc3c7f58da77deb307584ef3c2460554f74e1e3c14460ca` / A25 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `javax.vecmath.Point2f` | A04 `60072637fd2321ac3792b38945b79cb46838715693c0bd8d8e98a330f67d183b`<br>A25 `575aefaaee2523dc2542b0a119d70264ab9bb362f3940b12fba98d176accfb52` | `575aefaaee2523dc2542b0a119d70264ab9bb362f3940b12fba98d176accfb52` / A25 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `javax.vecmath.Point3d` | A04 `f6b37ecbaad66592214dffa3c750daa150795122f2c7c8ab442da12dea8fde56`<br>A25 `5c1d13a7bab65d7ac9bff23d1baa740ca91ea8dad09d6b011c09f6a91c76244e` | `5c1d13a7bab65d7ac9bff23d1baa740ca91ea8dad09d6b011c09f6a91c76244e` / A25 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `javax.vecmath.Point3f` | A04 `ab2b9d6039061e1abb8c0eb93bb0a3ac713fb0427c489c74c9e912f30275f937`<br>A25 `409e73f8a217fab3ddebc4789af00c918c769790dfbdfabe8106f627b483d78b` | `409e73f8a217fab3ddebc4789af00c918c769790dfbdfabe8106f627b483d78b` / A25 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `javax.vecmath.Point3i` | A04 `e50fc0c4c8624ff5b9d606072f42f2f709c81bb05dfa537a6d2e4070e78806cf`<br>A25 `0713656bf89d1224848dcb52420b6e2fe5a310447ed7476f352419e75bf0e89d` | `0713656bf89d1224848dcb52420b6e2fe5a310447ed7476f352419e75bf0e89d` / A25 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `javax.vecmath.Point4d` | A04 `d9c26cd681f3457e6b534dda08c5d74c4b615d909b5616a15be76e5cc27d67c3`<br>A25 `3ee042b5ba583c29e237cd056a0a487526fdbc60e8c8a7d0472c0a19019958d6` | `3ee042b5ba583c29e237cd056a0a487526fdbc60e8c8a7d0472c0a19019958d6` / A25 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `javax.vecmath.Point4f` | A04 `d3f5b3ffcd00ae96f9b535eebdddb8f4f48a6b7e5e11762830fc37946cbdf841`<br>A25 `497feab53ad2a1565b5f801a0eed982a800ef1a93513e2d0d779080abb99a8a5` | `497feab53ad2a1565b5f801a0eed982a800ef1a93513e2d0d779080abb99a8a5` / A25 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `javax.vecmath.Point4i` | A04 `9fec07d9234e5df5024a60086f5d4648cc96011d499def006ffab370d003a7e5`<br>A25 `b2adaff282e2c7d70ea318484235d32b1cbfd57f9ca3644a377e1d57c9c4db6f` | `b2adaff282e2c7d70ea318484235d32b1cbfd57f9ca3644a377e1d57c9c4db6f` / A25 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `javax.vecmath.Quat4d` | A04 `f70bcf566ea09d8baad9fe5ebe255bbc6d90bdab995271e577f0a2cd31617508`<br>A25 `3a2d003b02d55ddafda609db82c508ec22511c13ba0bb6647f383e387f1103e9` | `3a2d003b02d55ddafda609db82c508ec22511c13ba0bb6647f383e387f1103e9` / A25 | COMPATIBLE | 8 Signaturen; verschiedene Bytes |
| `javax.vecmath.Quat4f` | A04 `e8ed1af80b798ca7125d50b05d8d112c5d7b13e6d77a6b35021b67a208a2aca6`<br>A25 `397a4a9344c98517601da674991395c68bc6fa213b3c846b3f17ff0f9c20ae9d` | `397a4a9344c98517601da674991395c68bc6fa213b3c846b3f17ff0f9c20ae9d` / A25 | COMPATIBLE | 8 Signaturen; verschiedene Bytes |
| `javax.vecmath.SingularMatrixException` | A04 `32770c9986465e503f5269f70c29e8b1ddae5238cf999db22c7164bdd6f1aea9`<br>A25 `458ead1ca2e337a7b742c440670bf72193bbf35b9d582013b38d14a28934ccb6` | `458ead1ca2e337a7b742c440670bf72193bbf35b9d582013b38d14a28934ccb6` / A25 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `javax.vecmath.TexCoord2f` | A04 `148d5cd620771288db6b9ea8ed3e625230e4a04c749dbbfe9d56cf56c3048e95`<br>A25 `865c9f445c62e366c652245dd9a306336fb701fe3233a18a2a4de53ab4129f2b` | `865c9f445c62e366c652245dd9a306336fb701fe3233a18a2a4de53ab4129f2b` / A25 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `javax.vecmath.TexCoord3f` | A04 `c757d7f82721a4482661cf84b86b94e1356dbf36096d7648a64f56171d6c194c`<br>A25 `5ff257094c4a4609846a4074d9931616fc08eaaf65c0f91fa091da90dfc54a72` | `5ff257094c4a4609846a4074d9931616fc08eaaf65c0f91fa091da90dfc54a72` / A25 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `javax.vecmath.TexCoord4f` | A04 `bbd50241912c75f80fdad3fdff5ec41363d4daa83a5c7f8f7575a60f10e19475`<br>A25 `5851112c8dc39cd52f4a0de2e862db64810771f8a31a9752c13b09afcd1f3f14` | `5851112c8dc39cd52f4a0de2e862db64810771f8a31a9752c13b09afcd1f3f14` / A25 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple2d` | A04 `50ac9eaf7873aaff8e29cc3526197d704ba5641bbad41313500d11e52c1c9cc5`<br>A25 `14ca80cf7b00527a5a4dc000d8b03490a4866de94d108c566d274305983a69c4` | `14ca80cf7b00527a5a4dc000d8b03490a4866de94d108c566d274305983a69c4` / A25 | COMPATIBLE | 7 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple2f` | A04 `4092c9268ea768fddff6b7e0ca5511ffd1864df06676bb67deb526815b7c72f7`<br>A25 `55cc73464f410d9d962129fd843bf461d44ff14d06181809d8b3a0b233c165d5` | `55cc73464f410d9d962129fd843bf461d44ff14d06181809d8b3a0b233c165d5` / A25 | COMPATIBLE | 7 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple3b` | A04 `7d15e952f4c5d88cea948e23079585b779cec6ba69677e7e761210c8d4a789d5`<br>A25 `e97b0b1fdcca4045c7d0feb627794e6b1b7bb8bf56c6dc3924b570b1aa56e6a1` | `e97b0b1fdcca4045c7d0feb627794e6b1b7bb8bf56c6dc3924b570b1aa56e6a1` / A25 | COMPATIBLE | 7 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple3d` | A04 `dad233224b4620e3b0c3a8fac70f875b6303d010040f23c19ac326eff7fe35be`<br>A25 `c347ef84bdfeb75b4ab517e31296904b0510eb5a9211415cddffdb5188e262df` | `c347ef84bdfeb75b4ab517e31296904b0510eb5a9211415cddffdb5188e262df` / A25 | COMPATIBLE | 18 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple3f` | A04 `5c3b65accb0bc180a49ee594a47f9d7e03257a253cb38fc89053d8ac74618962`<br>A25 `d03e7897987361ae5775f293c939fa403f92f1571d97d0384c7d565b503f59dc` | `d03e7897987361ae5775f293c939fa403f92f1571d97d0384c7d565b503f59dc` / A25 | COMPATIBLE | 9 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple3i` | A04 `d2348bd17afaf12f7c50e88b2c7e07b9eeb6c080c026312b5627f8c0c2781556`<br>A25 `8c8011debf9906350175e4f060d4d38315f5ae71721a01651a36c216d221defe` | `8c8011debf9906350175e4f060d4d38315f5ae71721a01651a36c216d221defe` / A25 | COMPATIBLE | 7 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple4b` | A04 `1c68c6f40d1ec6d4c6619d2e994d07be1e0d56a891005f4f43eddc3877bd8a52`<br>A25 `5756d187b09fdabb90bda42db3bed15fdbadeaac4a4de65b2d67b425f12e47bc` | `5756d187b09fdabb90bda42db3bed15fdbadeaac4a4de65b2d67b425f12e47bc` / A25 | COMPATIBLE | 8 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple4d` | A04 `9c08e9b55725d07386ef56b5e1e0e10a88eb8f1647fbd5171b186550c97e6bb9`<br>A25 `ca494af246f562ecfc90e9bfee305aad51f08a8511b87c10710d98bd905783a9` | `ca494af246f562ecfc90e9bfee305aad51f08a8511b87c10710d98bd905783a9` / A25 | COMPATIBLE | 18 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple4f` | A04 `4f972438d9eab85ac4cfaf0742cce9fcd791441559e5eb44fa3c360f5474f6df`<br>A25 `6f21be400347f260e61f8b88dc93f4868cba20b6323fd01ff1a3dd148843d3da` | `6f21be400347f260e61f8b88dc93f4868cba20b6323fd01ff1a3dd148843d3da` / A25 | COMPATIBLE | 9 Signaturen; verschiedene Bytes |
| `javax.vecmath.Tuple4i` | A04 `9be37844f9ae89e3143b11cd9ab74a3988143c9e8a16346a1f439c9a80246c25`<br>A25 `d8a8de867e014d4e8b37e3a08db05d2cfd3c7a814f630685109f2d6a44409ea6` | `d8a8de867e014d4e8b37e3a08db05d2cfd3c7a814f630685109f2d6a44409ea6` / A25 | COMPATIBLE | 8 Signaturen; verschiedene Bytes |
| `javax.vecmath.VecMathI18N` | A04 `df05cbb83e365ebec6ac1cd8a6f966f45384e3011d74a0decab95e0d53d4effd`<br>A25 `df353fe52060e9125483812ddcc78d29db80be07e020a5de1bbb12bfcf4fd92a` | `df353fe52060e9125483812ddcc78d29db80be07e020a5de1bbb12bfcf4fd92a` / A25 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `javax.vecmath.Vector2d` | A04 `9b4bb242c02f973109e26a9e4a341fed5cfa215a89824f363011bdcb7b257d88`<br>A25 `5eef539d051037528b573f300c6bacabf3e871e6f2030a370f0fe7202a4b44a6` | `5eef539d051037528b573f300c6bacabf3e871e6f2030a370f0fe7202a4b44a6` / A25 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `javax.vecmath.Vector2f` | A04 `b3ad45628ca714848bdc9e16d0d88543767698e25d722ae90bac1957903dd580`<br>A25 `5d854e66bb18e33c9fda826882d464122be10ce13f98826a072dff86e6ab475f` | `5d854e66bb18e33c9fda826882d464122be10ce13f98826a072dff86e6ab475f` / A25 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `javax.vecmath.Vector3d` | A04 `269c0aba5b4b91c1aa5eb012e8d5e7af9f62d2cf853e7ce3b8b58167c1b9e6fe`<br>A25 `228a4f2876388796ebdd264838d8be12d98450f14dc554c2f510436b363a28b1` | `228a4f2876388796ebdd264838d8be12d98450f14dc554c2f510436b363a28b1` / A25 | COMPATIBLE | 6 Signaturen; verschiedene Bytes |
| `javax.vecmath.Vector3f` | A04 `deb300c8b3497d71c7ba96fc77bcee1f6853c27aa9e1e9e450e90b30c43b4306`<br>A25 `58dc186b595d3d3454974726f8fa8f4de88a9319c9cc864155a0e89829689d1e` | `58dc186b595d3d3454974726f8fa8f4de88a9319c9cc864155a0e89829689d1e` / A25 | COMPATIBLE | 5 Signaturen; verschiedene Bytes |
| `javax.vecmath.Vector4d` | A04 `b70fd490ede2f2c61789b7542e51487d14c6a7cd9751b67744cae04ce23fb54e`<br>A25 `be470a633bb50490167e08032679ae3ce413ae0114894ffef668f2e6eff56b5f` | `be470a633bb50490167e08032679ae3ce413ae0114894ffef668f2e6eff56b5f` / A25 | COMPATIBLE | 7 Signaturen; verschiedene Bytes |
| `javax.vecmath.Vector4f` | A04 `c00f730cb0df417f6a646da76ec8a55d726f4be97092c897009779387e97e3fe`<br>A25 `eafe49157271fc763f77a762ca5bfc9a06c3c295ea8ca9a188b896e6883f0733` | `eafe49157271fc763f77a762ca5bfc9a06c3c295ea8ca9a188b896e6883f0733` / A25 | COMPATIBLE | 6 Signaturen; verschiedene Bytes |
| `org.lwjgl.BufferChecks` | A15 `4469d0235b62711a9e1e7f98f24cd9de795cb2c63eac034576c76e5dabce587e`<br>A61 `4469d0235b62711a9e1e7f98f24cd9de795cb2c63eac034576c76e5dabce587e` | `4469d0235b62711a9e1e7f98f24cd9de795cb2c63eac034576c76e5dabce587e` / A15, A61 | COMPATIBLE | 30 Signaturen; identische Redundanz |
| `org.lwjgl.BufferUtils` | A15 `89215630d939e44ecc0dd9250d409b4d546e1c81b2ece123f1c7f28ef538eef1`<br>A61 `6a415a68e09549538c546d8ec6cfbd00230d357062aad521be9a5abb039f3f87` | `89215630d939e44ecc0dd9250d409b4d546e1c81b2ece123f1c7f28ef538eef1` / A15 | COMPATIBLE | 11 Signaturen; verschiedene Bytes |
| `org.lwjgl.DefaultSysImplementation` | A15 `7b5d3c6d71ed285bc8c85d2c42cd548aad0934f2a613c82b0fca68a4a0ccd2fc`<br>A61 `7b5d3c6d71ed285bc8c85d2c42cd548aad0934f2a613c82b0fca68a4a0ccd2fc` | `7b5d3c6d71ed285bc8c85d2c42cd548aad0934f2a613c82b0fca68a4a0ccd2fc` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.J2SESysImplementation` | A15 `1797c2b6c5d6e13cb5745eb4226023730340bd278a60cbf238ae7f7a471a8496`<br>A61 `1797c2b6c5d6e13cb5745eb4226023730340bd278a60cbf238ae7f7a471a8496` | `1797c2b6c5d6e13cb5745eb4226023730340bd278a60cbf238ae7f7a471a8496` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.LWJGLException` | A15 `93f5d49e82c746d3bc0509ba9546c35180f2c7d0ac6527ae45866c5f014e1c74`<br>A61 `93f5d49e82c746d3bc0509ba9546c35180f2c7d0ac6527ae45866c5f014e1c74` | `93f5d49e82c746d3bc0509ba9546c35180f2c7d0ac6527ae45866c5f014e1c74` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.LWJGLUtil$1` | A15 `93fbd132c739622e6afb257e2e990764674acdbd10b249d48d51b9d1c7756d8a`<br>A61 `3b6dedad902941e6165e519b73932f1bceb6a0a516d1cdf11d6389518e6d1314` | `93fbd132c739622e6afb257e2e990764674acdbd10b249d48d51b9d1c7756d8a` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.LWJGLUtil$2` | A15 `474e361e415abc38ccbc78d6045455b771fcb761b6408929e80711983203082c`<br>A61 `a40f068597e88f09ea46dba3332542d2fa7ffd2786af8bfb21f2f6ad51c4d0b3` | `474e361e415abc38ccbc78d6045455b771fcb761b6408929e80711983203082c` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.LWJGLUtil$3` | A15 `bbd2e14b152313a37abb868d748a8daff00d9dd58e20937a74805648e5d384ef`<br>A61 `d9657bdfae547aa4b4b7c0a9c792876af5afda3188dae7df01f8b8f1a440bcd9` | `bbd2e14b152313a37abb868d748a8daff00d9dd58e20937a74805648e5d384ef` / A15 | COMPATIBLE | 5 Signaturen; verschiedene Bytes |
| `org.lwjgl.LWJGLUtil$4` | A15 `c5c52ce4a7f2ddc302c73a44e15654cf8e20346c9d4d3dc0d8d7acd8bcf66089`<br>A61 `075ee304540f847e959229cbd5ad62bf74df5441c5882059a54f8c4663df7488` | `c5c52ce4a7f2ddc302c73a44e15654cf8e20346c9d4d3dc0d8d7acd8bcf66089` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.LWJGLUtil$5` | A15 `dc65e817528428a9851080e1cdf846171a08de5997c33666cc29d1b0a95eab3b`<br>A61 `4c0c7b17433cfd7655e34c029f5e9e3b5571cc78916962df2446b114196e0571` | `dc65e817528428a9851080e1cdf846171a08de5997c33666cc29d1b0a95eab3b` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.LWJGLUtil$6` | A15 `41ca1b8890b32f59739fe6b74c33abb8351f4412746e5d1a8cf65bf6f631f61d`<br>A61 `4b5c5888572cfa27fd8003d379196177ca49f77aa2a1d4888da28fbb1dece496` | `41ca1b8890b32f59739fe6b74c33abb8351f4412746e5d1a8cf65bf6f631f61d` / A15 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `org.lwjgl.LWJGLUtil$TokenFilter` | A15 `5465e8089decf4d442a98d24e74cd18d1ba9a47c7d04097ddc9c194a840bab4a`<br>A61 `fda5a3f3df3f77b929ca1a25027d13bb3b21c2ced0938a7e4a3fbbf7cdb58476` | `5465e8089decf4d442a98d24e74cd18d1ba9a47c7d04097ddc9c194a840bab4a` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.LWJGLUtil` | A15 `464e7b7d136a37d997ca3857d7beca36eb81a8dd321a2b9afb5e64e89025119d`<br>A61 `eb97982979a3cb10b3d688cc11ddb7d31351714edb377876c87eb8feb53c46b9` | `464e7b7d136a37d997ca3857d7beca36eb81a8dd321a2b9afb5e64e89025119d` / A15 | COMPATIBLE | 20 Signaturen; verschiedene Bytes |
| `org.lwjgl.LinuxSysImplementation$1` | A15 `a35c988ca38fade991f231af3b6f74f17bdb2bde52240b1ccffd2f6c2fc0b72b`<br>A61 `a35c988ca38fade991f231af3b6f74f17bdb2bde52240b1ccffd2f6c2fc0b72b` | `a35c988ca38fade991f231af3b6f74f17bdb2bde52240b1ccffd2f6c2fc0b72b` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.LinuxSysImplementation` | A15 `3fbc6796cf01dcb6c21ac8adb032d236248b6474ea97909abea30c802a98f30e`<br>A61 `702689b6d1ab3394386038cf40c091bfee48dc7a6c499396ff0d7884658fc8c4` | `3fbc6796cf01dcb6c21ac8adb032d236248b6474ea97909abea30c802a98f30e` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.MacOSXSysImplementation` | A15 `bcf0455a8902a60e33160069fe08853d4063b16e4cff78e3100037c579939ac0`<br>A61 `4e9ca6c835c1b00b8491ec8ed4202570e692c2e67d1504d7b313435d9b84dbfc` | `bcf0455a8902a60e33160069fe08853d4063b16e4cff78e3100037c579939ac0` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.MemoryUtil$1` | A15 `8a373108fc4d7b7d829bdb4e44d8482e8811bbcd287f43697448e55884996d10`<br>A61 `0ada9c4f56021af52adc1d22a309d40d44288ba8a7b045b7afcb382fe4350770` | `8a373108fc4d7b7d829bdb4e44d8482e8811bbcd287f43697448e55884996d10` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.MemoryUtil$Accessor` | A15 `c14609979fe7d452f1e359ab867a6d1e76a1dc45bbe7b4fd96e646503413080e`<br>A61 `e6df366630ff2a9ce265c919770f930aece04893a7fc111ed067523fdc07f644` | `c14609979fe7d452f1e359ab867a6d1e76a1dc45bbe7b4fd96e646503413080e` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.MemoryUtil$AccessorJNI` | A15 `5b17c76aa4dd320941c4014dd7d2bd8f8308c21668ad0b984e6c15f792caa0a9`<br>A61 `0eebe5a6e96213f36819fa704db17ab8c83682896d9abb3f3d5476178e818736` | `5b17c76aa4dd320941c4014dd7d2bd8f8308c21668ad0b984e6c15f792caa0a9` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.MemoryUtil$AccessorReflect` | A15 `10f2cea362418031be23be24aeeeead72a7256fa5087d295586dfac16f079104`<br>A61 `a844e661071a4a0ab27f4085509a83a86ec493e659f2bcc645f47b6be9b7dd04` | `10f2cea362418031be23be24aeeeead72a7256fa5087d295586dfac16f079104` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.MemoryUtil$CharSequenceNT` | A15 `780100e5750d9186ae903ea595a8990b43f7242fa7581ca883e006f437dc978d`<br>A61 `63a492269e9618310bf01caa03b90f83061053a9adfb7530b2260e8b7f11be6e` | `780100e5750d9186ae903ea595a8990b43f7242fa7581ca883e006f437dc978d` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.MemoryUtil` | A15 `42a9086c608c5669136a44d78fa7b2d3c8ce5bb14fefbf19fd787efc33f83bae`<br>A61 `2f757ae44b1575a56aaa6b72b9f7abbfcc97efb3b842eb4045e1f17d3c2498fb` | `42a9086c608c5669136a44d78fa7b2d3c8ce5bb14fefbf19fd787efc33f83bae` / A15 | COMPATIBLE | 41 Signaturen; verschiedene Bytes |
| `org.lwjgl.MemoryUtilSun$AccessorReflectFast` | A15 `83a1a68a625645167fe0367000f465b057e33cc4f67c987b39d94a2040c474f9`<br>A61 `83a1a68a625645167fe0367000f465b057e33cc4f67c987b39d94a2040c474f9` | `83a1a68a625645167fe0367000f465b057e33cc4f67c987b39d94a2040c474f9` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.MemoryUtilSun$AccessorUnsafe` | A15 `9e397ce7c2adc7e1a00268af297e92287b797929ad396c4bd6c7275a246729d2`<br>A61 `9e397ce7c2adc7e1a00268af297e92287b797929ad396c4bd6c7275a246729d2` | `9e397ce7c2adc7e1a00268af297e92287b797929ad396c4bd6c7275a246729d2` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.MemoryUtilSun` | A15 `de86e20f68b7ec88d2c200f642628b22c396fc8a4349ca93e388933eeb5e6e86`<br>A61 `de86e20f68b7ec88d2c200f642628b22c396fc8a4349ca93e388933eeb5e6e86` | `de86e20f68b7ec88d2c200f642628b22c396fc8a4349ca93e388933eeb5e6e86` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.PointerBuffer$PointerBufferR` | A15 `8bcf0cccd940d97c80430826d2d7279322f75382cd7bee99becf759c6a52a9f3`<br>A61 `c915f96d61995b5666bfdc0af5dc932813e0f2f113fff3ef21f3569f0f7274c5` | `8bcf0cccd940d97c80430826d2d7279322f75382cd7bee99becf759c6a52a9f3` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.PointerBuffer` | A15 `5759b7689d4e781e00d0cf6f60274542fca3541cb2b5ccc38c81cdd034db46e4`<br>A61 `b40d0cc3f77c6a6ec23acd0bda49a15edd16b0bb38dff3884291b911c74a10ca` | `5759b7689d4e781e00d0cf6f60274542fca3541cb2b5ccc38c81cdd034db46e4` / A15 | COMPATIBLE | 29 Signaturen; verschiedene Bytes |
| `org.lwjgl.PointerWrapper` | A15 `00b72e541812abc3138dee0d7c3e27cc6d4f0e6a75c7e28ede8b9a5f80a957e2`<br>A61 `8c8f0b742d6b541ed1339e7ce5dbfdfa364111180ded62ce3e66798d18770e08` | `00b72e541812abc3138dee0d7c3e27cc6d4f0e6a75c7e28ede8b9a5f80a957e2` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.PointerWrapperAbstract` | A15 `9ee399812192d9f02ad69fb7f8e26b3f6c6f517febcb5b926a230ec15d4e48be`<br>A61 `709886b50db32089da905ead038b6a5d009811181b41e0f490bb819b040a3f55` | `9ee399812192d9f02ad69fb7f8e26b3f6c6f517febcb5b926a230ec15d4e48be` / A15 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `org.lwjgl.Sys$1` | A15 `0392a509014d685b107e91034a1a41548a14455e312fe63b519a177fb1323752`<br>A61 `98464477b4f2d44a3ccc7380ae14f54962439cc753f8fa71d61cc7898d17bea9` | `0392a509014d685b107e91034a1a41548a14455e312fe63b519a177fb1323752` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.Sys$2` | A15 `0cd8e1ed0ca16c286bf05a79be24b5bed4de30597743a0f9c9631760d6b5d417`<br>A61 `b77884cdd17710b08ab409cf5069f14ae56d3e732f2b79adc032f426f7304ac5` | `0cd8e1ed0ca16c286bf05a79be24b5bed4de30597743a0f9c9631760d6b5d417` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.Sys$3` | A15 `b56b31771ec9983f37ac5f1f6dd465bb487abd234865cc6c24393dc4e2d821ad`<br>A61 `54cf6d236709f0c2351d3b3ff524abcdbf1bcaa6f88138f0053b3740176ad018` | `b56b31771ec9983f37ac5f1f6dd465bb487abd234865cc6c24393dc4e2d821ad` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.Sys` | A15 `61040bc9bf900be01d8d5daf8769bbfc1d5f6d632b67803c460e4704ddf36875`<br>A61 `cd927791b3a9277a7feda16b5de7fb5d59da96a4daa2034a36829d880a272f02` | `61040bc9bf900be01d8d5daf8769bbfc1d5f6d632b67803c460e4704ddf36875` / A15 | COMPATIBLE | 11 Signaturen; verschiedene Bytes |
| `org.lwjgl.SysImplementation` | A15 `b596df360e419a05ddc11b8f4bb63d63aef858683430e72e115ed1fd19630806`<br>A61 `b596df360e419a05ddc11b8f4bb63d63aef858683430e72e115ed1fd19630806` | `b596df360e419a05ddc11b8f4bb63d63aef858683430e72e115ed1fd19630806` / A15, A61 | COMPATIBLE | 10 Signaturen; identische Redundanz |
| `org.lwjgl.WindowsSysImplementation$1` | A15 `56ff73000b673b31221b365077d4a59037ff90269d465b5cd55992b8dac07285`<br>A61 `56ff73000b673b31221b365077d4a59037ff90269d465b5cd55992b8dac07285` | `56ff73000b673b31221b365077d4a59037ff90269d465b5cd55992b8dac07285` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.WindowsSysImplementation` | A15 `26adb666f73502b6353b78866bb3f56add85f16bbba1218ce7cd86c43f32e9f3`<br>A61 `26adb666f73502b6353b78866bb3f56add85f16bbba1218ce7cd86c43f32e9f3` | `26adb666f73502b6353b78866bb3f56add85f16bbba1218ce7cd86c43f32e9f3` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.input.Controller` | A15 `cf51a5c22aa9caeeb436de216c66f178e62cec83436ba25cdb8908a4f8120549`<br>A61 `ff8d8c4915930a22a3d6212c9a9a7f7307baa136e357d8a9265b8b052ea43b64` | `cf51a5c22aa9caeeb436de216c66f178e62cec83436ba25cdb8908a4f8120549` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.ControllerEvent` | A15 `b07187499f82940134c7ef3397ab9f521e64ad02383aff2f157c406a61f708bf`<br>A61 `c1a5246c6b31e6bc4939dd6b74ba067cffef336eac88dd3b49e4ad8c6bd749ec` | `b07187499f82940134c7ef3397ab9f521e64ad02383aff2f157c406a61f708bf` / A15 | COMPATIBLE | 23 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.Controllers` | A15 `957be9e467097709d8a69019f2eaa836a4392834991d8e0ad74377295685e35b`<br>A61 `64aa15be818dfae15013d60f57c91422edabed721f2f06bab72954547da89fa3` | `957be9e467097709d8a69019f2eaa836a4392834991d8e0ad74377295685e35b` / A15 | COMPATIBLE | 10 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.Cursor$CursorElement` | A15 `f42f102cd5a13c9e666ce5bd47372069e47c80332850ad2698fa0f3732b50d9c`<br>A61 `5c53f62be5293f37e1bdb1d7e887487af2fea2578627f3c65b818550e199c8ff` | `f42f102cd5a13c9e666ce5bd47372069e47c80332850ad2698fa0f3732b50d9c` / A15 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.Cursor` | A15 `a5baea504e6ad4c8c8f6c11637fd546dbe89f151d1e0d881f880621cb2bb129b`<br>A61 `2944835937fe7e4c7c87593386a6c48cbb5a69d09bb26a9834e7ed39b2c9f93b` | `a5baea504e6ad4c8c8f6c11637fd546dbe89f151d1e0d881f880621cb2bb129b` / A15 | COMPATIBLE | 13 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.JInputController` | A15 `6a3c09f607975b23d382526548db48efebb39192435e2480f9acca80a6155cf7`<br>A61 `c1db6a2523af6ab5edbd6eae01014d1cf9fdbf32f928304259d9818180fb90c7` | `6a3c09f607975b23d382526548db48efebb39192435e2480f9acca80a6155cf7` / A15 | COMPATIBLE | 23 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.Keyboard$1` | A15 `4095cc9e95c73510d00d05792a39664b83d4e89f7dcae04bd3abfd4ca9c7f27d`<br>A61 `4095cc9e95c73510d00d05792a39664b83d4e89f7dcae04bd3abfd4ca9c7f27d` | `4095cc9e95c73510d00d05792a39664b83d4e89f7dcae04bd3abfd4ca9c7f27d` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.input.Keyboard$KeyEvent` | A15 `d845f6789dcc53a7fca988adf4d2055ee7a3914813ba74fce44ed6c060016581`<br>A61 `ad674947fc1d9d2debeee4cb7f74c5eb70229255e2f7229aa7df2900e510043c` | `d845f6789dcc53a7fca988adf4d2055ee7a3914813ba74fce44ed6c060016581` / A15 | COMPATIBLE | 19 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.Keyboard` | A15 `f425e44bd6f67470baf216fe192de599ed7a219e92e8e13ca48386f81d7b2c12`<br>A61 `52cb0b71810b3322d9d2a2d1c7dafd659a1fc663d44a8f6af37a4b17f24c249d` | `f425e44bd6f67470baf216fe192de599ed7a219e92e8e13ca48386f81d7b2c12` / A15 | COMPATIBLE | 32 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.Mouse$1` | A15 `4da25e8036b54da6a18766ec6b91ddf77034a894425b74b591a0597a5e5a0979`<br>A61 `b980145732143fd99b3f63b2c71f77fb25f319ec52b86ecb862c5136c4cb971b` | `4da25e8036b54da6a18766ec6b91ddf77034a894425b74b591a0597a5e5a0979` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.Mouse` | A15 `67220bebad501bf1da136adfd1ea256d8aabf7b858bf7c8ae071fe36ff875516`<br>A61 `25e3d3fb67116fb49e5e28cfc6d4614cd5d099a2dd054828839135ec3644c9a2` | `67220bebad501bf1da136adfd1ea256d8aabf7b858bf7c8ae071fe36ff875516` / A15 | COMPATIBLE | 65 Signaturen; verschiedene Bytes |
| `org.lwjgl.input.OpenGLPackageAccess$1` | A15 `6a74d17a2ec14a43a229c48604d6b4c8e04585b8d20fdb706a1579480c1d0880`<br>A61 `6a74d17a2ec14a43a229c48604d6b4c8e04585b8d20fdb706a1579480c1d0880` | `6a74d17a2ec14a43a229c48604d6b4c8e04585b8d20fdb706a1579480c1d0880` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.input.OpenGLPackageAccess$2` | A15 `adc4f52ace45086a82a43a4aa89a868a521195dba57820742d64dfb8c815b010`<br>A61 `adc4f52ace45086a82a43a4aa89a868a521195dba57820742d64dfb8c815b010` | `adc4f52ace45086a82a43a4aa89a868a521195dba57820742d64dfb8c815b010` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.input.OpenGLPackageAccess` | A15 `68578f6258dfb185f575d0f80e58c9bc0b73dbeffe2d1707adcb46c9dfe91c2c`<br>A61 `68578f6258dfb185f575d0f80e58c9bc0b73dbeffe2d1707adcb46c9dfe91c2c` | `68578f6258dfb185f575d0f80e58c9bc0b73dbeffe2d1707adcb46c9dfe91c2c` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.openal.AL` | A15 `19b93be0fa85ed5e7059e38e74c81b8dc9e0aab07b7b0ed96cc33decc8580ef0`<br>A61 `4ca56734fe25f61702f4b9e3d74110bac22ddb600137e402f5aec2c17162855d` | `19b93be0fa85ed5e7059e38e74c81b8dc9e0aab07b7b0ed96cc33decc8580ef0` / A15 | COMPATIBLE | 14 Signaturen; verschiedene Bytes |
| `org.lwjgl.openal.AL10` | A15 `f5e4b2c5002a9bd8258481815e5ca97cbe7222c7392006c48b8b87ef6f24d1ef`<br>A61 `f5e4b2c5002a9bd8258481815e5ca97cbe7222c7392006c48b8b87ef6f24d1ef` | `f5e4b2c5002a9bd8258481815e5ca97cbe7222c7392006c48b8b87ef6f24d1ef` / A15, A61 | COMPATIBLE | 81 Signaturen; identische Redundanz |
| `org.lwjgl.openal.AL11` | A15 `ee4929b73a7f766f70a13dded819e4d3c343107b0d4145b73293713810baa0e1`<br>A61 `ee4929b73a7f766f70a13dded819e4d3c343107b0d4145b73293713810baa0e1` | `ee4929b73a7f766f70a13dded819e4d3c343107b0d4145b73293713810baa0e1` / A15, A61 | COMPATIBLE | 16 Signaturen; identische Redundanz |
| `org.lwjgl.openal.ALC10` | A15 `2d80a8aafde49e1acbf64e9b13662a0b33663c2a7fb66180fa1e6b531f35a2b6`<br>A61 `1cbe78927f1c05ff33ed274278a0334a0045b27014ab788b1cd4ab76939fb380` | `2d80a8aafde49e1acbf64e9b13662a0b33663c2a7fb66180fa1e6b531f35a2b6` / A15 | COMPATIBLE | 29 Signaturen; verschiedene Bytes |
| `org.lwjgl.openal.ALC11` | A15 `25a0a424b56883d73677f29301bf4624578df260e384da1088544f75ba0b24a5`<br>A61 `25a0a424b56883d73677f29301bf4624578df260e384da1088544f75ba0b24a5` | `25a0a424b56883d73677f29301bf4624578df260e384da1088544f75ba0b24a5` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.openal.ALCcontext` | A15 `87000e61abb313d48f25543125014976b3dc3cf3ece9d042e5516c47c696ce5f`<br>A61 `87000e61abb313d48f25543125014976b3dc3cf3ece9d042e5516c47c696ce5f` | `87000e61abb313d48f25543125014976b3dc3cf3ece9d042e5516c47c696ce5f` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.openal.ALCdevice` | A15 `90f79b5f091d7ba1395ad5ec6b29f047196cb3c08367eda10a3aad18d6433bec`<br>A61 `90f79b5f091d7ba1395ad5ec6b29f047196cb3c08367eda10a3aad18d6433bec` | `90f79b5f091d7ba1395ad5ec6b29f047196cb3c08367eda10a3aad18d6433bec` / A15, A61 | COMPATIBLE | 8 Signaturen; identische Redundanz |
| `org.lwjgl.openal.EFX10` | A15 `65a2fe1c3278a29eda33c6a8e0e139651320171a27e1500a84183b3a4e3e71c0`<br>A61 `65a2fe1c3278a29eda33c6a8e0e139651320171a27e1500a84183b3a4e3e71c0` | `65a2fe1c3278a29eda33c6a8e0e139651320171a27e1500a84183b3a4e3e71c0` / A15, A61 | COMPATIBLE | 46 Signaturen; identische Redundanz |
| `org.lwjgl.openal.EFXUtil` | A15 `65a6ecc5292525de5281a3ec475af6882ccd462a5c2cdcc8fed66376f223a318`<br>A61 `65a6ecc5292525de5281a3ec475af6882ccd462a5c2cdcc8fed66376f223a318` | `65a6ecc5292525de5281a3ec475af6882ccd462a5c2cdcc8fed66376f223a318` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.openal.OpenALException` | A15 `9d570da99aa09a9fb9ee55decd552b6d6dccef7058618180ff3862656d158a20`<br>A61 `9d570da99aa09a9fb9ee55decd552b6d6dccef7058618180ff3862656d158a20` | `9d570da99aa09a9fb9ee55decd552b6d6dccef7058618180ff3862656d158a20` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.openal.Util` | A15 `d4eb2fb33e877ec1936ce468fc541dbd2af8e33fa07b508fd77cf63879164c12`<br>A61 `d4eb2fb33e877ec1936ce468fc541dbd2af8e33fa07b508fd77cf63879164c12` | `d4eb2fb33e877ec1936ce468fc541dbd2af8e33fa07b508fd77cf63879164c12` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.AMDDeviceAttributeQuery` | A15 `f203109d1d8e1e5b32c864196a884b10b8a8cb94deb085bccbede01768813524`<br>A61 `f203109d1d8e1e5b32c864196a884b10b8a8cb94deb085bccbede01768813524` | `f203109d1d8e1e5b32c864196a884b10b8a8cb94deb085bccbede01768813524` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.AMDDeviceMemoryFlags` | A15 `3bd725a3cad674644d744cb54095cfaebcd33e3368a06a5b5425e897bc3337c1`<br>A61 `3bd725a3cad674644d744cb54095cfaebcd33e3368a06a5b5425e897bc3337c1` | `3bd725a3cad674644d744cb54095cfaebcd33e3368a06a5b5425e897bc3337c1` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.AMDOfflineDevices` | A15 `795b78919423b0bd84770fe43b867f0696db7d58c8ca52fd6723c559795b2da6`<br>A61 `795b78919423b0bd84770fe43b867f0696db7d58c8ca52fd6723c559795b2da6` | `795b78919423b0bd84770fe43b867f0696db7d58c8ca52fd6723c559795b2da6` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$1` | A15 `50bc25ec3d6c37f2979a7737958d69badaf52118672ae3b422915900b1daf143`<br>A61 `50bc25ec3d6c37f2979a7737958d69badaf52118672ae3b422915900b1daf143` | `50bc25ec3d6c37f2979a7737958d69badaf52118672ae3b422915900b1daf143` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$10` | A15 `88e3e2d9a5a91759bdd1ba8465ae49d7b5f729e117a40d52c383f8447c0300c8`<br>A61 `88e3e2d9a5a91759bdd1ba8465ae49d7b5f729e117a40d52c383f8447c0300c8` | `88e3e2d9a5a91759bdd1ba8465ae49d7b5f729e117a40d52c383f8447c0300c8` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$11` | A15 `8a818b8de36d8973aebe6fae2873c9b9eca746418092df7399aaf5083bde2e6d`<br>A61 `8a818b8de36d8973aebe6fae2873c9b9eca746418092df7399aaf5083bde2e6d` | `8a818b8de36d8973aebe6fae2873c9b9eca746418092df7399aaf5083bde2e6d` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$12` | A15 `0819ad0705f6bdccea4bc73437922ca61aa90ef8cec18c145c59b58b6caa1bc3`<br>A61 `0819ad0705f6bdccea4bc73437922ca61aa90ef8cec18c145c59b58b6caa1bc3` | `0819ad0705f6bdccea4bc73437922ca61aa90ef8cec18c145c59b58b6caa1bc3` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$2` | A15 `11ecfd2012f8937222584c045cfbe4c0f5f384148565b1817397e6852d63fd03`<br>A61 `11ecfd2012f8937222584c045cfbe4c0f5f384148565b1817397e6852d63fd03` | `11ecfd2012f8937222584c045cfbe4c0f5f384148565b1817397e6852d63fd03` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$3` | A15 `4298e58c531035a34c7b17bd9255d5f98869891995b6ba408211828b41ce6eea`<br>A61 `4298e58c531035a34c7b17bd9255d5f98869891995b6ba408211828b41ce6eea` | `4298e58c531035a34c7b17bd9255d5f98869891995b6ba408211828b41ce6eea` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$4` | A15 `c791baad34f112f5b0ca5210e8377920dfbc34f07cdd1a167c79fa15d1dcb21a`<br>A61 `c791baad34f112f5b0ca5210e8377920dfbc34f07cdd1a167c79fa15d1dcb21a` | `c791baad34f112f5b0ca5210e8377920dfbc34f07cdd1a167c79fa15d1dcb21a` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$5` | A15 `4885330fbca6a6e970d9490c1153572d8063ed03e832ed022baf36a02e475212`<br>A61 `4885330fbca6a6e970d9490c1153572d8063ed03e832ed022baf36a02e475212` | `4885330fbca6a6e970d9490c1153572d8063ed03e832ed022baf36a02e475212` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$6` | A15 `05c3f94d94aaca78345693e9a98de36525a366b4f41f3515c7d6806bd5f0a917`<br>A61 `05c3f94d94aaca78345693e9a98de36525a366b4f41f3515c7d6806bd5f0a917` | `05c3f94d94aaca78345693e9a98de36525a366b4f41f3515c7d6806bd5f0a917` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$7` | A15 `2a13efbf3b95a2daed6d422505aeea9eaf763b89f1cc54b6f3cf40f28fa2b0fd`<br>A61 `2a13efbf3b95a2daed6d422505aeea9eaf763b89f1cc54b6f3cf40f28fa2b0fd` | `2a13efbf3b95a2daed6d422505aeea9eaf763b89f1cc54b6f3cf40f28fa2b0fd` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$8` | A15 `77b68bfbf2a90f6fce833d12ce6167a342825cb82b6b9a1002ff50f4be103111`<br>A61 `77b68bfbf2a90f6fce833d12ce6167a342825cb82b6b9a1002ff50f4be103111` | `77b68bfbf2a90f6fce833d12ce6167a342825cb82b6b9a1002ff50f4be103111` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$9` | A15 `c94fc3e0ecf52eeab921e19d89113d6ed8da8c4925d041c186b0ef2167e9e8ec`<br>A61 `c94fc3e0ecf52eeab921e19d89113d6ed8da8c4925d041c186b0ef2167e9e8ec` | `c94fc3e0ecf52eeab921e19d89113d6ed8da8c4925d041c186b0ef2167e9e8ec` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$Buffers` | A15 `0e493815fcdd2d6f64bf0e40cb4b1bc34822ce97eebe5575faa9e9113b8d28b8`<br>A61 `0e493815fcdd2d6f64bf0e40cb4b1bc34822ce97eebe5575faa9e9113b8d28b8` | `0e493815fcdd2d6f64bf0e40cb4b1bc34822ce97eebe5575faa9e9113b8d28b8` / A15, A61 | COMPATIBLE | 8 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil$ObjectDestructor` | A15 `20981f6f45e87ba879dabc302926541343f22cc0d37f86bb03eacdb896bbeadf`<br>A61 `20981f6f45e87ba879dabc302926541343f22cc0d37f86bb03eacdb896bbeadf` | `20981f6f45e87ba879dabc302926541343f22cc0d37f86bb03eacdb896bbeadf` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APIUtil` | A15 `2e48ed48b9b945fcf0d43e317f5abefb53d2209ade44b2d31ec12ce203fdbced`<br>A61 `2e48ed48b9b945fcf0d43e317f5abefb53d2209ade44b2d31ec12ce203fdbced` | `2e48ed48b9b945fcf0d43e317f5abefb53d2209ade44b2d31ec12ce203fdbced` / A15, A61 | COMPATIBLE | 44 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APPLEContextLoggingFunctions` | A15 `99622f57d8b37eb003d4278fb5c8a39e157a717b846aed6dff78fe7bd00e923e`<br>A61 `99622f57d8b37eb003d4278fb5c8a39e157a717b846aed6dff78fe7bd00e923e` | `99622f57d8b37eb003d4278fb5c8a39e157a717b846aed6dff78fe7bd00e923e` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APPLEContextLoggingUtil$1` | A15 `96fded2caa954d8a6630c694c0901cad959af9e6e31ddaf698060ed25e5e3cc6`<br>A61 `96fded2caa954d8a6630c694c0901cad959af9e6e31ddaf698060ed25e5e3cc6` | `96fded2caa954d8a6630c694c0901cad959af9e6e31ddaf698060ed25e5e3cc6` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APPLEContextLoggingUtil$2` | A15 `887f44e54259883ef38b63c7132fb55255b50e602bf9bf82e0aaf3f6348babd6`<br>A61 `887f44e54259883ef38b63c7132fb55255b50e602bf9bf82e0aaf3f6348babd6` | `887f44e54259883ef38b63c7132fb55255b50e602bf9bf82e0aaf3f6348babd6` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APPLEContextLoggingUtil$3` | A15 `6b5a17d1a1805432632c17d5ce1ae03d08e244f9ddca15a6738cff0ab21d68a9`<br>A61 `6b5a17d1a1805432632c17d5ce1ae03d08e244f9ddca15a6738cff0ab21d68a9` | `6b5a17d1a1805432632c17d5ce1ae03d08e244f9ddca15a6738cff0ab21d68a9` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APPLEContextLoggingUtil` | A15 `ca98ef2d4d348ee84e3ebc93d95e7b18f7d14a7504762a42f1e15aecee9241cb`<br>A61 `ca98ef2d4d348ee84e3ebc93d95e7b18f7d14a7504762a42f1e15aecee9241cb` | `ca98ef2d4d348ee84e3ebc93d95e7b18f7d14a7504762a42f1e15aecee9241cb` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APPLEGLSharing` | A15 `1c2ddc2572e357bb759a0d60df542e630424d373dc94fae96a0add9984c36ed0`<br>A61 `1c2ddc2572e357bb759a0d60df542e630424d373dc94fae96a0add9984c36ed0` | `1c2ddc2572e357bb759a0d60df542e630424d373dc94fae96a0add9984c36ed0` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.APPLESetMemObjectDestructor` | A15 `5b444c6da1c5eea1142d294480f7773bc95a486cf86383a1f07715eff8783459`<br>A61 `5b444c6da1c5eea1142d294480f7773bc95a486cf86383a1f07715eff8783459` | `5b444c6da1c5eea1142d294480f7773bc95a486cf86383a1f07715eff8783459` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CL` | A15 `3c0c0f553d64dc716ca95ef43ad74f41903173a19c22dbf3ed0cdd7c83f04c7e`<br>A61 `3c0c0f553d64dc716ca95ef43ad74f41903173a19c22dbf3ed0cdd7c83f04c7e` | `3c0c0f553d64dc716ca95ef43ad74f41903173a19c22dbf3ed0cdd7c83f04c7e` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CL10` | A15 `2ed48b88e3589fab20b05aace621f46ab4c3f8f66eb6d789987dcf3e6ef9a96b`<br>A61 `78cd5a6880dcfa3bb695d88b77e856afa6f5deb61719469e08fc58ec7dc79908` | `2ed48b88e3589fab20b05aace621f46ab4c3f8f66eb6d789987dcf3e6ef9a96b` / A15 | COMPATIBLE | 101 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CL10GL` | A15 `5215693754d2e11dc69adce45f677a852771430900689e8041f92fe66215b02c`<br>A61 `5215693754d2e11dc69adce45f677a852771430900689e8041f92fe66215b02c` | `5215693754d2e11dc69adce45f677a852771430900689e8041f92fe66215b02c` / A15, A61 | COMPATIBLE | 10 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CL11` | A15 `87946894aadd2dc9be8489afb2582301aa23a9b7864a6c3c11325aa69a1642db`<br>A61 `87946894aadd2dc9be8489afb2582301aa23a9b7864a6c3c11325aa69a1642db` | `87946894aadd2dc9be8489afb2582301aa23a9b7864a6c3c11325aa69a1642db` / A15, A61 | COMPATIBLE | 9 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CL12` | A15 `e6e09b1fd460a0a8b65b3c89ab45d88105a1a52aec1e212a7f7687727c6e5b1f`<br>A61 `70ebf079f5c059cbac23fc840c0f2f761597d64527fd1aec6d9aa29420fd2031` | `e6e09b1fd460a0a8b65b3c89ab45d88105a1a52aec1e212a7f7687727c6e5b1f` / A15 | COMPATIBLE | 18 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CL12GL` | A15 `c44d50f72bd2e1c0218d8929807005a1f8e4cf3bd4694cfa94c42db87aea1135`<br>A61 `c44d50f72bd2e1c0218d8929807005a1f8e4cf3bd4694cfa94c42db87aea1135` | `c44d50f72bd2e1c0218d8929807005a1f8e4cf3bd4694cfa94c42db87aea1135` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLBuildProgramCallback` | A15 `726873f0da11b07c3c0a67dca5bf34960607595c34c86a85b0664f05afe8b2fa`<br>A61 `726873f0da11b07c3c0a67dca5bf34960607595c34c86a85b0664f05afe8b2fa` | `726873f0da11b07c3c0a67dca5bf34960607595c34c86a85b0664f05afe8b2fa` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLCapabilities` | A15 `4568fd3db5af29e36d315f55d8e57f77b3e2c018c1f1bf00adab0396f2ec5e09`<br>A61 `f35c95fcc433e6138bbdcc722e97a9a4c293356dc89f4dbf23a9cebf70698249` | `4568fd3db5af29e36d315f55d8e57f77b3e2c018c1f1bf00adab0396f2ec5e09` / A15 | COMPATIBLE | 143 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLChecks` | A15 `a99119f6a8e87e831ac29fd98db43b5feb15c88727c0da07a4b082322e5ebf5d`<br>A61 `a99119f6a8e87e831ac29fd98db43b5feb15c88727c0da07a4b082322e5ebf5d` | `a99119f6a8e87e831ac29fd98db43b5feb15c88727c0da07a4b082322e5ebf5d` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLCommandQueue` | A15 `02ca8c9a306009087a011f7cbae93ccf1b689f3ba91224e12ee7d99f03971afa`<br>A61 `02ca8c9a306009087a011f7cbae93ccf1b689f3ba91224e12ee7d99f03971afa` | `02ca8c9a306009087a011f7cbae93ccf1b689f3ba91224e12ee7d99f03971afa` / A15, A61 | COMPATIBLE | 12 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLCompileProgramCallback` | A15 `361c05901cf5572d6efbb0530d9293e0de8c53b47bb54101e18c735c8e9552ec`<br>A61 `361c05901cf5572d6efbb0530d9293e0de8c53b47bb54101e18c735c8e9552ec` | `361c05901cf5572d6efbb0530d9293e0de8c53b47bb54101e18c735c8e9552ec` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLContext$CLContextUtil` | A15 `adc12ca506e457426aa5633f9b8b98bee320f0267a2673189b66fb0ca2c6b0b8`<br>A61 `adc12ca506e457426aa5633f9b8b98bee320f0267a2673189b66fb0ca2c6b0b8` | `adc12ca506e457426aa5633f9b8b98bee320f0267a2673189b66fb0ca2c6b0b8` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLContext` | A15 `526a5a4c3891efeff6a256e0b2b2aed40604268b06c9d3bb46abd468431b8231`<br>A61 `526a5a4c3891efeff6a256e0b2b2aed40604268b06c9d3bb46abd468431b8231` | `526a5a4c3891efeff6a256e0b2b2aed40604268b06c9d3bb46abd468431b8231` / A15, A61 | COMPATIBLE | 28 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLContextCallback` | A15 `7c1e7c6007e3813508dad54b637c3b47750782471b7e010a3c4ee88ee2f525e3`<br>A61 `7c1e7c6007e3813508dad54b637c3b47750782471b7e010a3c4ee88ee2f525e3` | `7c1e7c6007e3813508dad54b637c3b47750782471b7e010a3c4ee88ee2f525e3` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLDevice` | A15 `1ea2dc8264f3130daea63b2a03c414e392be513ed1935ddba5d9d982ce7e1051`<br>A61 `1c7384b045c66a472c57045d835dacb14ecf76ef5b222cadf37e35687d69a057` | `1ea2dc8264f3130daea63b2a03c414e392be513ed1935ddba5d9d982ce7e1051` / A15 | COMPATIBLE | 20 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLDeviceCapabilities` | A15 `694e67f5c731d2b61a52e47d5fe940f9cabbf04e75d4231af06a48d318f3fde3`<br>A61 `548e2d969a346528a926572dca6067aab67362129371200bc2e42e46d1ae5a1c` | `694e67f5c731d2b61a52e47d5fe940f9cabbf04e75d4231af06a48d318f3fde3` / A15 | COMPATIBLE | 51 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLEvent$CLEventUtil` | A15 `0bf8be40b3316006e83ae6d3ba221171aab6323983bc455b945ba4af083ee995`<br>A61 `0bf8be40b3316006e83ae6d3ba221171aab6323983bc455b945ba4af083ee995` | `0bf8be40b3316006e83ae6d3ba221171aab6323983bc455b945ba4af083ee995` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLEvent` | A15 `754b6f478b9f8114dd2790b98740e4c340ce9e42bc85dc2df5a8435c39e4ae04`<br>A61 `754b6f478b9f8114dd2790b98740e4c340ce9e42bc85dc2df5a8435c39e4ae04` | `754b6f478b9f8114dd2790b98740e4c340ce9e42bc85dc2df5a8435c39e4ae04` / A15, A61 | COMPATIBLE | 12 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLEventCallback` | A15 `546014330b23c1fd56f7674114fe3d2f9b883df5526cd31bf49d9eb18c78cfc7`<br>A61 `546014330b23c1fd56f7674114fe3d2f9b883df5526cd31bf49d9eb18c78cfc7` | `546014330b23c1fd56f7674114fe3d2f9b883df5526cd31bf49d9eb18c78cfc7` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLFunctionAddress` | A15 `46afd1cd7208686c6354650f2337b196195f7edd765a4093d1cc9f979ca6cca2`<br>A61 `46afd1cd7208686c6354650f2337b196195f7edd765a4093d1cc9f979ca6cca2` | `46afd1cd7208686c6354650f2337b196195f7edd765a4093d1cc9f979ca6cca2` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLKernel$CLKernelUtil` | A15 `a1d2b83b19c8c94e81b95166a4c85a99341034ef930fcbb5bf359b923b67351b`<br>A61 `a1d2b83b19c8c94e81b95166a4c85a99341034ef930fcbb5bf359b923b67351b` | `a1d2b83b19c8c94e81b95166a4c85a99341034ef930fcbb5bf359b923b67351b` / A15, A61 | COMPATIBLE | 13 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLKernel` | A15 `058ecd8ce86303c1ad983616c8873940c1de93b30f93bbd5a6712cd1964910be`<br>A61 `058ecd8ce86303c1ad983616c8873940c1de93b30f93bbd5a6712cd1964910be` | `058ecd8ce86303c1ad983616c8873940c1de93b30f93bbd5a6712cd1964910be` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLLinkProgramCallback` | A15 `c0f6220f71fab761c5e4a7c9da6d22e934abd684be6d61104faf7a04bbb80112`<br>A61 `c0f6220f71fab761c5e4a7c9da6d22e934abd684be6d61104faf7a04bbb80112` | `c0f6220f71fab761c5e4a7c9da6d22e934abd684be6d61104faf7a04bbb80112` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLMem$CLMemUtil` | A15 `ae5274adfb4e74b0f0fb02aed2331f7335631e3944dbf5beb42539a5a546830b`<br>A61 `ae5274adfb4e74b0f0fb02aed2331f7335631e3944dbf5beb42539a5a546830b` | `ae5274adfb4e74b0f0fb02aed2331f7335631e3944dbf5beb42539a5a546830b` / A15, A61 | COMPATIBLE | 13 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLMem` | A15 `2e5da4069596b3cb8800d2ae74920a7c0d8855cc99bcb29beaf3902d0d830d51`<br>A61 `2e5da4069596b3cb8800d2ae74920a7c0d8855cc99bcb29beaf3902d0d830d51` | `2e5da4069596b3cb8800d2ae74920a7c0d8855cc99bcb29beaf3902d0d830d51` / A15, A61 | COMPATIBLE | 9 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLMemObjectDestructorCallback` | A15 `74f5090fe4e02c06d133c548641a98108097e4625f49f16f8f39d01fd0b5584a`<br>A61 `74f5090fe4e02c06d133c548641a98108097e4625f49f16f8f39d01fd0b5584a` | `74f5090fe4e02c06d133c548641a98108097e4625f49f16f8f39d01fd0b5584a` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLNativeKernel` | A15 `246bae487bfe98c5bafde769469d5bbb32f3b5ceabf316c6a3cb1afe3cb58bbc`<br>A61 `246bae487bfe98c5bafde769469d5bbb32f3b5ceabf316c6a3cb1afe3cb58bbc` | `246bae487bfe98c5bafde769469d5bbb32f3b5ceabf316c6a3cb1afe3cb58bbc` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLObject` | A15 `ea0d9e2d6a3e1422f6c16e908718df622ef35e6017e470fdab7ccad25edf7a2e`<br>A61 `7db77c8680e98340cb76f9695607e6f2ba5469ef721ad1fc7032ef22ca35d6b1` | `ea0d9e2d6a3e1422f6c16e908718df622ef35e6017e470fdab7ccad25edf7a2e` / A15 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLObjectChild` | A15 `b600d6f1ce37286c61d2e0061dcef87dd832b8f8a8214b52b9b0cc1b2fca70fe`<br>A61 `fdeb0b25c5d9beaabb900c7f52443a03aeecdf5f19ecd86382455976f63059c4` | `b600d6f1ce37286c61d2e0061dcef87dd832b8f8a8214b52b9b0cc1b2fca70fe` / A15 | COMPATIBLE | 7 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLObjectRegistry` | A15 `550cd98f4c85439a254f74daab46130ed120a8628a1280bfdeced5888db77403`<br>A61 `0510b2c6c6354b0535399213328874b8ebbf752f2e2f4dcab2cf1749ccf0b94c` | `550cd98f4c85439a254f74daab46130ed120a8628a1280bfdeced5888db77403` / A15 | COMPATIBLE | 9 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLObjectRetainable` | A15 `3cc817a34e449483431f8b5a7baf14f886d76b567b3b4ff91e919ff8b99f0941`<br>A61 `d29ff9155da6190b9259fb6a05f9d9b0a8742fbdbda111d335f290fbc824699d` | `3cc817a34e449483431f8b5a7baf14f886d76b567b3b4ff91e919ff8b99f0941` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLPlatform$CLPlatformUtil` | A15 `732a238f04cb417c01a3856fee0284a3e4846f312418fdfc4c9bd1a6849a70a4`<br>A61 `f2eca352e2240f02fd8f6d3330bef3c22e5a2b3d5ad92c705cfb961acbd92541` | `732a238f04cb417c01a3856fee0284a3e4846f312418fdfc4c9bd1a6849a70a4` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLPlatform` | A15 `c6cb2a683bf7cb5b042a4cb830324493c6fe6a54c6015986505ea671f2eb1e63`<br>A61 `3bf4052e81749bbc7dd959625813ca83a928432588cc91ba41bbad6bea0fae5f` | `c6cb2a683bf7cb5b042a4cb830324493c6fe6a54c6015986505ea671f2eb1e63` / A15 | COMPATIBLE | 20 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLPlatformCapabilities` | A15 `4ba311bbec6856918af8f6b0c8ed27dcc4913c4c510689c84dd2c7b2da6d90d6`<br>A61 `58be465a69d4113f7f5c0d43840ca153f9f98c8cd03ad2973b49e1d9536b0ef3` | `4ba311bbec6856918af8f6b0c8ed27dcc4913c4c510689c84dd2c7b2da6d90d6` / A15 | COMPATIBLE | 12 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.CLPrintfCallback` | A15 `4868b1fb88a0b0edf23c842ccfd6a56526513ee3187d472f1acdcd747ddc59cd`<br>A61 `4868b1fb88a0b0edf23c842ccfd6a56526513ee3187d472f1acdcd747ddc59cd` | `4868b1fb88a0b0edf23c842ccfd6a56526513ee3187d472f1acdcd747ddc59cd` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLProgram$CLProgramUtil` | A15 `8d31f2e6f78a7d2287c6f6e25161b42d8cd00b80cb6dead53c92568061029bda`<br>A61 `8d31f2e6f78a7d2287c6f6e25161b42d8cd00b80cb6dead53c92568061029bda` | `8d31f2e6f78a7d2287c6f6e25161b42d8cd00b80cb6dead53c92568061029bda` / A15, A61 | COMPATIBLE | 9 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLProgram` | A15 `5d87ae27c75567fec082a3afe5e4c187406ee1233680ad7654c5cfc4735beac1`<br>A61 `5d87ae27c75567fec082a3afe5e4c187406ee1233680ad7654c5cfc4735beac1` | `5d87ae27c75567fec082a3afe5e4c187406ee1233680ad7654c5cfc4735beac1` / A15, A61 | COMPATIBLE | 13 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLProgramCallback` | A15 `7dc12a28c95010d99711681518e0e6eba838e55b63a20ca3b6d4e3258dee24e9`<br>A61 `7dc12a28c95010d99711681518e0e6eba838e55b63a20ca3b6d4e3258dee24e9` | `7dc12a28c95010d99711681518e0e6eba838e55b63a20ca3b6d4e3258dee24e9` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CLSampler` | A15 `bb6e69250a25e2b16a3d0f2720af8b8833c73328e301aeaa98933fa0c95cc2fb`<br>A61 `bb6e69250a25e2b16a3d0f2720af8b8833c73328e301aeaa98933fa0c95cc2fb` | `bb6e69250a25e2b16a3d0f2720af8b8833c73328e301aeaa98933fa0c95cc2fb` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.CallbackUtil` | A15 `1d322377f143003f27ce0041167bc2fbc5a966486cd5313a656b3eac3df86b6c`<br>A61 `1d322377f143003f27ce0041167bc2fbc5a966486cd5313a656b3eac3df86b6c` | `1d322377f143003f27ce0041167bc2fbc5a966486cd5313a656b3eac3df86b6c` / A15, A61 | COMPATIBLE | 14 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.EXTAtomicCounters32` | A15 `408f39468f7063f0be9e1a0d22484f64bed11160da7785146520ecaa85114372`<br>A61 `408f39468f7063f0be9e1a0d22484f64bed11160da7785146520ecaa85114372` | `408f39468f7063f0be9e1a0d22484f64bed11160da7785146520ecaa85114372` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.EXTAtomicCounters64` | A15 `1dc663d633b94637469e241ffa4f21aad7ab9b14c46186280d1a1aba94f35fb9`<br>A61 `1dc663d633b94637469e241ffa4f21aad7ab9b14c46186280d1a1aba94f35fb9` | `1dc663d633b94637469e241ffa4f21aad7ab9b14c46186280d1a1aba94f35fb9` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.EXTDeviceFission` | A15 `e632ab777363dfcaefcc547c6c20d38e6805681c059ba96da17b4ac1cad226ea`<br>A61 `e632ab777363dfcaefcc547c6c20d38e6805681c059ba96da17b4ac1cad226ea` | `e632ab777363dfcaefcc547c6c20d38e6805681c059ba96da17b4ac1cad226ea` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.EXTMigrateMemobject` | A15 `cb19147b877ef7e3b7aa2876020f915625d431a9f07c4e134c171e7a23cbf6ca`<br>A61 `cb19147b877ef7e3b7aa2876020f915625d431a9f07c4e134c171e7a23cbf6ca` | `cb19147b877ef7e3b7aa2876020f915625d431a9f07c4e134c171e7a23cbf6ca` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.FastLongMap$Entry` | A15 `61cac8cf9744c5f8a02a698d9615dbb316e9d86429f6d2f03a4d69939c86722a`<br>A61 `c3f8243a8a3a4d7fe81e3c3824ecdff02ec093bccc5d359297fbb31f1f3ff944` | `61cac8cf9744c5f8a02a698d9615dbb316e9d86429f6d2f03a4d69939c86722a` / A15 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.FastLongMap$EntryIterator` | A15 `6f4e430d0c5d9a6dec757410bec6a2f9909a0d5e7c4deae68f870307190762dc`<br>A61 `270946092624cb1cd374621ff212faec50b4192c36f20666f016b2f9755448b8` | `6f4e430d0c5d9a6dec757410bec6a2f9909a0d5e7c4deae68f870307190762dc` / A15 | COMPATIBLE | 6 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.FastLongMap` | A15 `cbe10cefb34b680f35d7bbd369386a44f0ab57255f79df0e61579d1c75de3502`<br>A61 `17f9ecc18f8cc8ba6ce564f3b3fb3eb23c59d93157c6cb8cdb20e597cdfa09cf` | `cbe10cefb34b680f35d7bbd369386a44f0ab57255f79df0e61579d1c75de3502` / A15 | COMPATIBLE | 17 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.INTELImmediateExecution` | A15 `a8aec3fa2336703add2312d0565a31283f361d4be690080727599766753e6a3c`<br>A61 `a8aec3fa2336703add2312d0565a31283f361d4be690080727599766753e6a3c` | `a8aec3fa2336703add2312d0565a31283f361d4be690080727599766753e6a3c` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtil` | A15 `f65906fd438ba40b8878223f5d79df2c053c9bda2529287a030fc897fc698b4a`<br>A61 `436fa8e15258c4cb0d395f9c055f1d08719702286b30a0e227a0edcaa4da80e9` | `f65906fd438ba40b8878223f5d79df2c053c9bda2529287a030fc897fc698b4a` / A15 | COMPATIBLE | 5 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.InfoUtilAbstract` | A15 `340e9f446b805ad1529c2d7d5c17981960e624dd0ce12d208d6b3fc1eab7c0ee`<br>A61 `340e9f446b805ad1529c2d7d5c17981960e624dd0ce12d208d6b3fc1eab7c0ee` | `340e9f446b805ad1529c2d7d5c17981960e624dd0ce12d208d6b3fc1eab7c0ee` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtilFactory$1` | A15 `4c4df131fecc9b6213a810d07c6bf072bc264231de0b71638017d6ac22e8e4b9`<br>A61 `4c4df131fecc9b6213a810d07c6bf072bc264231de0b71638017d6ac22e8e4b9` | `4c4df131fecc9b6213a810d07c6bf072bc264231de0b71638017d6ac22e8e4b9` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtilFactory$2` | A15 `d75f26877c31be5c8da103cc53f19f0672fc234651d590d01b42931e99a21632`<br>A61 `d75f26877c31be5c8da103cc53f19f0672fc234651d590d01b42931e99a21632` | `d75f26877c31be5c8da103cc53f19f0672fc234651d590d01b42931e99a21632` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtilFactory$CLContextUtil` | A15 `16977750aba58b769b3522fe848222af6120d426b72e940e2fb3e7479c2ea2d6`<br>A61 `16977750aba58b769b3522fe848222af6120d426b72e940e2fb3e7479c2ea2d6` | `16977750aba58b769b3522fe848222af6120d426b72e940e2fb3e7479c2ea2d6` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtilFactory$CLDeviceUtil` | A15 `bcede73bb04a750b2ac9dc57f3511cdaecc23c6cc8c16e4b155a43e6752b409e`<br>A61 `bcede73bb04a750b2ac9dc57f3511cdaecc23c6cc8c16e4b155a43e6752b409e` | `bcede73bb04a750b2ac9dc57f3511cdaecc23c6cc8c16e4b155a43e6752b409e` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtilFactory$CLEventUtil` | A15 `17e7608a3c8f4e437b17960fdf9c98dbce21f68012cd99763a64fda948137f57`<br>A61 `17e7608a3c8f4e437b17960fdf9c98dbce21f68012cd99763a64fda948137f57` | `17e7608a3c8f4e437b17960fdf9c98dbce21f68012cd99763a64fda948137f57` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtilFactory$CLKernelUtil` | A15 `f1dbb033724e0eb9987ff0ae08ac8e97bb279ec292b297302fd23615d53b3348`<br>A61 `f1dbb033724e0eb9987ff0ae08ac8e97bb279ec292b297302fd23615d53b3348` | `f1dbb033724e0eb9987ff0ae08ac8e97bb279ec292b297302fd23615d53b3348` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtilFactory$CLMemUtil` | A15 `3ddc4e9982a4f937c44fe0932962af87d07c96f93b49157f89f767cb8baca5bc`<br>A61 `3ddc4e9982a4f937c44fe0932962af87d07c96f93b49157f89f767cb8baca5bc` | `3ddc4e9982a4f937c44fe0932962af87d07c96f93b49157f89f767cb8baca5bc` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtilFactory$CLPlatformUtil` | A15 `dac0ed2aac95286802619772e9d1b516c66e7f8da3962e1ab09cd1dbc33d9dae`<br>A61 `dac0ed2aac95286802619772e9d1b516c66e7f8da3962e1ab09cd1dbc33d9dae` | `dac0ed2aac95286802619772e9d1b516c66e7f8da3962e1ab09cd1dbc33d9dae` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.InfoUtilFactory$CLProgramUtil` | A15 `58da2616001bb18a877f9963e20082dc6c71c5f3b311ef959ba794ba7a094467`<br>A61 `b6778cc44a53f0cbb38e18d5b3886be7e422a3c669c6b1dace6ede38e18c023f` | `58da2616001bb18a877f9963e20082dc6c71c5f3b311ef959ba794ba7a094467` / A15 | COMPATIBLE | 7 Signaturen; verschiedene Bytes |
| `org.lwjgl.opencl.InfoUtilFactory` | A15 `008adfa58cf506a2f174bed3f6fbd0bc6043a8b8dea65604ef4d13e5687b6a43`<br>A61 `008adfa58cf506a2f174bed3f6fbd0bc6043a8b8dea65604ef4d13e5687b6a43` | `008adfa58cf506a2f174bed3f6fbd0bc6043a8b8dea65604ef4d13e5687b6a43` / A15, A61 | COMPATIBLE | 9 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.KHRFp16` | A15 `39ba4703ad3b23ccb9256c86e9b6c1510cf4de52ad15164fc4e74cf287b78418`<br>A61 `39ba4703ad3b23ccb9256c86e9b6c1510cf4de52ad15164fc4e74cf287b78418` | `39ba4703ad3b23ccb9256c86e9b6c1510cf4de52ad15164fc4e74cf287b78418` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.KHRFp64` | A15 `eae28ba9dde5aced8864c1060bcb6bf348e9be0580e6996011ced744e7000f6b`<br>A61 `eae28ba9dde5aced8864c1060bcb6bf348e9be0580e6996011ced744e7000f6b` | `eae28ba9dde5aced8864c1060bcb6bf348e9be0580e6996011ced744e7000f6b` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.KHRGLEvent` | A15 `80bff8abef396c43d389010e14cb66afd0c48e51590b88e86fb2a865e8487c41`<br>A61 `80bff8abef396c43d389010e14cb66afd0c48e51590b88e86fb2a865e8487c41` | `80bff8abef396c43d389010e14cb66afd0c48e51590b88e86fb2a865e8487c41` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.KHRGLSharing` | A15 `2e03e3438a1c5a144529c20d0d4d3b9b7f28095de62cfca6e10caa9eb9e666de`<br>A61 `2e03e3438a1c5a144529c20d0d4d3b9b7f28095de62cfca6e10caa9eb9e666de` | `2e03e3438a1c5a144529c20d0d4d3b9b7f28095de62cfca6e10caa9eb9e666de` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.KHRICD` | A15 `a358e75f5a04b5b898d0bcf031a4fba0820a6c1b8238be4fa8e854be5c6d0cc8`<br>A61 `a358e75f5a04b5b898d0bcf031a4fba0820a6c1b8238be4fa8e854be5c6d0cc8` | `a358e75f5a04b5b898d0bcf031a4fba0820a6c1b8238be4fa8e854be5c6d0cc8` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.NVDeviceAttributeQuery` | A15 `4d4555b2afbbaa4f58032e2ba4f13f4a85f8512e898a405fdb21b0882710a408`<br>A61 `4d4555b2afbbaa4f58032e2ba4f13f4a85f8512e898a405fdb21b0882710a408` | `4d4555b2afbbaa4f58032e2ba4f13f4a85f8512e898a405fdb21b0882710a408` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.OpenCLException` | A15 `c717f14f189bca838fec2cc7b4f60fc803710c55187a7adc27198e696719b9e9`<br>A61 `c717f14f189bca838fec2cc7b4f60fc803710c55187a7adc27198e696719b9e9` | `c717f14f189bca838fec2cc7b4f60fc803710c55187a7adc27198e696719b9e9` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.Util$1` | A15 `f844d6aa8692c9a01820f160792975a9d5b7917f91fb87feab9c877ba8ff60fb`<br>A61 `f844d6aa8692c9a01820f160792975a9d5b7917f91fb87feab9c877ba8ff60fb` | `f844d6aa8692c9a01820f160792975a9d5b7917f91fb87feab9c877ba8ff60fb` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.Util` | A15 `87411d66a7957dc1f85a24fff7c8b4af6f487ccc56cfe59ae6d176c0aec941b2`<br>A61 `87411d66a7957dc1f85a24fff7c8b4af6f487ccc56cfe59ae6d176c0aec941b2` | `87411d66a7957dc1f85a24fff7c8b4af6f487ccc56cfe59ae6d176c0aec941b2` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.api.CLBufferRegion` | A15 `511b51462fb0e17af2f27d035607ecee53b4d68cfb3f65546b326b5665878709`<br>A61 `511b51462fb0e17af2f27d035607ecee53b4d68cfb3f65546b326b5665878709` | `511b51462fb0e17af2f27d035607ecee53b4d68cfb3f65546b326b5665878709` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.api.CLImageFormat` | A15 `61624469dffd905615b94cf384ec3c5cf029b209f1f21555504eeb0b11681ce5`<br>A61 `61624469dffd905615b94cf384ec3c5cf029b209f1f21555504eeb0b11681ce5` | `61624469dffd905615b94cf384ec3c5cf029b209f1f21555504eeb0b11681ce5` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opencl.api.Filter` | A15 `ade08e7c0d4ecc3092ca84d79455c8296f435285f68b538ad448fe3cae626b45`<br>A61 `628cf517ebfc327221901cc9186e8aee0222609e92681519f1d1a0c874774fbc` | `ade08e7c0d4ecc3092ca84d79455c8296f435285f68b538ad448fe3cae626b45` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.AMDBlendMinmaxFactor` | A15 `ccf3fdbeb86477b2187c74409784693e13c75756c1c295317f35529f0cbd4619`<br>A61 `ccf3fdbeb86477b2187c74409784693e13c75756c1c295317f35529f0cbd4619` | `ccf3fdbeb86477b2187c74409784693e13c75756c1c295317f35529f0cbd4619` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AMDDebugOutput` | A15 `394b6fe7be54c462e4ac6269981fc3dc99de2f19c8fb30acd609342019e2470f`<br>A61 `394b6fe7be54c462e4ac6269981fc3dc99de2f19c8fb30acd609342019e2470f` | `394b6fe7be54c462e4ac6269981fc3dc99de2f19c8fb30acd609342019e2470f` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AMDDebugOutputCallback$1` | A15 `95482a4ce04d822e15824982f2299d8fa13e41d76e7080c7438d75cd4c13138c`<br>A61 `0e4dc6752523024687a27089e6ac77ce63dc0780e5b2c35dfd89c21e2a1b7cfb` | `95482a4ce04d822e15824982f2299d8fa13e41d76e7080c7438d75cd4c13138c` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.AMDDebugOutputCallback$Handler` | A15 `60e1633544bfa438c4bbe8d297cc6e5fd94e76058b81d924c8d61ff87cecfae2`<br>A61 `60e1633544bfa438c4bbe8d297cc6e5fd94e76058b81d924c8d61ff87cecfae2` | `60e1633544bfa438c4bbe8d297cc6e5fd94e76058b81d924c8d61ff87cecfae2` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AMDDebugOutputCallback` | A15 `ecbc30e445c713833df1ca89d299d28fa7d20921af6b52b3bdf81b0f33df15bb`<br>A61 `c738283d9cf0bb9774c2c9cf2f5653b1fc2ede29dd0e5f02d464fa6112552867` | `ecbc30e445c713833df1ca89d299d28fa7d20921af6b52b3bdf81b0f33df15bb` / A15 | COMPATIBLE | 5 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.AMDDepthClampSeparate` | A15 `d0854df321cc1caf7486ac382b908d64b0e5550c5d39be218b947105aca90d53`<br>A61 `d0854df321cc1caf7486ac382b908d64b0e5550c5d39be218b947105aca90d53` | `d0854df321cc1caf7486ac382b908d64b0e5550c5d39be218b947105aca90d53` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AMDDrawBuffersBlend` | A15 `82486f78f73952b950c979e26f2736141fdbac5def8e05f9c295e2322cc33b53`<br>A61 `82486f78f73952b950c979e26f2736141fdbac5def8e05f9c295e2322cc33b53` | `82486f78f73952b950c979e26f2736141fdbac5def8e05f9c295e2322cc33b53` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AMDMultiDrawIndirect` | A15 `0e47371c88e9d68a1209fee062a67ff4b610d45ce44c39983f3f738c14ba1704`<br>A61 `11c08453672f0c428b60b714bc732b9f37967fd0d2a333851a3baa847d7ddbb7` | `0e47371c88e9d68a1209fee062a67ff4b610d45ce44c39983f3f738c14ba1704` / A15 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.AMDNameGenDelete` | A15 `f72a69fe2f82fa0159837f07e814d23bad8b6f8687663eba4e0309f48c6dccc9`<br>A61 `f72a69fe2f82fa0159837f07e814d23bad8b6f8687663eba4e0309f48c6dccc9` | `f72a69fe2f82fa0159837f07e814d23bad8b6f8687663eba4e0309f48c6dccc9` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AMDPerformanceMonitor` | A15 `a377696ae1e6996446938ef21dc35a9739a640b171ac70f94ae4b4c0be5afa8e`<br>A61 `2b4739f8b6978794714db222df01d40a07cd3a1ef674e91a7cc4d36e126a84d8` | `a377696ae1e6996446938ef21dc35a9739a640b171ac70f94ae4b4c0be5afa8e` / A15 | COMPATIBLE | 11 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.AMDPinnedMemory` | A15 `c68324f90cc6a04de41bf3f4477cdb9cdc0862c4e5d64e6f0f882e4657d4231a`<br>A61 `c68324f90cc6a04de41bf3f4477cdb9cdc0862c4e5d64e6f0f882e4657d4231a` | `c68324f90cc6a04de41bf3f4477cdb9cdc0862c4e5d64e6f0f882e4657d4231a` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AMDSamplePositions` | A15 `d978320831a7a7239034f5446311c984a7084df4950eb9812999a5b894aa56ff`<br>A61 `d978320831a7a7239034f5446311c984a7084df4950eb9812999a5b894aa56ff` | `d978320831a7a7239034f5446311c984a7084df4950eb9812999a5b894aa56ff` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AMDSeamlessCubemapPerTexture` | A15 `125c5a2752239124062ed1ae2b817c58b9a009b9e4eb271d1c90081c01cbf6df`<br>A61 `125c5a2752239124062ed1ae2b817c58b9a009b9e4eb271d1c90081c01cbf6df` | `125c5a2752239124062ed1ae2b817c58b9a009b9e4eb271d1c90081c01cbf6df` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AMDVertexShaderTessellator` | A15 `9636187055e2222bb9abb43ad50b8ea5b4490d9ef0d77ef714e2de9b224da3de`<br>A61 `9636187055e2222bb9abb43ad50b8ea5b4490d9ef0d77ef714e2de9b224da3de` | `9636187055e2222bb9abb43ad50b8ea5b4490d9ef0d77ef714e2de9b224da3de` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APIUtil` | A15 `84c9ef831d7254ffbc1ceb8e6a5979b953488f72884f5a393cedc64716626544`<br>A61 `84c9ef831d7254ffbc1ceb8e6a5979b953488f72884f5a393cedc64716626544` | `84c9ef831d7254ffbc1ceb8e6a5979b953488f72884f5a393cedc64716626544` / A15, A61 | COMPATIBLE | 28 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEAuxDepthStencil` | A15 `f5f60737424a60092024b12c7b41fc6b22dbf4c4e65a05f989e14e5b438cf190`<br>A61 `f5f60737424a60092024b12c7b41fc6b22dbf4c4e65a05f989e14e5b438cf190` | `f5f60737424a60092024b12c7b41fc6b22dbf4c4e65a05f989e14e5b438cf190` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEClientStorage` | A15 `81e6848d64fabbf626edd8ada27d26749a5dc60bada4f70683592154cc44ce29`<br>A61 `81e6848d64fabbf626edd8ada27d26749a5dc60bada4f70683592154cc44ce29` | `81e6848d64fabbf626edd8ada27d26749a5dc60bada4f70683592154cc44ce29` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEElementArray` | A15 `c497121f6a7533fb3083c27a73673de939ad0a4de27ddf97cd3723dd9fef9c5a`<br>A61 `c497121f6a7533fb3083c27a73673de939ad0a4de27ddf97cd3723dd9fef9c5a` | `c497121f6a7533fb3083c27a73673de939ad0a4de27ddf97cd3723dd9fef9c5a` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEFence` | A15 `d50d89855d78978109b02bb5a9d2672235b1bef06986b9e44fedc4ffa56ec215`<br>A61 `d50d89855d78978109b02bb5a9d2672235b1bef06986b9e44fedc4ffa56ec215` | `d50d89855d78978109b02bb5a9d2672235b1bef06986b9e44fedc4ffa56ec215` / A15, A61 | COMPATIBLE | 8 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEFloatPixels` | A15 `a578a5870c91c4cc5ef7499cb0b20a38bc7e0b548f8379ccb3c574c5648c063b`<br>A61 `a578a5870c91c4cc5ef7499cb0b20a38bc7e0b548f8379ccb3c574c5648c063b` | `a578a5870c91c4cc5ef7499cb0b20a38bc7e0b548f8379ccb3c574c5648c063b` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEFlushBufferRange` | A15 `97d8727d730cddab905abab1a315d6a14bc8c6eebb15ce79ca493068d5d668cf`<br>A61 `97d8727d730cddab905abab1a315d6a14bc8c6eebb15ce79ca493068d5d668cf` | `97d8727d730cddab905abab1a315d6a14bc8c6eebb15ce79ca493068d5d668cf` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEObjectPurgeable` | A15 `bc98be84d90490b0fd3aff4f86cba82f0edfe522550092733adf1a01b0e80417`<br>A61 `f41ebad26c013a227cc8783d455e64949d895dd5c3a2d2ec20ee391f8f36e941` | `bc98be84d90490b0fd3aff4f86cba82f0edfe522550092733adf1a01b0e80417` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.APPLEPackedPixels` | A15 `d3b07eba91b8ca60e647fe669ea1f0bd9798ab4db31f91dae19302316457fc8e`<br>A61 `d3b07eba91b8ca60e647fe669ea1f0bd9798ab4db31f91dae19302316457fc8e` | `d3b07eba91b8ca60e647fe669ea1f0bd9798ab4db31f91dae19302316457fc8e` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLERgb422` | A15 `5b391168dcdc97d2b9de7643dab8c7d5b593045dce3fd263ff82e00d41bb02d0`<br>A61 `5b391168dcdc97d2b9de7643dab8c7d5b593045dce3fd263ff82e00d41bb02d0` | `5b391168dcdc97d2b9de7643dab8c7d5b593045dce3fd263ff82e00d41bb02d0` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLERowBytes` | A15 `72b00ea26ea0ce803d8e5dc2f4560c2aebb56485b48b62cc8556e5ae69d205d9`<br>A61 `72b00ea26ea0ce803d8e5dc2f4560c2aebb56485b48b62cc8556e5ae69d205d9` | `72b00ea26ea0ce803d8e5dc2f4560c2aebb56485b48b62cc8556e5ae69d205d9` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLETextureRange` | A15 `411c77798e5ce72110ab8190c799abb6af645fc430f7c7b020846b6047e3b30b`<br>A61 `411c77798e5ce72110ab8190c799abb6af645fc430f7c7b020846b6047e3b30b` | `411c77798e5ce72110ab8190c799abb6af645fc430f7c7b020846b6047e3b30b` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEVertexArrayObject` | A15 `a5cca48e05580601928f74b38e5fc2a058712e940f8e2e3aad366a1061263c7c`<br>A61 `a5cca48e05580601928f74b38e5fc2a058712e940f8e2e3aad366a1061263c7c` | `a5cca48e05580601928f74b38e5fc2a058712e940f8e2e3aad366a1061263c7c` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEVertexArrayRange` | A15 `5466e8a728f3a6372b04f43cf7cd427bb4ae066865036a17299fca52f90f13b0`<br>A61 `5466e8a728f3a6372b04f43cf7cd427bb4ae066865036a17299fca52f90f13b0` | `5466e8a728f3a6372b04f43cf7cd427bb4ae066865036a17299fca52f90f13b0` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEVertexProgramEvaluators` | A15 `5ede5c9265d265d1f975ded58f25f2eb20b847dcfc730ab39be7e6d62837f585`<br>A61 `5ede5c9265d265d1f975ded58f25f2eb20b847dcfc730ab39be7e6d62837f585` | `5ede5c9265d265d1f975ded58f25f2eb20b847dcfc730ab39be7e6d62837f585` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.APPLEYcbcr422` | A15 `d463aed579eecdd11fd397673f2cc6834108f80e58c2c2a13745ef21e1bef9a7`<br>A61 `d463aed579eecdd11fd397673f2cc6834108f80e58c2c2a13745ef21e1bef9a7` | `d463aed579eecdd11fd397673f2cc6834108f80e58c2c2a13745ef21e1bef9a7` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBBaseInstance` | A15 `4de34d3852d8b0353d67cc1d6b2612dd399b4a4a5fe830536121418b3bb0616d`<br>A61 `4de34d3852d8b0353d67cc1d6b2612dd399b4a4a5fe830536121418b3bb0616d` | `4de34d3852d8b0353d67cc1d6b2612dd399b4a4a5fe830536121418b3bb0616d` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBBlendFuncExtended` | A15 `ca936ba1dd2f789e4731a0bd30c8bf5ef5dd858fdbc94007e3b4f2db3424ec11`<br>A61 `ca936ba1dd2f789e4731a0bd30c8bf5ef5dd858fdbc94007e3b4f2db3424ec11` | `ca936ba1dd2f789e4731a0bd30c8bf5ef5dd858fdbc94007e3b4f2db3424ec11` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBBufferObject` | A15 `c43ca7af3aa3881ff89e409a060128236eef991ce273908a2915e574555d5ba6`<br>A61 `7b275012b8427b3030d0b78d0d64f310de0e765d8087949ac82c6a72ce8422e7` | `c43ca7af3aa3881ff89e409a060128236eef991ce273908a2915e574555d5ba6` / A15 | COMPATIBLE | 13 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBCLEvent` | A15 `19430392599e91f7a11af9286a1714f1f14e816c27c2dcda4bbf0a1e56805ed3`<br>A61 `19430392599e91f7a11af9286a1714f1f14e816c27c2dcda4bbf0a1e56805ed3` | `19430392599e91f7a11af9286a1714f1f14e816c27c2dcda4bbf0a1e56805ed3` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBColorBufferFloat` | A15 `2b51501991568cc209ab3090493dcb570da0469d3f85fe2880a2d5687692355d`<br>A61 `2b51501991568cc209ab3090493dcb570da0469d3f85fe2880a2d5687692355d` | `2b51501991568cc209ab3090493dcb570da0469d3f85fe2880a2d5687692355d` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBCompressedTexturePixelStorage` | A15 `dd2366a6703f8a03e4d4299aeb471caf91e5ddebb1562e1fd1b4b190364032ef`<br>A61 `dd2366a6703f8a03e4d4299aeb471caf91e5ddebb1562e1fd1b4b190364032ef` | `dd2366a6703f8a03e4d4299aeb471caf91e5ddebb1562e1fd1b4b190364032ef` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBCopyBuffer` | A15 `7c625456e7184122b0a420b1c389253dcc4b789bda44a14fcfcc391279288c5d`<br>A61 `7c625456e7184122b0a420b1c389253dcc4b789bda44a14fcfcc391279288c5d` | `7c625456e7184122b0a420b1c389253dcc4b789bda44a14fcfcc391279288c5d` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBDebugOutput` | A15 `2c039eb6433d206816d3b2d8792cc1f7c9b14991e6bce8384a50e3955721654d`<br>A61 `2c039eb6433d206816d3b2d8792cc1f7c9b14991e6bce8384a50e3955721654d` | `2c039eb6433d206816d3b2d8792cc1f7c9b14991e6bce8384a50e3955721654d` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBDebugOutputCallback$1` | A15 `7b066399b3fd5d501e8c41d47bba98e26ed6c561b4efb8609be9adab1cedbe23`<br>A61 `310e34a974701f8f4023d58917fd87b69088fb9dd0ff2603416ec510fcb95337` | `7b066399b3fd5d501e8c41d47bba98e26ed6c561b4efb8609be9adab1cedbe23` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBDebugOutputCallback$Handler` | A15 `7b1817e770d944d33d8481e68bb13c742426934d1e17b980f79876a328efb184`<br>A61 `7b1817e770d944d33d8481e68bb13c742426934d1e17b980f79876a328efb184` | `7b1817e770d944d33d8481e68bb13c742426934d1e17b980f79876a328efb184` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBDebugOutputCallback` | A15 `3a94beb9986ac3cb5be3f2119210a7cbf5f65921a6cbe8aee7289eb63715900e`<br>A61 `dca59eb0065189c5ab76cd6b38f967a4c2197f4613bdbda0f99ebfa15be2437e` | `3a94beb9986ac3cb5be3f2119210a7cbf5f65921a6cbe8aee7289eb63715900e` / A15 | COMPATIBLE | 5 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBDepthBufferFloat` | A15 `f858abae5a624387c528ea69b12ee49bdd1280c99da815a5d07e32d660c10e51`<br>A61 `f858abae5a624387c528ea69b12ee49bdd1280c99da815a5d07e32d660c10e51` | `f858abae5a624387c528ea69b12ee49bdd1280c99da815a5d07e32d660c10e51` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBDepthClamp` | A15 `72feceaedcbd1777e270c70ec9da6704749052f1b62beac4d8c59a692af3d00f`<br>A61 `72feceaedcbd1777e270c70ec9da6704749052f1b62beac4d8c59a692af3d00f` | `72feceaedcbd1777e270c70ec9da6704749052f1b62beac4d8c59a692af3d00f` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBDepthTexture` | A15 `efde91bb11b897ba11fcac85de2b88f2bd6a729d0b89e723009659c1bce7f8e3`<br>A61 `efde91bb11b897ba11fcac85de2b88f2bd6a729d0b89e723009659c1bce7f8e3` | `efde91bb11b897ba11fcac85de2b88f2bd6a729d0b89e723009659c1bce7f8e3` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBDrawBuffers` | A15 `7fa3e217e63ceb7b64add8b845d49892c82f159cd1a038a2e550b40002e989ef`<br>A61 `7fa3e217e63ceb7b64add8b845d49892c82f159cd1a038a2e550b40002e989ef` | `7fa3e217e63ceb7b64add8b845d49892c82f159cd1a038a2e550b40002e989ef` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBDrawBuffersBlend` | A15 `7c01935a157884ffb0c67a4be83c538789e2a4891fa188f7290293b42d818017`<br>A61 `7c01935a157884ffb0c67a4be83c538789e2a4891fa188f7290293b42d818017` | `7c01935a157884ffb0c67a4be83c538789e2a4891fa188f7290293b42d818017` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBDrawElementsBaseVertex` | A15 `947a94ef498b8eae23f992aed0b6ff6eb907c941af98e2142fc9327c69254c0c`<br>A61 `947a94ef498b8eae23f992aed0b6ff6eb907c941af98e2142fc9327c69254c0c` | `947a94ef498b8eae23f992aed0b6ff6eb907c941af98e2142fc9327c69254c0c` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBDrawIndirect` | A15 `c20995cccced6fe7185dcbc42974b7c40ef826650bdac76c96ef9e9401bf5ba2`<br>A61 `8f739626bb07460e965671aec33d8d45c3b8ed1c95699d6798864481e4d28310` | `c20995cccced6fe7185dcbc42974b7c40ef826650bdac76c96ef9e9401bf5ba2` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBDrawInstanced` | A15 `d9d9349806f29a0a28004c3185b5d47dfc66e16783ee54335c90b409d6c00812`<br>A61 `d9d9349806f29a0a28004c3185b5d47dfc66e16783ee54335c90b409d6c00812` | `d9d9349806f29a0a28004c3185b5d47dfc66e16783ee54335c90b409d6c00812` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBES2Compatibility` | A15 `e8f7440286183b3df7e1cd29e66094e79c74975a91d5cd332dfcd2a937215bfe`<br>A61 `4926d4379f13efeca54bdb818ea88cc9b82afe8350430da59d4b7d2072f1406b` | `e8f7440286183b3df7e1cd29e66094e79c74975a91d5cd332dfcd2a937215bfe` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBFragmentProgram` | A15 `665f7a8b8a8de60f340fbee8e3e383ef9fcb57a08130a10c7cad72b37144c7ae`<br>A61 `665f7a8b8a8de60f340fbee8e3e383ef9fcb57a08130a10c7cad72b37144c7ae` | `665f7a8b8a8de60f340fbee8e3e383ef9fcb57a08130a10c7cad72b37144c7ae` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBFragmentShader` | A15 `72286687b200cef3517889fe9de6d32ada56ffbfba431a2b39201fd23052a7bb`<br>A61 `72286687b200cef3517889fe9de6d32ada56ffbfba431a2b39201fd23052a7bb` | `72286687b200cef3517889fe9de6d32ada56ffbfba431a2b39201fd23052a7bb` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBFramebufferObject` | A15 `e28121d09e749a013825c53a06cf18997ea0a2b589b4d107d1c447af93ecd6eb`<br>A61 `19ee3b68405d5f7c06026e2c09917271e4548d293f49f2f42d69ec538ed21e1d` | `e28121d09e749a013825c53a06cf18997ea0a2b589b4d107d1c447af93ecd6eb` / A15 | COMPATIBLE | 12 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBFramebufferSRGB` | A15 `bd7ca0eb2c92340924dde9d4bb87ffd1c7f75a960157f11234656ede0688e273`<br>A61 `bd7ca0eb2c92340924dde9d4bb87ffd1c7f75a960157f11234656ede0688e273` | `bd7ca0eb2c92340924dde9d4bb87ffd1c7f75a960157f11234656ede0688e273` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBGeometryShader4` | A15 `a9ca35538b5118bf1fcf216bd9ac8ba32a73f576670fc263ca9a8172caf96b3f`<br>A61 `a9ca35538b5118bf1fcf216bd9ac8ba32a73f576670fc263ca9a8172caf96b3f` | `a9ca35538b5118bf1fcf216bd9ac8ba32a73f576670fc263ca9a8172caf96b3f` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBGetProgramBinary` | A15 `c23ae708628e97c6caf1c8899edc2e6b2573d563bc3fa5c0f63d648888f2c534`<br>A61 `c23ae708628e97c6caf1c8899edc2e6b2573d563bc3fa5c0f63d648888f2c534` | `c23ae708628e97c6caf1c8899edc2e6b2573d563bc3fa5c0f63d648888f2c534` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBGpuShader5` | A15 `56b13dc4dcc91dda6f69e30244440410b10b14a23c867cb46231bbea6f285526`<br>A61 `56b13dc4dcc91dda6f69e30244440410b10b14a23c867cb46231bbea6f285526` | `56b13dc4dcc91dda6f69e30244440410b10b14a23c867cb46231bbea6f285526` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBGpuShaderFp64` | A15 `00661c7bdaaf17c2a469b902860c5ad433afbe61f3d212e048cdcdeca59f11d8`<br>A61 `f87b2ba5a79603dd316f23f933b48a3ce38e6f05afd6231495f6a245b4dd73d0` | `00661c7bdaaf17c2a469b902860c5ad433afbe61f3d212e048cdcdeca59f11d8` / A15 | COMPATIBLE | 17 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBHalfFloatPixel` | A15 `f1cea9c5e8e133fc334e923a74b8e3a24e1a39dfa8bce7221a79772fa4f27a2d`<br>A61 `f1cea9c5e8e133fc334e923a74b8e3a24e1a39dfa8bce7221a79772fa4f27a2d` | `f1cea9c5e8e133fc334e923a74b8e3a24e1a39dfa8bce7221a79772fa4f27a2d` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBHalfFloatVertex` | A15 `145fc4d64fa27a76b012cb208324ba944be394ad46e236f2537cec44729a58b8`<br>A61 `145fc4d64fa27a76b012cb208324ba944be394ad46e236f2537cec44729a58b8` | `145fc4d64fa27a76b012cb208324ba944be394ad46e236f2537cec44729a58b8` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBImaging` | A15 `eb83a0c3e86bd3b6d6490a0de2653b93fac94ac03a4a05aa1559b4e496ce93f6`<br>A61 `829b4700074dc822d57a2dd7b59371a12ade0409f59bb29aa4f669209cbe5e78` | `eb83a0c3e86bd3b6d6490a0de2653b93fac94ac03a4a05aa1559b4e496ce93f6` / A15 | COMPATIBLE | 41 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBInstancedArrays` | A15 `24830718e8c8470421153c37119316e4c0f7571d3dbdcc8547bcecb8d8b5d90f`<br>A61 `24830718e8c8470421153c37119316e4c0f7571d3dbdcc8547bcecb8d8b5d90f` | `24830718e8c8470421153c37119316e4c0f7571d3dbdcc8547bcecb8d8b5d90f` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBInternalformatQuery` | A15 `e7e3b52a0d0bdbf8e4397e16bca32457c86edcd0efa08fca582e5196eabe0f32`<br>A61 `e7e3b52a0d0bdbf8e4397e16bca32457c86edcd0efa08fca582e5196eabe0f32` | `e7e3b52a0d0bdbf8e4397e16bca32457c86edcd0efa08fca582e5196eabe0f32` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBMapBufferAlignment` | A15 `be355fb30661e02a99e84c1aefb75cdfac62b7cfc3b1fd94480d4c50d22daf78`<br>A61 `be355fb30661e02a99e84c1aefb75cdfac62b7cfc3b1fd94480d4c50d22daf78` | `be355fb30661e02a99e84c1aefb75cdfac62b7cfc3b1fd94480d4c50d22daf78` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBMapBufferRange` | A15 `ca14e7019b012a89e621b09e02dd645f3b91b1990dbd9e44a09d2dc832fc7a5e`<br>A61 `ca14e7019b012a89e621b09e02dd645f3b91b1990dbd9e44a09d2dc832fc7a5e` | `ca14e7019b012a89e621b09e02dd645f3b91b1990dbd9e44a09d2dc832fc7a5e` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBMatrixPalette` | A15 `1111e12222761da518f6ba0189d321e74a9d12a20ad804f3b0cce5422b756c4d`<br>A61 `1111e12222761da518f6ba0189d321e74a9d12a20ad804f3b0cce5422b756c4d` | `1111e12222761da518f6ba0189d321e74a9d12a20ad804f3b0cce5422b756c4d` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBMultisample` | A15 `841c81cb050ff7ebbe83f74696eb7b585c152e219df35429f16fe2607c373071`<br>A61 `841c81cb050ff7ebbe83f74696eb7b585c152e219df35429f16fe2607c373071` | `841c81cb050ff7ebbe83f74696eb7b585c152e219df35429f16fe2607c373071` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBMultitexture` | A15 `f73b524a62e1dbf50816c17a464da9e3c439b3c6effbd818599c75867db28f72`<br>A61 `f73b524a62e1dbf50816c17a464da9e3c439b3c6effbd818599c75867db28f72` | `f73b524a62e1dbf50816c17a464da9e3c439b3c6effbd818599c75867db28f72` / A15, A61 | COMPATIBLE | 21 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBOcclusionQuery` | A15 `6de92049b8194da23f9b8b9ccdd5c94c64fe21c1ac3663380360de959b8b876a`<br>A61 `4245f40b40b3b49a01a10670f16af4900ce2ae323b6da7cbeaa16cd5f626869a` | `6de92049b8194da23f9b8b9ccdd5c94c64fe21c1ac3663380360de959b8b876a` / A15 | COMPATIBLE | 9 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBOcclusionQuery2` | A15 `390a4aef94c6c629a4c59044fa0437abc1e59982e91bbe7252eb7629deda5ea2`<br>A61 `390a4aef94c6c629a4c59044fa0437abc1e59982e91bbe7252eb7629deda5ea2` | `390a4aef94c6c629a4c59044fa0437abc1e59982e91bbe7252eb7629deda5ea2` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBPixelBufferObject` | A15 `8fd42141872bbd3f59d54c697009d3f1348c8ea3d9ba088c54c952e3fa463222`<br>A61 `8fd42141872bbd3f59d54c697009d3f1348c8ea3d9ba088c54c952e3fa463222` | `8fd42141872bbd3f59d54c697009d3f1348c8ea3d9ba088c54c952e3fa463222` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBPointParameters` | A15 `ededdf026badfe1294619939783e979078ee55e0756350add1768f37e79561c2`<br>A61 `ededdf026badfe1294619939783e979078ee55e0756350add1768f37e79561c2` | `ededdf026badfe1294619939783e979078ee55e0756350add1768f37e79561c2` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBPointSprite` | A15 `c33e82ea6aadf74dd67e422a769eb73eace253fadf702bb7b22d73523cdde278`<br>A61 `c33e82ea6aadf74dd67e422a769eb73eace253fadf702bb7b22d73523cdde278` | `c33e82ea6aadf74dd67e422a769eb73eace253fadf702bb7b22d73523cdde278` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBProgram` | A15 `ef3cef2f29d5653ba6565c5ea67ee33a84820ce2bd67110014e00b07a78c294f`<br>A61 `4d15a22e41fb8312ed5b3902472b1661ccadb6b1e34bdc1b1064e72f9a430a5a` | `ef3cef2f29d5653ba6565c5ea67ee33a84820ce2bd67110014e00b07a78c294f` / A15 | COMPATIBLE | 21 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBProvokingVertex` | A15 `934441da13e28e0d67f4a0b7d43433279a7578c6ba4f501cea0e01405627f9c8`<br>A61 `934441da13e28e0d67f4a0b7d43433279a7578c6ba4f501cea0e01405627f9c8` | `934441da13e28e0d67f4a0b7d43433279a7578c6ba4f501cea0e01405627f9c8` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBRobustness` | A15 `0f040497e346a7546861c4799c3eb1c4b1143c53b8d79383411320ed8b030dba`<br>A61 `a249b85a451307676333d2ebd8c4fd9807356d935d6a893e1a679b043fd57cc9` | `0f040497e346a7546861c4799c3eb1c4b1143c53b8d79383411320ed8b030dba` / A15 | COMPATIBLE | 27 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBSampleShading` | A15 `c49d53165075ece903bbaad01f85d736a1c9902a454f75cfedcd7a223f7a22f2`<br>A61 `c49d53165075ece903bbaad01f85d736a1c9902a454f75cfedcd7a223f7a22f2` | `c49d53165075ece903bbaad01f85d736a1c9902a454f75cfedcd7a223f7a22f2` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBSamplerObjects` | A15 `a503b15ab84607eab219bedcfbe255706d18f2d17a8acfe6d0a0a9222946a289`<br>A61 `a503b15ab84607eab219bedcfbe255706d18f2d17a8acfe6d0a0a9222946a289` | `a503b15ab84607eab219bedcfbe255706d18f2d17a8acfe6d0a0a9222946a289` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBSeamlessCubeMap` | A15 `12f27171686c314ce038b95e1e40572bf92a55847e14a8a17135bea3f1f82b98`<br>A61 `12f27171686c314ce038b95e1e40572bf92a55847e14a8a17135bea3f1f82b98` | `12f27171686c314ce038b95e1e40572bf92a55847e14a8a17135bea3f1f82b98` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBSeparateShaderObjects` | A15 `7b489258b1d0b198820f9b171a29c94cbbc5397edf4ae845d7aeb37f7a46456e`<br>A61 `312a25def7b1a38d0bb420de0335ebbc85053ffb98cc32f03c2b199892085758` | `7b489258b1d0b198820f9b171a29c94cbbc5397edf4ae845d7aeb37f7a46456e` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBShaderAtomicCounters` | A15 `91ac40f8a675813225be9dfb9effdc789bb7152c53a1aeb77826edfac3bb128d`<br>A61 `91ac40f8a675813225be9dfb9effdc789bb7152c53a1aeb77826edfac3bb128d` | `91ac40f8a675813225be9dfb9effdc789bb7152c53a1aeb77826edfac3bb128d` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBShaderImageLoadStore` | A15 `c10284b919670f6a15fe034b18082876521df47febee48054a2e8262f3176542`<br>A61 `c10284b919670f6a15fe034b18082876521df47febee48054a2e8262f3176542` | `c10284b919670f6a15fe034b18082876521df47febee48054a2e8262f3176542` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBShaderObjects` | A15 `d9246660492f16ae07f354d916e1624f3ad6e84f00cdedd3d4a2a03012f32e68`<br>A61 `c9528aa832e546da2dc79375611dc88afcbe776c2aafaae34431e0e878b475c1` | `d9246660492f16ae07f354d916e1624f3ad6e84f00cdedd3d4a2a03012f32e68` / A15 | COMPATIBLE | 74 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBShaderSubroutine` | A15 `2207737870ad29ac28ca64e0b94575173b5b19d272e3ea75ae07924a22b6e126`<br>A61 `e8c8e3d971ee86e0c02b6b3c07ccbfd33cefd63e49f1fd0ed3656e8c2a4661eb` | `2207737870ad29ac28ca64e0b94575173b5b19d272e3ea75ae07924a22b6e126` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBShadingLanguage100` | A15 `0f6fc44832b912d3ef835fe20e5dc845976ebb6b59f6d21b4acee6b9fd52b917`<br>A61 `0f6fc44832b912d3ef835fe20e5dc845976ebb6b59f6d21b4acee6b9fd52b917` | `0f6fc44832b912d3ef835fe20e5dc845976ebb6b59f6d21b4acee6b9fd52b917` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBShadingLanguageInclude` | A15 `1e0e13363f77a927e2af425365d91933e17a5e10792d6da27c8847ed11f6c2da`<br>A61 `1e0e13363f77a927e2af425365d91933e17a5e10792d6da27c8847ed11f6c2da` | `1e0e13363f77a927e2af425365d91933e17a5e10792d6da27c8847ed11f6c2da` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBShadow` | A15 `0adae2cd4e33e939396ab44b70074c9c483e4c886b0283a38946f0f76f7ae0b5`<br>A61 `0adae2cd4e33e939396ab44b70074c9c483e4c886b0283a38946f0f76f7ae0b5` | `0adae2cd4e33e939396ab44b70074c9c483e4c886b0283a38946f0f76f7ae0b5` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBShadowAmbient` | A15 `9cd3f28b9000a0a4bd213e2ef14424c308189f532c775a9a76abf75c0051269a`<br>A61 `9cd3f28b9000a0a4bd213e2ef14424c308189f532c775a9a76abf75c0051269a` | `9cd3f28b9000a0a4bd213e2ef14424c308189f532c775a9a76abf75c0051269a` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBSync` | A15 `4c6e4651ebaecd8d38f57b71cff01efa8da5b113bcd1f0f369f3c1c0eb68daa2`<br>A61 `8f5c684ea331e4593aceb6ac3e4f2747b40d6057fbf2c9365c324c0965f33094` | `4c6e4651ebaecd8d38f57b71cff01efa8da5b113bcd1f0f369f3c1c0eb68daa2` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBTessellationShader` | A15 `fe88500aa7f3b023b93957b16f1449e95271aa0c380f0b905b7624dde69a55d9`<br>A61 `6d6f7bacb72849dedd36af42c4fe94cd851e1e3b5038fb1eac82e677d28c52e0` | `fe88500aa7f3b023b93957b16f1449e95271aa0c380f0b905b7624dde69a55d9` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBTextureBorderClamp` | A15 `a7b593ec8756be6e6594f59cc0d5d58f35846cc226cbb088e585e4a293a75359`<br>A61 `a7b593ec8756be6e6594f59cc0d5d58f35846cc226cbb088e585e4a293a75359` | `a7b593ec8756be6e6594f59cc0d5d58f35846cc226cbb088e585e4a293a75359` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureBufferObject` | A15 `04124607a808e34d5793149dfd7dffe80e28c60f39f613097cd8375ce6d5f7fb`<br>A61 `04124607a808e34d5793149dfd7dffe80e28c60f39f613097cd8375ce6d5f7fb` | `04124607a808e34d5793149dfd7dffe80e28c60f39f613097cd8375ce6d5f7fb` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureCompression` | A15 `9adcb30c218576a63b28b9273894a15dba7fbd35bead4a9eba9cae3420cf95a1`<br>A61 `9adcb30c218576a63b28b9273894a15dba7fbd35bead4a9eba9cae3420cf95a1` | `9adcb30c218576a63b28b9273894a15dba7fbd35bead4a9eba9cae3420cf95a1` / A15, A61 | COMPATIBLE | 14 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureCompressionBPTC` | A15 `9a15d57e16184a0b56dc6c0da6002acfebdfca0f41935b43f330d872a2e953db`<br>A61 `9a15d57e16184a0b56dc6c0da6002acfebdfca0f41935b43f330d872a2e953db` | `9a15d57e16184a0b56dc6c0da6002acfebdfca0f41935b43f330d872a2e953db` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureCompressionRGTC` | A15 `b27e5584eb8f779b87d43b34e67ec4df8fe7431a1624c12bdf59e09a9ec81490`<br>A61 `d410728a51dd1f3552c57d18d96bd22f31e2e0b9303323b01a17701737af5f52` | `b27e5584eb8f779b87d43b34e67ec4df8fe7431a1624c12bdf59e09a9ec81490` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBTextureCubeMap` | A15 `476e9c5c5855667d413cc9769e6503e3f54891c806cb7c3d7c4166f8fc96022a`<br>A61 `476e9c5c5855667d413cc9769e6503e3f54891c806cb7c3d7c4166f8fc96022a` | `476e9c5c5855667d413cc9769e6503e3f54891c806cb7c3d7c4166f8fc96022a` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureCubeMapArray` | A15 `8ac70d2d08f312d1bad8879b920b6976769d9f43dcf489d40d4f53c6f5a57f05`<br>A61 `8ac70d2d08f312d1bad8879b920b6976769d9f43dcf489d40d4f53c6f5a57f05` | `8ac70d2d08f312d1bad8879b920b6976769d9f43dcf489d40d4f53c6f5a57f05` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureEnvCombine` | A15 `db655b78bd8eb9b6edc0218716eafdde475272a76bb9b1b7dd2a10b92bd99fa7`<br>A61 `db655b78bd8eb9b6edc0218716eafdde475272a76bb9b1b7dd2a10b92bd99fa7` | `db655b78bd8eb9b6edc0218716eafdde475272a76bb9b1b7dd2a10b92bd99fa7` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureEnvDot3` | A15 `3248416c231b08829e1704e2ac811ea8ea23dbe4f67a76bd6d2296ee0e347ef7`<br>A61 `3248416c231b08829e1704e2ac811ea8ea23dbe4f67a76bd6d2296ee0e347ef7` | `3248416c231b08829e1704e2ac811ea8ea23dbe4f67a76bd6d2296ee0e347ef7` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureFloat` | A15 `1433abe9fdbe60c96f59a543ff0bed3cfa783507a4e9cf8b05064b5199726f42`<br>A61 `1433abe9fdbe60c96f59a543ff0bed3cfa783507a4e9cf8b05064b5199726f42` | `1433abe9fdbe60c96f59a543ff0bed3cfa783507a4e9cf8b05064b5199726f42` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureGather` | A15 `ea420d6f5367feefa44725deb7e1c5308de2f26a9a466029198f4f0ff40e5040`<br>A61 `ea420d6f5367feefa44725deb7e1c5308de2f26a9a466029198f4f0ff40e5040` | `ea420d6f5367feefa44725deb7e1c5308de2f26a9a466029198f4f0ff40e5040` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureMirroredRepeat` | A15 `bf6d774506491a312fdc979874e4a16a46c3953dcece906ec6742111fab7395f`<br>A61 `bf6d774506491a312fdc979874e4a16a46c3953dcece906ec6742111fab7395f` | `bf6d774506491a312fdc979874e4a16a46c3953dcece906ec6742111fab7395f` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureMultisample` | A15 `c3a5ba022b5b972db8fbc234f51dad77c0a3db80c8b95c613b535fc09e9c1616`<br>A61 `c3a5ba022b5b972db8fbc234f51dad77c0a3db80c8b95c613b535fc09e9c1616` | `c3a5ba022b5b972db8fbc234f51dad77c0a3db80c8b95c613b535fc09e9c1616` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureRGB10_A2UI` | A15 `d6e36380ed397182d7721835061e4eb8bf17f1066f619b1976fbc2bc53f3e535`<br>A61 `d6e36380ed397182d7721835061e4eb8bf17f1066f619b1976fbc2bc53f3e535` | `d6e36380ed397182d7721835061e4eb8bf17f1066f619b1976fbc2bc53f3e535` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureRectangle` | A15 `6845bfadee868669800457e3791f0907db089f0410d32e89e9d195a081d617b3`<br>A61 `6845bfadee868669800457e3791f0907db089f0410d32e89e9d195a081d617b3` | `6845bfadee868669800457e3791f0907db089f0410d32e89e9d195a081d617b3` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureRg` | A15 `35016d6570055f1113f5af66f9435d9a9c3cd42505a7e16aa284edf7fb54c8f1`<br>A61 `b78525c4e26340442796280790d20da9ed0d2e1ee2cd244befbe317eb4006a7e` | `35016d6570055f1113f5af66f9435d9a9c3cd42505a7e16aa284edf7fb54c8f1` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBTextureStorage` | A15 `d754157d523a1743a6be11cab3939391017bcc65be217ebfcf50239f0feac2a8`<br>A61 `d754157d523a1743a6be11cab3939391017bcc65be217ebfcf50239f0feac2a8` | `d754157d523a1743a6be11cab3939391017bcc65be217ebfcf50239f0feac2a8` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTextureSwizzle` | A15 `73ed8086ffb0faa468766113d05edaa1f966056dffffa17c23bb94d093d15c90`<br>A61 `73ed8086ffb0faa468766113d05edaa1f966056dffffa17c23bb94d093d15c90` | `73ed8086ffb0faa468766113d05edaa1f966056dffffa17c23bb94d093d15c90` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTimerQuery` | A15 `d890a52df49bd8a8b660b044f545a92282da25004c19ed3a3eef7ef181cc2c59`<br>A61 `4384d2ee7997c6b6a4e6a43dd7aefbce42631e52081a89f146c7722d8e56c287` | `d890a52df49bd8a8b660b044f545a92282da25004c19ed3a3eef7ef181cc2c59` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBTransformFeedback2` | A15 `b11a9fb602a7599cb3894fd2c2a085dd43bd7277f3d15673483eb559be744ebb`<br>A61 `b11a9fb602a7599cb3894fd2c2a085dd43bd7277f3d15673483eb559be744ebb` | `b11a9fb602a7599cb3894fd2c2a085dd43bd7277f3d15673483eb559be744ebb` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTransformFeedback3` | A15 `570cb08b39f535c74a4f04f513f1e1872d37f995f1e098a0c722f01815d21773`<br>A61 `01b6aecd2be5e53310d41c5cb9acb6caf8abd3df41bc337bbcf17b4eaaac540d` | `570cb08b39f535c74a4f04f513f1e1872d37f995f1e098a0c722f01815d21773` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBTransformFeedbackInstanced` | A15 `2b7d3e795c17c5b3775755a902ad87fc6b718b575473ae77ca1d506e0c03a72b`<br>A61 `2b7d3e795c17c5b3775755a902ad87fc6b718b575473ae77ca1d506e0c03a72b` | `2b7d3e795c17c5b3775755a902ad87fc6b718b575473ae77ca1d506e0c03a72b` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBTransposeMatrix` | A15 `07253403b275d9de13924489ea8fbf98e630dd86c13b5082fd791830af89633f`<br>A61 `07253403b275d9de13924489ea8fbf98e630dd86c13b5082fd791830af89633f` | `07253403b275d9de13924489ea8fbf98e630dd86c13b5082fd791830af89633f` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBUniformBufferObject` | A15 `6a6749a13b87fbfd743ca174ae6b5137b3f6b4b872f3a7e12135128d22637881`<br>A61 `f64bbbbc60a4002879a0f4bb4919f9581581f8b76776868d39bf76714a2a5783` | `6a6749a13b87fbfd743ca174ae6b5137b3f6b4b872f3a7e12135128d22637881` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBVertexArrayBgra` | A15 `3a618bb0fb75653b34623c6cc8dbbfbf1789766e8c37b9dbb28fdcad48c5adac`<br>A61 `3a618bb0fb75653b34623c6cc8dbbfbf1789766e8c37b9dbb28fdcad48c5adac` | `3a618bb0fb75653b34623c6cc8dbbfbf1789766e8c37b9dbb28fdcad48c5adac` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBVertexArrayObject` | A15 `9046fc6782fadb5bd88791b653615097efb4bbfbf3242f530fad71d7a9ab1a7a`<br>A61 `9046fc6782fadb5bd88791b653615097efb4bbfbf3242f530fad71d7a9ab1a7a` | `9046fc6782fadb5bd88791b653615097efb4bbfbf3242f530fad71d7a9ab1a7a` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBVertexAttrib64bit` | A15 `51c9890f94772ada531a83d4a93f9125778a82fcc49aa6c89b53ce84f74b2c53`<br>A61 `51c9890f94772ada531a83d4a93f9125778a82fcc49aa6c89b53ce84f74b2c53` | `51c9890f94772ada531a83d4a93f9125778a82fcc49aa6c89b53ce84f74b2c53` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBVertexBlend` | A15 `bfa7220ea7cc7619c565cb24595aa6d4d9451e9d156b3e0fcb3155f66359d54f`<br>A61 `bfa7220ea7cc7619c565cb24595aa6d4d9451e9d156b3e0fcb3155f66359d54f` | `bfa7220ea7cc7619c565cb24595aa6d4d9451e9d156b3e0fcb3155f66359d54f` / A15, A61 | COMPATIBLE | 11 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBVertexBufferObject` | A15 `851b0beaee010a731c78a2de32c44af66a7eed857940fdecc6db56d022edcb0a`<br>A61 `851b0beaee010a731c78a2de32c44af66a7eed857940fdecc6db56d022edcb0a` | `851b0beaee010a731c78a2de32c44af66a7eed857940fdecc6db56d022edcb0a` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBVertexProgram` | A15 `fa103e8367e6c103d41fa34af36d974b7ae7370c43bf1c3fb4b8167a8dfa1c94`<br>A61 `fa103e8367e6c103d41fa34af36d974b7ae7370c43bf1c3fb4b8167a8dfa1c94` | `fa103e8367e6c103d41fa34af36d974b7ae7370c43bf1c3fb4b8167a8dfa1c94` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBVertexShader` | A15 `9ebb27e8755dd19fa36e04e74229aa7a8051ca6671af7232b064cf000b852448`<br>A61 `55ced8d475283ab9a90cec968208d1a9ec570c67fb7b77b8edf62419768fe7ef` | `9ebb27e8755dd19fa36e04e74229aa7a8051ca6671af7232b064cf000b852448` / A15 | COMPATIBLE | 51 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBVertexType2_10_10_10_REV` | A15 `194549bee3cb30a901cdbcdcbb8f7aead934208ae2044be6eb2042331f848e3c`<br>A61 `194549bee3cb30a901cdbcdcbb8f7aead934208ae2044be6eb2042331f848e3c` | `194549bee3cb30a901cdbcdcbb8f7aead934208ae2044be6eb2042331f848e3c` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ARBViewportArray` | A15 `d24d7ebfaccc4483322400a0792624c2aecf4753b4f3c12244e9b155b92b8364`<br>A61 `55a9668de0f067b553fdb0425bef499fae9189128cea5994a4f4672363dca117` | `d24d7ebfaccc4483322400a0792624c2aecf4753b4f3c12244e9b155b92b8364` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ARBWindowPos` | A15 `228d24ccd46c83677a1f05434d5a66db9581cdece7d55a1068bef8031e30a37b`<br>A61 `228d24ccd46c83677a1f05434d5a66db9581cdece7d55a1068bef8031e30a37b` | `228d24ccd46c83677a1f05434d5a66db9581cdece7d55a1068bef8031e30a37b` / A15, A61 | COMPATIBLE | 8 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATIDrawBuffers` | A15 `ce66c6b1c39a2983e4e5743cdcfbe5b9db3156b004d4625d8b4e4c9e6e112876`<br>A61 `ce66c6b1c39a2983e4e5743cdcfbe5b9db3156b004d4625d8b4e4c9e6e112876` | `ce66c6b1c39a2983e4e5743cdcfbe5b9db3156b004d4625d8b4e4c9e6e112876` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATIElementArray` | A15 `c5c7b9e4e953f8703db71aafafcc50eb118c33dfa97ea11887d60ba8e5d9bfed`<br>A61 `c5c7b9e4e953f8703db71aafafcc50eb118c33dfa97ea11887d60ba8e5d9bfed` | `c5c7b9e4e953f8703db71aafafcc50eb118c33dfa97ea11887d60ba8e5d9bfed` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATIEnvmapBumpmap` | A15 `a8bc647a903cbf103f190fb2966a538b7ca6f942c684e9f051f9aae707915162`<br>A61 `a8bc647a903cbf103f190fb2966a538b7ca6f942c684e9f051f9aae707915162` | `a8bc647a903cbf103f190fb2966a538b7ca6f942c684e9f051f9aae707915162` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATIFragmentShader` | A15 `332da57c73ed37a65f534d50449e415bde0dcf0c47a2a3c0f1d1a63fc40f4326`<br>A61 `332da57c73ed37a65f534d50449e415bde0dcf0c47a2a3c0f1d1a63fc40f4326` | `332da57c73ed37a65f534d50449e415bde0dcf0c47a2a3c0f1d1a63fc40f4326` / A15, A61 | COMPATIBLE | 14 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATIMapObjectBuffer` | A15 `939701d7a0d54b6771cc8966e517892d85f216c46c4f06359a0c9cbfdca77f36`<br>A61 `a112c11184d25380e9850a3148c4e72b1f3a6209f938fe4d240ff1f9b6f70e4d` | `939701d7a0d54b6771cc8966e517892d85f216c46c4f06359a0c9cbfdca77f36` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ATIMeminfo` | A15 `b089be7c0178abd20dce6d714b15af0ec384b4a38ee276e973a65418f5d989cb`<br>A61 `b089be7c0178abd20dce6d714b15af0ec384b4a38ee276e973a65418f5d989cb` | `b089be7c0178abd20dce6d714b15af0ec384b4a38ee276e973a65418f5d989cb` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATIPnTriangles` | A15 `5f145201498861f0925ac69236fe8d56d8a1eb9c3df594b9de50777fffbe8f28`<br>A61 `5f145201498861f0925ac69236fe8d56d8a1eb9c3df594b9de50777fffbe8f28` | `5f145201498861f0925ac69236fe8d56d8a1eb9c3df594b9de50777fffbe8f28` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATISeparateStencil` | A15 `d8bd86c7bb42b864a7ff2407f7e59577c29a6d7a46892e6b784e7ba133e9f5d1`<br>A61 `d8bd86c7bb42b864a7ff2407f7e59577c29a6d7a46892e6b784e7ba133e9f5d1` | `d8bd86c7bb42b864a7ff2407f7e59577c29a6d7a46892e6b784e7ba133e9f5d1` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATITextFragmentShader` | A15 `8dda384890d6224d9ab885ed374885291cb5858a7c7e9c34720e7c09c177b8ae`<br>A61 `8dda384890d6224d9ab885ed374885291cb5858a7c7e9c34720e7c09c177b8ae` | `8dda384890d6224d9ab885ed374885291cb5858a7c7e9c34720e7c09c177b8ae` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATITextureCompression3DC` | A15 `3d4986d1419498069d2c93d43f387e25b1adcdaa9c8648627173e335e4004c31`<br>A61 `3d4986d1419498069d2c93d43f387e25b1adcdaa9c8648627173e335e4004c31` | `3d4986d1419498069d2c93d43f387e25b1adcdaa9c8648627173e335e4004c31` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATITextureEnvCombine3` | A15 `83ef722a81f8093e78204dc23d0aa8d1fbc4a5d90a0ac8c26652f2e387722dde`<br>A61 `83ef722a81f8093e78204dc23d0aa8d1fbc4a5d90a0ac8c26652f2e387722dde` | `83ef722a81f8093e78204dc23d0aa8d1fbc4a5d90a0ac8c26652f2e387722dde` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATITextureFloat` | A15 `13a9741c966ed6c169ecf615c430038829878af83f645c3d4ff6211548a68704`<br>A61 `13a9741c966ed6c169ecf615c430038829878af83f645c3d4ff6211548a68704` | `13a9741c966ed6c169ecf615c430038829878af83f645c3d4ff6211548a68704` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATITextureMirrorOnce` | A15 `f940e6cf99bd7bb805a6bc387bcccfde21a6dbb698d69021f5aefa0d846ef940`<br>A61 `f940e6cf99bd7bb805a6bc387bcccfde21a6dbb698d69021f5aefa0d846ef940` | `f940e6cf99bd7bb805a6bc387bcccfde21a6dbb698d69021f5aefa0d846ef940` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATIVertexArrayObject` | A15 `09ef69c75c828271b4032dd58ab6572c9e3e7e3bdc2474c386804057bff95e65`<br>A61 `0ae23446a899ffdeee6e593e77bb9a3083c3901fda30bffdbe1d499f9c019740` | `09ef69c75c828271b4032dd58ab6572c9e3e7e3bdc2474c386804057bff95e65` / A15 | COMPATIBLE | 13 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ATIVertexAttribArrayObject` | A15 `b60adc3cdd9823d8729a1769e7cc65cd950b02be0ebf7542f2b645b46bcf256f`<br>A61 `b60adc3cdd9823d8729a1769e7cc65cd950b02be0ebf7542f2b645b46bcf256f` | `b60adc3cdd9823d8729a1769e7cc65cd950b02be0ebf7542f2b645b46bcf256f` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ATIVertexStreams` | A15 `26a3ba8315518362071618ef6c706352192a8189d7ae131715ee75592aeb0a94`<br>A61 `26a3ba8315518362071618ef6c706352192a8189d7ae131715ee75592aeb0a94` | `26a3ba8315518362071618ef6c706352192a8189d7ae131715ee75592aeb0a94` / A15, A61 | COMPATIBLE | 20 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AWTCanvasImplementation` | A15 `a63a6ce6711fe46186ee910c4435581ccab429af10f28edff8b387e062200453`<br>A61 `a63a6ce6711fe46186ee910c4435581ccab429af10f28edff8b387e062200453` | `a63a6ce6711fe46186ee910c4435581ccab429af10f28edff8b387e062200453` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AWTGLCanvas` | A15 `d8e7fff14e184c8ccdad2964b6f6884a1a89aa6ede94abf53c8441f24142169a`<br>A61 `d8e7fff14e184c8ccdad2964b6f6884a1a89aa6ede94abf53c8441f24142169a` | `d8e7fff14e184c8ccdad2964b6f6884a1a89aa6ede94abf53c8441f24142169a` / A15, A61 | COMPATIBLE | 27 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AWTSurfaceLock$1` | A15 `4279d6818068f43b349dca1425f582bf2edfb1939ef04d1c5e44bac7f65f1413`<br>A61 `65791d575131073a617846efeb32df2940f0f6ad549ac18acbca5fcc9bd0024e` | `4279d6818068f43b349dca1425f582bf2edfb1939ef04d1c5e44bac7f65f1413` / A15 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.AWTSurfaceLock` | A15 `2172b9b825a122a93685f070601d179cb86b3c13d85f44652f055802f5b530af`<br>A61 `b9286ff7e1c6c3309799efdea3583370ee1dc91f759b4fa2ebcd66383fef0c1c` | `2172b9b825a122a93685f070601d179cb86b3c13d85f44652f055802f5b530af` / A15 | COMPATIBLE | 11 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.AWTUtil$1` | A15 `cec9e57d83a895d2f7072aa3e70eac6a31df8b6e87d42bf6d0866e896857069f`<br>A61 `cec9e57d83a895d2f7072aa3e70eac6a31df8b6e87d42bf6d0866e896857069f` | `cec9e57d83a895d2f7072aa3e70eac6a31df8b6e87d42bf6d0866e896857069f` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AWTUtil$2` | A15 `196ff2445c74812b565e41b60243aba6abd0ec4b3646f787bce5891d39bbaba6`<br>A61 `196ff2445c74812b565e41b60243aba6abd0ec4b3646f787bce5891d39bbaba6` | `196ff2445c74812b565e41b60243aba6abd0ec4b3646f787bce5891d39bbaba6` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.AWTUtil` | A15 `94ac71d95c0231363555a47e5dfa62f43caf1eca2cb8e0b3fb21613462b1eb3d`<br>A61 `94ac71d95c0231363555a47e5dfa62f43caf1eca2cb8e0b3fb21613462b1eb3d` | `94ac71d95c0231363555a47e5dfa62f43caf1eca2cb8e0b3fb21613462b1eb3d` / A15, A61 | COMPATIBLE | 9 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.BaseReferences` | A15 `64d7dc662c1ce7b7f4f73b299bfa51b2d34ff18c31df895cd35af45be0404876`<br>A61 `64d7dc662c1ce7b7f4f73b299bfa51b2d34ff18c31df895cd35af45be0404876` | `64d7dc662c1ce7b7f4f73b299bfa51b2d34ff18c31df895cd35af45be0404876` / A15, A61 | COMPATIBLE | 12 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.CallbackUtil` | A15 `4769b0052f615529ed2584d21d6c7d426e63cf1c401e13f25c7ae010fb33e8ee`<br>A61 `79925dd37b36dfb5c6582763d64a51feadbb16d4616dd0a913657eae8ecaccee` | `4769b0052f615529ed2584d21d6c7d426e63cf1c401e13f25c7ae010fb33e8ee` / A15 | COMPATIBLE | 11 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.Context` | A15 `e991d4103baff32bad530dce454564c97f3cf9bd862dbf25656792c0855a3dd0`<br>A61 `e991d4103baff32bad530dce454564c97f3cf9bd862dbf25656792c0855a3dd0` | `e991d4103baff32bad530dce454564c97f3cf9bd862dbf25656792c0855a3dd0` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ContextAttribs` | A15 `4a26afc0f12a39d6749eed67230f5250f6c653228694e8af2cfe74beb7159af1`<br>A61 `2b6dbfeeeb00b7654093050e92fe69f4dd18d16bb3ad844796803302d79c5961` | `4a26afc0f12a39d6749eed67230f5250f6c653228694e8af2cfe74beb7159af1` / A15 | COMPATIBLE | 22 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ContextCapabilities` | A15 `3683b93d75b02ec0d32316b07d1e4add71afec5f7cb8b1655e69916426d1aee5`<br>A61 `9d11f7afe419c2f796ff19f6aa6b306a0fee92b5fe11d829dc92e827d0464c9a` | `3683b93d75b02ec0d32316b07d1e4add71afec5f7cb8b1655e69916426d1aee5` / A15 | COMPATIBLE | 2576 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ContextGL` | A15 `88cb71c4ed41a02d143a8db0dfabb9373a2d0f487d6852cbde05895972f32d29`<br>A61 `4847e0f746c1b22459246f36f606ef82695d5a0e0322002323bd6263aca9e75b` | `88cb71c4ed41a02d143a8db0dfabb9373a2d0f487d6852cbde05895972f32d29` / A15 | COMPATIBLE | 28 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ContextGLES` | A15 `f6f0b773897f93bd855e95dc472df41132050cffcf885277c83ae1a6f7bbc400`<br>A61 `f6f0b773897f93bd855e95dc472df41132050cffcf885277c83ae1a6f7bbc400` | `f6f0b773897f93bd855e95dc472df41132050cffcf885277c83ae1a6f7bbc400` / A15, A61 | UNKNOWN | 21 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.ContextImplementation` | A15 `40a274a047e9933143993da6993ab29e01485ff9d8b69df7a1b21ad589b7cfa2`<br>A61 `40a274a047e9933143993da6993ab29e01485ff9d8b69df7a1b21ad589b7cfa2` | `40a274a047e9933143993da6993ab29e01485ff9d8b69df7a1b21ad589b7cfa2` / A15, A61 | COMPATIBLE | 9 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.Display$1` | A15 `87a10b7cf0d16ad3b1e003467f0d680c91d7fb07b9e231f2cbd09f09adb695e0`<br>A61 `7087d77cfc1a336149f56ecd7f25c7594ee845b6f4e5a17649cc891f05cfe8c6` | `87a10b7cf0d16ad3b1e003467f0d680c91d7fb07b9e231f2cbd09f09adb695e0` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.Display$2` | A15 `f2e2774ddad640da2e840cfaf5cf0d86219d035de748f4f95341086b651a448e`<br>A61 `f2e2774ddad640da2e840cfaf5cf0d86219d035de748f4f95341086b651a448e` | `f2e2774ddad640da2e840cfaf5cf0d86219d035de748f4f95341086b651a448e` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.Display$3` | A15 `eeaf9e25d669bdfdb5c85e6ce797288758f1c5da5c41f943357073d74df33cac`<br>A61 `ceaa188d1c7ab5741606990dafa00d9df2353514d5a90984d4f1483718ed929d` | `eeaf9e25d669bdfdb5c85e6ce797288758f1c5da5c41f943357073d74df33cac` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.Display$4` | A15 `095040beae647e25dffe982747e2f5ae74b62e2bd7daa2d2a12e67daa25b50c6`<br>A61 `9cb4ba10e2ec532f1a682ee2e2b665dba6324660542b43422e034e3246c19347` | `095040beae647e25dffe982747e2f5ae74b62e2bd7daa2d2a12e67daa25b50c6` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.Display$5` | A15 `f89d9acba818d923ca408774bb28747ba953b3d2c3007eec5a891d2f45666c21`<br>A61 `61e27a35d8b56b9e7f74c308d50c14015423c47e29d23af04291bd393aed736b` | `f89d9acba818d923ca408774bb28747ba953b3d2c3007eec5a891d2f45666c21` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.Display$6` | A15 `0d303b0743b1c09313fa34f731f2fd7a1094bf80026309bf3b093f9226747428`<br>A61 `ef9c7b155cc63644448cb3204df3dff354e6f46d31ab59d54961bf27f9bbcd0a` | `0d303b0743b1c09313fa34f731f2fd7a1094bf80026309bf3b093f9226747428` / A15 | UNKNOWN | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.Display$7` | A15 `5587e5c2051a9caa7016a2d580f1a3706373a796bb3debd5b3d540f46081a1dd`<br>A61 `9fca600246c6ff919cc814cb2e4a159ac72bdb3c520a5c88b17274a921e17aaa` | `5587e5c2051a9caa7016a2d580f1a3706373a796bb3debd5b3d540f46081a1dd` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.Display` | A15 `516e7ec8058c7e521894771015a34a8eba140cbd7df8c98652b49aa5a5075b4f`<br>A61 `39ac7fd8ebac4bdb318f88ae64f1e4070369ca765b51be597bac856b89606bf7` | `516e7ec8058c7e521894771015a34a8eba140cbd7df8c98652b49aa5a5075b4f` / A15 | COMPATIBLE | 84 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.DisplayImplementation` | A15 `572bd75b13ca18693361552bfecf46e51a25caa98b65d5d2ff7b6b98a5788ce5`<br>A61 `ded5d08f97be09e1cc27ccbd02cbfd855baa792d1da3fea7eed2a94943c2242c` | `572bd75b13ca18693361552bfecf46e51a25caa98b65d5d2ff7b6b98a5788ce5` / A15 | COMPATIBLE | 32 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.DisplayMode` | A15 `645cd109a0623ee17fbedd4bd4fd598c27ebc57371a4105c7b53d667d5d09e78`<br>A61 `645cd109a0623ee17fbedd4bd4fd598c27ebc57371a4105c7b53d667d5d09e78` | `645cd109a0623ee17fbedd4bd4fd598c27ebc57371a4105c7b53d667d5d09e78` / A15, A61 | COMPATIBLE | 14 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.Drawable` | A15 `6cbe1f2cff9bac732e563b0f0db99c56f4cbd1d57cde1a00c0a2cb39a710e789`<br>A61 `6cbe1f2cff9bac732e563b0f0db99c56f4cbd1d57cde1a00c0a2cb39a710e789` | `6cbe1f2cff9bac732e563b0f0db99c56f4cbd1d57cde1a00c0a2cb39a710e789` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.DrawableGL` | A15 `f5b8f78843a30c6c1088ad705baf77e349214626920c7ae5d31bafef67395416`<br>A61 `f5b8f78843a30c6c1088ad705baf77e349214626920c7ae5d31bafef67395416` | `f5b8f78843a30c6c1088ad705baf77e349214626920c7ae5d31bafef67395416` / A15, A61 | COMPATIBLE | 19 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.DrawableGLES` | A15 `2120225647ffb58bae3ba915fe5ff76085c11cccce913c36e8a1923aeff440c3`<br>A61 `2120225647ffb58bae3ba915fe5ff76085c11cccce913c36e8a1923aeff440c3` | `2120225647ffb58bae3ba915fe5ff76085c11cccce913c36e8a1923aeff440c3` / A15, A61 | UNKNOWN | 17 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.DrawableLWJGL` | A15 `dee1621c49a25fa03531f639fa716acb3c13330ff46e23d4e236f7db04edb3df`<br>A61 `dee1621c49a25fa03531f639fa716acb3c13330ff46e23d4e236f7db04edb3df` | `dee1621c49a25fa03531f639fa716acb3c13330ff46e23d4e236f7db04edb3df` / A15, A61 | COMPATIBLE | 11 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTAbgr` | A15 `25caa892c5599eba703c0fb2c5dfd8affc48b15a139e4fd6a4a55018d2a46b8e`<br>A61 `25caa892c5599eba703c0fb2c5dfd8affc48b15a139e4fd6a4a55018d2a46b8e` | `25caa892c5599eba703c0fb2c5dfd8affc48b15a139e4fd6a4a55018d2a46b8e` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTBgra` | A15 `94140af4ee7a58625678ae70a5d1719e33b24c51dffb3b79d8eb22b9b1f37b67`<br>A61 `94140af4ee7a58625678ae70a5d1719e33b24c51dffb3b79d8eb22b9b1f37b67` | `94140af4ee7a58625678ae70a5d1719e33b24c51dffb3b79d8eb22b9b1f37b67` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTBindableUniform` | A15 `2070301e24e73ce09e9200ccabe4af788c6cafc4166037be0fd4f193a8b297e8`<br>A61 `2070301e24e73ce09e9200ccabe4af788c6cafc4166037be0fd4f193a8b297e8` | `2070301e24e73ce09e9200ccabe4af788c6cafc4166037be0fd4f193a8b297e8` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTBlendColor` | A15 `e15d6c31dc37ca95d26c314c100f0b5d503d275ecac3710a211473a7a472ab3e`<br>A61 `e15d6c31dc37ca95d26c314c100f0b5d503d275ecac3710a211473a7a472ab3e` | `e15d6c31dc37ca95d26c314c100f0b5d503d275ecac3710a211473a7a472ab3e` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTBlendEquationSeparate` | A15 `1588bfd29409c531c0ff9c55cc006f3d1f1ef7e6b65b97117d89c3ef829e6f1c`<br>A61 `1588bfd29409c531c0ff9c55cc006f3d1f1ef7e6b65b97117d89c3ef829e6f1c` | `1588bfd29409c531c0ff9c55cc006f3d1f1ef7e6b65b97117d89c3ef829e6f1c` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTBlendFuncSeparate` | A15 `eb1348c9ae30c901ce65a08fb07f629880e272865ec61a18ef14c01288de0d3c`<br>A61 `eb1348c9ae30c901ce65a08fb07f629880e272865ec61a18ef14c01288de0d3c` | `eb1348c9ae30c901ce65a08fb07f629880e272865ec61a18ef14c01288de0d3c` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTBlendMinmax` | A15 `c8f7526ca20883f0f493f4e82b923b6e866519da20616624c19a9748c556c7c2`<br>A61 `c8f7526ca20883f0f493f4e82b923b6e866519da20616624c19a9748c556c7c2` | `c8f7526ca20883f0f493f4e82b923b6e866519da20616624c19a9748c556c7c2` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTBlendSubtract` | A15 `8b0b131356344c04b645d4f391a5537ff4ef3331251d57f4b453b6fba3f9cf78`<br>A61 `8b0b131356344c04b645d4f391a5537ff4ef3331251d57f4b453b6fba3f9cf78` | `8b0b131356344c04b645d4f391a5537ff4ef3331251d57f4b453b6fba3f9cf78` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTCgShader` | A15 `bf14848b2f6f7ef4ab25891653b5ab895392fbefe7ef6c22e2cb0c20f1e7afaf`<br>A61 `bf14848b2f6f7ef4ab25891653b5ab895392fbefe7ef6c22e2cb0c20f1e7afaf` | `bf14848b2f6f7ef4ab25891653b5ab895392fbefe7ef6c22e2cb0c20f1e7afaf` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTCompiledVertexArray` | A15 `057df67d0dafa42e3c00b47bac8b62e418360ca4ef3178b78526c67fc20e6a15`<br>A61 `057df67d0dafa42e3c00b47bac8b62e418360ca4ef3178b78526c67fc20e6a15` | `057df67d0dafa42e3c00b47bac8b62e418360ca4ef3178b78526c67fc20e6a15` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTDepthBoundsTest` | A15 `e5f35325e27fd81a63a11b94663eeb4d2d73dbdf9a59cf6df0a832eb3742c1fb`<br>A61 `e5f35325e27fd81a63a11b94663eeb4d2d73dbdf9a59cf6df0a832eb3742c1fb` | `e5f35325e27fd81a63a11b94663eeb4d2d73dbdf9a59cf6df0a832eb3742c1fb` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTDirectStateAccess` | A15 `2c04a2e7a2c9c9dbacca9715a5b5841f03224300e8a6f06f54e8aa08ae245cfd`<br>A61 `08f7d2ea6742be079f63d50ef2fdb6a171fe3bb0294a6f6f2157b0ebf3893dc4` | `2c04a2e7a2c9c9dbacca9715a5b5841f03224300e8a6f06f54e8aa08ae245cfd` / A15 | COMPATIBLE | 244 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.EXTDrawBuffers2` | A15 `5582be5fbf127a24cb7ea4656655a9e60277a3120bbecdd24b17341c069f394b`<br>A61 `5582be5fbf127a24cb7ea4656655a9e60277a3120bbecdd24b17341c069f394b` | `5582be5fbf127a24cb7ea4656655a9e60277a3120bbecdd24b17341c069f394b` / A15, A61 | COMPATIBLE | 13 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTDrawInstanced` | A15 `3055527aee28cbe1f3b6de96dab7388bf1c38507cc61ddfe263748bc43cf46f9`<br>A61 `3055527aee28cbe1f3b6de96dab7388bf1c38507cc61ddfe263748bc43cf46f9` | `3055527aee28cbe1f3b6de96dab7388bf1c38507cc61ddfe263748bc43cf46f9` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTDrawRangeElements` | A15 `9cc486c6a176694902494cec5a81dd8521030f2c3a9cbbedfaf3c0a88bd524fe`<br>A61 `9cc486c6a176694902494cec5a81dd8521030f2c3a9cbbedfaf3c0a88bd524fe` | `9cc486c6a176694902494cec5a81dd8521030f2c3a9cbbedfaf3c0a88bd524fe` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTFogCoord` | A15 `9d6556881289501d0a01809399744d818b5ba3e5748bfea89dd61b109a0a0a4b`<br>A61 `9d6556881289501d0a01809399744d818b5ba3e5748bfea89dd61b109a0a0a4b` | `9d6556881289501d0a01809399744d818b5ba3e5748bfea89dd61b109a0a0a4b` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTFramebufferBlit` | A15 `5ddef944ce99e0240a6695a8b9a8ec189e243f10b3d503bb5505e68134f9cd6d`<br>A61 `5ddef944ce99e0240a6695a8b9a8ec189e243f10b3d503bb5505e68134f9cd6d` | `5ddef944ce99e0240a6695a8b9a8ec189e243f10b3d503bb5505e68134f9cd6d` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTFramebufferMultisample` | A15 `d1ec9b6756b690383004cda7c576d10015b0627937160a2997616cea4b2afd32`<br>A61 `d1ec9b6756b690383004cda7c576d10015b0627937160a2997616cea4b2afd32` | `d1ec9b6756b690383004cda7c576d10015b0627937160a2997616cea4b2afd32` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTFramebufferMultisampleBlitScaled` | A15 `f600187fc501d0b2a5171f2116a924ca8f40b4245f9e31696ed6318876072296`<br>A61 `f600187fc501d0b2a5171f2116a924ca8f40b4245f9e31696ed6318876072296` | `f600187fc501d0b2a5171f2116a924ca8f40b4245f9e31696ed6318876072296` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTFramebufferObject` | A15 `69efaf76d2096b3b3d0b2e1f183fb0bc35dc694a11932a54052176b6cfd50c4d`<br>A61 `887113a14972c78588fd0ff004e687718ff4180bf7d1f4acef40c5d2c126aa51` | `69efaf76d2096b3b3d0b2e1f183fb0bc35dc694a11932a54052176b6cfd50c4d` / A15 | COMPATIBLE | 31 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.EXTFramebufferSRGB` | A15 `6075434f500bb9139074da85862630001759695b09850ea3c621959e4ec6c274`<br>A61 `6075434f500bb9139074da85862630001759695b09850ea3c621959e4ec6c274` | `6075434f500bb9139074da85862630001759695b09850ea3c621959e4ec6c274` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTGeometryShader4` | A15 `11d16bc910a24a7447d8815c5bd9ad28bf9d6494c5e5e2d9de5269ccf5267252`<br>A61 `11d16bc910a24a7447d8815c5bd9ad28bf9d6494c5e5e2d9de5269ccf5267252` | `11d16bc910a24a7447d8815c5bd9ad28bf9d6494c5e5e2d9de5269ccf5267252` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTGpuProgramParameters` | A15 `becabf7fd77991f6eaf2aa10e03c479c0428139d206001e30049760135adf960`<br>A61 `becabf7fd77991f6eaf2aa10e03c479c0428139d206001e30049760135adf960` | `becabf7fd77991f6eaf2aa10e03c479c0428139d206001e30049760135adf960` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTGpuShader4` | A15 `aa727b697275f3067e8f24b4d797c7a38431653a55af8e999dd93d0ffffabe69`<br>A61 `afc0937b0afdcc7181e16a5dba33e4c00f5c35180aa821bb0b9ee07404bcb65c` | `aa727b697275f3067e8f24b4d797c7a38431653a55af8e999dd93d0ffffabe69` / A15 | COMPATIBLE | 35 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.EXTMultiDrawArrays` | A15 `578d7325bf9d3df92a5b100e62f7e3bfff9fb8bf60f8825d92670c0fa81fba00`<br>A61 `578d7325bf9d3df92a5b100e62f7e3bfff9fb8bf60f8825d92670c0fa81fba00` | `578d7325bf9d3df92a5b100e62f7e3bfff9fb8bf60f8825d92670c0fa81fba00` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTPackedDepthStencil` | A15 `ea0fc9a89c9ccecb026d938e899b1f8a8422ddddd2881e2d6750a6812d2270fe`<br>A61 `ea0fc9a89c9ccecb026d938e899b1f8a8422ddddd2881e2d6750a6812d2270fe` | `ea0fc9a89c9ccecb026d938e899b1f8a8422ddddd2881e2d6750a6812d2270fe` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTPackedFloat` | A15 `499b6cfe7f69999e91d784fe5679f8e51d1ef8458c4ebfc2d4c91ae20b65fb43`<br>A61 `499b6cfe7f69999e91d784fe5679f8e51d1ef8458c4ebfc2d4c91ae20b65fb43` | `499b6cfe7f69999e91d784fe5679f8e51d1ef8458c4ebfc2d4c91ae20b65fb43` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTPackedPixels` | A15 `577e3a8c9346a5c7ea54200b7815f21f47d156bb24ddd2d94dfaba36d8e27ccd`<br>A61 `577e3a8c9346a5c7ea54200b7815f21f47d156bb24ddd2d94dfaba36d8e27ccd` | `577e3a8c9346a5c7ea54200b7815f21f47d156bb24ddd2d94dfaba36d8e27ccd` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTPalettedTexture` | A15 `38a360e478d3105f8fa517b8069dd2f0f93c276648b48119f0ac80350103245e`<br>A61 `38a360e478d3105f8fa517b8069dd2f0f93c276648b48119f0ac80350103245e` | `38a360e478d3105f8fa517b8069dd2f0f93c276648b48119f0ac80350103245e` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTPixelBufferObject` | A15 `f8ae9066018075950af9c21d2695ac3bdd3f8441cff71c3e5dd0539c2daaaa8b`<br>A61 `f8ae9066018075950af9c21d2695ac3bdd3f8441cff71c3e5dd0539c2daaaa8b` | `f8ae9066018075950af9c21d2695ac3bdd3f8441cff71c3e5dd0539c2daaaa8b` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTPointParameters` | A15 `93814d8282db1c33a3f1080a8c6d1c4eee8cd7314b6d320c3886ae1242c32c29`<br>A61 `93814d8282db1c33a3f1080a8c6d1c4eee8cd7314b6d320c3886ae1242c32c29` | `93814d8282db1c33a3f1080a8c6d1c4eee8cd7314b6d320c3886ae1242c32c29` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTProvokingVertex` | A15 `2faf7b09b78cb0235647afac144b0076403274d32bc3f3b97d6fd3bd4b363b81`<br>A61 `2faf7b09b78cb0235647afac144b0076403274d32bc3f3b97d6fd3bd4b363b81` | `2faf7b09b78cb0235647afac144b0076403274d32bc3f3b97d6fd3bd4b363b81` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTRescaleNormal` | A15 `db0a7cf7243b63e4510905f7f812994940cd80bb13dfca8dcfaa514c78ccad50`<br>A61 `db0a7cf7243b63e4510905f7f812994940cd80bb13dfca8dcfaa514c78ccad50` | `db0a7cf7243b63e4510905f7f812994940cd80bb13dfca8dcfaa514c78ccad50` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTSecondaryColor` | A15 `8e3611bd4dfcb2c7a9d8ec780f62219364a29bd235e5b38faf47007cc4c0b409`<br>A61 `8e3611bd4dfcb2c7a9d8ec780f62219364a29bd235e5b38faf47007cc4c0b409` | `8e3611bd4dfcb2c7a9d8ec780f62219364a29bd235e5b38faf47007cc4c0b409` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTSeparateShaderObjects` | A15 `08f3201870817a0d348292fe63ca1c8606d9198f575fa2d0d485f07d864f1ec6`<br>A61 `08f3201870817a0d348292fe63ca1c8606d9198f575fa2d0d485f07d864f1ec6` | `08f3201870817a0d348292fe63ca1c8606d9198f575fa2d0d485f07d864f1ec6` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTSeparateSpecularColor` | A15 `d9ee122b3ccb7b9504f818653230ef5e7ce563e93f4730bc06dc9c5035ea2129`<br>A61 `d9ee122b3ccb7b9504f818653230ef5e7ce563e93f4730bc06dc9c5035ea2129` | `d9ee122b3ccb7b9504f818653230ef5e7ce563e93f4730bc06dc9c5035ea2129` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTShaderImageLoadStore` | A15 `3250e7fbaa3b52ba05901d22523f10285dc7dc1a29c30cbdcc85d392dd876c83`<br>A61 `3250e7fbaa3b52ba05901d22523f10285dc7dc1a29c30cbdcc85d392dd876c83` | `3250e7fbaa3b52ba05901d22523f10285dc7dc1a29c30cbdcc85d392dd876c83` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTSharedTexturePalette` | A15 `26fc72b29012e6eb0cc6fcf0089e62fcde1fcb4fd394e8be4daf3d055bce8ea3`<br>A61 `26fc72b29012e6eb0cc6fcf0089e62fcde1fcb4fd394e8be4daf3d055bce8ea3` | `26fc72b29012e6eb0cc6fcf0089e62fcde1fcb4fd394e8be4daf3d055bce8ea3` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTStencilClearTag` | A15 `a7197fc2912baed738c312c3580c8ebe91adcf7d928ae25e487ac1f683154990`<br>A61 `a7197fc2912baed738c312c3580c8ebe91adcf7d928ae25e487ac1f683154990` | `a7197fc2912baed738c312c3580c8ebe91adcf7d928ae25e487ac1f683154990` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTStencilTwoSide` | A15 `36eb82768e83c0ba93bb189a2fb3020a7e3178102215b5fffaf8ee258e84ed9c`<br>A61 `36eb82768e83c0ba93bb189a2fb3020a7e3178102215b5fffaf8ee258e84ed9c` | `36eb82768e83c0ba93bb189a2fb3020a7e3178102215b5fffaf8ee258e84ed9c` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTStencilWrap` | A15 `5d1761f6ebbbd9316f8f1d47990299eff0ccfc065514ffc8dd5e0564579a5260`<br>A61 `5d1761f6ebbbd9316f8f1d47990299eff0ccfc065514ffc8dd5e0564579a5260` | `5d1761f6ebbbd9316f8f1d47990299eff0ccfc065514ffc8dd5e0564579a5260` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureArray` | A15 `fac7f1db2cce2dd9f9d6c0538281c853996c5fc7b2f27fc0854b282d44bb0340`<br>A61 `fac7f1db2cce2dd9f9d6c0538281c853996c5fc7b2f27fc0854b282d44bb0340` | `fac7f1db2cce2dd9f9d6c0538281c853996c5fc7b2f27fc0854b282d44bb0340` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureBufferObject` | A15 `631b5e5ce186c81ee935206bd055ced868dca043a4853bee8d25e9d0e849df60`<br>A61 `631b5e5ce186c81ee935206bd055ced868dca043a4853bee8d25e9d0e849df60` | `631b5e5ce186c81ee935206bd055ced868dca043a4853bee8d25e9d0e849df60` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureCompressionLATC` | A15 `01f53a7c05cb1e646393905c5df6fdc9b2de2478b7099d4680623aabfd0b1315`<br>A61 `01f53a7c05cb1e646393905c5df6fdc9b2de2478b7099d4680623aabfd0b1315` | `01f53a7c05cb1e646393905c5df6fdc9b2de2478b7099d4680623aabfd0b1315` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureCompressionRGTC` | A15 `5a21e866d525b476d383de04c0d073983da722d3bbd491e9d301e1fada39e59e`<br>A61 `5a21e866d525b476d383de04c0d073983da722d3bbd491e9d301e1fada39e59e` | `5a21e866d525b476d383de04c0d073983da722d3bbd491e9d301e1fada39e59e` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureCompressionS3TC` | A15 `21e55cb2675eb37e37b284432999a0d0db37d063d25e938c8be49b401957c43e`<br>A61 `21e55cb2675eb37e37b284432999a0d0db37d063d25e938c8be49b401957c43e` | `21e55cb2675eb37e37b284432999a0d0db37d063d25e938c8be49b401957c43e` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureEnvCombine` | A15 `b051d7714fef8afd3f61bb5349924dbf51b909146612a28375bb1699352aeefd`<br>A61 `b051d7714fef8afd3f61bb5349924dbf51b909146612a28375bb1699352aeefd` | `b051d7714fef8afd3f61bb5349924dbf51b909146612a28375bb1699352aeefd` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureEnvDot3` | A15 `0ff9d64c968fccd22290a8270f42faf0c28a39fdace17d97ee526cf62f8429dd`<br>A61 `0ff9d64c968fccd22290a8270f42faf0c28a39fdace17d97ee526cf62f8429dd` | `0ff9d64c968fccd22290a8270f42faf0c28a39fdace17d97ee526cf62f8429dd` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureFilterAnisotropic` | A15 `79d21740722ad9c3f0c838c28ac091f43aff8977b2d8b077cffca5e998c9a38b`<br>A61 `79d21740722ad9c3f0c838c28ac091f43aff8977b2d8b077cffca5e998c9a38b` | `79d21740722ad9c3f0c838c28ac091f43aff8977b2d8b077cffca5e998c9a38b` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureInteger` | A15 `6e7fd52038c98c7c1715d6c742ee5a21579c7f7a83a13e1ea315fed264352aaa`<br>A61 `6e7fd52038c98c7c1715d6c742ee5a21579c7f7a83a13e1ea315fed264352aaa` | `6e7fd52038c98c7c1715d6c742ee5a21579c7f7a83a13e1ea315fed264352aaa` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureLODBias` | A15 `53977d9d08abf77c6514b31865361d16a814190163a5e8f7b30e2cef2cd4d164`<br>A61 `53977d9d08abf77c6514b31865361d16a814190163a5e8f7b30e2cef2cd4d164` | `53977d9d08abf77c6514b31865361d16a814190163a5e8f7b30e2cef2cd4d164` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureMirrorClamp` | A15 `793305b1baee1392d4d5c48ecf08bd4b3d8c7a9612e42b1b5d7a212b20760788`<br>A61 `793305b1baee1392d4d5c48ecf08bd4b3d8c7a9612e42b1b5d7a212b20760788` | `793305b1baee1392d4d5c48ecf08bd4b3d8c7a9612e42b1b5d7a212b20760788` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureRectangle` | A15 `b7b67068ec34f6abdef9b14fdd59af3d7a68c0a191b7a888c23c1dd6aa476a12`<br>A61 `b7b67068ec34f6abdef9b14fdd59af3d7a68c0a191b7a888c23c1dd6aa476a12` | `b7b67068ec34f6abdef9b14fdd59af3d7a68c0a191b7a888c23c1dd6aa476a12` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureSRGB` | A15 `3942a4296620e79cbcf12f5bc2c86a3a0eccb2c3bb9cbdb827cbeca3b3c6f49d`<br>A61 `3942a4296620e79cbcf12f5bc2c86a3a0eccb2c3bb9cbdb827cbeca3b3c6f49d` | `3942a4296620e79cbcf12f5bc2c86a3a0eccb2c3bb9cbdb827cbeca3b3c6f49d` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureSRGBDecode` | A15 `b9e4fb007a92770a4895f36b8998aef8c4dda14910f959f2b2ed8a9090b3e049`<br>A61 `b9e4fb007a92770a4895f36b8998aef8c4dda14910f959f2b2ed8a9090b3e049` | `b9e4fb007a92770a4895f36b8998aef8c4dda14910f959f2b2ed8a9090b3e049` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureSharedExponent` | A15 `a21e4c2dad7fde47c95c5ed16d159b79febae55f0add05f7739adbe9ef001a4b`<br>A61 `a21e4c2dad7fde47c95c5ed16d159b79febae55f0add05f7739adbe9ef001a4b` | `a21e4c2dad7fde47c95c5ed16d159b79febae55f0add05f7739adbe9ef001a4b` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureSnorm` | A15 `baada81ec1f1959f9e0d14c1d99468d5aa157a1698e60a78710214fed9b9b4de`<br>A61 `baada81ec1f1959f9e0d14c1d99468d5aa157a1698e60a78710214fed9b9b4de` | `baada81ec1f1959f9e0d14c1d99468d5aa157a1698e60a78710214fed9b9b4de` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTextureSwizzle` | A15 `f355251625bb2ee3c0fd3182d7f7bacc08047e6a6adc178dac6d7573360d208b`<br>A61 `f355251625bb2ee3c0fd3182d7f7bacc08047e6a6adc178dac6d7573360d208b` | `f355251625bb2ee3c0fd3182d7f7bacc08047e6a6adc178dac6d7573360d208b` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTimerQuery` | A15 `976bf08be040852211e2c38cfbc97781552b15a3b2b2c2d5a087b5c844425d3e`<br>A61 `976bf08be040852211e2c38cfbc97781552b15a3b2b2c2d5a087b5c844425d3e` | `976bf08be040852211e2c38cfbc97781552b15a3b2b2c2d5a087b5c844425d3e` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTTransformFeedback` | A15 `993f134a99e73b292eb66849595fe459debe0cf3a062ce4c13288beec5ccabfb`<br>A61 `993f134a99e73b292eb66849595fe459debe0cf3a062ce4c13288beec5ccabfb` | `993f134a99e73b292eb66849595fe459debe0cf3a062ce4c13288beec5ccabfb` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTVertexArrayBgra` | A15 `a4d47536b736734adbb9713c59e936faba17e76af5bd8d4117dae9e44d88c36f`<br>A61 `a4d47536b736734adbb9713c59e936faba17e76af5bd8d4117dae9e44d88c36f` | `a4d47536b736734adbb9713c59e936faba17e76af5bd8d4117dae9e44d88c36f` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTVertexAttrib64bit` | A15 `0362451860df8a0138d5f22e091e81bc13b503669044d886f54af4846b8224de`<br>A61 `0362451860df8a0138d5f22e091e81bc13b503669044d886f54af4846b8224de` | `0362451860df8a0138d5f22e091e81bc13b503669044d886f54af4846b8224de` / A15, A61 | COMPATIBLE | 11 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTVertexShader` | A15 `de468b1e964502b4bc40281ec96453d7e303eadaf078355c9b31c8fec66120eb`<br>A61 `de468b1e964502b4bc40281ec96453d7e303eadaf078355c9b31c8fec66120eb` | `de468b1e964502b4bc40281ec96453d7e303eadaf078355c9b31c8fec66120eb` / A15, A61 | COMPATIBLE | 43 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EXTVertexWeighting` | A15 `6a5c577713dd6cf215380186b48dbc7dea3466e2bb4c8518c7092c23336b5078`<br>A61 `6a5c577713dd6cf215380186b48dbc7dea3466e2bb4c8518c7092c23336b5078` | `6a5c577713dd6cf215380186b48dbc7dea3466e2bb4c8518c7092c23336b5078` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.EventQueue` | A15 `925e3de5846f675a4357dd859a724907411ce682984a71257155522066a26661`<br>A61 `925e3de5846f675a4357dd859a724907411ce682984a71257155522066a26661` | `925e3de5846f675a4357dd859a724907411ce682984a71257155522066a26661` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.FastIntMap$Entry` | A15 `3d9920c3428fba39272a279205fa4ba7a880c075a5ea3c542a95f31c73e68680`<br>A61 `3d9920c3428fba39272a279205fa4ba7a880c075a5ea3c542a95f31c73e68680` | `3d9920c3428fba39272a279205fa4ba7a880c075a5ea3c542a95f31c73e68680` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.FastIntMap$EntryIterator` | A15 `c8296855f6b9308a9b5d4b0d6f324cd91626130f6336498659d50da96d854a24`<br>A61 `c8296855f6b9308a9b5d4b0d6f324cd91626130f6336498659d50da96d854a24` | `c8296855f6b9308a9b5d4b0d6f324cd91626130f6336498659d50da96d854a24` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.FastIntMap` | A15 `ec7d9013c036d392cd0c7d45ba9ee48c345233a93faac2046cd64688122db8f7`<br>A61 `ec7d9013c036d392cd0c7d45ba9ee48c345233a93faac2046cd64688122db8f7` | `ec7d9013c036d392cd0c7d45ba9ee48c345233a93faac2046cd64688122db8f7` / A15, A61 | COMPATIBLE | 16 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.GL11` | A15 `875ff80814db1f6c16dd118fb27df7a7dc97adb4876dde023afd0e4ca0f18ce4`<br>A61 `d0ccea6140d82543ae950bec6b09007cd36105126da9c24d858ce9fd80da2cb9` | `875ff80814db1f6c16dd118fb27df7a7dc97adb4876dde023afd0e4ca0f18ce4` / A15 | COMPATIBLE | 359 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL12` | A15 `1c72b4c6c747e29c81eed2a757bf840e7f72efe3161ef06a0983e24eab39cefc`<br>A61 `1c72b4c6c747e29c81eed2a757bf840e7f72efe3161ef06a0983e24eab39cefc` | `1c72b4c6c747e29c81eed2a757bf840e7f72efe3161ef06a0983e24eab39cefc` / A15, A61 | COMPATIBLE | 8 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.GL13` | A15 `54a7f00a0710058dbd113906d51dbaf4008da1ce07e9b9fd860d49b156ce1a3c`<br>A61 `1e2ca2072b4c63033ce434e5b9c986a3298016a2c1a0ebb0fa20ba930c4612f2` | `54a7f00a0710058dbd113906d51dbaf4008da1ce07e9b9fd860d49b156ce1a3c` / A15 | COMPATIBLE | 32 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL14` | A15 `54d58af3da7faab0a5c5f9b2a2e3c5bdbe3087cb5567791137cc101142d13e2c`<br>A61 `54d58af3da7faab0a5c5f9b2a2e3c5bdbe3087cb5567791137cc101142d13e2c` | `54d58af3da7faab0a5c5f9b2a2e3c5bdbe3087cb5567791137cc101142d13e2c` / A15, A61 | COMPATIBLE | 28 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.GL15` | A15 `1652dc1b0e9928d7ab3a47ede0a5b4571b934c62e8d6b5acae3c6ffd504b1f49`<br>A61 `7f2adeac407519d3e3d0c77232151375d104ef19f3739ac5261fdb5e74f13473` | `1652dc1b0e9928d7ab3a47ede0a5b4571b934c62e8d6b5acae3c6ffd504b1f49` / A15 | COMPATIBLE | 27 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL20` | A15 `4ce53c6eb13178a9ef2097318d0802f4317f7925baa3b6c27d0f7a96581c5451`<br>A61 `9efedec80143f6d0789eb09d66fdb5d3fbeca4c057ee503064eda240c4254c1e` | `4ce53c6eb13178a9ef2097318d0802f4317f7925baa3b6c27d0f7a96581c5451` / A15 | COMPATIBLE | 114 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL21` | A15 `268b86ce8b63ef7d576392ce0626952f970a7530d91de550e18247c18f87c382`<br>A61 `268b86ce8b63ef7d576392ce0626952f970a7530d91de550e18247c18f87c382` | `268b86ce8b63ef7d576392ce0626952f970a7530d91de550e18247c18f87c382` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.GL30` | A15 `a765c9689e79cbf4249826ee99365e4c1b28510ae2cdcaf4c34c1449ec14984c`<br>A61 `39419b63f8301ff52932490d8b958471afe489c07e9f73a000ae0ed0ca097745` | `a765c9689e79cbf4249826ee99365e4c1b28510ae2cdcaf4c34c1449ec14984c` / A15 | COMPATIBLE | 124 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL31` | A15 `6bbe486f94f161dc44aa07b3c4e3500ffacd36c9fd9ab6f3021d89904d6771be`<br>A61 `71247ede20d70230c1d5894fed6ab60cb4c50540a3bb8ab75ed37799124df24a` | `6bbe486f94f161dc44aa07b3c4e3500ffacd36c9fd9ab6f3021d89904d6771be` / A15 | COMPATIBLE | 27 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL32` | A15 `63c2dae46832f0f34fb395f7dd3be8de1b26f649020c7b4fe9c3864f74bfdb48`<br>A61 `60a693a92b6de40fd62667f8089d245f19d27b3551bfd718229ee44b8956ed46` | `63c2dae46832f0f34fb395f7dd3be8de1b26f649020c7b4fe9c3864f74bfdb48` / A15 | COMPATIBLE | 48 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL33` | A15 `3a64d53b419659320e0e0db38f67e4d23e8be6f84408712b0f3b6766a3e4f331`<br>A61 `74f799f23e50a23e3eb1335416cf0cf400c10a34f5273a67b267e3d6495b180e` | `3a64d53b419659320e0e0db38f67e4d23e8be6f84408712b0f3b6766a3e4f331` / A15 | COMPATIBLE | 125 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL40` | A15 `fe29670079c5db7690be723864e3bedaa440b97351bc4edfa6ca0cdf1ea9587e`<br>A61 `5d61430c0cf199236dc5f0336920b4a0753b7b8a829de3972f24254580d5b1e8` | `fe29670079c5db7690be723864e3bedaa440b97351bc4edfa6ca0cdf1ea9587e` / A15 | COMPATIBLE | 103 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL41` | A15 `0c9a7b3f35f7cfbaadf13f7e34fd88af360ecbad00357e01f0d50c112a8f7246`<br>A61 `ee87010eb017eecc54ca211bd3577e0be878f0d45e440d542a0cdc6c105467e6` | `0c9a7b3f35f7cfbaadf13f7e34fd88af360ecbad00357e01f0d50c112a8f7246` / A15 | COMPATIBLE | 190 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GL42` | A15 `ef6f7d879dc9219a7e6c47237ff19428df8c68dd8a1b7d2ce42fa761b6203eb0`<br>A61 `ef6f7d879dc9219a7e6c47237ff19428df8c68dd8a1b7d2ce42fa761b6203eb0` | `ef6f7d879dc9219a7e6c47237ff19428df8c68dd8a1b7d2ce42fa761b6203eb0` / A15, A61 | COMPATIBLE | 34 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.GLChecks` | A15 `d9ebf856444e2391ac36e24bee35eeec4d9cc1e38a221da5525c63b38ee83825`<br>A61 `7cb5b881af129f50264212ad2c9ba9157f32af019f67668711a4f0243d8828dc` | `d9ebf856444e2391ac36e24bee35eeec4d9cc1e38a221da5525c63b38ee83825` / A15 | COMPATIBLE | 26 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GLContext$1` | A15 `f8a7a1cbba40c3bcf9014edd5dc55c5ae864cb9d276396ef6ece36a69a84d0be`<br>A61 `499065347fdab442a5fd57a634d2ee3ba8748e2769423f21bcf5cda65298cf9e` | `f8a7a1cbba40c3bcf9014edd5dc55c5ae864cb9d276396ef6ece36a69a84d0be` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GLContext$2` | A15 `8b7255922f78a6a18681750ef155b4f51fba1f3d43b5047a02acbed7c9fbe6f5`<br>A61 `d1b05fbb44d72c8fcfd6422e90af2543259983d71a01852ec7e3880baa83763f` | `8b7255922f78a6a18681750ef155b4f51fba1f3d43b5047a02acbed7c9fbe6f5` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GLContext$CapabilitiesCacheEntry` | A15 `49a313c79a33fe612272fc38342cdc71ba76ff26cddc108d33bf5711bd740d37`<br>A61 `fd4a96078d8154e385426b6cb50ffe91e8d553e728755a2556b299e3f2bd4fba` | `49a313c79a33fe612272fc38342cdc71ba76ff26cddc108d33bf5711bd740d37` / A15 | COMPATIBLE | 4 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GLContext` | A15 `43052b65a414b16a539cca78df76a362c5171bbb335e23f546369048f478d9f6`<br>A61 `3c54a66d23ce79b142e88aefde53555d570928fad9341b86d00b3cd914b8b727` | `43052b65a414b16a539cca78df76a362c5171bbb335e23f546369048f478d9f6` / A15 | COMPATIBLE | 23 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.GLSync` | A15 `bd47d5b8f88c1dacf983c68006a331e12287d899d8bb2b6d4a2124a17fa15f82`<br>A61 `bd47d5b8f88c1dacf983c68006a331e12287d899d8bb2b6d4a2124a17fa15f82` | `bd47d5b8f88c1dacf983c68006a331e12287d899d8bb2b6d4a2124a17fa15f82` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.GLUConstants` | A15 `a7a21f96584e587377161d239aca8e8b88404c7c2202729f30fdebd0fad5bd68`<br>A61 `a7a21f96584e587377161d239aca8e8b88404c7c2202729f30fdebd0fad5bd68` | `a7a21f96584e587377161d239aca8e8b88404c7c2202729f30fdebd0fad5bd68` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.GREMEDYStringMarker` | A15 `5f6a77ec80b27b8113759ddbb6be5608c478e841237da8173b6e664f6806c872`<br>A61 `5f6a77ec80b27b8113759ddbb6be5608c478e841237da8173b6e664f6806c872` | `5f6a77ec80b27b8113759ddbb6be5608c478e841237da8173b6e664f6806c872` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.GlobalLock` | A15 `a300462b071d715b0791992b55a0c4fe5899748b1c249c4576b00ebdc575e9f0`<br>A61 `a300462b071d715b0791992b55a0c4fe5899748b1c249c4576b00ebdc575e9f0` | `a300462b071d715b0791992b55a0c4fe5899748b1c249c4576b00ebdc575e9f0` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.HPOcclusionTest` | A15 `e5f618d4e28df08c1663b7c5b63dede96406dbd5bd092bfae67e1cd43f423014`<br>A61 `e5f618d4e28df08c1663b7c5b63dede96406dbd5bd092bfae67e1cd43f423014` | `e5f618d4e28df08c1663b7c5b63dede96406dbd5bd092bfae67e1cd43f423014` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.IBMRasterposClip` | A15 `047800dd63c9449eec2576bc91e43a10bda61ad84fdd17b4083ab245abb5373e`<br>A61 `047800dd63c9449eec2576bc91e43a10bda61ad84fdd17b4083ab245abb5373e` | `047800dd63c9449eec2576bc91e43a10bda61ad84fdd17b4083ab245abb5373e` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.InputImplementation` | A15 `ac55c96e6410d54e14bdb2644c83707ae885aa2056c2bbb9535c84acd8b00b76`<br>A61 `ac55c96e6410d54e14bdb2644c83707ae885aa2056c2bbb9535c84acd8b00b76` | `ac55c96e6410d54e14bdb2644c83707ae885aa2056c2bbb9535c84acd8b00b76` / A15, A61 | COMPATIBLE | 19 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.KeyboardEventQueue` | A15 `c372ea4bfb85603d3ecc032cd67d364e666bc6fceb39c4c15ad7d745b6a22b52`<br>A61 `c372ea4bfb85603d3ecc032cd67d364e666bc6fceb39c4c15ad7d745b6a22b52` | `c372ea4bfb85603d3ecc032cd67d364e666bc6fceb39c4c15ad7d745b6a22b52` / A15, A61 | COMPATIBLE | 21 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.LinuxAWTGLCanvasPeerInfo` | A15 `4616dff31d3d8f6b4c05696750c07eb1d31d6c58f11376f2751f2955f0728490`<br>A61 `4616dff31d3d8f6b4c05696750c07eb1d31d6c58f11376f2751f2955f0728490` | `4616dff31d3d8f6b4c05696750c07eb1d31d6c58f11376f2751f2955f0728490` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.LinuxCanvasImplementation$1` | A15 `161ab48f8a8dea745dd2820981f6827da2a9ba4b719116b435feb16802b15c9c`<br>A61 `161ab48f8a8dea745dd2820981f6827da2a9ba4b719116b435feb16802b15c9c` | `161ab48f8a8dea745dd2820981f6827da2a9ba4b719116b435feb16802b15c9c` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.LinuxCanvasImplementation$2` | A15 `f40a61000bd7b4f39e83ca3bae4bc7159c765aeece35d0db4ece2982b8b22810`<br>A61 `f40a61000bd7b4f39e83ca3bae4bc7159c765aeece35d0db4ece2982b8b22810` | `f40a61000bd7b4f39e83ca3bae4bc7159c765aeece35d0db4ece2982b8b22810` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.LinuxCanvasImplementation` | A15 `6b766f501526ee9048d76988c4263562ceefc6bf06b8991a215d692ee16aa2d7`<br>A61 `6b766f501526ee9048d76988c4263562ceefc6bf06b8991a215d692ee16aa2d7` | `6b766f501526ee9048d76988c4263562ceefc6bf06b8991a215d692ee16aa2d7` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.LinuxContextImplementation` | A15 `50429690b2669a6f68f640b94f50840e01c920c6d5e689100014b8ca39e3f9de`<br>A61 `584206c4f7438989e0c32e382a850dd815f629fc0db180d77bd83d1937a7c00b` | `50429690b2669a6f68f640b94f50840e01c920c6d5e689100014b8ca39e3f9de` / A15 | COMPATIBLE | 10 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay$1` | A15 `50b82320bfeaec7bdb07a052f7c795ac23d174eeecb02d64104cc59eed8a9836`<br>A61 `4127022cd6c88b939315af9f546e402a920d4ce1464f89cc36032c00488b2140` | `50b82320bfeaec7bdb07a052f7c795ac23d174eeecb02d64104cc59eed8a9836` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay$2` | A15 `c561366a2b89d3d3e0b505b3c2066ad6c9487f2bc69b6c96f0c9db3c4b1357fd`<br>A61 `6e47028c79fa8b685cf673162bfd0caf3b0fa13a1679b82a6c68d25f18c4b9c5` | `c561366a2b89d3d3e0b505b3c2066ad6c9487f2bc69b6c96f0c9db3c4b1357fd` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay$3` | A15 `d9732db70395489c1f060412456703bb12adba962f8155b6b2f3b44fe12eeebb`<br>A61 `78501c4ea2ac1be70569be77cd17414a5541fe09030cf286a4740b991ecd2c8e` | `d9732db70395489c1f060412456703bb12adba962f8155b6b2f3b44fe12eeebb` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay$4` | A15 `ccfbd13ea9898d91ca6684f50e31c6dfc1204d4c79460e39e079c988771fad33`<br>A61 `2022ce12a63e26b16171ec7512ff6d73d29389f7bf1320023c144aa02496ee5b` | `ccfbd13ea9898d91ca6684f50e31c6dfc1204d4c79460e39e079c988771fad33` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay$Compiz$1$1` | A15 `ccc2b48e3cd4d1dfd5ba00157fb3d74744cf8223baf798c92ab06bd485ded680`<br>A61 `10aa804eb0a82da3a28b62289e80c10eb591594024f3cb2b21d1eefaf268460e` | `ccc2b48e3cd4d1dfd5ba00157fb3d74744cf8223baf798c92ab06bd485ded680` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay$Compiz$1$2` | A15 `4238d6052f011b21423db75c2673819b39cbc6f745393440cef13afd85c4e3eb`<br>A61 `929ebb76c8a286fec100eecc1cc97189653bc65130996abca1b181c2f3763ff0` | `4238d6052f011b21423db75c2673819b39cbc6f745393440cef13afd85c4e3eb` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay$Compiz$1` | A15 `6305af115f6a3e1cb4647153452a81e23e83159d3198762121d0337c2976d071`<br>A61 `4f0b73902003bb181397477bf59718d5426fc990e3a2a5d3f51096782ea7c073` | `6305af115f6a3e1cb4647153452a81e23e83159d3198762121d0337c2976d071` / A15 | COMPATIBLE | 1 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay$Compiz$2` | A15 `820bee50ce4ee75fc80f4c81fcacaab18a9efce8be31461e6bc200fce26bb53a`<br>A61 `8225884ef7122f066b36fb519931af4b187db7d68257d517075f501d38b19a9e` | `820bee50ce4ee75fc80f4c81fcacaab18a9efce8be31461e6bc200fce26bb53a` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay$Compiz$Provider` | A15 `7c47c448434040415316f38b0d964f98a62e22f29ab0074899a826857843aefd`<br>A61 `7c47c448434040415316f38b0d964f98a62e22f29ab0074899a826857843aefd` | `7c47c448434040415316f38b0d964f98a62e22f29ab0074899a826857843aefd` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.LinuxDisplay$Compiz` | A15 `3ed0b180482ac67e4aa67a770020f37245a53dcbd9682536b140685852701298`<br>A61 `800d6febf9f5150aa1ae9185984737b3606bc5f17e33420e7017fed0796dcb03` | `3ed0b180482ac67e4aa67a770020f37245a53dcbd9682536b140685852701298` / A15 | COMPATIBLE | 11 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplay` | A15 `c2c316f351b20619fe85d098a3dbee4bb72f61c56e00a43905a844cbd5b220a1`<br>A61 `5ab31cde1529df0acc0c1ac646d49dd6d20e8ab8aa8924cfcdcd804c23d2180a` | `c2c316f351b20619fe85d098a3dbee4bb72f61c56e00a43905a844cbd5b220a1` / A15 | COMPATIBLE | 143 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxDisplayPeerInfo` | A15 `0458be449f1fb3aaacbf3b022002993cd9ba5d02075572d100d3e3201fb96bd2`<br>A61 `0458be449f1fb3aaacbf3b022002993cd9ba5d02075572d100d3e3201fb96bd2` | `0458be449f1fb3aaacbf3b022002993cd9ba5d02075572d100d3e3201fb96bd2` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.LinuxEvent` | A15 `33c4745af2f03f38a8f0cdef8271f375ad188d369a360ee0c8e39dcc120b1154`<br>A61 `ca2c4f1a581c93fc7e3362648355f39e731e1d5e2817b11ed313a23590464144` | `33c4745af2f03f38a8f0cdef8271f375ad188d369a360ee0c8e39dcc120b1154` / A15 | COMPATIBLE | 53 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxKeyboard` | A15 `4de50d3bc5fd8a509547f9f60c91ca18dec421321b492bc2a9260bd95bf44d4e`<br>A61 `8f7b4025f78d4013c2a8684a0081fb45bd094fe9732abda93d92ff9d23b006c9` | `4de50d3bc5fd8a509547f9f60c91ca18dec421321b492bc2a9260bd95bf44d4e` / A15 | COMPATIBLE | 55 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxKeycodes` | A15 `713870536d273f916bd04f1367305867055fc92a90152db68a26fd9c46b061f2`<br>A61 `713870536d273f916bd04f1367305867055fc92a90152db68a26fd9c46b061f2` | `713870536d273f916bd04f1367305867055fc92a90152db68a26fd9c46b061f2` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.LinuxMouse` | A15 `b762090b1500c68e3632bc23db35cc23af98b11657311f4afef656646b99390d`<br>A61 `ca417dbfdf28eea313c641568366f1b4e632ff0aee5fc4fcc2cafbb1f3308586` | `b762090b1500c68e3632bc23db35cc23af98b11657311f4afef656646b99390d` / A15 | COMPATIBLE | 41 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.LinuxPbufferPeerInfo` | A15 `5b6152df76f7d5abd1e6a25257a0fe2328955e79fdf5494fb8eb0c62f3a847af`<br>A61 `5b6152df76f7d5abd1e6a25257a0fe2328955e79fdf5494fb8eb0c62f3a847af` | `5b6152df76f7d5abd1e6a25257a0fe2328955e79fdf5494fb8eb0c62f3a847af` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.LinuxPeerInfo` | A15 `a820ed2e185a653f65425639ce4091952f49c6dfd973e2bea02e0bc57ee8b8ba`<br>A61 `a820ed2e185a653f65425639ce4091952f49c6dfd973e2bea02e0bc57ee8b8ba` | `a820ed2e185a653f65425639ce4091952f49c6dfd973e2bea02e0bc57ee8b8ba` / A15, A61 | COMPATIBLE | 9 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.MacOSXAWTGLCanvasPeerInfo` | A15 `e3c0c2e3b0eabdc4a10d98dd30a521125017a6086f7a2d2b9986db66f198a0a6`<br>A61 `e3c0c2e3b0eabdc4a10d98dd30a521125017a6086f7a2d2b9986db66f198a0a6` | `e3c0c2e3b0eabdc4a10d98dd30a521125017a6086f7a2d2b9986db66f198a0a6` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.MacOSXCanvasImplementation` | A15 `e95d5778cbebbbfb55b3e9c3a55cbccd8ef8df10e89fcd882339f2fb6eaa4545`<br>A61 `e95d5778cbebbbfb55b3e9c3a55cbccd8ef8df10e89fcd882339f2fb6eaa4545` | `e95d5778cbebbbfb55b3e9c3a55cbccd8ef8df10e89fcd882339f2fb6eaa4545` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.MacOSXCanvasListener$1` | A15 `9a40de246665682c106beae7f55d9cbaa61463f74f41935468c9e9951fe96ad6`<br>A61 `9a40de246665682c106beae7f55d9cbaa61463f74f41935468c9e9951fe96ad6` | `9a40de246665682c106beae7f55d9cbaa61463f74f41935468c9e9951fe96ad6` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.MacOSXCanvasListener` | A15 `bea0fe19c23ea74c89b0438691511165c809fdbeccf21a0eb22899771aa5dfd3`<br>A61 `bea0fe19c23ea74c89b0438691511165c809fdbeccf21a0eb22899771aa5dfd3` | `bea0fe19c23ea74c89b0438691511165c809fdbeccf21a0eb22899771aa5dfd3` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.MacOSXCanvasPeerInfo` | A15 `21f60e7654d1acf8e82a76e461bd014bc6bcc6a6355c967787db17d958f760b5`<br>A61 `c8dab74a64f9fd00e5c658582d136196e609dec33f5a88ee1dc355bf37fbb6fd` | `21f60e7654d1acf8e82a76e461bd014bc6bcc6a6355c967787db17d958f760b5` / A15 | COMPATIBLE | 11 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.MacOSXContextImplementation` | A15 `a3564ae1ec6c6c0637c31cbd495641d2b6dcdabaf8d358af0f234b1f5ed5a5ef`<br>A61 `a3564ae1ec6c6c0637c31cbd495641d2b6dcdabaf8d358af0f234b1f5ed5a5ef` | `a3564ae1ec6c6c0637c31cbd495641d2b6dcdabaf8d358af0f234b1f5ed5a5ef` / A15, A61 | COMPATIBLE | 12 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.MacOSXDisplay` | A15 `bcf4f1ce3cf566da609e05c0c11b323cc737d0f86a4720c30d41f51d96e8e40f`<br>A61 `e9967314f2def901a000b63e4b9c5ab6d00a065f468bb39e47f9b1b636b8f234` | `bcf4f1ce3cf566da609e05c0c11b323cc737d0f86a4720c30d41f51d96e8e40f` / A15 | VIOLATION | 45 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.MacOSXDisplayPeerInfo` | A15 `5da749a9452a7da76e8d20b98e38c62728ada637f173aeab6e1421e2782f0ace`<br>A61 `5da749a9452a7da76e8d20b98e38c62728ada637f173aeab6e1421e2782f0ace` | `5da749a9452a7da76e8d20b98e38c62728ada637f173aeab6e1421e2782f0ace` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.MacOSXGLCanvas` | A15 `36f7b91d46c69fe39ff4c8209ce22fa8231bae26c8b3760f387b541d5e538925`<br>A61 `36f7b91d46c69fe39ff4c8209ce22fa8231bae26c8b3760f387b541d5e538925` | `36f7b91d46c69fe39ff4c8209ce22fa8231bae26c8b3760f387b541d5e538925` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.MacOSXMouseEventQueue` | A15 `b757b4edfcb5cba0e18b2bfd7d2449aa3c3a0cdefb729cc3be217c470090166d`<br>A61 `d6fc9ec5e406642f6c5c512473aea1e9189bc50147309efa319204ed080cec36` | `b757b4edfcb5cba0e18b2bfd7d2449aa3c3a0cdefb729cc3be217c470090166d` / A15 | COMPATIBLE | 18 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.MacOSXPbufferPeerInfo` | A15 `7be09488ded063d678a623ab76d179f92a9b3f48cc82046e9cd5592d4b83bd76`<br>A61 `7be09488ded063d678a623ab76d179f92a9b3f48cc82046e9cd5592d4b83bd76` | `7be09488ded063d678a623ab76d179f92a9b3f48cc82046e9cd5592d4b83bd76` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.MacOSXPeerInfo` | A15 `dcb1b21cc1d22184e84d5172cfd0ddd29eee0db160ae1ddfa873eba438672940`<br>A61 `eaf657f5ef68586a9e64ef00788d1e2ae12f69a9675fb53d26bfcf5a8a254e17` | `dcb1b21cc1d22184e84d5172cfd0ddd29eee0db160ae1ddfa873eba438672940` / A15 | COMPATIBLE | 6 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.MouseEventQueue` | A15 `05c3a6c8e1a4e2d3d152e80a5f382953928bddfe2603a51628ef0350ef5c1bf9`<br>A61 `dc0a7e1082719f6c9877f039b49a1f405da320889161574468cf3b573862751b` | `05c3a6c8e1a4e2d3d152e80a5f382953928bddfe2603a51628ef0350ef5c1bf9` / A15 | COMPATIBLE | 25 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.NVConditionalRender` | A15 `683f48703f0d6671a79ab75281705f9fd29512e9596380b90f90d9270e328480`<br>A61 `683f48703f0d6671a79ab75281705f9fd29512e9596380b90f90d9270e328480` | `683f48703f0d6671a79ab75281705f9fd29512e9596380b90f90d9270e328480` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVCopyDepthToColor` | A15 `90c2a216d914686762891ae4b10e76b5adfe72edf490cafc642cc72638fbe16b`<br>A61 `90c2a216d914686762891ae4b10e76b5adfe72edf490cafc642cc72638fbe16b` | `90c2a216d914686762891ae4b10e76b5adfe72edf490cafc642cc72638fbe16b` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVCopyImage` | A15 `abe65179d51f89f9ebafd1ac15b2dc85509676cbcd7759b08829536e365141ae`<br>A61 `abe65179d51f89f9ebafd1ac15b2dc85509676cbcd7759b08829536e365141ae` | `abe65179d51f89f9ebafd1ac15b2dc85509676cbcd7759b08829536e365141ae` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVDepthBufferFloat` | A15 `5958e1a7f4e7feca5f3bad5f897584e924a79d979f5c897ee66270f58d2841cc`<br>A61 `5958e1a7f4e7feca5f3bad5f897584e924a79d979f5c897ee66270f58d2841cc` | `5958e1a7f4e7feca5f3bad5f897584e924a79d979f5c897ee66270f58d2841cc` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVDepthClamp` | A15 `d269288016963eb971abdd748dc1a500893f61ee43bd27e7ad04da25e725b157`<br>A61 `d269288016963eb971abdd748dc1a500893f61ee43bd27e7ad04da25e725b157` | `d269288016963eb971abdd748dc1a500893f61ee43bd27e7ad04da25e725b157` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVEvaluators` | A15 `5af5580f1f623f045cd1b73a89eeaa329293b98105105f541b5c927548ebbed2`<br>A61 `5af5580f1f623f045cd1b73a89eeaa329293b98105105f541b5c927548ebbed2` | `5af5580f1f623f045cd1b73a89eeaa329293b98105105f541b5c927548ebbed2` / A15, A61 | COMPATIBLE | 9 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVExplicitMultisample` | A15 `52a8f573f20d435a7f92c8c38064a1914b2a9ee3042d8bff944059f630e0fb2e`<br>A61 `52a8f573f20d435a7f92c8c38064a1914b2a9ee3042d8bff944059f630e0fb2e` | `52a8f573f20d435a7f92c8c38064a1914b2a9ee3042d8bff944059f630e0fb2e` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVFence` | A15 `53556622b721e7096912098efe42fede61f9ee8b634bc03bc8f20568c856c32f`<br>A61 `53556622b721e7096912098efe42fede61f9ee8b634bc03bc8f20568c856c32f` | `53556622b721e7096912098efe42fede61f9ee8b634bc03bc8f20568c856c32f` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVFloatBuffer` | A15 `daf4d71d013edc2332fe04883d7c3ce1ff7887e04205ecc780567c329c956fd9`<br>A61 `daf4d71d013edc2332fe04883d7c3ce1ff7887e04205ecc780567c329c956fd9` | `daf4d71d013edc2332fe04883d7c3ce1ff7887e04205ecc780567c329c956fd9` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVFogDistance` | A15 `cff929b13d0974f157c5def8ecca4d32d511c3cf865c49a1bdb20e3077803644`<br>A61 `cff929b13d0974f157c5def8ecca4d32d511c3cf865c49a1bdb20e3077803644` | `cff929b13d0974f157c5def8ecca4d32d511c3cf865c49a1bdb20e3077803644` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVFragmentProgram` | A15 `96fbfe4c58b77fe9a77a29517b586cb2ffac9ffaac5d35f8811b5d46e9d60701`<br>A61 `96fbfe4c58b77fe9a77a29517b586cb2ffac9ffaac5d35f8811b5d46e9d60701` | `96fbfe4c58b77fe9a77a29517b586cb2ffac9ffaac5d35f8811b5d46e9d60701` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVFragmentProgram2` | A15 `50af35f4dcb1e340606eb9695e1937bf8a8c9b1ef3c321896979f0d708cb7cc3`<br>A61 `50af35f4dcb1e340606eb9695e1937bf8a8c9b1ef3c321896979f0d708cb7cc3` | `50af35f4dcb1e340606eb9695e1937bf8a8c9b1ef3c321896979f0d708cb7cc3` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVFramebufferMultisampleCoverage` | A15 `9bc67add4cdf732420eae3bac08891240cc1e582b051054e59d620e705c777ba`<br>A61 `9bc67add4cdf732420eae3bac08891240cc1e582b051054e59d620e705c777ba` | `9bc67add4cdf732420eae3bac08891240cc1e582b051054e59d620e705c777ba` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVGeometryProgram4` | A15 `7a4d3b22368b83cd2df0a8161ded77a40fb6e09e4057b8f0c771b97142c011d4`<br>A61 `7a4d3b22368b83cd2df0a8161ded77a40fb6e09e4057b8f0c771b97142c011d4` | `7a4d3b22368b83cd2df0a8161ded77a40fb6e09e4057b8f0c771b97142c011d4` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVGpuProgram4` | A15 `00b453069e73a968f2d740550f92059d948db963dabe0643f2d8b5df506132da`<br>A61 `00b453069e73a968f2d740550f92059d948db963dabe0643f2d8b5df506132da` | `00b453069e73a968f2d740550f92059d948db963dabe0643f2d8b5df506132da` / A15, A61 | COMPATIBLE | 16 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVGpuProgram5` | A15 `a93121f07cc9f9b98fb1411fc0b3e7185a06b7086869b91b3072b15355c5a3a1`<br>A61 `a93121f07cc9f9b98fb1411fc0b3e7185a06b7086869b91b3072b15355c5a3a1` | `a93121f07cc9f9b98fb1411fc0b3e7185a06b7086869b91b3072b15355c5a3a1` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVGpuShader5` | A15 `5dc5e35fd946d6717b3b0b8568e83d8219d27a1ed0a234d91db207f2af457083`<br>A61 `5dc5e35fd946d6717b3b0b8568e83d8219d27a1ed0a234d91db207f2af457083` | `5dc5e35fd946d6717b3b0b8568e83d8219d27a1ed0a234d91db207f2af457083` / A15, A61 | COMPATIBLE | 35 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVHalfFloat` | A15 `4bfabc997b63cd648c89c0cadd9aefca9b705d8d166b5e3d127d25afbfc36fb7`<br>A61 `4bfabc997b63cd648c89c0cadd9aefca9b705d8d166b5e3d127d25afbfc36fb7` | `4bfabc997b63cd648c89c0cadd9aefca9b705d8d166b5e3d127d25afbfc36fb7` / A15, A61 | COMPATIBLE | 25 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVLightMaxExponent` | A15 `5b6c5ad387e2c7da3091e1366a5c5732a8f35df569b182729945cacee3b12173`<br>A61 `5b6c5ad387e2c7da3091e1366a5c5732a8f35df569b182729945cacee3b12173` | `5b6c5ad387e2c7da3091e1366a5c5732a8f35df569b182729945cacee3b12173` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVMultisampleCoverage` | A15 `72c10c25eb821b400924d8a5cd19679dc5a1d7560bba01f885e2c7ad3566d7b6`<br>A61 `72c10c25eb821b400924d8a5cd19679dc5a1d7560bba01f885e2c7ad3566d7b6` | `72c10c25eb821b400924d8a5cd19679dc5a1d7560bba01f885e2c7ad3566d7b6` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVMultisampleFilterHint` | A15 `3700a4cf720e4b293ec684334f4c9ee7b101d9ef096ebfe728d81d3d130bb586`<br>A61 `3700a4cf720e4b293ec684334f4c9ee7b101d9ef096ebfe728d81d3d130bb586` | `3700a4cf720e4b293ec684334f4c9ee7b101d9ef096ebfe728d81d3d130bb586` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVOcclusionQuery` | A15 `81b3e644f283d4d3d73e4c196010245b2ffdb18db260e29b0d0dff016aec14ad`<br>A61 `81b3e644f283d4d3d73e4c196010245b2ffdb18db260e29b0d0dff016aec14ad` | `81b3e644f283d4d3d73e4c196010245b2ffdb18db260e29b0d0dff016aec14ad` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVPackedDepthStencil` | A15 `506a48265deeef724c6db4a1907935b6b5a5269cd030e4a26fce3dec150b3966`<br>A61 `506a48265deeef724c6db4a1907935b6b5a5269cd030e4a26fce3dec150b3966` | `506a48265deeef724c6db4a1907935b6b5a5269cd030e4a26fce3dec150b3966` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVParameterBufferObject` | A15 `586478e7c3f01f7cad62792282c727726d13d8a27e416da6eec6c539e9801099`<br>A61 `586478e7c3f01f7cad62792282c727726d13d8a27e416da6eec6c539e9801099` | `586478e7c3f01f7cad62792282c727726d13d8a27e416da6eec6c539e9801099` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVPathRendering` | A15 `699ccd5db7a73014137053c2193612d0c99511c632bad7f41c1c4f407f73c14f`<br>A61 `9f0d0431ecf2bcc82a07cdd8d88e88c628b5e3cd1d109fffad4b062ef689c3b1` | `699ccd5db7a73014137053c2193612d0c99511c632bad7f41c1c4f407f73c14f` / A15 | COMPATIBLE | 49 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.NVPixelDataRange` | A15 `6e78822d7ef22918857591b4d7dd7ebebee8a100ee982fb9843036fe783d69c3`<br>A61 `6e78822d7ef22918857591b4d7dd7ebebee8a100ee982fb9843036fe783d69c3` | `6e78822d7ef22918857591b4d7dd7ebebee8a100ee982fb9843036fe783d69c3` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVPointSprite` | A15 `59f38c3b611685e327f728797a81af8ccdc4544999dab7e1c3f0eb613b9da552`<br>A61 `59f38c3b611685e327f728797a81af8ccdc4544999dab7e1c3f0eb613b9da552` | `59f38c3b611685e327f728797a81af8ccdc4544999dab7e1c3f0eb613b9da552` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVPresentVideo` | A15 `93cbee927a176573c14b28b261fb8ae9d1f2c0366586774e4f43119a7344b9e6`<br>A61 `3fbd756354bda4c77701b2dea9c22a6d3d7f19eaf78efcd3d961e934e6a78a46` | `93cbee927a176573c14b28b261fb8ae9d1f2c0366586774e4f43119a7344b9e6` / A15 | COMPATIBLE | 6 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.NVPresentVideoUtil` | A15 `fb84180f9141de65467a4eae88045c6f1e94dd282dc68c02e7f8dc2c6a97b681`<br>A61 `fb84180f9141de65467a4eae88045c6f1e94dd282dc68c02e7f8dc2c6a97b681` | `fb84180f9141de65467a4eae88045c6f1e94dd282dc68c02e7f8dc2c6a97b681` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVPrimitiveRestart` | A15 `97df1a8cb9465250d9fc25dba681498c92d8bcf9778a19830c3a6773df198724`<br>A61 `97df1a8cb9465250d9fc25dba681498c92d8bcf9778a19830c3a6773df198724` | `97df1a8cb9465250d9fc25dba681498c92d8bcf9778a19830c3a6773df198724` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVProgram` | A15 `193b5cf3be611f0dc3d43dfdac0f35a985d9afbcb8115064a79e7ea8f6d3d369`<br>A61 `7f9bf5d6fd8bf2cc3a7b5f621c18a6f44bdc0eee39009f1997a28033166aed34` | `193b5cf3be611f0dc3d43dfdac0f35a985d9afbcb8115064a79e7ea8f6d3d369` / A15 | COMPATIBLE | 11 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.NVRegisterCombiners` | A15 `527ac227c169cbc5c01bd9f1948f69b21ff688b61cb0079244051c8e0ab2d4ac`<br>A61 `527ac227c169cbc5c01bd9f1948f69b21ff688b61cb0079244051c8e0ab2d4ac` | `527ac227c169cbc5c01bd9f1948f69b21ff688b61cb0079244051c8e0ab2d4ac` / A15, A61 | COMPATIBLE | 13 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVRegisterCombiners2` | A15 `874b972e8442102ecc564888e8fb97cc4679e329b34b8f0380ae3f6785e16b8e`<br>A61 `874b972e8442102ecc564888e8fb97cc4679e329b34b8f0380ae3f6785e16b8e` | `874b972e8442102ecc564888e8fb97cc4679e329b34b8f0380ae3f6785e16b8e` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVShaderBufferLoad` | A15 `7c011d521db18d1cec8dba0b3cdf3aaf54e06a5643939970e97c97636a84a103`<br>A61 `9f53fdc0c8f32c561f4838b6d6c6b632887bf5a9af3479a3e37413ef6ae5d533` | `7c011d521db18d1cec8dba0b3cdf3aaf54e06a5643939970e97c97636a84a103` / A15 | COMPATIBLE | 13 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.NVShaderBufferStore` | A15 `d6fe1c071562335ba74d882525971b3c471a45d683b481e8f2e2c292957f5dbd`<br>A61 `495aa2d2e94a179297c3b8c7d8bf4280b059c4a18df6554d0e2addf7c598d0e5` | `d6fe1c071562335ba74d882525971b3c471a45d683b481e8f2e2c292957f5dbd` / A15 | UNKNOWN | 0 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.NVTessellationProgram5` | A15 `0a6447d26e9697773d50d62fb1e9812b91d428bcad3e6b9e8ead54a58a2a1fef`<br>A61 `0a6447d26e9697773d50d62fb1e9812b91d428bcad3e6b9e8ead54a58a2a1fef` | `0a6447d26e9697773d50d62fb1e9812b91d428bcad3e6b9e8ead54a58a2a1fef` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTexgenReflection` | A15 `f9b2942dc6a9ae09cc7e14149019b8e1df08401f0c4a753a18a9e7b80bb7061b`<br>A61 `f9b2942dc6a9ae09cc7e14149019b8e1df08401f0c4a753a18a9e7b80bb7061b` | `f9b2942dc6a9ae09cc7e14149019b8e1df08401f0c4a753a18a9e7b80bb7061b` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTextureBarrier` | A15 `fd9a3b1d8f3d47501e44ea8ee2f834c406f4d067f4b8a3a94dc41d3554793213`<br>A61 `fd9a3b1d8f3d47501e44ea8ee2f834c406f4d067f4b8a3a94dc41d3554793213` | `fd9a3b1d8f3d47501e44ea8ee2f834c406f4d067f4b8a3a94dc41d3554793213` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTextureCompressionVTC` | A15 `03adcb56112d31deb5a2af0d60bb618fc9a4e2db948bbff197d3f4ad76b02636`<br>A61 `03adcb56112d31deb5a2af0d60bb618fc9a4e2db948bbff197d3f4ad76b02636` | `03adcb56112d31deb5a2af0d60bb618fc9a4e2db948bbff197d3f4ad76b02636` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTextureEnvCombine4` | A15 `c78b61be256419af672b1f78110b8c56be733e16f75ff7c4c794d12b8a1ee715`<br>A61 `c78b61be256419af672b1f78110b8c56be733e16f75ff7c4c794d12b8a1ee715` | `c78b61be256419af672b1f78110b8c56be733e16f75ff7c4c794d12b8a1ee715` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTextureExpandNormal` | A15 `9b6b5788833c9ba4c31d0321e1a1e3fbc177baf0a858521f0fb9e28a8f60c16e`<br>A61 `9b6b5788833c9ba4c31d0321e1a1e3fbc177baf0a858521f0fb9e28a8f60c16e` | `9b6b5788833c9ba4c31d0321e1a1e3fbc177baf0a858521f0fb9e28a8f60c16e` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTextureMultisample` | A15 `6792c1cb38844538a985c3e365769c6183282bbcf121221ca9b85488b4bef70b`<br>A61 `6792c1cb38844538a985c3e365769c6183282bbcf121221ca9b85488b4bef70b` | `6792c1cb38844538a985c3e365769c6183282bbcf121221ca9b85488b4bef70b` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTextureRectangle` | A15 `a87207c40dbcd010a6071de9dd31a575712dab0a3a0b011c46b5d6024e4d852d`<br>A61 `a87207c40dbcd010a6071de9dd31a575712dab0a3a0b011c46b5d6024e4d852d` | `a87207c40dbcd010a6071de9dd31a575712dab0a3a0b011c46b5d6024e4d852d` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTextureShader` | A15 `26990a6578ef89f5eeeaf9b438035576162a803c3d4577919353ea85cac04efd`<br>A61 `26990a6578ef89f5eeeaf9b438035576162a803c3d4577919353ea85cac04efd` | `26990a6578ef89f5eeeaf9b438035576162a803c3d4577919353ea85cac04efd` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTextureShader2` | A15 `294ccaa8376156771631b8f11f0b575bbd021f3640860a0d4c185b44c62fc2f3`<br>A61 `294ccaa8376156771631b8f11f0b575bbd021f3640860a0d4c185b44c62fc2f3` | `294ccaa8376156771631b8f11f0b575bbd021f3640860a0d4c185b44c62fc2f3` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTextureShader3` | A15 `092060a4641aba8409e2eaece857a4752ae3ed3a9bcae73ab67c1ab84e34bca4`<br>A61 `092060a4641aba8409e2eaece857a4752ae3ed3a9bcae73ab67c1ab84e34bca4` | `092060a4641aba8409e2eaece857a4752ae3ed3a9bcae73ab67c1ab84e34bca4` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTransformFeedback` | A15 `a86d5e1172d511750bee3028df02fe0f56614b523809b399f7dd4197471f2830`<br>A61 `a86d5e1172d511750bee3028df02fe0f56614b523809b399f7dd4197471f2830` | `a86d5e1172d511750bee3028df02fe0f56614b523809b399f7dd4197471f2830` / A15, A61 | COMPATIBLE | 11 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVTransformFeedback2` | A15 `7d536cf1be65b9a5921e65702b7099e8bde66a897e1ed321e3e39aac982bccbe`<br>A61 `7d536cf1be65b9a5921e65702b7099e8bde66a897e1ed321e3e39aac982bccbe` | `7d536cf1be65b9a5921e65702b7099e8bde66a897e1ed321e3e39aac982bccbe` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVVertexArrayRange` | A15 `b55c942dfe29fb0b781b1b8fce5d4ea7e07192ed3cb163bb61f536c95d626b53`<br>A61 `b55c942dfe29fb0b781b1b8fce5d4ea7e07192ed3cb163bb61f536c95d626b53` | `b55c942dfe29fb0b781b1b8fce5d4ea7e07192ed3cb163bb61f536c95d626b53` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVVertexArrayRange2` | A15 `9f3484024050bac624ca9bf1b3924402d8ae1d5d499faee42f0e8689aab366ad`<br>A61 `9f3484024050bac624ca9bf1b3924402d8ae1d5d499faee42f0e8689aab366ad` | `9f3484024050bac624ca9bf1b3924402d8ae1d5d499faee42f0e8689aab366ad` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVVertexAttribInteger64bit` | A15 `eefc87e7ab08b126aabafb91b429194cc82fb457191800dedb5b3e523d6bfbaf`<br>A61 `eefc87e7ab08b126aabafb91b429194cc82fb457191800dedb5b3e523d6bfbaf` | `eefc87e7ab08b126aabafb91b429194cc82fb457191800dedb5b3e523d6bfbaf` / A15, A61 | COMPATIBLE | 19 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVVertexBufferUnifiedMemory` | A15 `b9fd0885879c9e512743ce0a49db3213d8e602e8b3842547fa24ed21cb1e5fc0`<br>A61 `4f2f1dc419fdef149706a86a772f1cd4b26917e66b727850fc181fe9ad4a0a29` | `b9fd0885879c9e512743ce0a49db3213d8e602e8b3842547fa24ed21cb1e5fc0` / A15 | COMPATIBLE | 12 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.NVVertexProgram` | A15 `836c7dd6f2a5ba54ba04b811785f2f146272f47b0ad72777584849e9066e4409`<br>A61 `836c7dd6f2a5ba54ba04b811785f2f146272f47b0ad72777584849e9066e4409` | `836c7dd6f2a5ba54ba04b811785f2f146272f47b0ad72777584849e9066e4409` / A15, A61 | COMPATIBLE | 40 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVVertexProgram2Option` | A15 `82954fde6dcd1a086af1c757288d65ecbf73141660ef9d63d0c55dd0eed947a9`<br>A61 `82954fde6dcd1a086af1c757288d65ecbf73141660ef9d63d0c55dd0eed947a9` | `82954fde6dcd1a086af1c757288d65ecbf73141660ef9d63d0c55dd0eed947a9` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVVertexProgram3` | A15 `234fa63426018c890a10917e8157023db516d585e3649205c8d9909386c95770`<br>A61 `234fa63426018c890a10917e8157023db516d585e3649205c8d9909386c95770` | `234fa63426018c890a10917e8157023db516d585e3649205c8d9909386c95770` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVVideoCapture` | A15 `af546abb28786cff1ad998fa3b3cd82a4e5849c006222c138d82bae1a0f97559`<br>A61 `9990e4fac97dbe14b0f6d4f5c74f821b24fd1697f17578cf0bf25600e89db88e` | `af546abb28786cff1ad998fa3b3cd82a4e5849c006222c138d82bae1a0f97559` / A15 | COMPATIBLE | 12 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.NVVideoCaptureUtil` | A15 `69d3ae20229ce3c1f2d975bf6062c6ab4f3a11f5d6a36eedb30acc23750129cf`<br>A61 `69d3ae20229ce3c1f2d975bf6062c6ab4f3a11f5d6a36eedb30acc23750129cf` | `69d3ae20229ce3c1f2d975bf6062c6ab4f3a11f5d6a36eedb30acc23750129cf` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.NVXGpuMemoryInfo` | A15 `588e8c1c206690e8b57586e440ef0476fc68eb929777d52313be850cba834571`<br>A61 `588e8c1c206690e8b57586e440ef0476fc68eb929777d52313be850cba834571` | `588e8c1c206690e8b57586e440ef0476fc68eb929777d52313be850cba834571` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.OpenGLException` | A15 `49b0ee02dc60a9b58a93124895c58c26ed035ba4884dc18db93dbb5cf85882ad`<br>A61 `49b0ee02dc60a9b58a93124895c58c26ed035ba4884dc18db93dbb5cf85882ad` | `49b0ee02dc60a9b58a93124895c58c26ed035ba4884dc18db93dbb5cf85882ad` / A15, A61 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.Pbuffer` | A15 `0934f74454aaefceb875d566e13de3294c17736b4e5d469ff9f747cfbd59db34`<br>A61 `0934f74454aaefceb875d566e13de3294c17736b4e5d469ff9f747cfbd59db34` | `0934f74454aaefceb875d566e13de3294c17736b4e5d469ff9f747cfbd59db34` / A15, A61 | COMPATIBLE | 8 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.PeerInfo` | A15 `292959e959ca52c291a6d04250ed16523721c92b4fafd48d898c571b6f23ee7f`<br>A61 `292959e959ca52c291a6d04250ed16523721c92b4fafd48d898c571b6f23ee7f` | `292959e959ca52c291a6d04250ed16523721c92b4fafd48d898c571b6f23ee7f` / A15, A61 | COMPATIBLE | 10 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.PixelFormat` | A15 `0592d6f58f4b689b4f57e6f779fd124715c62f7d87f081c35755872bb41980fd`<br>A61 `0592d6f58f4b689b4f57e6f779fd124715c62f7d87f081c35755872bb41980fd` | `0592d6f58f4b689b4f57e6f779fd124715c62f7d87f081c35755872bb41980fd` / A15, A61 | COMPATIBLE | 23 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.PixelFormatLWJGL` | A15 `f1044709a41e5561e79f8a0ec470d920db66bb3a03c3f72fb701d64cd212fe1a`<br>A61 `f1044709a41e5561e79f8a0ec470d920db66bb3a03c3f72fb701d64cd212fe1a` | `f1044709a41e5561e79f8a0ec470d920db66bb3a03c3f72fb701d64cd212fe1a` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.References` | A15 `f201ae932762b1f6f52fb1e358b5514280e1e69301d607c171b30f9bdd465e3c`<br>A61 `bf1aa03be4d8a71da2b1655c23e1865c197f0ad48679739cc7da63ed969f4508` | `f201ae932762b1f6f52fb1e358b5514280e1e69301d607c171b30f9bdd465e3c` / A15 | COMPATIBLE | 22 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.ReferencesStack` | A15 `9ea2f78cd0b29402ccbb897b0a20bdf4d47cafd571d78f302bdf4dcbe4d1ec7a`<br>A61 `9ea2f78cd0b29402ccbb897b0a20bdf4d47cafd571d78f302bdf4dcbe4d1ec7a` | `9ea2f78cd0b29402ccbb897b0a20bdf4d47cafd571d78f302bdf4dcbe4d1ec7a` / A15, A61 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.RenderTexture` | A15 `95956e3a7c6f5e37618b5d75b75f8d0236940c63bd128cf5151cdee5ca39e37a`<br>A61 `95956e3a7c6f5e37618b5d75b75f8d0236940c63bd128cf5151cdee5ca39e37a` | `95956e3a7c6f5e37618b5d75b75f8d0236940c63bd128cf5151cdee5ca39e37a` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.SGISGenerateMipmap` | A15 `25ca766adc7ac17c0cd3b396b805b4603991305e6201d259290bf14f0942c04f`<br>A61 `25ca766adc7ac17c0cd3b396b805b4603991305e6201d259290bf14f0942c04f` | `25ca766adc7ac17c0cd3b396b805b4603991305e6201d259290bf14f0942c04f` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.SGISTextureLOD` | A15 `54c7ba8538e7cc40f90a585610de2b9c49dfa5d87218fdc0161223273f3b7b9e`<br>A61 `54c7ba8538e7cc40f90a585610de2b9c49dfa5d87218fdc0161223273f3b7b9e` | `54c7ba8538e7cc40f90a585610de2b9c49dfa5d87218fdc0161223273f3b7b9e` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.SUNSliceAccum` | A15 `99952b412796633e737495646044ff8696af8c8f74d48d944d70415dc89d8e6e`<br>A61 `99952b412796633e737495646044ff8696af8c8f74d48d944d70415dc89d8e6e` | `99952b412796633e737495646044ff8696af8c8f74d48d944d70415dc89d8e6e` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.SharedDrawable` | A15 `5db9ea704f69fb49294d213c738c45ace78ef93cfc6c3fa7a0d9cadcb24c02b2`<br>A61 `5db9ea704f69fb49294d213c738c45ace78ef93cfc6c3fa7a0d9cadcb24c02b2` | `5db9ea704f69fb49294d213c738c45ace78ef93cfc6c3fa7a0d9cadcb24c02b2` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.StateStack` | A15 `99bd740a4fba4922279e72ee72b9190b01f6dd1dfcb98fc40d8fde751787832a`<br>A61 `99bd740a4fba4922279e72ee72b9190b01f6dd1dfcb98fc40d8fde751787832a` | `99bd740a4fba4922279e72ee72b9190b01f6dd1dfcb98fc40d8fde751787832a` / A15, A61 | COMPATIBLE | 6 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.StateTracker$1` | A15 `0d298fb002b5467622fa74a87a00a87576242b2600b2f43f97b4a258ab482935`<br>A61 `0d298fb002b5467622fa74a87a00a87576242b2600b2f43f97b4a258ab482935` | `0d298fb002b5467622fa74a87a00a87576242b2600b2f43f97b4a258ab482935` / A15, A61 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.StateTracker$VAOState` | A15 `295b81e218e3459924614259c5ba14a3cba36f606d843953f0674ac46e8a0d74`<br>A61 `295b81e218e3459924614259c5ba14a3cba36f606d843953f0674ac46e8a0d74` | `295b81e218e3459924614259c5ba14a3cba36f606d843953f0674ac46e8a0d74` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.StateTracker` | A15 `6c5e367a2ed794c15eae9e2537778e321fdb95c1d9a4c62e061a696e6266a6c8`<br>A61 `6c5e367a2ed794c15eae9e2537778e321fdb95c1d9a4c62e061a696e6266a6c8` | `6c5e367a2ed794c15eae9e2537778e321fdb95c1d9a4c62e061a696e6266a6c8` / A15, A61 | COMPATIBLE | 16 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.Util` | A15 `e1ffc69431075bee0862d51aafc1f2f636d991c56b8326b4a65382cc0b4ecfbf`<br>A61 `e1ffc69431075bee0862d51aafc1f2f636d991c56b8326b4a65382cc0b4ecfbf` | `e1ffc69431075bee0862d51aafc1f2f636d991c56b8326b4a65382cc0b4ecfbf` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsAWTGLCanvasPeerInfo` | A15 `ceed361f3a39cbc76e9c10e800b6fa9f38121d664708d6a29f15f0663b5486c4`<br>A61 `ceed361f3a39cbc76e9c10e800b6fa9f38121d664708d6a29f15f0663b5486c4` | `ceed361f3a39cbc76e9c10e800b6fa9f38121d664708d6a29f15f0663b5486c4` / A15, A61 | COMPATIBLE | 10 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsCanvasImplementation$1` | A15 `27af7266fef1d568bb7b65d6c794f67c8559122eed715575ca9e01c0c7d046a8`<br>A61 `27af7266fef1d568bb7b65d6c794f67c8559122eed715575ca9e01c0c7d046a8` | `27af7266fef1d568bb7b65d6c794f67c8559122eed715575ca9e01c0c7d046a8` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsCanvasImplementation` | A15 `7c27770940513846591d73313b1b882867067b329a6871e644431111ef47d6b8`<br>A61 `7c27770940513846591d73313b1b882867067b329a6871e644431111ef47d6b8` | `7c27770940513846591d73313b1b882867067b329a6871e644431111ef47d6b8` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsContextImplementation` | A15 `4f30ab8b5075677849d543395f5abf1f14a390cde8ad27b7daa4c01d074ba3a5`<br>A61 `4f30ab8b5075677849d543395f5abf1f14a390cde8ad27b7daa4c01d074ba3a5` | `4f30ab8b5075677849d543395f5abf1f14a390cde8ad27b7daa4c01d074ba3a5` / A15, A61 | COMPATIBLE | 10 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsDisplay$1` | A15 `29ab85ee5bba885ee747cdcce6a7382fff76258fc68b2b20cb386d2e69c99f13`<br>A61 `7c18db5c2274d4fd5b8d67b07e3e88c475dc2724033598ad0cb77912fda10d0f` | `29ab85ee5bba885ee747cdcce6a7382fff76258fc68b2b20cb386d2e69c99f13` / A15 | COMPATIBLE | 2 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.WindowsDisplay$Rect` | A15 `f75324b058f68524abe0ff88fb473ec9c3572c8c15e73c2984ce58109dc2c9af`<br>A61 `9d497f77b9693cddefd6a4dfa8c5c48c8b92f071a2afab8f59a238035a7f3e84` | `f75324b058f68524abe0ff88fb473ec9c3572c8c15e73c2984ce58109dc2c9af` / A15 | COMPATIBLE | 9 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.WindowsDisplay` | A15 `defdfd712a7087cc20cd0845ee0ca7d56965520bd4b05fc414ef6c8dcf5641ca`<br>A61 `65a6c5aca23e7ae228e7bfb81f7dc91fdaa48cc044ca63a43b6ba8ca38b47d26` | `defdfd712a7087cc20cd0845ee0ca7d56965520bd4b05fc414ef6c8dcf5641ca` / A15 | COMPATIBLE | 113 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.WindowsDisplayPeerInfo` | A15 `12527eeb44f8c00e7b44285203a88f13a426a220a543e56e55d53f2d1de27dd4`<br>A61 `12527eeb44f8c00e7b44285203a88f13a426a220a543e56e55d53f2d1de27dd4` | `12527eeb44f8c00e7b44285203a88f13a426a220a543e56e55d53f2d1de27dd4` / A15, A61 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsFileVersion` | A15 `c27065006fe49ff985538528a3dc07f919848979d2245f1e8c15a4eda828b50b`<br>A61 `c27065006fe49ff985538528a3dc07f919848979d2245f1e8c15a4eda828b50b` | `c27065006fe49ff985538528a3dc07f919848979d2245f1e8c15a4eda828b50b` / A15, A61 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsKeyboard` | A15 `c26addb174407d6dce0a4085f33bdb753eb75ca9e6cd623b8ac0a7b29167e5ea`<br>A61 `a6623933375ee9a6a9ebb58e7950a04f81f98be0c1598b03294a88b8f62461d1` | `c26addb174407d6dce0a4085f33bdb753eb75ca9e6cd623b8ac0a7b29167e5ea` / A15 | COMPATIBLE | 23 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.WindowsKeycodes` | A15 `2777c979316bbb98918e3cf21e93b5deb48686abacecc08a91c8883f05f64fd7`<br>A61 `2777c979316bbb98918e3cf21e93b5deb48686abacecc08a91c8883f05f64fd7` | `2777c979316bbb98918e3cf21e93b5deb48686abacecc08a91c8883f05f64fd7` / A15, A61 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsMouse` | A15 `ba29f7fdfd06d230f780070dc59a06bb2bf17d81933e5b47dc635b809cb7d3cd`<br>A61 `313dcc76337918bb5cde0e66c8d1b3838d0ca21f42c5d59f63414c86064634ef` | `ba29f7fdfd06d230f780070dc59a06bb2bf17d81933e5b47dc635b809cb7d3cd` / A15 | COMPATIBLE | 29 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.WindowsPbufferPeerInfo` | A15 `b5e086bac0808af855d11077466694ceab608949cf53fa26501f28c62e282b9c`<br>A61 `b5e086bac0808af855d11077466694ceab608949cf53fa26501f28c62e282b9c` | `b5e086bac0808af855d11077466694ceab608949cf53fa26501f28c62e282b9c` / A15, A61 | COMPATIBLE | 12 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsPeerInfo` | A15 `05e6b11f82ba39801b7f5b6cb704f5b3a3954c9a1d0011ecd51ce28ce99acc19`<br>A61 `05e6b11f82ba39801b7f5b6cb704f5b3a3954c9a1d0011ecd51ce28ce99acc19` | `05e6b11f82ba39801b7f5b6cb704f5b3a3954c9a1d0011ecd51ce28ce99acc19` / A15, A61 | COMPATIBLE | 12 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.WindowsRegistry` | A15 `07413fff239774cfc1ee378b2b9fcb67445ab65babf15d1f2c84730d65147801`<br>A61 `07413fff239774cfc1ee378b2b9fcb67445ab65babf15d1f2c84730d65147801` | `07413fff239774cfc1ee378b2b9fcb67445ab65babf15d1f2c84730d65147801` / A15, A61 | COMPATIBLE | 2 Signaturen; identische Redundanz |
| `org.lwjgl.opengl.XRandR$Screen` | A15 `e0c74f53a29ee41d99274eb4593f3f52ab9c0c9c7cb85a40c258bc572e126b09`<br>A61 `b450c484f3891f32a8d17afbed8bcc31e6218ecce81419ed81ef57bb2ad96f3e` | `e0c74f53a29ee41d99274eb4593f3f52ab9c0c9c7cb85a40c258bc572e126b09` / A15 | COMPATIBLE | 9 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengl.XRandR` | A15 `8163c69974e15541ece75f1e5ecde1abba7ceecdbb60f6ec2ebcce4140110092`<br>A61 `1b3e309e94958724327d156f4d6fecc9f85b96fed89e1f9bb1529c69841231b3` | `8163c69974e15541ece75f1e5ecde1abba7ceecdbb60f6ec2ebcce4140110092` / A15 | COMPATIBLE | 20 Signaturen; verschiedene Bytes |
| `org.lwjgl.opengles.ContextAttribs` | A15 `07d141d38ec444cb6f40faf161d15515bc6b41999caf25223ce0eaa841780584`<br>A61 `66f71f1643e7da38a5233e4d614af6b3e27104986004e3cefd4c1c44bb0d4b0c` | `07d141d38ec444cb6f40faf161d15515bc6b41999caf25223ce0eaa841780584` / A15 | COMPATIBLE | 3 Signaturen; verschiedene Bytes |
| `paulscode.sound.Channel` | A11 `32c27829184c6d2f11e2e03b02fe105225a32d809821a92fd69601fb3bc1d0ad`<br>A24 `32c27829184c6d2f11e2e03b02fe105225a32d809821a92fd69601fb3bc1d0ad` | `32c27829184c6d2f11e2e03b02fe105225a32d809821a92fd69601fb3bc1d0ad` / A11, A24 | COMPATIBLE | 22 Signaturen; identische Redundanz |
| `paulscode.sound.CommandObject` | A11 `ec51033f81b1db612e4288bb1a126761a37edaf006139446c0f7ab8ea15023b6`<br>A24 `ec51033f81b1db612e4288bb1a126761a37edaf006139446c0f7ab8ea15023b6` | `ec51033f81b1db612e4288bb1a126761a37edaf006139446c0f7ab8ea15023b6` / A11, A24 | COMPATIBLE | 28 Signaturen; identische Redundanz |
| `paulscode.sound.CommandThread` | A11 `117b68a1a561875c3bdb467f237423236b7f9ff51cb84c0d4776417ba7bc49c7`<br>A24 `117b68a1a561875c3bdb467f237423236b7f9ff51cb84c0d4776417ba7bc49c7` | `117b68a1a561875c3bdb467f237423236b7f9ff51cb84c0d4776417ba7bc49c7` / A11, A24 | COMPATIBLE | 12 Signaturen; identische Redundanz |
| `paulscode.sound.FilenameURL` | A11 `b46545d8799b4536f22506906eb85a9b9a696aa841fabf7e2593ff446a30ab0f`<br>A24 `b46545d8799b4536f22506906eb85a9b9a696aa841fabf7e2593ff446a30ab0f` | `b46545d8799b4536f22506906eb85a9b9a696aa841fabf7e2593ff446a30ab0f` / A11, A24 | COMPATIBLE | 9 Signaturen; identische Redundanz |
| `paulscode.sound.ICodec` | A11 `4219fdc6f881c7e471b531237746db0fea7b33d77fd75e5485b2790630c82127`<br>A24 `4219fdc6f881c7e471b531237746db0fea7b33d77fd75e5485b2790630c82127` | `4219fdc6f881c7e471b531237746db0fea7b33d77fd75e5485b2790630c82127` / A11, A24 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `paulscode.sound.IStreamListener` | A11 `7b8ed4e8925ec529986d1ca5009e94b80203eacb47cd5e0c72d26bce6aeaf7bd`<br>A24 `7b8ed4e8925ec529986d1ca5009e94b80203eacb47cd5e0c72d26bce6aeaf7bd` | `7b8ed4e8925ec529986d1ca5009e94b80203eacb47cd5e0c72d26bce6aeaf7bd` / A11, A24 | COMPATIBLE | 1 Signaturen; identische Redundanz |
| `paulscode.sound.Library` | A11 `d4a234f9caebbbdedb7eb830a15665df83acc675f142933a47f1f050377c3070`<br>A24 `d4a234f9caebbbdedb7eb830a15665df83acc675f142933a47f1f050377c3070` | `d4a234f9caebbbdedb7eb830a15665df83acc675f142933a47f1f050377c3070` / A11, A24 | COMPATIBLE | 73 Signaturen; identische Redundanz |
| `paulscode.sound.ListenerData` | A11 `1bcac6511bcb05f25dad0915eade6994d8928d53a6757f2278f32d9c67c62c88`<br>A24 `1bcac6511bcb05f25dad0915eade6994d8928d53a6757f2278f32d9c67c62c88` | `1bcac6511bcb05f25dad0915eade6994d8928d53a6757f2278f32d9c67c62c88` / A11, A24 | COMPATIBLE | 11 Signaturen; identische Redundanz |
| `paulscode.sound.MidiChannel$1` | A11 `6810e7b367c5c7bb7ceaf6030a5b0250a10469e01f3c7b8c89837a58eb631bd4`<br>A24 `6810e7b367c5c7bb7ceaf6030a5b0250a10469e01f3c7b8c89837a58eb631bd4` | `6810e7b367c5c7bb7ceaf6030a5b0250a10469e01f3c7b8c89837a58eb631bd4` / A11, A24 | COMPATIBLE | 0 Signaturen; identische Redundanz |
| `paulscode.sound.MidiChannel$FadeThread` | A11 `6326005664882db0a7673806cf3fc909fc55495c509013085c29d6a04f4920e9`<br>A24 `6326005664882db0a7673806cf3fc909fc55495c509013085c29d6a04f4920e9` | `6326005664882db0a7673806cf3fc909fc55495c509013085c29d6a04f4920e9` / A11, A24 | COMPATIBLE | 10 Signaturen; identische Redundanz |
| `paulscode.sound.MidiChannel` | A11 `9afc53f225da7fbcf34e3dce4db8accefd297959b0706b93971902b6dacce4f5`<br>A24 `9afc53f225da7fbcf34e3dce4db8accefd297959b0706b93971902b6dacce4f5` | `9afc53f225da7fbcf34e3dce4db8accefd297959b0706b93971902b6dacce4f5` / A11, A24 | COMPATIBLE | 54 Signaturen; identische Redundanz |
| `paulscode.sound.SimpleThread$1` | A11 `e1157fa94141603d761d17be46e6c049af9f90ad4a993492f8598deb7c41e088`<br>A24 `e1157fa94141603d761d17be46e6c049af9f90ad4a993492f8598deb7c41e088` | `e1157fa94141603d761d17be46e6c049af9f90ad4a993492f8598deb7c41e088` / A11, A24 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `paulscode.sound.SimpleThread` | A11 `d59c7df62a77a94a5dafd48d6945cdcbb6985ffc389db6dfe5db1cfff065227c`<br>A24 `d59c7df62a77a94a5dafd48d6945cdcbb6985ffc389db6dfe5db1cfff065227c` | `d59c7df62a77a94a5dafd48d6945cdcbb6985ffc389db6dfe5db1cfff065227c` / A11, A24 | COMPATIBLE | 10 Signaturen; identische Redundanz |
| `paulscode.sound.SoundBuffer` | A11 `d0eb17f89d4c5bf1948ba03a3786250e7d96754480034f8e4981ae3f30bf8961`<br>A24 `d0eb17f89d4c5bf1948ba03a3786250e7d96754480034f8e4981ae3f30bf8961` | `d0eb17f89d4c5bf1948ba03a3786250e7d96754480034f8e4981ae3f30bf8961` / A11, A24 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `paulscode.sound.SoundSystem` | A11 `4af6710feb041afc250612996ade147c2e14eb946a2689498062da2c4946d617`<br>A24 `4af6710feb041afc250612996ade147c2e14eb946a2689498062da2c4946d617` | `4af6710feb041afc250612996ade147c2e14eb946a2689498062da2c4946d617` / A11, A24 | COMPATIBLE | 68 Signaturen; identische Redundanz |
| `paulscode.sound.SoundSystemConfig$1` | A11 `05190e6dfb083b47c18f207734204ff98bfee762b6fe94dd88cdc830fbad7f2c`<br>A24 `05190e6dfb083b47c18f207734204ff98bfee762b6fe94dd88cdc830fbad7f2c` | `05190e6dfb083b47c18f207734204ff98bfee762b6fe94dd88cdc830fbad7f2c` / A11, A24 | COMPATIBLE | 4 Signaturen; identische Redundanz |
| `paulscode.sound.SoundSystemConfig$Codec` | A11 `25502d0ad3b043d4578f576c2474bea3b155a42b5845bdd1b165c6dfa7b0ed1a`<br>A24 `25502d0ad3b043d4578f576c2474bea3b155a42b5845bdd1b165c6dfa7b0ed1a` | `25502d0ad3b043d4578f576c2474bea3b155a42b5845bdd1b165c6dfa7b0ed1a` / A11, A24 | COMPATIBLE | 5 Signaturen; identische Redundanz |
| `paulscode.sound.SoundSystemConfig` | A11 `0cfe2370925d79bdb64cc4a9b69f16a53bba03bbff2ef5c9b023d09c06b44e4a`<br>A24 `0cfe2370925d79bdb64cc4a9b69f16a53bba03bbff2ef5c9b023d09c06b44e4a` | `0cfe2370925d79bdb64cc4a9b69f16a53bba03bbff2ef5c9b023d09c06b44e4a` / A11, A24 | COMPATIBLE | 56 Signaturen; identische Redundanz |
| `paulscode.sound.SoundSystemException` | A11 `ba0f2d80c9cb7180bdd3b148f58007877877d64c0bf3005862b310019c93f91c`<br>A24 `ba0f2d80c9cb7180bdd3b148f58007877877d64c0bf3005862b310019c93f91c` | `ba0f2d80c9cb7180bdd3b148f58007877877d64c0bf3005862b310019c93f91c` / A11, A24 | COMPATIBLE | 3 Signaturen; identische Redundanz |
| `paulscode.sound.SoundSystemLogger` | A11 `02f136103c537600db71a9b321347120afba42f03268085038e003264ad13b0f`<br>A24 `02f136103c537600db71a9b321347120afba42f03268085038e003264ad13b0f` | `02f136103c537600db71a9b321347120afba42f03268085038e003264ad13b0f` / A11, A24 | COMPATIBLE | 7 Signaturen; identische Redundanz |
| `paulscode.sound.Source` | A11 `e9860187b7b46cc9d3cefbda1474b97fccb1ee5d03f3d5100a6c3304d38687ab`<br>A24 `e9860187b7b46cc9d3cefbda1474b97fccb1ee5d03f3d5100a6c3304d38687ab` | `e9860187b7b46cc9d3cefbda1474b97fccb1ee5d03f3d5100a6c3304d38687ab` / A11, A24 | COMPATIBLE | 79 Signaturen; identische Redundanz |
| `paulscode.sound.StreamThread` | A11 `5dfcdcb7f32fd4c2256793f8461ce01ff98f7ea6d6a80c4437ea63e7b8b60a29`<br>A24 `5dfcdcb7f32fd4c2256793f8461ce01ff98f7ea6d6a80c4437ea63e7b8b60a29` | `5dfcdcb7f32fd4c2256793f8461ce01ff98f7ea6d6a80c4437ea63e7b8b60a29` / A11, A24 | COMPATIBLE | 12 Signaturen; identische Redundanz |
| `paulscode.sound.Vector3D` | A11 `e91b0aacf029f58f7620846962de45b5c489acb523dd390f254e1aa30db992f8`<br>A24 `e91b0aacf029f58f7620846962de45b5c489acb523dd390f254e1aa30db992f8` | `e91b0aacf029f58f7620846962de45b5c489acb523dd390f254e1aa30db992f8` / A11, A24 | COMPATIBLE | 11 Signaturen; identische Redundanz |

### Erwartete Signaturen je FQN

Nur erfasste Memberkonstanten; vollständige Caller und Auflösung im JSON.

#### `io.netty.bootstrap.Bootstrap$1`

```text
<init>(Lio/netty/bootstrap/Bootstrap;Lio/netty/channel/ChannelFuture;Lio/netty/channel/Channel;Ljava/net/SocketAddress;Ljava/net/SocketAddress;Lio/netty/channel/ChannelPromise;)V
operationComplete(Lio/netty/channel/ChannelFuture;)V
this$0Lio/netty/bootstrap/Bootstrap;
val$channelLio/netty/channel/Channel;
val$localAddressLjava/net/SocketAddress;
val$promiseLio/netty/channel/ChannelPromise;
val$regFutureLio/netty/channel/ChannelFuture;
val$remoteAddressLjava/net/SocketAddress;
```

#### `io.netty.bootstrap.Bootstrap$2`

```text
<init>(Lio/netty/channel/ChannelFuture;Ljava/net/SocketAddress;Lio/netty/channel/Channel;Ljava/net/SocketAddress;Lio/netty/channel/ChannelPromise;)V
val$channelLio/netty/channel/Channel;
val$localAddressLjava/net/SocketAddress;
val$promiseLio/netty/channel/ChannelPromise;
val$regFutureLio/netty/channel/ChannelFuture;
val$remoteAddressLjava/net/SocketAddress;
```

#### `io.netty.bootstrap.Bootstrap`

```text
<init>()V
<init>(Lio/netty/bootstrap/Bootstrap;)V
access$000(Lio/netty/channel/ChannelFuture;Lio/netty/channel/Channel;Ljava/net/SocketAddress;Ljava/net/SocketAddress;Lio/netty/channel/ChannelPromise;)V
attrs()Ljava/util/Map;
channel(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;
clone()Lio/netty/bootstrap/Bootstrap;
connect(Ljava/lang/String;I)Lio/netty/channel/ChannelFuture;
connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;
connect(Ljava/net/SocketAddress;)Lio/netty/channel/ChannelFuture;
doConnect(Ljava/net/SocketAddress;Ljava/net/SocketAddress;)Lio/netty/channel/ChannelFuture;
doConnect0(Lio/netty/channel/ChannelFuture;Lio/netty/channel/Channel;Ljava/net/SocketAddress;Ljava/net/SocketAddress;Lio/netty/channel/ChannelPromise;)V
group(Lio/netty/channel/EventLoopGroup;)Lio/netty/bootstrap/AbstractBootstrap;
handler()Lio/netty/channel/ChannelHandler;
handler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/AbstractBootstrap;
initAndRegister()Lio/netty/channel/ChannelFuture;
localAddress()Ljava/net/SocketAddress;
loggerLio/netty/util/internal/logging/InternalLogger;
options()Ljava/util/Map;
remoteAddressLjava/net/SocketAddress;
validate()Lio/netty/bootstrap/Bootstrap;
```

#### `javax.vecmath.AxisAngle4d`

```text
angleD
xD
yD
zD
```

#### `javax.vecmath.AxisAngle4f`

```text
angleF
xF
yF
zF
```

#### `javax.vecmath.Color3b`

```text
xB
yB
zB
```

#### `javax.vecmath.Color3f`

```text
xF
yF
zF
```

#### `javax.vecmath.Color4b`

```text
wB
xB
yB
zB
```

#### `javax.vecmath.Color4f`

```text
wF
xF
yF
zF
```

#### `javax.vecmath.GMatrix`

```text
<init>(II)V
<init>(Ljavax/vecmath/GMatrix;)V
checkMatrix(Ljavax/vecmath/GMatrix;)V
computeSVD(Ljavax/vecmath/GMatrix;Ljavax/vecmath/GMatrix;Ljavax/vecmath/GMatrix;Ljavax/vecmath/GMatrix;)I
compute_2X2(DDD[D[D[D[D[DI)I
compute_qr(II[D[DLjavax/vecmath/GMatrix;Ljavax/vecmath/GMatrix;)V
compute_rot(DD[D[D)D
compute_shift(DDD)D
d_sign(DD)D
epsilonEquals(Ljavax/vecmath/GMatrix;D)Z
getNumCol()I
getNumRow()I
invert()V
invertGeneral(Ljavax/vecmath/GMatrix;)V
luBacksubstitution(I[D[I[D)V
luDecomposition(I[D[I[I)Z
max(DD)D
min(DD)D
mul(Ljavax/vecmath/GMatrix;Ljavax/vecmath/GMatrix;)V
mulTransposeLeft(Ljavax/vecmath/GMatrix;Ljavax/vecmath/GMatrix;)V
mulTransposeRight(Ljavax/vecmath/GMatrix;Ljavax/vecmath/GMatrix;)V
nColI
nRowI
setIdentity()V
toString()Ljava/lang/String;
toString(Ljavax/vecmath/GMatrix;)Ljava/lang/String;
transpose()V
update_u(ILjavax/vecmath/GMatrix;[D[D)V
update_u_split(IILjavax/vecmath/GMatrix;[D[DLjavax/vecmath/GMatrix;Ljavax/vecmath/GMatrix;)V
update_v(ILjavax/vecmath/GMatrix;[D[D)V
update_v_split(IILjavax/vecmath/GMatrix;[D[DLjavax/vecmath/GMatrix;Ljavax/vecmath/GMatrix;)V
values[[D
```

#### `javax.vecmath.GVector`

```text
dot(Ljavax/vecmath/GVector;)D
getSize()I
interpolate(Ljavax/vecmath/GVector;D)V
interpolate(Ljavax/vecmath/GVector;Ljavax/vecmath/GVector;D)V
lengthI
mul(Ljavax/vecmath/GMatrix;Ljavax/vecmath/GVector;)V
norm()D
setSize(I)V
values[D
```

#### `javax.vecmath.Matrix3d`

```text
<init>()V
almostEqual(DD)Z
compute_2X2(DDD[D[D[D[D[DI)I
compute_qr([D[D[D[D)I
compute_rot(DD[D[DII)D
compute_shift(DDD)D
compute_svd([D[D[D)V
d_sign(DD)D
getScaleRotate([D[D)V
invertGeneral(Ljavax/vecmath/Matrix3d;)V
luBacksubstitution([D[I[D)V
luDecomposition([D[I)Z
m00D
m01D
m02D
m10D
m11D
m12D
m20D
m21D
m22D
mat_mul([D[D[D)V
max(DD)D
max3([D)D
min(DD)D
setZero()V
svdReorder([D[D[D[D[D[D)V
transpose()V
transpose_mat([D[D)V
```

#### `javax.vecmath.Matrix3f`

```text
<init>()V
getScaleRotate([D[D)V
invertGeneral(Ljavax/vecmath/Matrix3f;)V
luBacksubstitution([D[I[D)V
luDecomposition([D[I)Z
m00F
m01F
m02F
m10F
m11F
m12F
m20F
m21F
m22F
setZero()V
transpose()V
```

#### `javax.vecmath.Matrix4d`

```text
epsilonEquals(Ljavax/vecmath/Matrix4d;D)Z
get(Ljavax/vecmath/Matrix3d;)V
getScaleRotate([D[D)V
invertGeneral(Ljavax/vecmath/Matrix4d;)V
luBacksubstitution([D[I[D)V
luDecomposition([D[I)Z
m00D
m01D
m02D
m03D
m10D
m11D
m12D
m13D
m20D
m21D
m22D
m23D
m30D
m31D
m32D
m33D
setZero()V
transpose()V
```

#### `javax.vecmath.Matrix4f`

```text
<init>()V
get(Ljavax/vecmath/Matrix3d;)V
get(Ljavax/vecmath/Matrix3f;)V
getScaleRotate([D[D)V
invertGeneral(Ljavax/vecmath/Matrix4f;)V
luBacksubstitution([D[I[D)V
luDecomposition([D[I)Z
m00F
m01F
m02F
m03F
m10F
m11F
m12F
m13F
m20F
m21F
m22F
m23F
m30F
m31F
m32F
m33F
setZero()V
transpose()V
```

#### `javax.vecmath.MismatchedSizeException`

```text
<init>(Ljava/lang/String;)V
```

#### `javax.vecmath.Point2d`

```text
xD
yD
```

#### `javax.vecmath.Point2f`

```text
xF
yF
```

#### `javax.vecmath.Point3d`

```text
<init>(Ljavax/vecmath/Tuple3f;)V
xD
yD
zD
```

#### `javax.vecmath.Point3f`

```text
xF
yF
zF
```

#### `javax.vecmath.Point3i`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `javax.vecmath.Point4d`

```text
wD
xD
yD
zD
```

#### `javax.vecmath.Point4f`

```text
wF
xF
yF
zF
```

#### `javax.vecmath.Point4i`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `javax.vecmath.Quat4d`

```text
<init>(Ljavax/vecmath/Quat4d;)V
inverse()V
mul(Ljavax/vecmath/Quat4d;)V
mul(Ljavax/vecmath/Quat4d;Ljavax/vecmath/Quat4d;)V
wD
xD
yD
zD
```

#### `javax.vecmath.Quat4f`

```text
<init>(Ljavax/vecmath/Quat4f;)V
inverse()V
mul(Ljavax/vecmath/Quat4f;)V
mul(Ljavax/vecmath/Quat4f;Ljavax/vecmath/Quat4f;)V
wF
xF
yF
zF
```

#### `javax.vecmath.SingularMatrixException`

```text
<init>(Ljava/lang/String;)V
```

#### `javax.vecmath.TexCoord2f`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `javax.vecmath.TexCoord3f`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `javax.vecmath.TexCoord4f`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `javax.vecmath.Tuple2d`

```text
<init>()V
<init>(DD)V
<init>(Ljavax/vecmath/Tuple2d;)V
<init>(Ljavax/vecmath/Tuple2f;)V
<init>([D)V
xD
yD
```

#### `javax.vecmath.Tuple2f`

```text
<init>()V
<init>(FF)V
<init>(Ljavax/vecmath/Tuple2d;)V
<init>(Ljavax/vecmath/Tuple2f;)V
<init>([F)V
xF
yF
```

#### `javax.vecmath.Tuple3b`

```text
<init>()V
<init>(BBB)V
<init>(Ljavax/vecmath/Tuple3b;)V
<init>([B)V
xB
yB
zB
```

#### `javax.vecmath.Tuple3d`

```text
<init>()V
<init>(DDD)V
<init>(Ljavax/vecmath/Tuple3d;)V
<init>(Ljavax/vecmath/Tuple3f;)V
<init>([D)V
clamp(DD)V
clamp(DDLjavax/vecmath/Tuple3d;)V
clampMax(D)V
clampMax(DLjavax/vecmath/Tuple3d;)V
clampMin(D)V
clampMin(DLjavax/vecmath/Tuple3d;)V
interpolate(Ljavax/vecmath/Tuple3d;D)V
interpolate(Ljavax/vecmath/Tuple3d;Ljavax/vecmath/Tuple3d;D)V
scaleAdd(DLjavax/vecmath/Tuple3d;)V
set(DDD)V
xD
yD
zD
```

#### `javax.vecmath.Tuple3f`

```text
<init>()V
<init>(FFF)V
<init>(Ljavax/vecmath/Tuple3d;)V
<init>(Ljavax/vecmath/Tuple3f;)V
<init>([F)V
set(FFF)V
xF
yF
zF
```

#### `javax.vecmath.Tuple3i`

```text
<init>()V
<init>(III)V
<init>(Ljavax/vecmath/Tuple3i;)V
<init>([I)V
xI
yI
zI
```

#### `javax.vecmath.Tuple4b`

```text
<init>()V
<init>(BBBB)V
<init>(Ljavax/vecmath/Tuple4b;)V
<init>([B)V
wB
xB
yB
zB
```

#### `javax.vecmath.Tuple4d`

```text
<init>()V
<init>(DDDD)V
<init>(Ljavax/vecmath/Tuple4d;)V
<init>(Ljavax/vecmath/Tuple4f;)V
<init>([D)V
clamp(DD)V
clamp(DDLjavax/vecmath/Tuple4d;)V
clampMax(D)V
clampMax(DLjavax/vecmath/Tuple4d;)V
clampMin(D)V
clampMin(DLjavax/vecmath/Tuple4d;)V
interpolate(Ljavax/vecmath/Tuple4d;D)V
interpolate(Ljavax/vecmath/Tuple4d;Ljavax/vecmath/Tuple4d;D)V
scaleAdd(DLjavax/vecmath/Tuple4d;)V
wD
xD
yD
zD
```

#### `javax.vecmath.Tuple4f`

```text
<init>()V
<init>(FFFF)V
<init>(Ljavax/vecmath/Tuple4d;)V
<init>(Ljavax/vecmath/Tuple4f;)V
<init>([F)V
wF
xF
yF
zF
```

#### `javax.vecmath.Tuple4i`

```text
<init>()V
<init>(IIII)V
<init>(Ljavax/vecmath/Tuple4i;)V
<init>([I)V
wI
xI
yI
zI
```

#### `javax.vecmath.VecMathI18N`

```text
getString(Ljava/lang/String;)Ljava/lang/String;
```

#### `javax.vecmath.Vector2d`

```text
dot(Ljavax/vecmath/Vector2d;)D
length()D
xD
yD
```

#### `javax.vecmath.Vector2f`

```text
dot(Ljavax/vecmath/Vector2f;)F
length()F
xF
yF
```

#### `javax.vecmath.Vector3d`

```text
<init>(DDD)V
dot(Ljavax/vecmath/Vector3d;)D
length()D
xD
yD
zD
```

#### `javax.vecmath.Vector3f`

```text
dot(Ljavax/vecmath/Vector3f;)F
length()F
xF
yF
zF
```

#### `javax.vecmath.Vector4d`

```text
<init>(DDDD)V
dot(Ljavax/vecmath/Vector4d;)D
length()D
wD
xD
yD
zD
```

#### `javax.vecmath.Vector4f`

```text
dot(Ljavax/vecmath/Vector4f;)F
length()F
wF
xF
yF
zF
```

#### `org.lwjgl.BufferChecks`

```text
checkArray([JI)V
checkArray([Ljava/lang/Object;)V
checkArray([Ljava/lang/Object;I)V
checkBuffer(Ljava/nio/Buffer;I)I
checkBuffer(Ljava/nio/ByteBuffer;I)V
checkBuffer(Ljava/nio/DoubleBuffer;I)V
checkBuffer(Ljava/nio/FloatBuffer;I)V
checkBuffer(Ljava/nio/IntBuffer;I)V
checkBuffer(Ljava/nio/LongBuffer;I)V
checkBuffer(Ljava/nio/ShortBuffer;I)V
checkBuffer(Lorg/lwjgl/PointerBuffer;I)V
checkBufferSize(Ljava/nio/Buffer;I)V
checkDirect(Ljava/nio/ByteBuffer;)V
checkDirect(Ljava/nio/DoubleBuffer;)V
checkDirect(Ljava/nio/FloatBuffer;)V
checkDirect(Ljava/nio/IntBuffer;)V
checkDirect(Ljava/nio/LongBuffer;)V
checkDirect(Ljava/nio/ShortBuffer;)V
checkDirect(Lorg/lwjgl/PointerBuffer;)V
checkFunctionAddress(J)V
checkNotNull(Ljava/lang/Object;)V
checkNullTerminated(Ljava/nio/ByteBuffer;)V
checkNullTerminated(Ljava/nio/ByteBuffer;I)V
checkNullTerminated(Ljava/nio/IntBuffer;)V
checkNullTerminated(Ljava/nio/LongBuffer;)V
checkNullTerminated(Lorg/lwjgl/PointerBuffer;)V
throwArraySizeException([JI)V
throwArraySizeException([Ljava/lang/Object;I)V
throwBufferSizeException(Ljava/nio/Buffer;I)V
throwBufferSizeException(Lorg/lwjgl/PointerBuffer;I)V
```

#### `org.lwjgl.BufferUtils`

```text
createByteBuffer(I)Ljava/nio/ByteBuffer;
createCharBuffer(I)Ljava/nio/CharBuffer;
createDoubleBuffer(I)Ljava/nio/DoubleBuffer;
createFloatBuffer(I)Ljava/nio/FloatBuffer;
createIntBuffer(I)Ljava/nio/IntBuffer;
createLongBuffer(I)Ljava/nio/LongBuffer;
createPointerBuffer(I)Lorg/lwjgl/PointerBuffer;
createShortBuffer(I)Ljava/nio/ShortBuffer;
getBufferAddress(Ljava/nio/Buffer;)J
getElementSizeExponent(Ljava/nio/Buffer;)I
zeroBuffer0(Ljava/nio/Buffer;JJ)V
```

#### `org.lwjgl.DefaultSysImplementation`

```text
<init>()V
```

#### `org.lwjgl.J2SESysImplementation`

```text
<init>()V
```

#### `org.lwjgl.LWJGLException`

```text
<init>(Ljava/lang/String;)V
<init>(Ljava/lang/String;Ljava/lang/Throwable;)V
<init>(Ljava/lang/Throwable;)V
getMessage()Ljava/lang/String;
printStackTrace()V
printStackTrace(Ljava/io/PrintStream;)V
```

#### `org.lwjgl.LWJGLUtil$1`

```text
<init>([Ljava/lang/String;)V
run()Ljava/lang/Process;
val$cmd_array[Ljava/lang/String;
```

#### `org.lwjgl.LWJGLUtil$2`

```text
<init>(Ljava/lang/String;)V
run()Ljava/lang/String;
val$property_nameLjava/lang/String;
```

#### `org.lwjgl.LWJGLUtil$3`

```text
<init>(Ljava/lang/Class;Ljava/lang/ClassLoader;Ljava/lang/String;)V
run()Ljava/lang/String;
val$classloaderLjava/lang/ClassLoader;
val$clazzLjava/lang/Class;
val$libnameLjava/lang/String;
```

#### `org.lwjgl.LWJGLUtil$4`

```text
<init>(Ljava/lang/String;)V
run()Ljava/lang/Boolean;
val$property_nameLjava/lang/String;
```

#### `org.lwjgl.LWJGLUtil$5`

```text
<init>(Ljava/lang/String;)V
run()Ljava/lang/Integer;
val$property_nameLjava/lang/String;
```

#### `org.lwjgl.LWJGLUtil$6`

```text
<init>(Ljava/lang/String;I)V
run()Ljava/lang/Integer;
val$default_valI
val$property_nameLjava/lang/String;
```

#### `org.lwjgl.LWJGLUtil$TokenFilter`

```text
accept(Ljava/lang/reflect/Field;I)Z
```

#### `org.lwjgl.LWJGLUtil`

```text
CHECKSZ
DEBUGZ
LWJGLIcon16x16Ljava/nio/ByteBuffer;
LWJGLIcon32x32Ljava/nio/ByteBuffer;
PLATFORMI
execPrivileged([Ljava/lang/String;)V
getClassTokens(Lorg/lwjgl/LWJGLUtil$TokenFilter;Ljava/util/Map;Ljava/lang/Iterable;)Ljava/util/Map;
getClassTokens(Lorg/lwjgl/LWJGLUtil$TokenFilter;Ljava/util/Map;[Ljava/lang/Class;)Ljava/util/Map;
getLibraryPaths(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/ClassLoader;)[Ljava/lang/String;
getPathFromClassLoader(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/String;
getPlatform()I
getPrivilegedBoolean(Ljava/lang/String;)Z
getPrivilegedInteger(Ljava/lang/String;)Ljava/lang/Integer;
getPrivilegedInteger(Ljava/lang/String;I)Ljava/lang/Integer;
getPrivilegedProperty(Ljava/lang/String;)Ljava/lang/String;
isMacOSXEqualsOrBetterThan(II)Z
loadIcon(Ljava/lang/String;)Ljava/nio/ByteBuffer;
log(Ljava/lang/CharSequence;)V
mapLibraryName(Ljava/lang/String;)Ljava/lang/String;
toHexString(I)Ljava/lang/String;
```

#### `org.lwjgl.LinuxSysImplementation$1`

```text
<init>()V
```

#### `org.lwjgl.LinuxSysImplementation`

```text
<init>()V
```

#### `org.lwjgl.MacOSXSysImplementation`

```text
<init>()V
```

#### `org.lwjgl.MemoryUtil$1`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.MemoryUtil$Accessor`

```text
getAddress(Ljava/nio/Buffer;)J
```

#### `org.lwjgl.MemoryUtil$AccessorJNI`

```text
<init>()V
<init>(Lorg/lwjgl/MemoryUtil$1;)V
```

#### `org.lwjgl.MemoryUtil$AccessorReflect`

```text
<init>()V
addressLjava/lang/reflect/Field;
```

#### `org.lwjgl.MemoryUtil$CharSequenceNT`

```text
<init>(Ljava/lang/CharSequence;)V
sourceLjava/lang/CharSequence;
```

#### `org.lwjgl.MemoryUtil`

```text
asciiLjava/nio/charset/Charset;
decode(Ljava/nio/ByteBuffer;Ljava/nio/charset/Charset;)Ljava/lang/String;
decodeImpl(Ljava/nio/ByteBuffer;Ljava/nio/charset/Charset;)Ljava/lang/String;
decodeUTF8(Ljava/nio/ByteBuffer;)Ljava/lang/String;
encode(Ljava/lang/CharSequence;Ljava/nio/charset/Charset;)Ljava/nio/ByteBuffer;
encode(Ljava/nio/CharBuffer;Ljava/nio/charset/Charset;)Ljava/nio/ByteBuffer;
encodeASCII(Ljava/lang/CharSequence;)Ljava/nio/ByteBuffer;
encodeUTF16(Ljava/lang/CharSequence;)Ljava/nio/ByteBuffer;
encodeUTF8(Ljava/lang/CharSequence;)Ljava/nio/ByteBuffer;
getAddress(Ljava/nio/ByteBuffer;)J
getAddress(Ljava/nio/ByteBuffer;I)J
getAddress(Ljava/nio/CharBuffer;)J
getAddress(Ljava/nio/CharBuffer;I)J
getAddress(Ljava/nio/DoubleBuffer;)J
getAddress(Ljava/nio/DoubleBuffer;I)J
getAddress(Ljava/nio/FloatBuffer;)J
getAddress(Ljava/nio/FloatBuffer;I)J
getAddress(Ljava/nio/IntBuffer;)J
getAddress(Ljava/nio/IntBuffer;I)J
getAddress(Ljava/nio/LongBuffer;)J
getAddress(Ljava/nio/LongBuffer;I)J
getAddress(Ljava/nio/ShortBuffer;)J
getAddress(Ljava/nio/ShortBuffer;I)J
getAddress(Lorg/lwjgl/PointerBuffer;)J
getAddress(Lorg/lwjgl/PointerBuffer;I)J
getAddress0(Ljava/nio/Buffer;)J
getAddress0(Lorg/lwjgl/PointerBuffer;)J
getAddress0Safe(Ljava/nio/Buffer;)J
getAddressField()Ljava/lang/reflect/Field;
getAddressSafe(Ljava/nio/ByteBuffer;)J
getAddressSafe(Ljava/nio/DoubleBuffer;)J
getAddressSafe(Ljava/nio/FloatBuffer;)J
getAddressSafe(Ljava/nio/IntBuffer;)J
getAddressSafe(Ljava/nio/LongBuffer;)J
getAddressSafe(Ljava/nio/ShortBuffer;)J
getAddressSafe(Lorg/lwjgl/PointerBuffer;)J
getDeclaredFieldRecursive(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;
loadAccessor(Ljava/lang/String;)Lorg/lwjgl/MemoryUtil$Accessor;
memUtilLorg/lwjgl/MemoryUtil$Accessor;
utf16Ljava/nio/charset/Charset;
utf8Ljava/nio/charset/Charset;
```

#### `org.lwjgl.MemoryUtilSun$AccessorReflectFast`

```text
addressAccessorLsun/reflect/FieldAccessor;
```

#### `org.lwjgl.MemoryUtilSun$AccessorUnsafe`

```text
addressJ
getUnsafeInstance()Lsun/misc/Unsafe;
unsafeLsun/misc/Unsafe;
```

#### `org.lwjgl.MemoryUtilSun`

Identical byte redundancy.

#### `org.lwjgl.PointerBuffer$PointerBufferR`

```text
<init>(Ljava/nio/ByteBuffer;)V
duplicate()Lorg/lwjgl/PointerBuffer;
```

#### `org.lwjgl.PointerBuffer`

```text
<init>(I)V
<init>(Ljava/nio/ByteBuffer;)V
allocateDirect(I)Lorg/lwjgl/PointerBuffer;
capacity()I
checkBounds(III)V
checkSource(Ljava/nio/ByteBuffer;)V
clear()Lorg/lwjgl/PointerBuffer;
flip()Lorg/lwjgl/PointerBuffer;
get(I)J
get([JII)Lorg/lwjgl/PointerBuffer;
getBuffer()Ljava/nio/ByteBuffer;
getPointerSize()I
is64Bit()Z
is64BitZ
limit()I
limit(I)Lorg/lwjgl/PointerBuffer;
newInstance(Ljava/nio/ByteBuffer;)Lorg/lwjgl/PointerBuffer;
pointersLjava/nio/ByteBuffer;
position()I
position(I)Lorg/lwjgl/PointerBuffer;
put(IJ)Lorg/lwjgl/PointerBuffer;
put(ILorg/lwjgl/PointerWrapper;)Lorg/lwjgl/PointerBuffer;
put(J)Lorg/lwjgl/PointerBuffer;
put(Lorg/lwjgl/PointerWrapper;)Lorg/lwjgl/PointerBuffer;
put([JII)Lorg/lwjgl/PointerBuffer;
remaining()I
viewLjava/nio/Buffer;
view32Ljava/nio/IntBuffer;
view64Ljava/nio/LongBuffer;
```

#### `org.lwjgl.PointerWrapper`

```text
getPointer()J
```

#### `org.lwjgl.PointerWrapperAbstract`

```text
<init>(J)V
checkValid()V
isValid()Z
pointerJ
```

#### `org.lwjgl.Sys$1`

```text
<init>(Ljava/lang/String;)V
val$lib_nameLjava/lang/String;
```

#### `org.lwjgl.Sys$2`

```text
<init>(Ljava/lang/Class;)V
run()Ljava/lang/reflect/Method;
val$serviceManagerClassLjava/lang/Class;
```

#### `org.lwjgl.Sys$3`

```text
<init>(Ljava/lang/Class;)V
run()Ljava/lang/reflect/Method;
val$basicServiceClassLjava/lang/Class;
```

#### `org.lwjgl.Sys`

```text
createImplementation()Lorg/lwjgl/SysImplementation;
doLoadLibrary(Ljava/lang/String;)V
getTime()J
getTimerResolution()J
getVersion()Ljava/lang/String;
implementationLorg/lwjgl/SysImplementation;
initialize()V
is64Bit()Z
is64BitZ
loadLibrary(Ljava/lang/String;)V
openURL(Ljava/lang/String;)Z
```

#### `org.lwjgl.SysImplementation`

```text
alert(Ljava/lang/String;Ljava/lang/String;)V
getClipboard()Ljava/lang/String;
getJNIVersion()I
getPointerSize()I
getRequiredJNIVersion()I
getTime()J
getTimerResolution()J
has64Bit()Z
openURL(Ljava/lang/String;)Z
setDebug(Z)V
```

#### `org.lwjgl.WindowsSysImplementation$1`

```text
<init>()V
run()Ljava/lang/Long;
```

#### `org.lwjgl.WindowsSysImplementation`

```text
<init>()V
getHwnd()J
initCommonControls()V
nAlert(JJJ)V
nGetClipboard()Ljava/lang/String;
nGetTime()J
```

#### `org.lwjgl.input.Controller`

```text
poll()V
```

#### `org.lwjgl.input.ControllerEvent`

```text
<init>(Lorg/lwjgl/input/Controller;JIIZZ)V
<init>(Lorg/lwjgl/input/Controller;JIIZZZFF)V
buttonStateZ
getButtonState()Z
getControlIndex()I
getSource()Lorg/lwjgl/input/Controller;
getTimeStamp()J
getXAxisValue()F
getYAxisValue()F
indexI
isAxis()Z
isButton()Z
isPovX()Z
isPovY()Z
isXAxis()Z
isYAxis()Z
sourceLorg/lwjgl/input/Controller;
timeStampJ
typeI
xaxisZ
xaxisValueF
yaxisZ
yaxisValueF
```

#### `org.lwjgl.input.Controllers`

```text
addEvent(Lorg/lwjgl/input/ControllerEvent;)V
controllerCountI
controllersLjava/util/ArrayList;
createController(Lnet/java/games/input/Controller;)V
createdZ
eventLorg/lwjgl/input/ControllerEvent;
eventsLjava/util/ArrayList;
getController(I)Lorg/lwjgl/input/Controller;
isCreated()Z
poll()V
```

#### `org.lwjgl.input.Cursor$CursorElement`

```text
<init>(Ljava/lang/Object;JJ)V
cursorHandleLjava/lang/Object;
delayJ
timeoutJ
```

#### `org.lwjgl.input.Cursor`

```text
checkValid()V
convertARGBtoABGR(Ljava/nio/IntBuffer;)V
createCursors(IIIIILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)[Lorg/lwjgl/input/Cursor$CursorElement;
cursors[Lorg/lwjgl/input/Cursor$CursorElement;
destroyedZ
flipImage(IIILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)V
flipImages(IIILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)V
getCapabilities()I
getHandle()Ljava/lang/Object;
hasTimedOut()Z
indexI
nextCursor()V
setTimeout()V
```

#### `org.lwjgl.input.JInputController`

```text
<init>(ILnet/java/games/input/Controller;)V
axesLjava/util/ArrayList;
axesMax[F
axesValue[F
buttonState[Z
buttonsLjava/util/ArrayList;
deadZones[F
getAxisValue(I)F
getDeadZone(I)F
getPovX()F
getPovY()F
indexI
povLjava/util/ArrayList;
povValues[F
rumblers[Lnet/java/games/input/Rumbler;
rxaxisI
ryaxisI
rzaxisI
setDeadZone(IF)V
targetLnet/java/games/input/Controller;
xaxisI
yaxisI
zaxisI
```

#### `org.lwjgl.input.Keyboard$1`

Identical byte redundancy.

#### `org.lwjgl.input.Keyboard$KeyEvent`

```text
<init>()V
<init>(Lorg/lwjgl/input/Keyboard$1;)V
access$100(Lorg/lwjgl/input/Keyboard$KeyEvent;)V
access$200(Lorg/lwjgl/input/Keyboard$KeyEvent;)Z
access$202(Lorg/lwjgl/input/Keyboard$KeyEvent;Z)Z
access$300(Lorg/lwjgl/input/Keyboard$KeyEvent;)I
access$302(Lorg/lwjgl/input/Keyboard$KeyEvent;I)I
access$400(Lorg/lwjgl/input/Keyboard$KeyEvent;)Z
access$402(Lorg/lwjgl/input/Keyboard$KeyEvent;Z)Z
access$500(Lorg/lwjgl/input/Keyboard$KeyEvent;)I
access$502(Lorg/lwjgl/input/Keyboard$KeyEvent;I)I
access$600(Lorg/lwjgl/input/Keyboard$KeyEvent;)J
access$602(Lorg/lwjgl/input/Keyboard$KeyEvent;J)J
characterI
keyI
nanosJ
repeatZ
reset()V
stateZ
```

#### `org.lwjgl.input.Keyboard`

```text
areRepeatEventsEnabled()Z
counterI
create()V
create(Lorg/lwjgl/opengl/InputImplementation;)V
createdZ
current_eventLorg/lwjgl/input/Keyboard$KeyEvent;
destroy()V
enableRepeatEvents(Z)V
getEventCharacter()C
getEventKey()I
getEventKeyState()Z
getEventNanoseconds()J
getKeyIndex(Ljava/lang/String;)I
getKeyName(I)Ljava/lang/String;
implementationLorg/lwjgl/opengl/InputImplementation;
initialize()V
initializedZ
isCreated()Z
isKeyDown(I)Z
isRepeatEvent()Z
keyCountI
keyDownBufferLjava/nio/ByteBuffer;
keyMapLjava/util/Map;
keyName[Ljava/lang/String;
next()Z
poll()V
read()V
readBufferLjava/nio/ByteBuffer;
readNext(Lorg/lwjgl/input/Keyboard$KeyEvent;)Z
repeat_enabledZ
reset()V
tmp_eventLorg/lwjgl/input/Keyboard$KeyEvent;
```

#### `org.lwjgl.input.Mouse$1`

```text
<init>(Ljava/lang/String;)V
run()Ljava/lang/Boolean;
val$property_nameLjava/lang/String;
```

#### `org.lwjgl.input.Mouse`

```text
absolute_xI
absolute_yI
buttonCountI
buttonMapLjava/util/Map;
buttonName[Ljava/lang/String;
buttonsLjava/nio/ByteBuffer;
clipMouseCoordinatesToWindowZ
coord_bufferLjava/nio/IntBuffer;
create()V
create(Lorg/lwjgl/opengl/InputImplementation;)V
createdZ
currentCursorLorg/lwjgl/input/Cursor;
destroy()V
dwheelI
dxI
dyI
emulateCursorAnimationZ
eventButtonI
eventStateZ
event_dwheelI
event_dxI
event_dyI
event_nanosJ
event_xI
event_yI
getDWheel()I
getDX()I
getDY()I
getEventButton()I
getEventButtonState()Z
getEventDWheel()I
getEventDX()I
getEventDY()I
getEventNanoseconds()J
getEventX()I
getEventY()I
getImplementation()Lorg/lwjgl/opengl/InputImplementation;
getNativeCursor()Lorg/lwjgl/input/Cursor;
getPrivilegedBoolean(Ljava/lang/String;)Z
getX()I
getY()I
grab_xI
grab_yI
hasWheelZ
implementationLorg/lwjgl/opengl/InputImplementation;
initialize()V
initializedZ
isButtonDown(I)Z
isCreated()Z
isGrabbed()Z
isGrabbedZ
isInsideWindow()Z
last_event_raw_xI
last_event_raw_yI
next()Z
poll()V
read()V
readBufferLjava/nio/ByteBuffer;
resetMouse()V
setCursorPosition(II)V
setGrabbed(Z)V
setNativeCursor(Lorg/lwjgl/input/Cursor;)Lorg/lwjgl/input/Cursor;
updateCursor()V
xI
yI
```

#### `org.lwjgl.input.OpenGLPackageAccess$1`

```text
<init>()V
```

#### `org.lwjgl.input.OpenGLPackageAccess$2`

```text
<init>()V
run()Lorg/lwjgl/opengl/InputImplementation;
```

#### `org.lwjgl.input.OpenGLPackageAccess`

```text
createImplementation()Lorg/lwjgl/opengl/InputImplementation;
global_lockLjava/lang/Object;
```

#### `org.lwjgl.openal.AL`

```text
contextLorg/lwjgl/openal/ALCcontext;
create()V
create(Ljava/lang/String;IIZ)V
create(Ljava/lang/String;IIZZ)V
createdZ
destroy()V
deviceLorg/lwjgl/openal/ALCdevice;
getDevice()Lorg/lwjgl/openal/ALCdevice;
init(Ljava/lang/String;IIZZ)V
isCreated()Z
nCreate(Ljava/lang/String;)V
nCreateDefault()V
nDestroy()V
resetNativeStubs(Ljava/lang/Class;)V
```

#### `org.lwjgl.openal.AL10`

```text
alBufferData(IILjava/nio/ByteBuffer;I)V
alDeleteBuffers(Ljava/nio/IntBuffer;)V
alDeleteSources(Ljava/nio/IntBuffer;)V
alDopplerFactor(F)V
alDopplerVelocity(F)V
alGenBuffers(Ljava/nio/IntBuffer;)V
alGenSources(Ljava/nio/IntBuffer;)V
alGetBufferi(II)I
alGetError()I
alGetSourcei(II)I
alGetString(I)Ljava/lang/String;
alIsBuffer(I)Z
alListener(ILjava/nio/FloatBuffer;)V
alListenerf(IF)V
alSource(IILjava/nio/FloatBuffer;)V
alSourcePause(I)V
alSourcePlay(I)V
alSourceQueueBuffers(ILjava/nio/IntBuffer;)V
alSourceRewind(I)V
alSourceStop(I)V
alSourceStop(Ljava/nio/IntBuffer;)V
alSourceUnqueueBuffers(ILjava/nio/IntBuffer;)V
alSourcef(IIF)V
alSourcei(III)V
initNativeStubs()V
nalBufferData(IIJII)V
nalDeleteBuffers(IJ)V
nalDeleteBuffers2(II)V
nalDeleteSources(IJ)V
nalDeleteSources2(II)V
nalDisable(I)V
nalDistanceModel(I)V
nalDopplerFactor(F)V
nalDopplerVelocity(F)V
nalEnable(I)V
nalGenBuffers(IJ)V
nalGenBuffers2(I)I
nalGenSources(IJ)V
nalGenSources2(I)I
nalGetBoolean(I)Z
nalGetBufferf(II)F
nalGetBufferi(II)I
nalGetDouble(I)D
nalGetDoublev(IJ)V
nalGetEnumValue(Ljava/lang/String;)I
nalGetError()I
nalGetFloat(I)F
nalGetFloatv(IJ)V
nalGetInteger(I)I
nalGetIntegerv(IJ)V
nalGetListenerf(I)F
nalGetListenerfv(IJ)V
nalGetListeneri(I)I
nalGetSourcef(II)F
nalGetSourcefv(IIJ)V
nalGetSourcei(II)I
nalGetString(I)Ljava/lang/String;
nalIsBuffer(I)Z
nalIsEnabled(I)Z
nalIsExtensionPresent(Ljava/lang/String;)Z
nalIsSource(I)Z
nalListener3f(IFFF)V
nalListenerf(IF)V
nalListenerfv(IJ)V
nalListeneri(II)V
nalSource3f(IIFFF)V
nalSourcePause(I)V
nalSourcePausev(IJ)V
nalSourcePlay(I)V
nalSourcePlayv(IJ)V
nalSourceQueueBuffers(IIJ)V
nalSourceQueueBuffers2(III)V
nalSourceRewind(I)V
nalSourceRewindv(IJ)V
nalSourceStop(I)V
nalSourceStopv(IJ)V
nalSourceUnqueueBuffers(IIJ)V
nalSourceUnqueueBuffers2(II)I
nalSourcef(IIF)V
nalSourcefv(IIJ)V
nalSourcei(III)V
```

#### `org.lwjgl.openal.AL11`

```text
initNativeStubs()V
nalBuffer3f(IIFFF)V
nalBuffer3i(IIIII)V
nalBufferf(IIF)V
nalBufferfv(IIJ)V
nalBufferi(III)V
nalBufferiv(IIJ)V
nalGetBufferf(II)F
nalGetBufferfv(IIJ)V
nalGetBufferi(II)I
nalGetBufferiv(IIJ)V
nalGetListeneriv(IJ)V
nalListener3i(IIII)V
nalSource3i(IIIII)V
nalSourceiv(IIJ)V
nalSpeedOfSound(F)V
```

#### `org.lwjgl.openal.ALC10`

```text
alcCloseDevice(Lorg/lwjgl/openal/ALCdevice;)Z
alcCreateContext(Lorg/lwjgl/openal/ALCdevice;Ljava/nio/IntBuffer;)Lorg/lwjgl/openal/ALCcontext;
alcDestroyContext(Lorg/lwjgl/openal/ALCcontext;)V
alcGetContextsDevice(Lorg/lwjgl/openal/ALCcontext;)Lorg/lwjgl/openal/ALCdevice;
alcGetError(Lorg/lwjgl/openal/ALCdevice;)I
alcGetInteger(Lorg/lwjgl/openal/ALCdevice;ILjava/nio/IntBuffer;)V
alcGetString(Lorg/lwjgl/openal/ALCdevice;I)Ljava/lang/String;
alcIsExtensionPresent(Lorg/lwjgl/openal/ALCdevice;Ljava/lang/String;)Z
alcMakeContextCurrent(Lorg/lwjgl/openal/ALCcontext;)I
alcOpenDevice(Ljava/lang/String;)Lorg/lwjgl/openal/ALCdevice;
contextsLjava/util/HashMap;
devicesLjava/util/HashMap;
getContext(Lorg/lwjgl/openal/ALCcontext;)J
getDevice(Lorg/lwjgl/openal/ALCdevice;)J
initNativeStubs()V
nalcCloseDevice(J)Z
nalcCreateContext(JJ)J
nalcDestroyContext(J)V
nalcGetContextsDevice(J)J
nalcGetCurrentContext()J
nalcGetEnumValue(JJ)I
nalcGetError(J)I
nalcGetIntegerv(JIIJ)V
nalcGetString(JI)Ljava/nio/ByteBuffer;
nalcIsExtensionPresent(JJ)Z
nalcMakeContextCurrent(J)I
nalcOpenDevice(J)J
nalcProcessContext(J)V
nalcSuspendContext(J)V
```

#### `org.lwjgl.openal.ALC11`

```text
initNativeStubs()V
initialize()Z
nalcCaptureCloseDevice(J)Z
nalcCaptureOpenDevice(JIII)J
nalcCaptureSamples(JJI)V
nalcCaptureStart(J)V
nalcCaptureStop(J)V
```

#### `org.lwjgl.openal.ALCcontext`

```text
<init>(J)V
contextJ
createAttributeList(III)Ljava/nio/IntBuffer;
isValid()Z
setInvalid()V
validZ
```

#### `org.lwjgl.openal.ALCdevice`

```text
<init>(J)V
addContext(Lorg/lwjgl/openal/ALCcontext;)V
contextsLjava/util/HashMap;
deviceJ
isValid()Z
removeContext(Lorg/lwjgl/openal/ALCcontext;)V
setInvalid()V
validZ
```

#### `org.lwjgl.openal.EFX10`

```text
alDeleteEffects(I)V
alDeleteFilters(I)V
alEffecti(III)V
alFilteri(III)V
alGenEffects()I
alGenFilters()I
initNativeStubs()V
nalAuxiliaryEffectSlotf(IIF)V
nalAuxiliaryEffectSlotfv(IIJ)V
nalAuxiliaryEffectSloti(III)V
nalAuxiliaryEffectSlotiv(IIJ)V
nalDeleteAuxiliaryEffectSlots(IJ)V
nalDeleteAuxiliaryEffectSlots2(II)V
nalDeleteEffects(IJ)V
nalDeleteEffects2(II)V
nalDeleteFilters(IJ)V
nalDeleteFilters2(II)V
nalEffectf(IIF)V
nalEffectfv(IIJ)V
nalEffecti(III)V
nalEffectiv(IIJ)V
nalFilterf(IIF)V
nalFilterfv(IIJ)V
nalFilteri(III)V
nalFilteriv(IIJ)V
nalGenAuxiliaryEffectSlots(IJ)V
nalGenAuxiliaryEffectSlots2(I)I
nalGenEffects(IJ)V
nalGenEffects2(I)I
nalGenFilters(IJ)V
nalGenFilters2(I)I
nalGetAuxiliaryEffectSlotf(II)F
nalGetAuxiliaryEffectSlotfv(IIJ)V
nalGetAuxiliaryEffectSloti(II)I
nalGetAuxiliaryEffectSlotiv(IIJ)V
nalGetEffectf(II)F
nalGetEffectfv(IIJ)V
nalGetEffecti(II)I
nalGetEffectiv(IIJ)V
nalGetFilterf(II)F
nalGetFilterfv(IIJ)V
nalGetFilteri(II)I
nalGetFilteriv(IIJ)V
nalIsAuxiliaryEffectSlot(I)Z
nalIsEffect(I)Z
nalIsFilter(I)Z
```

#### `org.lwjgl.openal.EFXUtil`

```text
isEfxSupported()Z
testSupportGeneric(II)Z
```

#### `org.lwjgl.openal.OpenALException`

```text
<init>(I)V
<init>(Ljava/lang/String;)V
getMessage()Ljava/lang/String;
```

#### `org.lwjgl.openal.Util`

```text
checkALCError(Lorg/lwjgl/openal/ALCdevice;)V
checkALCValidContext(Lorg/lwjgl/openal/ALCcontext;)V
checkALCValidDevice(Lorg/lwjgl/openal/ALCdevice;)V
```

#### `org.lwjgl.opencl.AMDDeviceAttributeQuery`

Identical byte redundancy.

#### `org.lwjgl.opencl.AMDDeviceMemoryFlags`

Identical byte redundancy.

#### `org.lwjgl.opencl.AMDOfflineDevices`

Identical byte redundancy.

#### `org.lwjgl.opencl.APIUtil$1`

```text
<init>()V
initialValue()[C
```

#### `org.lwjgl.opencl.APIUtil$10`

```text
<init>()V
release(Lorg/lwjgl/opencl/CLProgram;)V
```

#### `org.lwjgl.opencl.APIUtil$11`

```text
<init>()V
release(Lorg/lwjgl/opencl/CLKernel;)V
```

#### `org.lwjgl.opencl.APIUtil$12`

```text
<init>()V
release(Lorg/lwjgl/opencl/CLEvent;)V
```

#### `org.lwjgl.opencl.APIUtil$2`

```text
<init>()V
initialValue()Ljava/nio/ByteBuffer;
```

#### `org.lwjgl.opencl.APIUtil$3`

```text
<init>()V
initialValue()Lorg/lwjgl/PointerBuffer;
```

#### `org.lwjgl.opencl.APIUtil$4`

```text
<init>()V
initialValue()Lorg/lwjgl/PointerBuffer;
```

#### `org.lwjgl.opencl.APIUtil$5`

```text
<init>()V
initialValue()Lorg/lwjgl/opencl/APIUtil$Buffers;
```

#### `org.lwjgl.opencl.APIUtil$6`

```text
<init>()V
release(Lorg/lwjgl/opencl/CLDevice;)V
```

#### `org.lwjgl.opencl.APIUtil$7`

```text
<init>()V
release(Lorg/lwjgl/opencl/CLMem;)V
```

#### `org.lwjgl.opencl.APIUtil$8`

```text
<init>()V
release(Lorg/lwjgl/opencl/CLCommandQueue;)V
```

#### `org.lwjgl.opencl.APIUtil$9`

```text
<init>()V
release(Lorg/lwjgl/opencl/CLSampler;)V
```

#### `org.lwjgl.opencl.APIUtil$Buffers`

```text
<init>()V
doublesLjava/nio/DoubleBuffer;
floatsLjava/nio/FloatBuffer;
intsLjava/nio/IntBuffer;
intsDebugLjava/nio/IntBuffer;
longsLjava/nio/LongBuffer;
pointersLorg/lwjgl/PointerBuffer;
shortsLjava/nio/ShortBuffer;
```

#### `org.lwjgl.opencl.APIUtil$ObjectDestructor`

```text
release(Lorg/lwjgl/opencl/CLObjectChild;)V
```

#### `org.lwjgl.opencl.APIUtil`

```text
DESTRUCTOR_CLCommandQueueLorg/lwjgl/opencl/APIUtil$ObjectDestructor;
DESTRUCTOR_CLEventLorg/lwjgl/opencl/APIUtil$ObjectDestructor;
DESTRUCTOR_CLKernelLorg/lwjgl/opencl/APIUtil$ObjectDestructor;
DESTRUCTOR_CLMemLorg/lwjgl/opencl/APIUtil$ObjectDestructor;
DESTRUCTOR_CLProgramLorg/lwjgl/opencl/APIUtil$ObjectDestructor;
DESTRUCTOR_CLSamplerLorg/lwjgl/opencl/APIUtil$ObjectDestructor;
DESTRUCTOR_CLSubDeviceLorg/lwjgl/opencl/APIUtil$ObjectDestructor;
arrayTLLjava/lang/ThreadLocal;
bufferByteTLLjava/lang/ThreadLocal;
bufferPointerTLLjava/lang/ThreadLocal;
buffersTLLjava/lang/ThreadLocal;
encode(Ljava/nio/ByteBuffer;Ljava/lang/CharSequence;)Ljava/nio/ByteBuffer;
getArray(I)[C
getBuffer(Ljava/lang/CharSequence;)J
getBuffer([Ljava/lang/CharSequence;)J
getBufferByte(I)Ljava/nio/ByteBuffer;
getBufferByteOffset(I)Ljava/nio/ByteBuffer;
getBufferDouble()Ljava/nio/DoubleBuffer;
getBufferFloat()Ljava/nio/FloatBuffer;
getBufferInt()Ljava/nio/IntBuffer;
getBufferLong()Ljava/nio/LongBuffer;
getBufferNT(Ljava/lang/CharSequence;)J
getBufferNT([Ljava/lang/CharSequence;)J
getBufferPointer()Lorg/lwjgl/PointerBuffer;
getBufferPointer(I)Lorg/lwjgl/PointerBuffer;
getBufferShort()Ljava/nio/ShortBuffer;
getCLPlatform(Lorg/lwjgl/PointerBuffer;)Lorg/lwjgl/opencl/CLPlatform;
getExtensions(Ljava/lang/String;)Ljava/util/Set;
getLengths(I)Lorg/lwjgl/PointerBuffer;
getLengths([Ljava/lang/CharSequence;)J
getLengths([Ljava/nio/ByteBuffer;)J
getNativeKernelArgs(J[Lorg/lwjgl/opencl/CLMem;[J)Ljava/nio/ByteBuffer;
getPointer(Lorg/lwjgl/PointerWrapper;)J
getPointerSafe(Lorg/lwjgl/PointerWrapper;)J
getSize(Lorg/lwjgl/PointerBuffer;)I
getString(Ljava/nio/ByteBuffer;)Ljava/lang/String;
getTotalLength([Ljava/lang/CharSequence;)I
isDevicesParam(I)Z
lengthsTLLjava/lang/ThreadLocal;
releaseObjects(Lorg/lwjgl/opencl/CLCommandQueue;)V
releaseObjects(Lorg/lwjgl/opencl/CLContext;)V
releaseObjects(Lorg/lwjgl/opencl/CLDevice;)V
releaseObjects(Lorg/lwjgl/opencl/CLObjectRegistry;Lorg/lwjgl/opencl/APIUtil$ObjectDestructor;)V
releaseObjects(Lorg/lwjgl/opencl/CLProgram;)V
```

#### `org.lwjgl.opencl.APPLEContextLoggingFunctions`

```text
nclLogMessagesToStderrAPPLE(JJJJJ)V
nclLogMessagesToStdoutAPPLE(JJJJJ)V
nclLogMessagesToSystemLogAPPLE(JJJJJ)V
```

#### `org.lwjgl.opencl.APPLEContextLoggingUtil$1`

```text
<init>(J)V
```

#### `org.lwjgl.opencl.APPLEContextLoggingUtil$2`

```text
<init>(J)V
```

#### `org.lwjgl.opencl.APPLEContextLoggingUtil$3`

```text
<init>(J)V
```

#### `org.lwjgl.opencl.APPLEContextLoggingUtil`

```text
STD_ERR_CALLBACKLorg/lwjgl/opencl/CLContextCallback;
STD_OUT_CALLBACKLorg/lwjgl/opencl/CLContextCallback;
SYSTEM_LOG_CALLBACKLorg/lwjgl/opencl/CLContextCallback;
```

#### `org.lwjgl.opencl.APPLEGLSharing`

```text
nclGetGLContextInfoAPPLE(JJIJJJJ)I
```

#### `org.lwjgl.opencl.APPLESetMemObjectDestructor`

```text
nclSetMemObjectDestructorAPPLE(JJJJ)I
```

#### `org.lwjgl.opencl.CL`

```text
createdZ
getFunctionAddress(Ljava/lang/String;)J
getHostBuffer(JI)Ljava/nio/ByteBuffer;
nCreate(Ljava/lang/String;)V
nCreateDefault()V
ngetFunctionAddress(J)J
```

#### `org.lwjgl.opencl.CL10`

```text
clCreateContextFromType(Lorg/lwjgl/PointerBuffer;JLorg/lwjgl/opencl/CLContextCallback;Ljava/nio/IntBuffer;)Lorg/lwjgl/opencl/CLContext;
clCreateKernelsInProgram(Lorg/lwjgl/opencl/CLProgram;Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;)I
clGetCommandQueueInfo(Lorg/lwjgl/opencl/CLCommandQueue;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetContextInfo(Lorg/lwjgl/opencl/CLContext;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetDeviceIDs(Lorg/lwjgl/opencl/CLPlatform;JLorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;)I
clGetDeviceInfo(Lorg/lwjgl/opencl/CLDevice;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetEventInfo(Lorg/lwjgl/opencl/CLEvent;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetEventProfilingInfo(Lorg/lwjgl/opencl/CLEvent;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetImageInfo(Lorg/lwjgl/opencl/CLMem;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetKernelInfo(Lorg/lwjgl/opencl/CLKernel;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetKernelWorkGroupInfo(Lorg/lwjgl/opencl/CLKernel;Lorg/lwjgl/opencl/CLDevice;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetMemObjectInfo(Lorg/lwjgl/opencl/CLMem;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetPlatformIDs(Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;)I
clGetPlatformInfo(Lorg/lwjgl/opencl/CLPlatform;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetProgramBuildInfo(Lorg/lwjgl/opencl/CLProgram;Lorg/lwjgl/opencl/CLDevice;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetProgramInfo(Lorg/lwjgl/opencl/CLProgram;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetProgramInfo(Lorg/lwjgl/opencl/CLProgram;Lorg/lwjgl/PointerBuffer;Ljava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetProgramInfo(Lorg/lwjgl/opencl/CLProgram;[Ljava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetSamplerInfo(Lorg/lwjgl/opencl/CLSampler;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
clGetSupportedImageFormats(Lorg/lwjgl/opencl/CLContext;JILjava/nio/ByteBuffer;Ljava/nio/IntBuffer;)I
clReleaseCommandQueue(Lorg/lwjgl/opencl/CLCommandQueue;)I
clReleaseEvent(Lorg/lwjgl/opencl/CLEvent;)I
clReleaseKernel(Lorg/lwjgl/opencl/CLKernel;)I
clReleaseMemObject(Lorg/lwjgl/opencl/CLMem;)I
clReleaseProgram(Lorg/lwjgl/opencl/CLProgram;)I
clReleaseSampler(Lorg/lwjgl/opencl/CLSampler;)I
clSetKernelArg(Lorg/lwjgl/opencl/CLKernel;IJ)I
clSetKernelArg(Lorg/lwjgl/opencl/CLKernel;IJLjava/nio/Buffer;)I
clSetKernelArg(Lorg/lwjgl/opencl/CLKernel;ILorg/lwjgl/opencl/CLObject;)I
nclBuildProgram(JIJJJJJ)I
nclCreateBuffer(JJJJJJ)J
nclCreateCommandQueue(JJJJJ)J
nclCreateContext(JIJJJJJ)J
nclCreateContextFromType(JJJJJJ)J
nclCreateImage2D(JJJJJJJJJ)J
nclCreateImage3D(JJJJJJJJJJJ)J
nclCreateKernel(JJJJ)J
nclCreateKernelsInProgram(JIJJJ)I
nclCreateProgramWithBinary(JIJJJJJJ)J
nclCreateProgramWithBinary2(JIJJJJJJ)J
nclCreateProgramWithBinary3(JIJJ[Ljava/nio/ByteBuffer;JJJ)J
nclCreateProgramWithSource(JIJJJJ)J
nclCreateProgramWithSource2(JIJJJJ)J
nclCreateProgramWithSource3(JI[Ljava/nio/ByteBuffer;JJJ)J
nclCreateProgramWithSource4(JIJJJJ)J
nclCreateSampler(JIIIJJ)J
nclEnqueueBarrier(JJ)I
nclEnqueueCopyBuffer(JJJJJJIJJJ)I
nclEnqueueCopyBufferToImage(JJJJJJIJJJ)I
nclEnqueueCopyImage(JJJJJJIJJJ)I
nclEnqueueCopyImageToBuffer(JJJJJJIJJJ)I
nclEnqueueMapBuffer(JJIJJJIJJJJJ)Ljava/nio/ByteBuffer;
nclEnqueueMapImage(JJIJJJJJIJJJJ)Ljava/nio/ByteBuffer;
nclEnqueueMarker(JJJ)I
nclEnqueueNDRangeKernel(JJIJJJIJJJ)I
nclEnqueueNativeKernel(JJJJI[Lorg/lwjgl/opencl/CLMem;IJJJ)I
nclEnqueueReadBuffer(JJIJJJIJJJ)I
nclEnqueueReadImage(JJIJJJJJIJJJ)I
nclEnqueueTask(JJIJJJ)I
nclEnqueueUnmapMemObject(JJJIJJJ)I
nclEnqueueWaitForEvents(JIJJ)I
nclEnqueueWriteBuffer(JJIJJJIJJJ)I
nclEnqueueWriteImage(JJIJJJJJIJJJ)I
nclFinish(JJ)I
nclFlush(JJ)I
nclGetCommandQueueInfo(JIJJJJ)I
nclGetContextInfo(JIJJJJ)I
nclGetDeviceIDs(JJIJJJ)I
nclGetDeviceInfo(JIJJJJ)I
nclGetEventInfo(JIJJJJ)I
nclGetEventProfilingInfo(JIJJJJ)I
nclGetExtensionFunctionAddress(JJ)J
nclGetImageInfo(JIJJJJ)I
nclGetKernelInfo(JIJJJJ)I
nclGetKernelWorkGroupInfo(JJIJJJJ)I
nclGetMemObjectInfo(JIJJJJ)I
nclGetPlatformIDs(IJJJ)I
nclGetPlatformInfo(JIJJJJ)I
nclGetProgramBuildInfo(JJIJJJJ)I
nclGetProgramInfo(JIJJJJ)I
nclGetProgramInfo2(JIJJJJJ)I
nclGetProgramInfo3(JIJ[Ljava/nio/ByteBuffer;JJ)I
nclGetSamplerInfo(JIJJJJ)I
nclGetSupportedImageFormats(JJIIJJJ)I
nclReleaseCommandQueue(JJ)I
nclReleaseContext(JJ)I
nclReleaseEvent(JJ)I
nclReleaseKernel(JJ)I
nclReleaseMemObject(JJ)I
nclReleaseProgram(JJ)I
nclReleaseSampler(JJ)I
nclRetainCommandQueue(JJ)I
nclRetainContext(JJ)I
nclRetainEvent(JJ)I
nclRetainKernel(JJ)I
nclRetainMemObject(JJ)I
nclRetainProgram(JJ)I
nclRetainSampler(JJ)I
nclSetKernelArg(JIJJJ)I
nclUnloadCompiler(J)I
nclWaitForEvents(IJJ)I
```

#### `org.lwjgl.opencl.CL10GL`

```text
clGetGLObjectInfo(Lorg/lwjgl/opencl/CLMem;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;)I
clGetGLTextureInfo(Lorg/lwjgl/opencl/CLMem;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
nclCreateFromGLBuffer(JJIJJ)J
nclCreateFromGLRenderbuffer(JJIJJ)J
nclCreateFromGLTexture2D(JJIIIJJ)J
nclCreateFromGLTexture3D(JJIIIJJ)J
nclEnqueueAcquireGLObjects(JIJIJJJ)I
nclEnqueueReleaseGLObjects(JIJIJJJ)I
nclGetGLObjectInfo(JJJJ)I
nclGetGLTextureInfo(JIJJJJ)I
```

#### `org.lwjgl.opencl.CL11`

```text
clCreateSubBuffer(Lorg/lwjgl/opencl/CLMem;JILjava/nio/ByteBuffer;Ljava/nio/IntBuffer;)Lorg/lwjgl/opencl/CLMem;
nclCreateSubBuffer(JJIJJJ)J
nclCreateUserEvent(JJJ)J
nclEnqueueCopyBufferRect(JJJJJJJJJJIJJJ)I
nclEnqueueReadBufferRect(JJIJJJJJJJJIJJJ)I
nclEnqueueWriteBufferRect(JJIJJJJJJJJIJJJ)I
nclSetEventCallback(JIJJJ)I
nclSetMemObjectDestructorCallback(JJJJ)I
nclSetUserEventStatus(JIJ)I
```

#### `org.lwjgl.opencl.CL12`

```text
nclCompileProgram(JIJJIJJJJJ)I
nclCompileProgram3(JIJJIJ[Ljava/nio/ByteBuffer;JJJ)I
nclCompileProgramMulti(JIJJIJJJJJ)I
nclCreateImage(JJJJJJJ)J
nclCreateProgramWithBuiltInKernels(JIJJJJ)J
nclCreateSubDevices(JJIJJJ)I
nclEnqueueBarrierWithWaitList(JIJJJ)I
nclEnqueueFillBuffer(JJJJJJIJJJ)I
nclEnqueueFillImage(JJJJJIJJJ)I
nclEnqueueMarkerWithWaitList(JIJJJ)I
nclEnqueueMigrateMemObjects(JIJJIJJJ)I
nclGetExtensionFunctionAddressForPlatform(JJJ)J
nclGetKernelArgInfo(JIIJJJJ)I
nclLinkProgram(JIJJIJJJJJ)J
nclReleaseDevice(JJ)I
nclRetainDevice(JJ)I
nclSetPrintfCallback(JJJJ)I
nclUnloadPlatformCompiler(JJ)I
```

#### `org.lwjgl.opencl.CL12GL`

```text
nclCreateFromGLTexture(JJIIIJJ)J
```

#### `org.lwjgl.opencl.CLBuildProgramCallback`

```text
getPointer()J
setContext(Lorg/lwjgl/opencl/CLContext;)V
```

#### `org.lwjgl.opencl.CLCapabilities`

```text
CL_APPLE_ContextLoggingFunctionsZ
CL_APPLE_SetMemObjectDestructorZ
CL_APPLE_gl_sharingZ
CL_EXT_device_fissionZ
CL_EXT_migrate_memobjectZ
CL_KHR_gl_eventZ
CL_KHR_gl_sharingZ
CL_KHR_icdZ
CL_KHR_subgroupsZ
CL_KHR_terminate_contextZ
OpenCL10Z
OpenCL10GLZ
OpenCL11Z
OpenCL12Z
OpenCL12GLZ
clBuildProgramJ
clCompileProgramJ
clCreateBufferJ
clCreateCommandQueueJ
clCreateContextJ
clCreateContextFromTypeJ
clCreateEventFromGLsyncKHRJ
clCreateFromGLBufferJ
clCreateFromGLRenderbufferJ
clCreateFromGLTextureJ
clCreateFromGLTexture2DJ
clCreateFromGLTexture3DJ
clCreateImageJ
clCreateImage2DJ
clCreateImage3DJ
clCreateKernelJ
clCreateKernelsInProgramJ
clCreateProgramWithBinaryJ
clCreateProgramWithBuiltInKernelsJ
clCreateProgramWithSourceJ
clCreateSamplerJ
clCreateSubBufferJ
clCreateSubDevicesJ
clCreateSubDevicesEXTJ
clCreateUserEventJ
clEnqueueAcquireGLObjectsJ
clEnqueueBarrierJ
clEnqueueBarrierWithWaitListJ
clEnqueueCopyBufferJ
clEnqueueCopyBufferRectJ
clEnqueueCopyBufferToImageJ
clEnqueueCopyImageJ
clEnqueueCopyImageToBufferJ
clEnqueueFillBufferJ
clEnqueueFillImageJ
clEnqueueMapBufferJ
clEnqueueMapImageJ
clEnqueueMarkerJ
clEnqueueMarkerWithWaitListJ
clEnqueueMigrateMemObjectEXTJ
clEnqueueMigrateMemObjectsJ
clEnqueueNDRangeKernelJ
clEnqueueNativeKernelJ
clEnqueueReadBufferJ
clEnqueueReadBufferRectJ
clEnqueueReadImageJ
clEnqueueReleaseGLObjectsJ
clEnqueueTaskJ
clEnqueueUnmapMemObjectJ
clEnqueueWaitForEventsJ
clEnqueueWriteBufferJ
clEnqueueWriteBufferRectJ
clEnqueueWriteImageJ
clFinishJ
clFlushJ
clGetCommandQueueInfoJ
clGetContextInfoJ
clGetDeviceIDsJ
clGetDeviceInfoJ
clGetEventInfoJ
clGetEventProfilingInfoJ
clGetExtensionFunctionAddressJ
clGetExtensionFunctionAddressForPlatformJ
clGetGLContextInfoAPPLEJ
clGetGLContextInfoKHRJ
clGetGLObjectInfoJ
clGetGLTextureInfoJ
clGetImageInfoJ
clGetKernelArgInfoJ
clGetKernelInfoJ
clGetKernelSubGroupInfoKHRJ
clGetKernelWorkGroupInfoJ
clGetMemObjectInfoJ
clGetPlatformIDsJ
clGetPlatformInfoJ
clGetProgramBuildInfoJ
clGetProgramInfoJ
clGetSamplerInfoJ
clGetSupportedImageFormatsJ
clIcdGetPlatformIDsKHRJ
clLinkProgramJ
clLogMessagesToStderrAPPLEJ
clLogMessagesToStdoutAPPLEJ
clLogMessagesToSystemLogAPPLEJ
clReleaseCommandQueueJ
clReleaseContextJ
clReleaseDeviceJ
clReleaseDeviceEXTJ
clReleaseEventJ
clReleaseKernelJ
clReleaseMemObjectJ
clReleaseProgramJ
clReleaseSamplerJ
clRetainCommandQueueJ
clRetainContextJ
clRetainDeviceJ
clRetainDeviceEXTJ
clRetainEventJ
clRetainKernelJ
clRetainMemObjectJ
clRetainProgramJ
clRetainSamplerJ
clSetEventCallbackJ
clSetKernelArgJ
clSetMemObjectDestructorAPPLEJ
clSetMemObjectDestructorCallbackJ
clSetPrintfCallbackJ
clSetUserEventStatusJ
clTerminateContextKHRJ
clUnloadCompilerJ
clUnloadPlatformCompilerJ
clWaitForEventsJ
getPlatformCapabilities(Lorg/lwjgl/opencl/CLPlatform;)Lorg/lwjgl/opencl/CLPlatformCapabilities;
isAPPLE_ContextLoggingFunctionsSupported()Z
isAPPLE_SetMemObjectDestructorSupported()Z
isAPPLE_gl_sharingSupported()Z
isCL10GLSupported()Z
isCL10Supported()Z
isCL11Supported()Z
isCL12GLSupported()Z
isCL12Supported()Z
isEXT_device_fissionSupported()Z
isEXT_migrate_memobjectSupported()Z
isKHR_gl_eventSupported()Z
isKHR_gl_sharingSupported()Z
isKHR_icdSupported()Z
isKHR_subgroupsSupported()Z
isKHR_terminate_contextSupported()Z
```

#### `org.lwjgl.opencl.CLChecks`

```text
calculateBufferRectSize(Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/PointerBuffer;JJ)I
calculateImage2DSize(Ljava/nio/ByteBuffer;JJJ)I
calculateImage3DSize(Ljava/nio/ByteBuffer;JJJJJ)I
calculateImageSize(Lorg/lwjgl/PointerBuffer;JJ)I
getChannelCount(I)I
getChannelSize(I)I
getElementSize(Ljava/nio/ByteBuffer;)I
```

#### `org.lwjgl.opencl.CLCommandQueue`

```text
<init>(JLorg/lwjgl/opencl/CLContext;Lorg/lwjgl/opencl/CLDevice;)V
clEventsLorg/lwjgl/opencl/CLObjectRegistry;
deviceLorg/lwjgl/opencl/CLDevice;
getCLEventRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
getParent()Lorg/lwjgl/opencl/CLObject;
getPointer()J
getReferenceCount()I
isValid()Z
registerCLEvent(Lorg/lwjgl/PointerBuffer;)V
release()I
retain()I
utilLorg/lwjgl/opencl/InfoUtil;
```

#### `org.lwjgl.opencl.CLCompileProgramCallback`

```text
getPointer()J
setContext(Lorg/lwjgl/opencl/CLContext;)V
```

#### `org.lwjgl.opencl.CLContext$CLContextUtil`

```text
create(Lorg/lwjgl/opencl/CLPlatform;Ljava/util/List;Lorg/lwjgl/opencl/CLContextCallback;Lorg/lwjgl/opengl/Drawable;Ljava/nio/IntBuffer;)Lorg/lwjgl/opencl/CLContext;
createFromType(Lorg/lwjgl/opencl/CLPlatform;JLorg/lwjgl/opencl/CLContextCallback;Lorg/lwjgl/opengl/Drawable;Ljava/nio/IntBuffer;)Lorg/lwjgl/opencl/CLContext;
getInfoDevices(Lorg/lwjgl/opencl/CLContext;)Ljava/util/List;
getInfoInt(Lorg/lwjgl/opencl/CLObject;I)I
getSupportedImageFormats(Lorg/lwjgl/opencl/CLContext;JILorg/lwjgl/opencl/api/Filter;)Ljava/util/List;
```

#### `org.lwjgl.opencl.CLContext`

```text
<init>(JLorg/lwjgl/opencl/CLPlatform;)V
checkCallback(JI)Z
checkValid()V
clCommandQueuesLorg/lwjgl/opencl/CLObjectRegistry;
clEventsLorg/lwjgl/opencl/CLObjectRegistry;
clMemsLorg/lwjgl/opencl/CLObjectRegistry;
clProgramsLorg/lwjgl/opencl/CLObjectRegistry;
clSamplersLorg/lwjgl/opencl/CLObjectRegistry;
contextCallbackJ
create(Lorg/lwjgl/opencl/CLPlatform;Ljava/util/List;Lorg/lwjgl/opencl/CLContextCallback;Lorg/lwjgl/opengl/Drawable;Ljava/nio/IntBuffer;)Lorg/lwjgl/opencl/CLContext;
getCLCommandQueueRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
getCLEventRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
getCLMemRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
getCLProgram(J)Lorg/lwjgl/opencl/CLProgram;
getCLProgramRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
getCLSamplerRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
getParent()Lorg/lwjgl/opencl/CLObject;
getPointer()J
getReferenceCount()I
getSupportedImageFormats(JILorg/lwjgl/opencl/api/Filter;)Ljava/util/List;
isValid()Z
printfCallbackJ
release()I
releaseImpl()V
retain()I
setContextCallback(J)V
setPrintfCallback(JI)V
utilLorg/lwjgl/opencl/CLContext$CLContextUtil;
```

#### `org.lwjgl.opencl.CLContextCallback`

```text
<init>(J)V
customZ
getPointer()J
isCustom()Z
```

#### `org.lwjgl.opencl.CLDevice`

```text
<init>(JLorg/lwjgl/opencl/CLDevice;)V
<init>(JLorg/lwjgl/opencl/CLDevice;Lorg/lwjgl/opencl/CLPlatform;)V
<init>(JLorg/lwjgl/opencl/CLPlatform;)V
capsLjava/lang/Object;
checkValid()V
getCapabilities()Ljava/lang/Object;
getInfoString(I)Ljava/lang/String;
getParent()Lorg/lwjgl/opencl/CLObject;
getPlatform()Lorg/lwjgl/opencl/CLPlatform;
getPointer()J
getReferenceCount()I
getSubCLDeviceRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
isValid()Z
platformLorg/lwjgl/opencl/CLPlatform;
registerSubCLDevices(Lorg/lwjgl/PointerBuffer;)V
release()I
retain()I
setCapabilities(Ljava/lang/Object;)V
subCLDevicesLorg/lwjgl/opencl/CLObjectRegistry;
utilLorg/lwjgl/opencl/InfoUtil;
```

#### `org.lwjgl.opencl.CLDeviceCapabilities`

```text
<init>(Lorg/lwjgl/opencl/CLDevice;)V
CL_AMD_device_attribute_queryZ
CL_AMD_device_memory_flagsZ
CL_AMD_fp64Z
CL_AMD_media_opsZ
CL_AMD_media_ops2Z
CL_AMD_offline_devicesZ
CL_AMD_popcntZ
CL_AMD_printfZ
CL_AMD_vec3Z
CL_APPLE_ContextLoggingFunctionsZ
CL_APPLE_SetMemObjectDestructorZ
CL_APPLE_gl_sharingZ
CL_EXT_atomic_counters_32Z
CL_EXT_atomic_counters_64Z
CL_EXT_device_fissionZ
CL_EXT_migrate_memobjectZ
CL_INTEL_immediate_executionZ
CL_INTEL_printfZ
CL_INTEL_thread_local_execZ
CL_KHR_3d_image_writesZ
CL_KHR_byte_addressable_storeZ
CL_KHR_depth_imagesZ
CL_KHR_fp16Z
CL_KHR_fp64Z
CL_KHR_gl_depth_imagesZ
CL_KHR_gl_eventZ
CL_KHR_gl_msaa_sharingZ
CL_KHR_gl_sharingZ
CL_KHR_global_int32_base_atomicsZ
CL_KHR_global_int32_extended_atomicsZ
CL_KHR_image2d_from_bufferZ
CL_KHR_initialize_memoryZ
CL_KHR_int64_base_atomicsZ
CL_KHR_int64_extended_atomicsZ
CL_KHR_local_int32_base_atomicsZ
CL_KHR_local_int32_extended_atomicsZ
CL_KHR_mipmap_imageZ
CL_KHR_mipmap_image_writesZ
CL_KHR_select_fprounding_modeZ
CL_KHR_spirZ
CL_KHR_srgb_image_writesZ
CL_KHR_subgroupsZ
CL_KHR_terminate_contextZ
CL_NV_compiler_optionsZ
CL_NV_device_attribute_queryZ
CL_NV_pragma_unrollZ
OpenCL11Z
OpenCL12Z
majorVersionI
minorVersionI
```

#### `org.lwjgl.opencl.CLEvent$CLEventUtil`

```text
getInfoInt(Lorg/lwjgl/opencl/CLObject;I)I
getProfilingInfoLong(Lorg/lwjgl/opencl/CLEvent;I)J
```

#### `org.lwjgl.opencl.CLEvent`

```text
<init>(JLorg/lwjgl/opencl/CLCommandQueue;)V
<init>(JLorg/lwjgl/opencl/CLContext;)V
<init>(JLorg/lwjgl/opencl/CLContext;Lorg/lwjgl/opencl/CLCommandQueue;)V
checkValid()V
getParent()Lorg/lwjgl/opencl/CLObject;
getParentRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
getPointer()J
isValid()Z
queueLorg/lwjgl/opencl/CLCommandQueue;
release()I
retain()I
utilLorg/lwjgl/opencl/CLEvent$CLEventUtil;
```

#### `org.lwjgl.opencl.CLEventCallback`

```text
eventRegistryLorg/lwjgl/opencl/CLObjectRegistry;
getPointer()J
handleMessage(Lorg/lwjgl/opencl/CLEvent;I)V
setRegistry(Lorg/lwjgl/opencl/CLObjectRegistry;)V
```

#### `org.lwjgl.opencl.CLFunctionAddress`

```text
<init>(J)V
```

#### `org.lwjgl.opencl.CLKernel$CLKernelUtil`

```text
getInfoInt(Lorg/lwjgl/opencl/CLObject;I)I
getInfoString(Lorg/lwjgl/opencl/CLObject;I)Ljava/lang/String;
getWorkGroupInfoLong(Lorg/lwjgl/opencl/CLKernel;Lorg/lwjgl/opencl/CLDevice;I)J
getWorkGroupInfoSize(Lorg/lwjgl/opencl/CLKernel;Lorg/lwjgl/opencl/CLDevice;I)J
getWorkGroupInfoSizeArray(Lorg/lwjgl/opencl/CLKernel;Lorg/lwjgl/opencl/CLDevice;I)[J
setArg(Lorg/lwjgl/opencl/CLKernel;IB)V
setArg(Lorg/lwjgl/opencl/CLKernel;ID)V
setArg(Lorg/lwjgl/opencl/CLKernel;IF)V
setArg(Lorg/lwjgl/opencl/CLKernel;II)V
setArg(Lorg/lwjgl/opencl/CLKernel;IJ)V
setArg(Lorg/lwjgl/opencl/CLKernel;ILorg/lwjgl/opencl/CLObject;)V
setArg(Lorg/lwjgl/opencl/CLKernel;IS)V
setArgSize(Lorg/lwjgl/opencl/CLKernel;IJ)V
```

#### `org.lwjgl.opencl.CLKernel`

```text
<init>(JLorg/lwjgl/opencl/CLProgram;)V
getParent()Lorg/lwjgl/opencl/CLObject;
getPointer()J
isValid()Z
release()I
retain()I
utilLorg/lwjgl/opencl/CLKernel$CLKernelUtil;
```

#### `org.lwjgl.opencl.CLLinkProgramCallback`

```text
getPointer()J
setContext(Lorg/lwjgl/opencl/CLContext;)V
```

#### `org.lwjgl.opencl.CLMem$CLMemUtil`

```text
createImage2D(Lorg/lwjgl/opencl/CLContext;JLorg/lwjgl/opencl/api/CLImageFormat;JJJLjava/nio/Buffer;Ljava/nio/IntBuffer;)Lorg/lwjgl/opencl/CLMem;
createImage3D(Lorg/lwjgl/opencl/CLContext;JLorg/lwjgl/opencl/api/CLImageFormat;JJJJJLjava/nio/Buffer;Ljava/nio/IntBuffer;)Lorg/lwjgl/opencl/CLMem;
createSubBuffer(Lorg/lwjgl/opencl/CLMem;JILorg/lwjgl/opencl/api/CLBufferRegion;Ljava/nio/IntBuffer;)Lorg/lwjgl/opencl/CLMem;
getGLObjectName(Lorg/lwjgl/opencl/CLMem;)I
getGLObjectType(Lorg/lwjgl/opencl/CLMem;)I
getGLTextureInfoInt(Lorg/lwjgl/opencl/CLMem;I)I
getImageInfoFormat(Lorg/lwjgl/opencl/CLMem;)Lorg/lwjgl/opencl/api/CLImageFormat;
getImageInfoFormat(Lorg/lwjgl/opencl/CLMem;I)I
getImageInfoSize(Lorg/lwjgl/opencl/CLMem;I)J
getInfoHostBuffer(Lorg/lwjgl/opencl/CLMem;)Ljava/nio/ByteBuffer;
getInfoInt(Lorg/lwjgl/opencl/CLObject;I)I
getInfoLong(Lorg/lwjgl/opencl/CLObject;I)J
getInfoSize(Lorg/lwjgl/opencl/CLObject;I)J
```

#### `org.lwjgl.opencl.CLMem`

```text
<init>(JLorg/lwjgl/opencl/CLContext;)V
checkValid()V
create(JLorg/lwjgl/opencl/CLContext;)Lorg/lwjgl/opencl/CLMem;
getParent()Lorg/lwjgl/opencl/CLObject;
getPointer()J
isValid()Z
release()I
retain()I
utilLorg/lwjgl/opencl/CLMem$CLMemUtil;
```

#### `org.lwjgl.opencl.CLMemObjectDestructorCallback`

```text
getPointer()J
```

#### `org.lwjgl.opencl.CLNativeKernel`

```text
getPointer()J
```

#### `org.lwjgl.opencl.CLObject`

```text
<init>(J)V
checkValid()V
isValid()Z
pointerJ
```

#### `org.lwjgl.opencl.CLObjectChild`

```text
<init>(JLorg/lwjgl/opencl/CLObject;)V
getPointer()J
getPointerUnsafe()J
isValid()Z
parentLorg/lwjgl/opencl/CLObject;
release()I
retain()I
```

#### `org.lwjgl.opencl.CLObjectRegistry`

```text
<init>()V
getAll()Ljava/lang/Iterable;
getMap()Lorg/lwjgl/opencl/FastLongMap;
getObject(J)Lorg/lwjgl/opencl/CLObjectChild;
hasObject(J)Z
isEmpty()Z
registerObject(Lorg/lwjgl/opencl/CLObjectChild;)V
registryLorg/lwjgl/opencl/FastLongMap;
unregisterObject(Lorg/lwjgl/opencl/CLObjectChild;)V
```

#### `org.lwjgl.opencl.CLObjectRetainable`

```text
<init>(J)V
checkValid()V
refCountI
```

#### `org.lwjgl.opencl.CLPlatform$CLPlatformUtil`

```text
getDevices(Lorg/lwjgl/opencl/CLPlatform;ILorg/lwjgl/opencl/api/Filter;)Ljava/util/List;
getInfoString(Lorg/lwjgl/opencl/CLObject;I)Ljava/lang/String;
getPlatforms(Lorg/lwjgl/opencl/api/Filter;)Ljava/util/List;
```

#### `org.lwjgl.opencl.CLPlatform`

```text
<init>(J)V
capsLjava/lang/Object;
checkValid()V
clDevicesLorg/lwjgl/opencl/CLObjectRegistry;
clPlatformsLorg/lwjgl/opencl/FastLongMap;
getCLDevice(J)Lorg/lwjgl/opencl/CLDevice;
getCLDeviceRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
getCLPlatform(J)Lorg/lwjgl/opencl/CLPlatform;
getCapabilities()Ljava/lang/Object;
getDevices(ILorg/lwjgl/opencl/api/Filter;)Ljava/util/List;
getInfoString(I)Ljava/lang/String;
getInfoUtilInstance(Ljava/lang/Class;Ljava/lang/String;)Lorg/lwjgl/opencl/InfoUtil;
getPlatforms(Lorg/lwjgl/opencl/api/Filter;)Ljava/util/List;
getPointer()J
isValid()Z
registerCLDevices(Ljava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)V
registerCLDevices(Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;)V
registerCLPlatforms(Lorg/lwjgl/PointerBuffer;Ljava/nio/IntBuffer;)V
setCapabilities(Ljava/lang/Object;)V
utilLorg/lwjgl/opencl/CLPlatform$CLPlatformUtil;
```

#### `org.lwjgl.opencl.CLPlatformCapabilities`

```text
<init>(Lorg/lwjgl/opencl/CLPlatform;)V
CL_APPLE_ContextLoggingFunctionsZ
CL_APPLE_SetMemObjectDestructorZ
CL_APPLE_gl_sharingZ
CL_KHR_d3d10_sharingZ
CL_KHR_gl_eventZ
CL_KHR_gl_sharingZ
CL_KHR_icdZ
OpenCL11Z
OpenCL12Z
majorVersionI
minorVersionI
```

#### `org.lwjgl.opencl.CLPrintfCallback`

```text
getPointer()J
```

#### `org.lwjgl.opencl.CLProgram$CLProgramUtil`

```text
createKernelsInProgram(Lorg/lwjgl/opencl/CLProgram;)[Lorg/lwjgl/opencl/CLKernel;
getBuildInfoInt(Lorg/lwjgl/opencl/CLProgram;Lorg/lwjgl/opencl/CLDevice;I)I
getBuildInfoString(Lorg/lwjgl/opencl/CLProgram;Lorg/lwjgl/opencl/CLDevice;I)Ljava/lang/String;
getInfoBinaries(Lorg/lwjgl/opencl/CLProgram;Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
getInfoBinaries(Lorg/lwjgl/opencl/CLProgram;[Ljava/nio/ByteBuffer;)[Ljava/nio/ByteBuffer;
getInfoDevices(Lorg/lwjgl/opencl/CLProgram;)[Lorg/lwjgl/opencl/CLDevice;
getInfoInt(Lorg/lwjgl/opencl/CLObject;I)I
getInfoSizeArray(Lorg/lwjgl/opencl/CLObject;I)[J
getInfoString(Lorg/lwjgl/opencl/CLObject;I)Ljava/lang/String;
```

#### `org.lwjgl.opencl.CLProgram`

```text
<init>(JLorg/lwjgl/opencl/CLContext;)V
checkValid()V
clKernelsLorg/lwjgl/opencl/CLObjectRegistry;
getCLKernel(J)Lorg/lwjgl/opencl/CLKernel;
getCLKernelRegistry()Lorg/lwjgl/opencl/CLObjectRegistry;
getParent()Lorg/lwjgl/opencl/CLObject;
getPointer()J
getReferenceCount()I
isValid()Z
registerCLKernels(Lorg/lwjgl/PointerBuffer;)V
release()I
retain()I
utilLorg/lwjgl/opencl/CLProgram$CLProgramUtil;
```

#### `org.lwjgl.opencl.CLProgramCallback`

```text
<init>()V
contextLorg/lwjgl/opencl/CLContext;
handleMessage(Lorg/lwjgl/opencl/CLProgram;)V
```

#### `org.lwjgl.opencl.CLSampler`

```text
<init>(JLorg/lwjgl/opencl/CLContext;)V
getParent()Lorg/lwjgl/opencl/CLObject;
getPointer()J
isValid()Z
release()I
retain()I
utilLorg/lwjgl/opencl/InfoUtil;
```

#### `org.lwjgl.opencl.CallbackUtil`

```text
checkCallback(IJ)V
contextUserDataLjava/util/Map;
createGlobalRef(Ljava/lang/Object;)J
deleteGlobalRef(J)V
getContextCallback()J
getEventCallback()J
getLogMessageToStderrAPPLE()J
getLogMessageToStdoutAPPLE()J
getLogMessageToSystemLogAPPLE()J
getMemObjectDestructorCallback()J
getNativeKernelCallback()J
getPrintfCallback()J
getProgramCallback()J
ncreateGlobalRef(Ljava/lang/Object;)J
```

#### `org.lwjgl.opencl.EXTAtomicCounters32`

Identical byte redundancy.

#### `org.lwjgl.opencl.EXTAtomicCounters64`

Identical byte redundancy.

#### `org.lwjgl.opencl.EXTDeviceFission`

```text
clReleaseDeviceEXT(Lorg/lwjgl/opencl/CLDevice;)I
nclCreateSubDevicesEXT(JJIJJJ)I
nclReleaseDeviceEXT(JJ)I
nclRetainDeviceEXT(JJ)I
```

#### `org.lwjgl.opencl.EXTMigrateMemobject`

```text
nclEnqueueMigrateMemObjectEXT(JIJJIJJJ)I
```

#### `org.lwjgl.opencl.FastLongMap$Entry`

```text
<init>(JLjava/lang/Object;Lorg/lwjgl/opencl/FastLongMap$Entry;)V
keyJ
nextLorg/lwjgl/opencl/FastLongMap$Entry;
valueLjava/lang/Object;
```

#### `org.lwjgl.opencl.FastLongMap$EntryIterator`

```text
<init>(Lorg/lwjgl/opencl/FastLongMap;)V
currentLorg/lwjgl/opencl/FastLongMap$Entry;
next()Lorg/lwjgl/opencl/FastLongMap$Entry;
nextIndexI
reset()V
this$0Lorg/lwjgl/opencl/FastLongMap;
```

#### `org.lwjgl.opencl.FastLongMap`

```text
<init>()V
<init>(IF)V
access$000(Lorg/lwjgl/opencl/FastLongMap;)[Lorg/lwjgl/opencl/FastLongMap$Entry;
capacityI
containsKey(J)Z
get(J)Ljava/lang/Object;
index(J)I
index(JI)I
isEmpty()Z
iterator()Lorg/lwjgl/opencl/FastLongMap$EntryIterator;
maskI
put(JLjava/lang/Object;)Ljava/lang/Object;
rehash([Lorg/lwjgl/opencl/FastLongMap$Entry;)V
remove(J)Ljava/lang/Object;
sizeI
table[Lorg/lwjgl/opencl/FastLongMap$Entry;
thresholdI
```

#### `org.lwjgl.opencl.INTELImmediateExecution`

Identical byte redundancy.

#### `org.lwjgl.opencl.InfoUtil`

```text
getInfoInt(Lorg/lwjgl/opencl/CLObject;I)I
getInfoLong(Lorg/lwjgl/opencl/CLObject;I)J
getInfoSize(Lorg/lwjgl/opencl/CLObject;I)J
getInfoSizeArray(Lorg/lwjgl/opencl/CLObject;I)[J
getInfoString(Lorg/lwjgl/opencl/CLObject;I)Ljava/lang/String;
```

#### `org.lwjgl.opencl.InfoUtilAbstract`

```text
<init>()V
getInfo(Lorg/lwjgl/opencl/CLObject;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
getInfoSizeArraySize(Lorg/lwjgl/opencl/CLObject;I)I
getSizeRet(Lorg/lwjgl/opencl/CLObject;I)I
```

#### `org.lwjgl.opencl.InfoUtilFactory$1`

```text
<init>()V
getInfo(Lorg/lwjgl/opencl/CLCommandQueue;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
```

#### `org.lwjgl.opencl.InfoUtilFactory$2`

```text
<init>()V
getInfo(Lorg/lwjgl/opencl/CLSampler;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
```

#### `org.lwjgl.opencl.InfoUtilFactory$CLContextUtil`

```text
<init>()V
<init>(Lorg/lwjgl/opencl/InfoUtilFactory$1;)V
getInfo(Lorg/lwjgl/opencl/CLContext;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
getInfoInt(Lorg/lwjgl/opencl/CLObject;I)I
```

#### `org.lwjgl.opencl.InfoUtilFactory$CLDeviceUtil`

```text
<init>()V
<init>(Lorg/lwjgl/opencl/InfoUtilFactory$1;)V
getInfo(Lorg/lwjgl/opencl/CLDevice;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
getInfoInt(Lorg/lwjgl/opencl/CLObject;I)I
getInfoSizeArraySize(Lorg/lwjgl/opencl/CLDevice;I)I
```

#### `org.lwjgl.opencl.InfoUtilFactory$CLEventUtil`

```text
<init>()V
<init>(Lorg/lwjgl/opencl/InfoUtilFactory$1;)V
getInfo(Lorg/lwjgl/opencl/CLEvent;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
```

#### `org.lwjgl.opencl.InfoUtilFactory$CLKernelUtil`

```text
<init>()V
<init>(Lorg/lwjgl/opencl/InfoUtilFactory$1;)V
getInfo(Lorg/lwjgl/opencl/CLKernel;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
```

#### `org.lwjgl.opencl.InfoUtilFactory$CLMemUtil`

```text
<init>()V
<init>(Lorg/lwjgl/opencl/InfoUtilFactory$1;)V
getInfo(Lorg/lwjgl/opencl/CLMem;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
getInfoLong(Lorg/lwjgl/opencl/CLObject;I)J
getInfoSize(Lorg/lwjgl/opencl/CLObject;I)J
```

#### `org.lwjgl.opencl.InfoUtilFactory$CLPlatformUtil`

```text
<init>()V
<init>(Lorg/lwjgl/opencl/InfoUtilFactory$1;)V
getInfo(Lorg/lwjgl/opencl/CLPlatform;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
```

#### `org.lwjgl.opencl.InfoUtilFactory$CLProgramUtil`

```text
<init>()V
<init>(Lorg/lwjgl/opencl/InfoUtilFactory$1;)V
getBuildSizeRet(Lorg/lwjgl/opencl/CLProgram;Lorg/lwjgl/opencl/CLDevice;I)I
getInfo(Lorg/lwjgl/opencl/CLProgram;ILjava/nio/ByteBuffer;Lorg/lwjgl/PointerBuffer;)I
getInfoInt(Lorg/lwjgl/opencl/CLObject;I)I
getInfoSizeArraySize(Lorg/lwjgl/opencl/CLProgram;I)I
getSizesBuffer(Lorg/lwjgl/opencl/CLObject;I)Lorg/lwjgl/PointerBuffer;
```

#### `org.lwjgl.opencl.InfoUtilFactory`

```text
CL_COMMAND_QUEUE_UTILLorg/lwjgl/opencl/InfoUtil;
CL_CONTEXT_UTILLorg/lwjgl/opencl/CLContext$CLContextUtil;
CL_DEVICE_UTILLorg/lwjgl/opencl/InfoUtil;
CL_EVENT_UTILLorg/lwjgl/opencl/CLEvent$CLEventUtil;
CL_KERNEL_UTILLorg/lwjgl/opencl/CLKernel$CLKernelUtil;
CL_MEM_UTILLorg/lwjgl/opencl/CLMem$CLMemUtil;
CL_PLATFORM_UTILLorg/lwjgl/opencl/CLPlatform$CLPlatformUtil;
CL_PROGRAM_UTILLorg/lwjgl/opencl/CLProgram$CLProgramUtil;
CL_SAMPLER_UTILLorg/lwjgl/opencl/InfoUtil;
```

#### `org.lwjgl.opencl.KHRFp16`

Identical byte redundancy.

#### `org.lwjgl.opencl.KHRFp64`

Identical byte redundancy.

#### `org.lwjgl.opencl.KHRGLEvent`

```text
nclCreateEventFromGLsyncKHR(JJJJ)J
```

#### `org.lwjgl.opencl.KHRGLSharing`

```text
nclGetGLContextInfoKHR(JIJJJJ)I
```

#### `org.lwjgl.opencl.KHRICD`

```text
nclIcdGetPlatformIDsKHR(IJJJ)I
```

#### `org.lwjgl.opencl.NVDeviceAttributeQuery`

Identical byte redundancy.

#### `org.lwjgl.opencl.OpenCLException`

```text
<init>(Ljava/lang/String;)V
```

#### `org.lwjgl.opencl.Util$1`

```text
<init>()V
```

#### `org.lwjgl.opencl.Util`

```text
CL_ERROR_TOKENSLjava/util/Map;
checkCLError(I)V
throwCLError(I)V
```

#### `org.lwjgl.opencl.api.CLBufferRegion`

```text
STRUCT_SIZEI
getOrigin()I
getSize()I
originI
sizeI
```

#### `org.lwjgl.opencl.api.CLImageFormat`

```text
<init>(II)V
channelOrderI
channelTypeI
getChannelOrder()I
getChannelType()I
```

#### `org.lwjgl.opencl.api.Filter`

```text
accept(Ljava/lang/Object;)Z
```

#### `org.lwjgl.opengl.AMDBlendMinmaxFactor`

Identical byte redundancy.

#### `org.lwjgl.opengl.AMDDebugOutput`

```text
nglDebugMessageCallbackAMD(JJJ)V
nglDebugMessageEnableAMD(IIIJZJ)V
nglDebugMessageInsertAMD(IIIIJJ)V
nglGetDebugMessageLogAMD(IIJJJJJJ)I
```

#### `org.lwjgl.opengl.AMDDebugOutputCallback$1`

```text
<init>()V
printUnknownToken(I)Ljava/lang/String;
```

#### `org.lwjgl.opengl.AMDDebugOutputCallback$Handler`

Identical byte redundancy.

#### `org.lwjgl.opengl.AMDDebugOutputCallback`

```text
<init>(Lorg/lwjgl/opengl/AMDDebugOutputCallback$Handler;)V
CALLBACK_POINTERJ
getHandler()Lorg/lwjgl/opengl/AMDDebugOutputCallback$Handler;
getPointer()J
handlerLorg/lwjgl/opengl/AMDDebugOutputCallback$Handler;
```

#### `org.lwjgl.opengl.AMDDepthClampSeparate`

Identical byte redundancy.

#### `org.lwjgl.opengl.AMDDrawBuffersBlend`

```text
nglBlendEquationIndexedAMD(IIJ)V
nglBlendEquationSeparateIndexedAMD(IIIJ)V
nglBlendFuncIndexedAMD(IIIJ)V
nglBlendFuncSeparateIndexedAMD(IIIIIJ)V
```

#### `org.lwjgl.opengl.AMDMultiDrawIndirect`

```text
nglMultiDrawArraysIndirectAMD(IJIIJ)V
nglMultiDrawArraysIndirectAMDBO(IJIIJ)V
nglMultiDrawElementsIndirectAMD(IIJIIJ)V
nglMultiDrawElementsIndirectAMDBO(IIJIIJ)V
```

#### `org.lwjgl.opengl.AMDNameGenDelete`

```text
nglDeleteNamesAMD(IIJJ)V
nglGenNamesAMD(IIJJ)V
nglIsNameAMD(IIJ)Z
```

#### `org.lwjgl.opengl.AMDPerformanceMonitor`

```text
nglBeginPerfMonitorAMD(IJ)V
nglDeletePerfMonitorsAMD(IJJ)V
nglEndPerfMonitorAMD(IJ)V
nglGenPerfMonitorsAMD(IJJ)V
nglGetPerfMonitorCounterDataAMD(IIIJJJ)V
nglGetPerfMonitorCounterInfoAMD(IIIJJ)V
nglGetPerfMonitorCounterStringAMD(IIIJJJ)V
nglGetPerfMonitorCountersAMD(IJJIJJ)V
nglGetPerfMonitorGroupStringAMD(IIJJJ)V
nglGetPerfMonitorGroupsAMD(JIJJ)V
nglSelectPerfMonitorCountersAMD(IZIIJJ)V
```

#### `org.lwjgl.opengl.AMDPinnedMemory`

Identical byte redundancy.

#### `org.lwjgl.opengl.AMDSamplePositions`

```text
nglSetMultisamplefvAMD(IIJJ)V
```

#### `org.lwjgl.opengl.AMDSeamlessCubemapPerTexture`

Identical byte redundancy.

#### `org.lwjgl.opengl.AMDVertexShaderTessellator`

```text
nglTessellationFactorAMD(FJ)V
nglTessellationModeAMD(IJ)V
```

#### `org.lwjgl.opengl.APIUtil`

```text
<init>()V
array[C
bufferLjava/nio/ByteBuffer;
doublesLjava/nio/DoubleBuffer;
encode(Ljava/nio/ByteBuffer;Ljava/lang/CharSequence;)Ljava/nio/ByteBuffer;
floatsLjava/nio/FloatBuffer;
getArray(Lorg/lwjgl/opengl/ContextCapabilities;I)[C
getBuffer(Lorg/lwjgl/opengl/ContextCapabilities;Ljava/lang/CharSequence;)J
getBuffer(Lorg/lwjgl/opengl/ContextCapabilities;Ljava/lang/CharSequence;I)J
getBuffer(Lorg/lwjgl/opengl/ContextCapabilities;[Ljava/lang/CharSequence;)J
getBufferByte(Lorg/lwjgl/opengl/ContextCapabilities;I)Ljava/nio/ByteBuffer;
getBufferByte0(Lorg/lwjgl/opengl/ContextCapabilities;)J
getBufferByteOffset(Lorg/lwjgl/opengl/ContextCapabilities;I)Ljava/nio/ByteBuffer;
getBufferDouble(Lorg/lwjgl/opengl/ContextCapabilities;)Ljava/nio/DoubleBuffer;
getBufferFloat(Lorg/lwjgl/opengl/ContextCapabilities;)Ljava/nio/FloatBuffer;
getBufferInt(Lorg/lwjgl/opengl/ContextCapabilities;)Ljava/nio/IntBuffer;
getBufferLong(Lorg/lwjgl/opengl/ContextCapabilities;)Ljava/nio/LongBuffer;
getBufferNT(Lorg/lwjgl/opengl/ContextCapabilities;Ljava/lang/CharSequence;)J
getBufferNT(Lorg/lwjgl/opengl/ContextCapabilities;[Ljava/lang/CharSequence;)J
getInt(Lorg/lwjgl/opengl/ContextCapabilities;I)J
getLengths(Lorg/lwjgl/opengl/ContextCapabilities;)Ljava/nio/IntBuffer;
getLengths(Lorg/lwjgl/opengl/ContextCapabilities;I)Ljava/nio/IntBuffer;
getLengths(Lorg/lwjgl/opengl/ContextCapabilities;[Ljava/lang/CharSequence;)J
getString(Lorg/lwjgl/opengl/ContextCapabilities;Ljava/nio/ByteBuffer;)Ljava/lang/String;
getTotalLength([Ljava/lang/CharSequence;)I
intsLjava/nio/IntBuffer;
lengthsLjava/nio/IntBuffer;
longsLjava/nio/LongBuffer;
```

#### `org.lwjgl.opengl.APPLEAuxDepthStencil`

Identical byte redundancy.

#### `org.lwjgl.opengl.APPLEClientStorage`

Identical byte redundancy.

#### `org.lwjgl.opengl.APPLEElementArray`

```text
nglDrawElementArrayAPPLE(IIIJ)V
nglDrawRangeElementArrayAPPLE(IIIIIJ)V
nglElementPointerAPPLE(IJJ)V
nglMultiDrawElementArrayAPPLE(IJJIJ)V
nglMultiDrawRangeElementArrayAPPLE(IIIJJIJ)V
```

#### `org.lwjgl.opengl.APPLEFence`

```text
nglDeleteFencesAPPLE(IJJ)V
nglFinishFenceAPPLE(IJ)V
nglFinishObjectAPPLE(IIJ)V
nglGenFencesAPPLE(IJJ)V
nglIsFenceAPPLE(IJ)Z
nglSetFenceAPPLE(IJ)V
nglTestFenceAPPLE(IJ)Z
nglTestObjectAPPLE(IIJ)Z
```

#### `org.lwjgl.opengl.APPLEFloatPixels`

Identical byte redundancy.

#### `org.lwjgl.opengl.APPLEFlushBufferRange`

```text
nglBufferParameteriAPPLE(IIIJ)V
nglFlushMappedBufferRangeAPPLE(IJJJ)V
```

#### `org.lwjgl.opengl.APPLEObjectPurgeable`

```text
nglGetObjectParameterivAPPLE(IIIJJ)V
nglObjectPurgeableAPPLE(IIIJ)I
nglObjectUnpurgeableAPPLE(IIIJ)I
```

#### `org.lwjgl.opengl.APPLEPackedPixels`

Identical byte redundancy.

#### `org.lwjgl.opengl.APPLERgb422`

Identical byte redundancy.

#### `org.lwjgl.opengl.APPLERowBytes`

Identical byte redundancy.

#### `org.lwjgl.opengl.APPLETextureRange`

```text
nglGetTexParameterPointervAPPLE(IIJJ)Ljava/nio/Buffer;
nglTextureRangeAPPLE(IIJJ)V
```

#### `org.lwjgl.opengl.APPLEVertexArrayObject`

```text
nglBindVertexArrayAPPLE(IJ)V
nglDeleteVertexArraysAPPLE(IJJ)V
nglGenVertexArraysAPPLE(IJJ)V
nglIsVertexArrayAPPLE(IJ)Z
```

#### `org.lwjgl.opengl.APPLEVertexArrayRange`

```text
nglFlushVertexArrayRangeAPPLE(IJJ)V
nglVertexArrayParameteriAPPLE(IIJ)V
nglVertexArrayRangeAPPLE(IJJ)V
```

#### `org.lwjgl.opengl.APPLEVertexProgramEvaluators`

```text
nglDisableVertexAttribAPPLE(IIJ)V
nglEnableVertexAttribAPPLE(IIJ)V
nglIsVertexAttribEnabledAPPLE(IIJ)Z
nglMapVertexAttrib1dAPPLE(IIDDIIJJ)V
nglMapVertexAttrib1fAPPLE(IIFFIIJJ)V
nglMapVertexAttrib2dAPPLE(IIDDIIDDIIJJ)V
nglMapVertexAttrib2fAPPLE(IIFFIIFFIIJJ)V
```

#### `org.lwjgl.opengl.APPLEYcbcr422`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBBaseInstance`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBBlendFuncExtended`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBBufferObject`

```text
<init>()V
glGetBufferParameteriARB(II)I
nglBindBufferARB(IIJ)V
nglBufferDataARB(IJJIJ)V
nglBufferSubDataARB(IJJJJ)V
nglDeleteBuffersARB(IJJ)V
nglGenBuffersARB(IJJ)V
nglGetBufferParameterivARB(IIJJ)V
nglGetBufferPointervARB(IIJJ)Ljava/nio/ByteBuffer;
nglGetBufferSubDataARB(IJJJJ)V
nglIsBufferARB(IJ)Z
nglMapBufferARB(IIJLjava/nio/ByteBuffer;J)Ljava/nio/ByteBuffer;
nglUnmapBufferARB(IJ)Z
```

#### `org.lwjgl.opengl.ARBCLEvent`

```text
nglCreateSyncFromCLeventARB(JJIJ)J
```

#### `org.lwjgl.opengl.ARBColorBufferFloat`

```text
nglClampColorARB(IIJ)V
```

#### `org.lwjgl.opengl.ARBCompressedTexturePixelStorage`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBCopyBuffer`

```text
glCopyBufferSubData(IIJJJ)V
```

#### `org.lwjgl.opengl.ARBDebugOutput`

```text
glDebugMessageCallbackARB(Lorg/lwjgl/opengl/ARBDebugOutputCallback;)V
glDebugMessageControlARB(IIILjava/nio/IntBuffer;Z)V
nglDebugMessageCallbackARB(JJJ)V
nglDebugMessageControlARB(IIIIJZJ)V
nglDebugMessageInsertARB(IIIIIJJ)V
nglGetDebugMessageLogARB(IIJJJJJJJ)I
```

#### `org.lwjgl.opengl.ARBDebugOutputCallback$1`

```text
<init>()V
printUnknownToken(I)Ljava/lang/String;
```

#### `org.lwjgl.opengl.ARBDebugOutputCallback$Handler`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBDebugOutputCallback`

```text
<init>(Lorg/lwjgl/opengl/ARBDebugOutputCallback$Handler;)V
CALLBACK_POINTERJ
getHandler()Lorg/lwjgl/opengl/ARBDebugOutputCallback$Handler;
getPointer()J
handlerLorg/lwjgl/opengl/ARBDebugOutputCallback$Handler;
```

#### `org.lwjgl.opengl.ARBDepthBufferFloat`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBDepthClamp`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBDepthTexture`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBDrawBuffers`

```text
nglDrawBuffersARB(IJJ)V
```

#### `org.lwjgl.opengl.ARBDrawBuffersBlend`

```text
nglBlendEquationSeparateiARB(IIIJ)V
nglBlendEquationiARB(IIJ)V
nglBlendFuncSeparateiARB(IIIIIJ)V
nglBlendFunciARB(IIIJ)V
```

#### `org.lwjgl.opengl.ARBDrawElementsBaseVertex`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBDrawIndirect`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBDrawInstanced`

```text
nglDrawArraysInstancedARB(IIIIJ)V
nglDrawElementsInstancedARB(IIIJIJ)V
nglDrawElementsInstancedARBBO(IIIJIJ)V
```

#### `org.lwjgl.opengl.ARBES2Compatibility`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBFragmentProgram`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBFragmentShader`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBFramebufferObject`

```text
glBindFramebuffer(II)V
glBindRenderbuffer(II)V
glCheckFramebufferStatus(I)I
glDeleteFramebuffers(I)V
glDeleteRenderbuffers(I)V
glFramebufferRenderbuffer(IIII)V
glFramebufferTexture2D(IIIII)V
glGenFramebuffers()I
glGenRenderbuffers()I
glGetRenderbufferParameteri(II)I
glIsFramebuffer(I)Z
glRenderbufferStorage(IIII)V
```

#### `org.lwjgl.opengl.ARBFramebufferSRGB`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBGeometryShader4`

```text
glProgramParameteriARB(III)V
nglFramebufferTextureARB(IIIIJ)V
nglFramebufferTextureFaceARB(IIIIIJ)V
nglFramebufferTextureLayerARB(IIIIIJ)V
nglProgramParameteriARB(IIIJ)V
```

#### `org.lwjgl.opengl.ARBGetProgramBinary`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBGpuShader5`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBGpuShaderFp64`

```text
nglProgramUniform1dEXT(IIDJ)V
nglProgramUniform1dvEXT(IIIJJ)V
nglProgramUniform2dEXT(IIDDJ)V
nglProgramUniform2dvEXT(IIIJJ)V
nglProgramUniform3dEXT(IIDDDJ)V
nglProgramUniform3dvEXT(IIIJJ)V
nglProgramUniform4dEXT(IIDDDDJ)V
nglProgramUniform4dvEXT(IIIJJ)V
nglProgramUniformMatrix2dvEXT(IIIZJJ)V
nglProgramUniformMatrix2x3dvEXT(IIIZJJ)V
nglProgramUniformMatrix2x4dvEXT(IIIZJJ)V
nglProgramUniformMatrix3dvEXT(IIIZJJ)V
nglProgramUniformMatrix3x2dvEXT(IIIZJJ)V
nglProgramUniformMatrix3x4dvEXT(IIIZJJ)V
nglProgramUniformMatrix4dvEXT(IIIZJJ)V
nglProgramUniformMatrix4x2dvEXT(IIIZJJ)V
nglProgramUniformMatrix4x3dvEXT(IIIZJJ)V
```

#### `org.lwjgl.opengl.ARBHalfFloatPixel`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBHalfFloatVertex`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBImaging`

```text
nglColorSubTable(IIIIIJJ)V
nglColorSubTableBO(IIIIIJJ)V
nglColorTable(IIIIIJJ)V
nglColorTableBO(IIIIIJJ)V
nglColorTableParameterfv(IIJJ)V
nglColorTableParameteriv(IIJJ)V
nglConvolutionFilter1D(IIIIIJJ)V
nglConvolutionFilter1DBO(IIIIIJJ)V
nglConvolutionFilter2D(IIIIIIJJ)V
nglConvolutionFilter2DBO(IIIIIIJJ)V
nglConvolutionParameterf(IIFJ)V
nglConvolutionParameterfv(IIJJ)V
nglConvolutionParameteri(IIIJ)V
nglConvolutionParameteriv(IIJJ)V
nglCopyColorSubTable(IIIIIJ)V
nglCopyColorTable(IIIIIJ)V
nglCopyConvolutionFilter1D(IIIIIJ)V
nglCopyConvolutionFilter2D(IIIIIIJ)V
nglGetColorTable(IIIJJ)V
nglGetColorTableParameterfv(IIJJ)V
nglGetColorTableParameteriv(IIJJ)V
nglGetConvolutionFilter(IIIJJ)V
nglGetConvolutionFilterBO(IIIJJ)V
nglGetConvolutionParameterfv(IIJJ)V
nglGetConvolutionParameteriv(IIJJ)V
nglGetHistogram(IZIIJJ)V
nglGetHistogramBO(IZIIJJ)V
nglGetHistogramParameterfv(IIJJ)V
nglGetHistogramParameteriv(IIJJ)V
nglGetMinmax(IZIIJJ)V
nglGetMinmaxBO(IZIIJJ)V
nglGetMinmaxParameterfv(IIJJ)V
nglGetMinmaxParameteriv(IIJJ)V
nglGetSeparableFilter(IIIJJJJ)V
nglGetSeparableFilterBO(IIIJJJJ)V
nglHistogram(IIIZJ)V
nglMinmax(IIZJ)V
nglResetHistogram(IJ)V
nglResetMinmax(IJ)V
nglSeparableFilter2D(IIIIIIJJJ)V
nglSeparableFilter2DBO(IIIIIIJJJ)V
```

#### `org.lwjgl.opengl.ARBInstancedArrays`

```text
nglVertexAttribDivisorARB(IIJ)V
```

#### `org.lwjgl.opengl.ARBInternalformatQuery`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBMapBufferAlignment`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBMapBufferRange`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBMatrixPalette`

```text
nglCurrentPaletteMatrixARB(IJ)V
nglMatrixIndexPointerARB(IIIJJ)V
nglMatrixIndexPointerARBBO(IIIJJ)V
nglMatrixIndexubvARB(IJJ)V
nglMatrixIndexuivARB(IJJ)V
nglMatrixIndexusvARB(IJJ)V
```

#### `org.lwjgl.opengl.ARBMultisample`

```text
nglSampleCoverageARB(FZJ)V
```

#### `org.lwjgl.opengl.ARBMultitexture`

```text
glActiveTextureARB(I)V
glClientActiveTextureARB(I)V
glMultiTexCoord2fARB(IFF)V
nglActiveTextureARB(IJ)V
nglClientActiveTextureARB(IJ)V
nglMultiTexCoord1dARB(IDJ)V
nglMultiTexCoord1fARB(IFJ)V
nglMultiTexCoord1iARB(IIJ)V
nglMultiTexCoord1sARB(ISJ)V
nglMultiTexCoord2dARB(IDDJ)V
nglMultiTexCoord2fARB(IFFJ)V
nglMultiTexCoord2iARB(IIIJ)V
nglMultiTexCoord2sARB(ISSJ)V
nglMultiTexCoord3dARB(IDDDJ)V
nglMultiTexCoord3fARB(IFFFJ)V
nglMultiTexCoord3iARB(IIIIJ)V
nglMultiTexCoord3sARB(ISSSJ)V
nglMultiTexCoord4dARB(IDDDDJ)V
nglMultiTexCoord4fARB(IFFFFJ)V
nglMultiTexCoord4iARB(IIIIIJ)V
nglMultiTexCoord4sARB(ISSSSJ)V
```

#### `org.lwjgl.opengl.ARBOcclusionQuery`

```text
glGetQueryiARB(II)I
nglBeginQueryARB(IIJ)V
nglDeleteQueriesARB(IJJ)V
nglEndQueryARB(IJ)V
nglGenQueriesARB(IJJ)V
nglGetQueryObjectivARB(IIJJ)V
nglGetQueryObjectuivARB(IIJJ)V
nglGetQueryivARB(IIJJ)V
nglIsQueryARB(IJ)Z
```

#### `org.lwjgl.opengl.ARBOcclusionQuery2`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBPixelBufferObject`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBPointParameters`

```text
nglPointParameterfARB(IFJ)V
nglPointParameterfvARB(IJJ)V
```

#### `org.lwjgl.opengl.ARBPointSprite`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBProgram`

```text
<init>()V
glGetProgramiARB(II)I
nglBindProgramARB(IIJ)V
nglDeleteProgramsARB(IJJ)V
nglGenProgramsARB(IJJ)V
nglGetProgramEnvParameterdvARB(IIJJ)V
nglGetProgramEnvParameterfvARB(IIJJ)V
nglGetProgramLocalParameterdvARB(IIJJ)V
nglGetProgramLocalParameterfvARB(IIJJ)V
nglGetProgramStringARB(IIJJ)V
nglGetProgramivARB(IIJJ)V
nglIsProgramARB(IJ)Z
nglProgramEnvParameter4dARB(IIDDDDJ)V
nglProgramEnvParameter4dvARB(IIJJ)V
nglProgramEnvParameter4fARB(IIFFFFJ)V
nglProgramEnvParameter4fvARB(IIJJ)V
nglProgramLocalParameter4dARB(IIDDDDJ)V
nglProgramLocalParameter4dvARB(IIJJ)V
nglProgramLocalParameter4fARB(IIFFFFJ)V
nglProgramLocalParameter4fvARB(IIJJ)V
nglProgramStringARB(IIIJJ)V
```

#### `org.lwjgl.opengl.ARBProvokingVertex`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBRobustness`

```text
nglGetGraphicsResetStatusARB(J)I
nglGetnColorTableARB(IIIIJJ)V
nglGetnCompressedTexImageARB(IIIJJ)V
nglGetnCompressedTexImageARBBO(IIIJJ)V
nglGetnConvolutionFilterARB(IIIIJJ)V
nglGetnConvolutionFilterARBBO(IIIIJJ)V
nglGetnHistogramARB(IZIIIJJ)V
nglGetnHistogramARBBO(IZIIIJJ)V
nglGetnMapdvARB(IIIJJ)V
nglGetnMapfvARB(IIIJJ)V
nglGetnMapivARB(IIIJJ)V
nglGetnMinmaxARB(IZIIIJJ)V
nglGetnMinmaxARBBO(IZIIIJJ)V
nglGetnPixelMapfvARB(IIJJ)V
nglGetnPixelMapuivARB(IIJJ)V
nglGetnPixelMapusvARB(IIJJ)V
nglGetnPolygonStippleARB(IJJ)V
nglGetnSeparableFilterARB(IIIIJIJJJ)V
nglGetnSeparableFilterARBBO(IIIIJIJJJ)V
nglGetnTexImageARB(IIIIIJJ)V
nglGetnTexImageARBBO(IIIIIJJ)V
nglGetnUniformdvARB(IIIJJ)V
nglGetnUniformfvARB(IIIJJ)V
nglGetnUniformivARB(IIIJJ)V
nglGetnUniformuivARB(IIIJJ)V
nglReadnPixelsARB(IIIIIIIJJ)V
nglReadnPixelsARBBO(IIIIIIIJJ)V
```

#### `org.lwjgl.opengl.ARBSampleShading`

```text
nglMinSampleShadingARB(FJ)V
```

#### `org.lwjgl.opengl.ARBSamplerObjects`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBSeamlessCubeMap`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBSeparateShaderObjects`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBShaderAtomicCounters`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBShaderImageLoadStore`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBShaderObjects`

```text
glAttachObjectARB(II)V
glCompileShaderARB(I)V
glCreateProgramObjectARB()I
glCreateShaderObjectARB(I)I
glDeleteObjectARB(I)V
glDetachObjectARB(II)V
glGetInfoLogARB(II)Ljava/lang/String;
glGetInfoLogARB(ILjava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
glGetObjectParameterARB(IILjava/nio/IntBuffer;)V
glGetObjectParameteriARB(II)I
glGetUniformLocationARB(ILjava/lang/CharSequence;)I
glLinkProgramARB(I)V
glShaderSourceARB(ILjava/lang/CharSequence;)V
glShaderSourceARB(ILjava/nio/ByteBuffer;)V
glUniform1ARB(ILjava/nio/FloatBuffer;)V
glUniform1ARB(ILjava/nio/IntBuffer;)V
glUniform1fARB(IF)V
glUniform1iARB(II)V
glUniform2ARB(ILjava/nio/FloatBuffer;)V
glUniform2ARB(ILjava/nio/IntBuffer;)V
glUniform2fARB(IFF)V
glUniform2iARB(III)V
glUniform3ARB(ILjava/nio/FloatBuffer;)V
glUniform3ARB(ILjava/nio/IntBuffer;)V
glUniform3fARB(IFFF)V
glUniform4ARB(ILjava/nio/FloatBuffer;)V
glUniform4ARB(ILjava/nio/IntBuffer;)V
glUniform4fARB(IFFFF)V
glUniform4iARB(IIIII)V
glUniformMatrix2ARB(IZLjava/nio/FloatBuffer;)V
glUniformMatrix3ARB(IZLjava/nio/FloatBuffer;)V
glUniformMatrix4ARB(IZLjava/nio/FloatBuffer;)V
glUseProgramObjectARB(I)V
glValidateProgramARB(I)V
nglAttachObjectARB(IIJ)V
nglCompileShaderARB(IJ)V
nglCreateProgramObjectARB(J)I
nglCreateShaderObjectARB(IJ)I
nglDeleteObjectARB(IJ)V
nglDetachObjectARB(IIJ)V
nglGetActiveUniformARB(IIIJJJJJ)V
nglGetAttachedObjectsARB(IIJJJ)V
nglGetHandleARB(IJ)I
nglGetInfoLogARB(IIJJJ)V
nglGetObjectParameterfvARB(IIJJ)V
nglGetObjectParameterivARB(IIJJ)V
nglGetShaderSourceARB(IIJJJ)V
nglGetUniformLocationARB(IJJ)I
nglGetUniformfvARB(IIJJ)V
nglGetUniformivARB(IIJJ)V
nglLinkProgramARB(IJ)V
nglShaderSourceARB(IIJIJ)V
nglShaderSourceARB3(IIJJJ)V
nglUniform1fARB(IFJ)V
nglUniform1fvARB(IIJJ)V
nglUniform1iARB(IIJ)V
nglUniform1ivARB(IIJJ)V
nglUniform2fARB(IFFJ)V
nglUniform2fvARB(IIJJ)V
nglUniform2iARB(IIIJ)V
nglUniform2ivARB(IIJJ)V
nglUniform3fARB(IFFFJ)V
nglUniform3fvARB(IIJJ)V
nglUniform3iARB(IIIIJ)V
nglUniform3ivARB(IIJJ)V
nglUniform4fARB(IFFFFJ)V
nglUniform4fvARB(IIJJ)V
nglUniform4iARB(IIIIIJ)V
nglUniform4ivARB(IIJJ)V
nglUniformMatrix2fvARB(IIZJJ)V
nglUniformMatrix3fvARB(IIZJJ)V
nglUniformMatrix4fvARB(IIZJJ)V
nglUseProgramObjectARB(IJ)V
nglValidateProgramARB(IJ)V
```

#### `org.lwjgl.opengl.ARBShaderSubroutine`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBShadingLanguage100`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBShadingLanguageInclude`

```text
nglCompileShaderIncludeARB(IIJJJ)V
nglCompileShaderIncludeARB2(IIJJJ)V
nglDeleteNamedStringARB(IJJ)V
nglGetNamedStringARB(IJIJJJ)V
nglGetNamedStringivARB(IJIJJ)V
nglIsNamedStringARB(IJJ)Z
nglNamedStringARB(IIJIJJ)V
```

#### `org.lwjgl.opengl.ARBShadow`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBShadowAmbient`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBSync`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBTessellationShader`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBTextureBorderClamp`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureBufferObject`

```text
nglTexBufferARB(IIIJ)V
```

#### `org.lwjgl.opengl.ARBTextureCompression`

```text
nglCompressedTexImage1DARB(IIIIIIJJ)V
nglCompressedTexImage1DARBBO(IIIIIIJJ)V
nglCompressedTexImage2DARB(IIIIIIIJJ)V
nglCompressedTexImage2DARBBO(IIIIIIIJJ)V
nglCompressedTexImage3DARB(IIIIIIIIJJ)V
nglCompressedTexImage3DARBBO(IIIIIIIIJJ)V
nglCompressedTexSubImage1DARB(IIIIIIJJ)V
nglCompressedTexSubImage1DARBBO(IIIIIIJJ)V
nglCompressedTexSubImage2DARB(IIIIIIIIJJ)V
nglCompressedTexSubImage2DARBBO(IIIIIIIIJJ)V
nglCompressedTexSubImage3DARB(IIIIIIIIIIJJ)V
nglCompressedTexSubImage3DARBBO(IIIIIIIIIIJJ)V
nglGetCompressedTexImageARB(IIJJ)V
nglGetCompressedTexImageARBBO(IIJJ)V
```

#### `org.lwjgl.opengl.ARBTextureCompressionBPTC`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureCompressionRGTC`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBTextureCubeMap`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureCubeMapArray`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureEnvCombine`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureEnvDot3`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureFloat`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureGather`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureMirroredRepeat`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureMultisample`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureRGB10_A2UI`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureRectangle`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTextureRg`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBTextureStorage`

```text
nglTextureStorage1DEXT(IIIIIJ)V
nglTextureStorage2DEXT(IIIIIIJ)V
nglTextureStorage3DEXT(IIIIIIIJ)V
```

#### `org.lwjgl.opengl.ARBTextureSwizzle`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTimerQuery`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBTransformFeedback2`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTransformFeedback3`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBTransformFeedbackInstanced`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBTransposeMatrix`

```text
nglLoadTransposeMatrixfARB(JJ)V
nglMultTransposeMatrixfARB(JJ)V
```

#### `org.lwjgl.opengl.ARBUniformBufferObject`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBVertexArrayBgra`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBVertexArrayObject`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBVertexAttrib64bit`

```text
glVertexArrayVertexAttribLOffsetEXT(IIIIIIJ)V
nglVertexArrayVertexAttribLOffsetEXT(IIIIIIJJ)V
```

#### `org.lwjgl.opengl.ARBVertexBlend`

```text
nglVertexBlendARB(IJ)V
nglWeightPointerARB(IIIJJ)V
nglWeightPointerARBBO(IIIJJ)V
nglWeightbvARB(IJJ)V
nglWeightdvARB(IJJ)V
nglWeightfvARB(IJJ)V
nglWeightivARB(IJJ)V
nglWeightsvARB(IJJ)V
nglWeightubvARB(IJJ)V
nglWeightuivARB(IJJ)V
nglWeightusvARB(IJJ)V
```

#### `org.lwjgl.opengl.ARBVertexBufferObject`

```text
glBindBufferARB(II)V
glBufferDataARB(IJI)V
glBufferDataARB(ILjava/nio/ByteBuffer;I)V
glBufferSubDataARB(IJLjava/nio/ByteBuffer;)V
glDeleteBuffersARB(I)V
glGenBuffersARB()I
```

#### `org.lwjgl.opengl.ARBVertexProgram`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBVertexShader`

```text
glBindAttribLocationARB(IILjava/lang/CharSequence;)V
glDisableVertexAttribArrayARB(I)V
glEnableVertexAttribArrayARB(I)V
glGetAttribLocationARB(ILjava/lang/CharSequence;)I
glGetVertexAttribARB(IILjava/nio/DoubleBuffer;)V
glGetVertexAttribARB(IILjava/nio/FloatBuffer;)V
glGetVertexAttribARB(IILjava/nio/IntBuffer;)V
glGetVertexAttribPointerARB(IIJ)Ljava/nio/ByteBuffer;
glVertexAttrib1dARB(ID)V
glVertexAttrib1fARB(IF)V
glVertexAttrib1sARB(IS)V
glVertexAttrib2dARB(IDD)V
glVertexAttrib2fARB(IFF)V
glVertexAttrib2sARB(ISS)V
glVertexAttrib3dARB(IDDD)V
glVertexAttrib3fARB(IFFF)V
glVertexAttrib3sARB(ISSS)V
glVertexAttrib4NubARB(IBBBB)V
glVertexAttrib4dARB(IDDDD)V
glVertexAttrib4fARB(IFFFF)V
glVertexAttrib4sARB(ISSSS)V
glVertexAttribPointerARB(IIIZIJ)V
glVertexAttribPointerARB(IIZILjava/nio/DoubleBuffer;)V
glVertexAttribPointerARB(IIZILjava/nio/FloatBuffer;)V
glVertexAttribPointerARB(IIZZILjava/nio/ByteBuffer;)V
glVertexAttribPointerARB(IIZZILjava/nio/IntBuffer;)V
glVertexAttribPointerARB(IIZZILjava/nio/ShortBuffer;)V
nglBindAttribLocationARB(IIJJ)V
nglDisableVertexAttribArrayARB(IJ)V
nglEnableVertexAttribArrayARB(IJ)V
nglGetActiveAttribARB(IIIJJJJJ)V
nglGetAttribLocationARB(IJJ)I
nglGetVertexAttribPointervARB(IIJJ)Ljava/nio/ByteBuffer;
nglGetVertexAttribdvARB(IIJJ)V
nglGetVertexAttribfvARB(IIJJ)V
nglGetVertexAttribivARB(IIJJ)V
nglVertexAttrib1dARB(IDJ)V
nglVertexAttrib1fARB(IFJ)V
nglVertexAttrib1sARB(ISJ)V
nglVertexAttrib2dARB(IDDJ)V
nglVertexAttrib2fARB(IFFJ)V
nglVertexAttrib2sARB(ISSJ)V
nglVertexAttrib3dARB(IDDDJ)V
nglVertexAttrib3fARB(IFFFJ)V
nglVertexAttrib3sARB(ISSSJ)V
nglVertexAttrib4NubARB(IBBBBJ)V
nglVertexAttrib4dARB(IDDDDJ)V
nglVertexAttrib4fARB(IFFFFJ)V
nglVertexAttrib4sARB(ISSSSJ)V
nglVertexAttribPointerARB(IIIZIJJ)V
nglVertexAttribPointerARBBO(IIIZIJJ)V
```

#### `org.lwjgl.opengl.ARBVertexType2_10_10_10_REV`

Identical byte redundancy.

#### `org.lwjgl.opengl.ARBViewportArray`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.ARBWindowPos`

```text
nglWindowPos2dARB(DDJ)V
nglWindowPos2fARB(FFJ)V
nglWindowPos2iARB(IIJ)V
nglWindowPos2sARB(SSJ)V
nglWindowPos3dARB(DDDJ)V
nglWindowPos3fARB(FFFJ)V
nglWindowPos3iARB(IIIJ)V
nglWindowPos3sARB(SSSJ)V
```

#### `org.lwjgl.opengl.ATIDrawBuffers`

```text
nglDrawBuffersATI(IJJ)V
```

#### `org.lwjgl.opengl.ATIElementArray`

```text
nglDrawElementArrayATI(IIJ)V
nglDrawRangeElementArrayATI(IIIIJ)V
nglElementPointerATI(IJJ)V
```

#### `org.lwjgl.opengl.ATIEnvmapBumpmap`

```text
nglGetTexBumpParameterfvATI(IJJ)V
nglGetTexBumpParameterivATI(IJJ)V
nglTexBumpParameterfvATI(IJJ)V
nglTexBumpParameterivATI(IJJ)V
```

#### `org.lwjgl.opengl.ATIFragmentShader`

```text
nglAlphaFragmentOp1ATI(IIIIIIJ)V
nglAlphaFragmentOp2ATI(IIIIIIIIIJ)V
nglAlphaFragmentOp3ATI(IIIIIIIIIIIIJ)V
nglBeginFragmentShaderATI(J)V
nglBindFragmentShaderATI(IJ)V
nglColorFragmentOp1ATI(IIIIIIIJ)V
nglColorFragmentOp2ATI(IIIIIIIIIIJ)V
nglColorFragmentOp3ATI(IIIIIIIIIIIIIJ)V
nglDeleteFragmentShaderATI(IJ)V
nglEndFragmentShaderATI(J)V
nglGenFragmentShadersATI(IJ)I
nglPassTexCoordATI(IIIJ)V
nglSampleMapATI(IIIJ)V
nglSetFragmentShaderConstantATI(IJJ)V
```

#### `org.lwjgl.opengl.ATIMapObjectBuffer`

```text
nglMapObjectBufferATI(IJLjava/nio/ByteBuffer;J)Ljava/nio/ByteBuffer;
nglUnmapObjectBufferATI(IJ)V
```

#### `org.lwjgl.opengl.ATIMeminfo`

Identical byte redundancy.

#### `org.lwjgl.opengl.ATIPnTriangles`

```text
nglPNTrianglesfATI(IFJ)V
nglPNTrianglesiATI(IIJ)V
```

#### `org.lwjgl.opengl.ATISeparateStencil`

```text
nglStencilFuncSeparateATI(IIIIJ)V
nglStencilOpSeparateATI(IIIIJ)V
```

#### `org.lwjgl.opengl.ATITextFragmentShader`

Identical byte redundancy.

#### `org.lwjgl.opengl.ATITextureCompression3DC`

Identical byte redundancy.

#### `org.lwjgl.opengl.ATITextureEnvCombine3`

Identical byte redundancy.

#### `org.lwjgl.opengl.ATITextureFloat`

Identical byte redundancy.

#### `org.lwjgl.opengl.ATITextureMirrorOnce`

Identical byte redundancy.

#### `org.lwjgl.opengl.ATIVertexArrayObject`

```text
glGetObjectBufferiATI(II)I
nglArrayObjectATI(IIIIIIJ)V
nglFreeObjectBufferATI(IJ)V
nglGetArrayObjectfvATI(IIJJ)V
nglGetArrayObjectivATI(IIJJ)V
nglGetObjectBufferfvATI(IIJJ)V
nglGetObjectBufferivATI(IIJJ)V
nglGetVariantArrayObjectfvATI(IIJJ)V
nglGetVariantArrayObjectivATI(IIJJ)V
nglIsObjectBufferATI(IJ)Z
nglNewObjectBufferATI(IJIJ)I
nglUpdateObjectBufferATI(IIIJIJ)V
nglVariantArrayObjectATI(IIIIIJ)V
```

#### `org.lwjgl.opengl.ATIVertexAttribArrayObject`

```text
nglGetVertexAttribArrayObjectfvATI(IIJJ)V
nglGetVertexAttribArrayObjectivATI(IIJJ)V
nglVertexAttribArrayObjectATI(IIIZIIIJ)V
```

#### `org.lwjgl.opengl.ATIVertexStreams`

```text
nglClientActiveVertexStreamATI(IJ)V
nglNormalStream3bATI(IBBBJ)V
nglNormalStream3dATI(IDDDJ)V
nglNormalStream3fATI(IFFFJ)V
nglNormalStream3iATI(IIIIJ)V
nglNormalStream3sATI(ISSSJ)V
nglVertexBlendEnvfATI(IFJ)V
nglVertexBlendEnviATI(IIJ)V
nglVertexStream2dATI(IDDJ)V
nglVertexStream2fATI(IFFJ)V
nglVertexStream2iATI(IIIJ)V
nglVertexStream2sATI(ISSJ)V
nglVertexStream3dATI(IDDDJ)V
nglVertexStream3fATI(IFFFJ)V
nglVertexStream3iATI(IIIIJ)V
nglVertexStream3sATI(ISSSJ)V
nglVertexStream4dATI(IDDDDJ)V
nglVertexStream4fATI(IFFFFJ)V
nglVertexStream4iATI(IIIIIJ)V
nglVertexStream4sATI(ISSSSJ)V
```

#### `org.lwjgl.opengl.AWTCanvasImplementation`

```text
createPeerInfo(Ljava/awt/Canvas;Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;)Lorg/lwjgl/opengl/PeerInfo;
findConfiguration(Ljava/awt/GraphicsDevice;Lorg/lwjgl/opengl/PixelFormat;)Ljava/awt/GraphicsConfiguration;
```

#### `org.lwjgl.opengl.AWTGLCanvas`

```text
<init>(Ljava/awt/GraphicsDevice;Lorg/lwjgl/opengl/PixelFormat;)V
<init>(Ljava/awt/GraphicsDevice;Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/Drawable;)V
<init>(Ljava/awt/GraphicsDevice;Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/Drawable;Lorg/lwjgl/opengl/ContextAttribs;)V
<init>(Lorg/lwjgl/opengl/PixelFormat;)V
SYNC_LOCKLjava/lang/Object;
addComponentListener(Ljava/awt/event/ComponentListener;)V
addHierarchyListener(Ljava/awt/event/HierarchyListener;)V
attribsLorg/lwjgl/opengl/ContextAttribs;
contextLorg/lwjgl/opengl/ContextGL;
createImplementation()Lorg/lwjgl/opengl/AWTCanvasImplementation;
createSharedContext()Lorg/lwjgl/opengl/ContextGL;
destroy()V
drawableLorg/lwjgl/opengl/Drawable;
exceptionOccurred(Lorg/lwjgl/LWJGLException;)V
first_runZ
getContext()Lorg/lwjgl/opengl/ContextGL;
implementationLorg/lwjgl/opengl/AWTCanvasImplementation;
initGL()V
isDisplayable()Z
paint(Ljava/awt/Graphics;)V
paintGL()V
peer_infoLorg/lwjgl/opengl/PeerInfo;
pixel_formatLorg/lwjgl/opengl/PixelFormat;
reentry_countI
setSwapInterval(I)V
setUpdate()V
update_contextZ
```

#### `org.lwjgl.opengl.AWTSurfaceLock$1`

```text
<init>(Lorg/lwjgl/opengl/AWTSurfaceLock;Ljava/awt/Canvas;)V
run()Ljava/lang/Boolean;
this$0Lorg/lwjgl/opengl/AWTSurfaceLock;
val$componentLjava/awt/Canvas;
```

#### `org.lwjgl.opengl.AWTSurfaceLock`

```text
<init>()V
access$000(Lorg/lwjgl/opengl/AWTSurfaceLock;)Ljava/nio/ByteBuffer;
access$100(Ljava/nio/ByteBuffer;Ljava/awt/Canvas;)Z
createHandle()Ljava/nio/ByteBuffer;
firstLockSucceededZ
lockAndGetHandle(Ljava/awt/Canvas;)Ljava/nio/ByteBuffer;
lockAndInitHandle(Ljava/nio/ByteBuffer;Ljava/awt/Canvas;)Z
lock_bufferLjava/nio/ByteBuffer;
nUnlock(Ljava/nio/ByteBuffer;)V
privilegedLockAndInitHandle(Ljava/awt/Canvas;)Z
unlock()V
```

#### `org.lwjgl.opengl.AWTUtil$1`

```text
<init>(Ljava/awt/Component;)V
run()Ljava/awt/Robot;
val$componentLjava/awt/Component;
```

#### `org.lwjgl.opengl.AWTUtil$2`

```text
<init>()V
run()Ljava/awt/PointerInfo;
```

#### `org.lwjgl.opengl.AWTUtil`

```text
createCursor(IIIIILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)Ljava/awt/Cursor;
createRobot(Ljava/awt/Component;)Ljava/awt/Robot;
getButtonCount()I
getCursorPosition(Ljava/awt/Component;)Ljava/awt/Point;
getMaxCursorSize()I
getNativeCursorCapabilities()I
getPointerLocation(Ljava/awt/Component;)Ljava/awt/Point;
hasWheel()Z
transformY(Ljava/awt/Component;I)I
```

#### `org.lwjgl.opengl.BaseReferences`

```text
<init>(Lorg/lwjgl/opengl/ContextCapabilities;)V
arrayBufferI
clear()V
copy(Lorg/lwjgl/opengl/BaseReferences;I)V
elementArrayBufferI
glClientActiveTextureI
glTexCoordPointer_buffer[Ljava/nio/Buffer;
glVertexAttribPointer_buffer[Ljava/nio/Buffer;
indirectBufferI
pixelPackBufferI
pixelUnpackBufferI
vertexArrayObjectI
```

#### `org.lwjgl.opengl.CallbackUtil`

```text
contextUserParamsAMDLjava/util/Map;
contextUserParamsARBLjava/util/Map;
contextUserParamsKHRLjava/util/Map;
createGlobalRef(Ljava/lang/Object;)J
deleteGlobalRef(J)V
ncreateGlobalRef(Ljava/lang/Object;)J
registerContextCallback(JLjava/util/Map;)V
registerContextCallbackAMD(J)V
registerContextCallbackARB(J)V
registerContextCallbackKHR(J)V
unregisterCallbacks(Ljava/lang/Object;)V
```

#### `org.lwjgl.opengl.Context`

```text
isCurrent()Z
makeCurrent()V
releaseCurrent()V
releaseDrawable()V
```

#### `org.lwjgl.opengl.ContextAttribs`

```text
<init>()V
<init>(II)V
<init>(IIII)V
<init>(Lorg/lwjgl/opengl/ContextAttribs;)V
contextFlagsI
contextReleaseBehaviorI
contextResetNotificationStrategyI
getAttribList()Ljava/nio/IntBuffer;
getMajorVersion()I
getMinorVersion()I
hasFlag(I)Z
hasMask(I)Z
isForwardCompatible()Z
isProfileCore()Z
layerPlaneI
majorVersionI
minorVersionI
profileMaskI
toggleFlag(IZ)Lorg/lwjgl/opengl/ContextAttribs;
toggleMask(IZ)Lorg/lwjgl/opengl/ContextAttribs;
withDebug(Z)Lorg/lwjgl/opengl/ContextAttribs;
withResetNotificationStrategy(I)Lorg/lwjgl/opengl/ContextAttribs;
```

#### `org.lwjgl.opengl.ContextCapabilities`

```text
<init>(Z)V
AMD_debug_output_initNativeFunctionAddresses()Z
AMD_draw_buffers_blend_initNativeFunctionAddresses()Z
AMD_interleaved_elements_initNativeFunctionAddresses()Z
AMD_multi_draw_indirect_initNativeFunctionAddresses()Z
AMD_name_gen_delete_initNativeFunctionAddresses()Z
AMD_performance_monitor_initNativeFunctionAddresses()Z
AMD_sample_positions_initNativeFunctionAddresses()Z
AMD_sparse_texture_initNativeFunctionAddresses()Z
AMD_stencil_operation_extended_initNativeFunctionAddresses()Z
AMD_vertex_shader_tessellator_initNativeFunctionAddresses()Z
APPLE_element_array_initNativeFunctionAddresses()Z
APPLE_fence_initNativeFunctionAddresses()Z
APPLE_flush_buffer_range_initNativeFunctionAddresses()Z
APPLE_object_purgeable_initNativeFunctionAddresses()Z
APPLE_texture_range_initNativeFunctionAddresses()Z
APPLE_vertex_array_object_initNativeFunctionAddresses()Z
APPLE_vertex_array_range_initNativeFunctionAddresses()Z
APPLE_vertex_program_evaluators_initNativeFunctionAddresses()Z
ARB_ES2_compatibility_initNativeFunctionAddresses()Z
ARB_ES3_1_compatibility_initNativeFunctionAddresses()Z
ARB_base_instance_initNativeFunctionAddresses()Z
ARB_bindless_texture_initNativeFunctionAddresses()Z
ARB_blend_func_extended_initNativeFunctionAddresses()Z
ARB_buffer_object_initNativeFunctionAddresses()Z
ARB_buffer_storage_initNativeFunctionAddresses(Ljava/util/Set;)Z
ARB_cl_event_initNativeFunctionAddresses()Z
ARB_clear_buffer_object_initNativeFunctionAddresses(Ljava/util/Set;)Z
ARB_clear_texture_initNativeFunctionAddresses()Z
ARB_clip_control_initNativeFunctionAddresses()Z
ARB_color_buffer_float_initNativeFunctionAddresses()Z
ARB_compute_shader_initNativeFunctionAddresses()Z
ARB_compute_variable_group_size_initNativeFunctionAddresses()Z
ARB_copy_buffer_initNativeFunctionAddresses()Z
ARB_copy_image_initNativeFunctionAddresses()Z
ARB_debug_output_initNativeFunctionAddresses()Z
ARB_direct_state_access_initNativeFunctionAddresses()Z
ARB_draw_buffers_blend_initNativeFunctionAddresses()Z
ARB_draw_buffers_initNativeFunctionAddresses()Z
ARB_draw_elements_base_vertex_initNativeFunctionAddresses()Z
ARB_draw_indirect_initNativeFunctionAddresses()Z
ARB_draw_instanced_initNativeFunctionAddresses()Z
ARB_framebuffer_no_attachments_initNativeFunctionAddresses(Ljava/util/Set;)Z
ARB_framebuffer_object_initNativeFunctionAddresses()Z
ARB_geometry_shader4_initNativeFunctionAddresses()Z
ARB_get_program_binary_initNativeFunctionAddresses()Z
ARB_get_texture_sub_image_initNativeFunctionAddresses()Z
ARB_gpu_shader_fp64_initNativeFunctionAddresses(Ljava/util/Set;)Z
ARB_imaging_initNativeFunctionAddresses(Z)Z
ARB_indirect_parameters_initNativeFunctionAddresses()Z
ARB_instanced_arrays_initNativeFunctionAddresses()Z
ARB_internalformat_query2_initNativeFunctionAddresses()Z
ARB_internalformat_query_initNativeFunctionAddresses()Z
ARB_invalidate_subdata_initNativeFunctionAddresses()Z
ARB_map_buffer_range_initNativeFunctionAddresses()Z
ARB_matrix_palette_initNativeFunctionAddresses()Z
ARB_multi_bind_initNativeFunctionAddresses()Z
ARB_multi_draw_indirect_initNativeFunctionAddresses()Z
ARB_multisample_initNativeFunctionAddresses()Z
ARB_multitexture_initNativeFunctionAddresses()Z
ARB_occlusion_query_initNativeFunctionAddresses()Z
ARB_point_parameters_initNativeFunctionAddresses()Z
ARB_program_initNativeFunctionAddresses()Z
ARB_program_interface_query_initNativeFunctionAddresses()Z
ARB_provoking_vertex_initNativeFunctionAddresses()Z
ARB_robustness_initNativeFunctionAddresses(ZLjava/util/Set;)Z
ARB_sample_shading_initNativeFunctionAddresses()Z
ARB_sampler_objects_initNativeFunctionAddresses()Z
ARB_separate_shader_objects_initNativeFunctionAddresses()Z
ARB_shader_atomic_counters_initNativeFunctionAddresses()Z
ARB_shader_image_load_store_initNativeFunctionAddresses()Z
ARB_shader_objects_initNativeFunctionAddresses()Z
ARB_shader_storage_buffer_object_initNativeFunctionAddresses()Z
ARB_shader_subroutine_initNativeFunctionAddresses()Z
ARB_shading_language_include_initNativeFunctionAddresses()Z
ARB_sparse_buffer_initNativeFunctionAddresses()Z
ARB_sparse_texture_initNativeFunctionAddresses(Ljava/util/Set;)Z
ARB_sync_initNativeFunctionAddresses()Z
ARB_tessellation_shader_initNativeFunctionAddresses()Z
ARB_texture_barrier_initNativeFunctionAddresses()Z
ARB_texture_buffer_object_initNativeFunctionAddresses()Z
ARB_texture_buffer_range_initNativeFunctionAddresses(Ljava/util/Set;)Z
ARB_texture_compression_initNativeFunctionAddresses()Z
ARB_texture_multisample_initNativeFunctionAddresses()Z
ARB_texture_storage_initNativeFunctionAddresses(Ljava/util/Set;)Z
ARB_texture_storage_multisample_initNativeFunctionAddresses(Ljava/util/Set;)Z
ARB_texture_view_initNativeFunctionAddresses()Z
ARB_timer_query_initNativeFunctionAddresses()Z
ARB_transform_feedback2_initNativeFunctionAddresses()Z
ARB_transform_feedback3_initNativeFunctionAddresses()Z
ARB_transform_feedback_instanced_initNativeFunctionAddresses()Z
ARB_transpose_matrix_initNativeFunctionAddresses()Z
ARB_uniform_buffer_object_initNativeFunctionAddresses()Z
ARB_vertex_array_object_initNativeFunctionAddresses()Z
ARB_vertex_attrib_64bit_initNativeFunctionAddresses(Ljava/util/Set;)Z
ARB_vertex_attrib_binding_initNativeFunctionAddresses()Z
ARB_vertex_blend_initNativeFunctionAddresses()Z
ARB_vertex_program_initNativeFunctionAddresses()Z
ARB_vertex_shader_initNativeFunctionAddresses()Z
ARB_vertex_type_2_10_10_10_rev_initNativeFunctionAddresses()Z
ARB_viewport_array_initNativeFunctionAddresses()Z
ARB_window_pos_initNativeFunctionAddresses(Z)Z
ATI_draw_buffers_initNativeFunctionAddresses()Z
ATI_element_array_initNativeFunctionAddresses()Z
ATI_envmap_bumpmap_initNativeFunctionAddresses()Z
ATI_fragment_shader_initNativeFunctionAddresses()Z
ATI_map_object_buffer_initNativeFunctionAddresses()Z
ATI_pn_triangles_initNativeFunctionAddresses()Z
ATI_separate_stencil_initNativeFunctionAddresses()Z
ATI_vertex_array_object_initNativeFunctionAddresses()Z
ATI_vertex_attrib_array_object_initNativeFunctionAddresses()Z
ATI_vertex_streams_initNativeFunctionAddresses()Z
EXT_bindable_uniform_initNativeFunctionAddresses()Z
EXT_blend_color_initNativeFunctionAddresses()Z
EXT_blend_equation_separate_initNativeFunctionAddresses()Z
EXT_blend_func_separate_initNativeFunctionAddresses()Z
EXT_blend_minmax_initNativeFunctionAddresses()Z
EXT_compiled_vertex_array_initNativeFunctionAddresses()Z
EXT_depth_bounds_test_initNativeFunctionAddresses()Z
EXT_direct_state_access_initNativeFunctionAddresses(ZLjava/util/Set;)Z
EXT_draw_buffers2_initNativeFunctionAddresses()Z
EXT_draw_instanced_initNativeFunctionAddresses()Z
EXT_draw_range_elements_initNativeFunctionAddresses()Z
EXT_fog_coord_initNativeFunctionAddresses()Z
EXT_framebuffer_blit_initNativeFunctionAddresses()Z
EXT_framebuffer_multisample_initNativeFunctionAddresses()Z
EXT_framebuffer_object_initNativeFunctionAddresses()Z
EXT_geometry_shader4_initNativeFunctionAddresses()Z
EXT_gpu_program_parameters_initNativeFunctionAddresses()Z
EXT_gpu_shader4_initNativeFunctionAddresses()Z
EXT_multi_draw_arrays_initNativeFunctionAddresses()Z
EXT_paletted_texture_initNativeFunctionAddresses()Z
EXT_point_parameters_initNativeFunctionAddresses()Z
EXT_provoking_vertex_initNativeFunctionAddresses()Z
EXT_secondary_color_initNativeFunctionAddresses()Z
EXT_separate_shader_objects_initNativeFunctionAddresses()Z
EXT_shader_image_load_store_initNativeFunctionAddresses()Z
EXT_stencil_clear_tag_initNativeFunctionAddresses()Z
EXT_stencil_two_side_initNativeFunctionAddresses()Z
EXT_texture_array_initNativeFunctionAddresses()Z
EXT_texture_buffer_object_initNativeFunctionAddresses()Z
EXT_texture_integer_initNativeFunctionAddresses()Z
EXT_timer_query_initNativeFunctionAddresses()Z
EXT_transform_feedback_initNativeFunctionAddresses()Z
EXT_vertex_attrib_64bit_initNativeFunctionAddresses(Ljava/util/Set;)Z
EXT_vertex_shader_initNativeFunctionAddresses()Z
EXT_vertex_weighting_initNativeFunctionAddresses()Z
GL11_initNativeFunctionAddresses(Z)Z
GL12_initNativeFunctionAddresses()Z
GL13_initNativeFunctionAddresses(Z)Z
GL14_initNativeFunctionAddresses(Z)Z
GL15_initNativeFunctionAddresses()Z
GL20_initNativeFunctionAddresses()Z
GL21_initNativeFunctionAddresses()Z
GL30_initNativeFunctionAddresses()Z
GL31_initNativeFunctionAddresses()Z
GL32_initNativeFunctionAddresses()Z
GL33_initNativeFunctionAddresses(Z)Z
GL40_initNativeFunctionAddresses()Z
GL41_initNativeFunctionAddresses()Z
GL42_initNativeFunctionAddresses()Z
GL43_initNativeFunctionAddresses()Z
GL44_initNativeFunctionAddresses()Z
GL45_initNativeFunctionAddresses()Z
GL_AMD_blend_minmax_factorZ
GL_AMD_conservative_depthZ
GL_AMD_debug_outputZ
GL_AMD_depth_clamp_separateZ
GL_AMD_draw_buffers_blendZ
GL_AMD_interleaved_elementsZ
GL_AMD_multi_draw_indirectZ
GL_AMD_name_gen_deleteZ
GL_AMD_performance_monitorZ
GL_AMD_pinned_memoryZ
GL_AMD_query_buffer_objectZ
GL_AMD_sample_positionsZ
GL_AMD_seamless_cubemap_per_textureZ
GL_AMD_shader_atomic_counter_opsZ
GL_AMD_shader_stencil_exportZ
GL_AMD_shader_trinary_minmaxZ
GL_AMD_sparse_textureZ
GL_AMD_stencil_operation_extendedZ
GL_AMD_texture_texture4Z
GL_AMD_transform_feedback3_lines_trianglesZ
GL_AMD_vertex_shader_layerZ
GL_AMD_vertex_shader_tessellatorZ
GL_AMD_vertex_shader_viewport_indexZ
GL_APPLE_aux_depth_stencilZ
GL_APPLE_client_storageZ
GL_APPLE_element_arrayZ
GL_APPLE_fenceZ
GL_APPLE_float_pixelsZ
GL_APPLE_flush_buffer_rangeZ
GL_APPLE_object_purgeableZ
GL_APPLE_packed_pixelsZ
GL_APPLE_rgb_422Z
GL_APPLE_row_bytesZ
GL_APPLE_texture_rangeZ
GL_APPLE_vertex_array_objectZ
GL_APPLE_vertex_array_rangeZ
GL_APPLE_vertex_program_evaluatorsZ
GL_APPLE_ycbcr_422Z
GL_ARB_ES2_compatibilityZ
GL_ARB_ES3_1_compatibilityZ
GL_ARB_ES3_compatibilityZ
GL_ARB_arrays_of_arraysZ
GL_ARB_base_instanceZ
GL_ARB_bindless_textureZ
GL_ARB_blend_func_extendedZ
GL_ARB_buffer_storageZ
GL_ARB_cl_eventZ
GL_ARB_clear_buffer_objectZ
GL_ARB_clear_textureZ
GL_ARB_clip_controlZ
GL_ARB_color_buffer_floatZ
GL_ARB_compatibilityZ
GL_ARB_compressed_texture_pixel_storageZ
GL_ARB_compute_shaderZ
GL_ARB_compute_variable_group_sizeZ
GL_ARB_conditional_render_invertedZ
GL_ARB_conservative_depthZ
GL_ARB_copy_bufferZ
GL_ARB_copy_imageZ
GL_ARB_cull_distanceZ
GL_ARB_debug_outputZ
GL_ARB_depth_buffer_floatZ
GL_ARB_depth_clampZ
GL_ARB_depth_textureZ
GL_ARB_derivative_controlZ
GL_ARB_direct_state_accessZ
GL_ARB_draw_buffersZ
GL_ARB_draw_buffers_blendZ
GL_ARB_draw_elements_base_vertexZ
GL_ARB_draw_indirectZ
GL_ARB_draw_instancedZ
GL_ARB_enhanced_layoutsZ
GL_ARB_explicit_attrib_locationZ
GL_ARB_explicit_uniform_locationZ
GL_ARB_fragment_coord_conventionsZ
GL_ARB_fragment_layer_viewportZ
GL_ARB_fragment_programZ
GL_ARB_fragment_program_shadowZ
GL_ARB_fragment_shaderZ
GL_ARB_framebuffer_no_attachmentsZ
GL_ARB_framebuffer_objectZ
GL_ARB_framebuffer_sRGBZ
GL_ARB_geometry_shader4Z
GL_ARB_get_program_binaryZ
GL_ARB_get_texture_sub_imageZ
GL_ARB_gpu_shader5Z
GL_ARB_gpu_shader_fp64Z
GL_ARB_half_float_pixelZ
GL_ARB_half_float_vertexZ
GL_ARB_imagingZ
GL_ARB_indirect_parametersZ
GL_ARB_instanced_arraysZ
GL_ARB_internalformat_queryZ
GL_ARB_internalformat_query2Z
GL_ARB_invalidate_subdataZ
GL_ARB_map_buffer_alignmentZ
GL_ARB_map_buffer_rangeZ
GL_ARB_matrix_paletteZ
GL_ARB_multi_bindZ
GL_ARB_multi_draw_indirectZ
GL_ARB_multisampleZ
GL_ARB_multitextureZ
GL_ARB_occlusion_queryZ
GL_ARB_occlusion_query2Z
GL_ARB_pipeline_statistics_queryZ
GL_ARB_pixel_buffer_objectZ
GL_ARB_point_parametersZ
GL_ARB_point_spriteZ
GL_ARB_program_interface_queryZ
GL_ARB_provoking_vertexZ
GL_ARB_query_buffer_objectZ
GL_ARB_robust_buffer_access_behaviorZ
GL_ARB_robustnessZ
GL_ARB_robustness_isolationZ
GL_ARB_sample_shadingZ
GL_ARB_sampler_objectsZ
GL_ARB_seamless_cube_mapZ
GL_ARB_seamless_cubemap_per_textureZ
GL_ARB_separate_shader_objectsZ
GL_ARB_shader_atomic_countersZ
GL_ARB_shader_bit_encodingZ
GL_ARB_shader_draw_parametersZ
GL_ARB_shader_group_voteZ
GL_ARB_shader_image_load_storeZ
GL_ARB_shader_image_sizeZ
GL_ARB_shader_objectsZ
GL_ARB_shader_precisionZ
GL_ARB_shader_stencil_exportZ
GL_ARB_shader_storage_buffer_objectZ
GL_ARB_shader_subroutineZ
GL_ARB_shader_texture_image_samplesZ
GL_ARB_shader_texture_lodZ
GL_ARB_shading_language_100Z
GL_ARB_shading_language_420packZ
GL_ARB_shading_language_includeZ
GL_ARB_shading_language_packingZ
GL_ARB_shadowZ
GL_ARB_shadow_ambientZ
GL_ARB_sparse_bufferZ
GL_ARB_sparse_textureZ
GL_ARB_stencil_texturingZ
GL_ARB_syncZ
GL_ARB_tessellation_shaderZ
GL_ARB_texture_barrierZ
GL_ARB_texture_border_clampZ
GL_ARB_texture_buffer_objectZ
GL_ARB_texture_buffer_object_rgb32Z
GL_ARB_texture_buffer_rangeZ
GL_ARB_texture_compressionZ
GL_ARB_texture_compression_bptcZ
GL_ARB_texture_compression_rgtcZ
GL_ARB_texture_cube_mapZ
GL_ARB_texture_cube_map_arrayZ
GL_ARB_texture_env_addZ
GL_ARB_texture_env_combineZ
GL_ARB_texture_env_crossbarZ
GL_ARB_texture_env_dot3Z
GL_ARB_texture_floatZ
GL_ARB_texture_gatherZ
GL_ARB_texture_mirror_clamp_to_edgeZ
GL_ARB_texture_mirrored_repeatZ
GL_ARB_texture_multisampleZ
GL_ARB_texture_non_power_of_twoZ
GL_ARB_texture_query_levelsZ
GL_ARB_texture_query_lodZ
GL_ARB_texture_rectangleZ
GL_ARB_texture_rgZ
GL_ARB_texture_rgb10_a2uiZ
GL_ARB_texture_stencil8Z
GL_ARB_texture_storageZ
GL_ARB_texture_storage_multisampleZ
GL_ARB_texture_swizzleZ
GL_ARB_texture_viewZ
GL_ARB_timer_queryZ
GL_ARB_transform_feedback2Z
GL_ARB_transform_feedback3Z
GL_ARB_transform_feedback_instancedZ
GL_ARB_transform_feedback_overflow_queryZ
GL_ARB_transpose_matrixZ
GL_ARB_uniform_buffer_objectZ
GL_ARB_vertex_array_bgraZ
GL_ARB_vertex_array_objectZ
GL_ARB_vertex_attrib_64bitZ
GL_ARB_vertex_attrib_bindingZ
GL_ARB_vertex_blendZ
GL_ARB_vertex_buffer_objectZ
GL_ARB_vertex_programZ
GL_ARB_vertex_shaderZ
GL_ARB_vertex_type_10f_11f_11f_revZ
GL_ARB_vertex_type_2_10_10_10_revZ
GL_ARB_viewport_arrayZ
GL_ARB_window_posZ
GL_ATI_draw_buffersZ
GL_ATI_element_arrayZ
GL_ATI_envmap_bumpmapZ
GL_ATI_fragment_shaderZ
GL_ATI_map_object_bufferZ
GL_ATI_meminfoZ
GL_ATI_pn_trianglesZ
GL_ATI_separate_stencilZ
GL_ATI_shader_texture_lodZ
GL_ATI_text_fragment_shaderZ
GL_ATI_texture_compression_3dcZ
GL_ATI_texture_env_combine3Z
GL_ATI_texture_floatZ
GL_ATI_texture_mirror_onceZ
GL_ATI_vertex_array_objectZ
GL_ATI_vertex_attrib_array_objectZ
GL_ATI_vertex_streamsZ
GL_EXT_Cg_shaderZ
GL_EXT_abgrZ
GL_EXT_bgraZ
GL_EXT_bindable_uniformZ
GL_EXT_blend_colorZ
GL_EXT_blend_equation_separateZ
GL_EXT_blend_func_separateZ
GL_EXT_blend_minmaxZ
GL_EXT_blend_subtractZ
GL_EXT_compiled_vertex_arrayZ
GL_EXT_depth_bounds_testZ
GL_EXT_direct_state_accessZ
GL_EXT_draw_buffers2Z
GL_EXT_draw_instancedZ
GL_EXT_draw_range_elementsZ
GL_EXT_fog_coordZ
GL_EXT_framebuffer_blitZ
GL_EXT_framebuffer_multisampleZ
GL_EXT_framebuffer_multisample_blit_scaledZ
GL_EXT_framebuffer_objectZ
GL_EXT_framebuffer_sRGBZ
GL_EXT_geometry_shader4Z
GL_EXT_gpu_program_parametersZ
GL_EXT_gpu_shader4Z
GL_EXT_multi_draw_arraysZ
GL_EXT_packed_depth_stencilZ
GL_EXT_packed_floatZ
GL_EXT_packed_pixelsZ
GL_EXT_paletted_textureZ
GL_EXT_pixel_buffer_objectZ
GL_EXT_point_parametersZ
GL_EXT_provoking_vertexZ
GL_EXT_rescale_normalZ
GL_EXT_secondary_colorZ
GL_EXT_separate_shader_objectsZ
GL_EXT_separate_specular_colorZ
GL_EXT_shader_image_load_storeZ
GL_EXT_shadow_funcsZ
GL_EXT_shared_texture_paletteZ
GL_EXT_stencil_clear_tagZ
GL_EXT_stencil_two_sideZ
GL_EXT_stencil_wrapZ
GL_EXT_texture_3dZ
GL_EXT_texture_arrayZ
GL_EXT_texture_buffer_objectZ
GL_EXT_texture_compression_latcZ
GL_EXT_texture_compression_rgtcZ
GL_EXT_texture_compression_s3tcZ
GL_EXT_texture_env_combineZ
GL_EXT_texture_env_dot3Z
GL_EXT_texture_filter_anisotropicZ
GL_EXT_texture_integerZ
GL_EXT_texture_lod_biasZ
GL_EXT_texture_mirror_clampZ
GL_EXT_texture_rectangleZ
GL_EXT_texture_sRGBZ
GL_EXT_texture_sRGB_decodeZ
GL_EXT_texture_shared_exponentZ
GL_EXT_texture_snormZ
GL_EXT_texture_swizzleZ
GL_EXT_timer_queryZ
GL_EXT_transform_feedbackZ
GL_EXT_vertex_array_bgraZ
GL_EXT_vertex_attrib_64bitZ
GL_EXT_vertex_shaderZ
GL_EXT_vertex_weightingZ
GL_GREMEDY_frame_terminatorZ
GL_GREMEDY_string_markerZ
GL_HP_occlusion_testZ
GL_IBM_rasterpos_clipZ
GL_INTEL_map_textureZ
GL_KHR_context_flush_controlZ
GL_KHR_debugZ
GL_KHR_robust_buffer_access_behaviorZ
GL_KHR_robustnessZ
GL_KHR_texture_compression_astc_ldrZ
GL_NVX_gpu_memory_infoZ
GL_NV_bindless_multi_draw_indirectZ
GL_NV_bindless_textureZ
GL_NV_blend_equation_advancedZ
GL_NV_blend_squareZ
GL_NV_compute_program5Z
GL_NV_conditional_renderZ
GL_NV_copy_depth_to_colorZ
GL_NV_copy_imageZ
GL_NV_deep_texture3DZ
GL_NV_depth_buffer_floatZ
GL_NV_depth_clampZ
GL_NV_draw_textureZ
GL_NV_evaluatorsZ
GL_NV_explicit_multisampleZ
GL_NV_fenceZ
GL_NV_float_bufferZ
GL_NV_fog_distanceZ
GL_NV_fragment_programZ
GL_NV_fragment_program2Z
GL_NV_fragment_program4Z
GL_NV_fragment_program_optionZ
GL_NV_framebuffer_multisample_coverageZ
GL_NV_geometry_program4Z
GL_NV_geometry_shader4Z
GL_NV_gpu_program4Z
GL_NV_gpu_program5Z
GL_NV_gpu_program5_mem_extendedZ
GL_NV_gpu_shader5Z
GL_NV_half_floatZ
GL_NV_light_max_exponentZ
GL_NV_multisample_coverageZ
GL_NV_multisample_filter_hintZ
GL_NV_occlusion_queryZ
GL_NV_packed_depth_stencilZ
GL_NV_parameter_buffer_objectZ
GL_NV_parameter_buffer_object2Z
GL_NV_path_renderingZ
GL_NV_pixel_data_rangeZ
GL_NV_point_spriteZ
GL_NV_present_videoZ
GL_NV_primitive_restartZ
GL_NV_register_combinersZ
GL_NV_register_combiners2Z
GL_NV_shader_atomic_countersZ
GL_NV_shader_atomic_floatZ
GL_NV_shader_buffer_loadZ
GL_NV_shader_buffer_storeZ
GL_NV_shader_storage_buffer_objectZ
GL_NV_tessellation_program5Z
GL_NV_texgen_reflectionZ
GL_NV_texture_barrierZ
GL_NV_texture_compression_vtcZ
GL_NV_texture_env_combine4Z
GL_NV_texture_expand_normalZ
GL_NV_texture_multisampleZ
GL_NV_texture_rectangleZ
GL_NV_texture_shaderZ
GL_NV_texture_shader2Z
GL_NV_texture_shader3Z
GL_NV_transform_feedbackZ
GL_NV_transform_feedback2Z
GL_NV_vertex_array_rangeZ
GL_NV_vertex_array_range2Z
GL_NV_vertex_attrib_integer_64bitZ
GL_NV_vertex_buffer_unified_memoryZ
GL_NV_vertex_programZ
GL_NV_vertex_program1_1Z
GL_NV_vertex_program2Z
GL_NV_vertex_program2_optionZ
GL_NV_vertex_program3Z
GL_NV_vertex_program4Z
GL_NV_video_captureZ
GL_SGIS_generate_mipmapZ
GL_SGIS_texture_lodZ
GL_SUN_slice_accumZ
GREMEDY_frame_terminator_initNativeFunctionAddresses()Z
GREMEDY_string_marker_initNativeFunctionAddresses()Z
INTEL_map_texture_initNativeFunctionAddresses()Z
KHR_debug_initNativeFunctionAddresses()Z
KHR_robustness_initNativeFunctionAddresses()Z
NV_bindless_multi_draw_indirect_initNativeFunctionAddresses()Z
NV_bindless_texture_initNativeFunctionAddresses()Z
NV_blend_equation_advanced_initNativeFunctionAddresses()Z
NV_conditional_render_initNativeFunctionAddresses()Z
NV_copy_image_initNativeFunctionAddresses()Z
NV_depth_buffer_float_initNativeFunctionAddresses()Z
NV_draw_texture_initNativeFunctionAddresses()Z
NV_evaluators_initNativeFunctionAddresses()Z
NV_explicit_multisample_initNativeFunctionAddresses()Z
NV_fence_initNativeFunctionAddresses()Z
NV_fragment_program_initNativeFunctionAddresses()Z
NV_framebuffer_multisample_coverage_initNativeFunctionAddresses()Z
NV_geometry_program4_initNativeFunctionAddresses()Z
NV_gpu_program4_initNativeFunctionAddresses()Z
NV_gpu_shader5_initNativeFunctionAddresses(Ljava/util/Set;)Z
NV_half_float_initNativeFunctionAddresses(Ljava/util/Set;)Z
NV_occlusion_query_initNativeFunctionAddresses()Z
NV_parameter_buffer_object_initNativeFunctionAddresses()Z
NV_path_rendering_initNativeFunctionAddresses()Z
NV_pixel_data_range_initNativeFunctionAddresses()Z
NV_point_sprite_initNativeFunctionAddresses()Z
NV_present_video_initNativeFunctionAddresses()Z
NV_primitive_restart_initNativeFunctionAddresses()Z
NV_program_initNativeFunctionAddresses()Z
NV_register_combiners2_initNativeFunctionAddresses()Z
NV_register_combiners_initNativeFunctionAddresses()Z
NV_shader_buffer_load_initNativeFunctionAddresses()Z
NV_texture_barrier_initNativeFunctionAddresses()Z
NV_texture_multisample_initNativeFunctionAddresses()Z
NV_transform_feedback2_initNativeFunctionAddresses()Z
NV_transform_feedback_initNativeFunctionAddresses()Z
NV_vertex_array_range_initNativeFunctionAddresses()Z
NV_vertex_attrib_integer_64bit_initNativeFunctionAddresses(Ljava/util/Set;)Z
NV_vertex_buffer_unified_memory_initNativeFunctionAddresses()Z
NV_vertex_program_initNativeFunctionAddresses()Z
NV_video_capture_initNativeFunctionAddresses()Z
OpenGL11Z
OpenGL12Z
OpenGL13Z
OpenGL14Z
OpenGL15Z
OpenGL20Z
OpenGL21Z
OpenGL30Z
OpenGL31Z
OpenGL32Z
OpenGL33Z
OpenGL40Z
OpenGL41Z
OpenGL42Z
OpenGL43Z
OpenGL44Z
OpenGL45Z
glAccumJ
glActiveProgramEXTJ
glActiveShaderProgramJ
glActiveStencilFaceEXTJ
glActiveTextureJ
glActiveTextureARBJ
glActiveVaryingNVJ
glAllocateMemoryNVJ
glAlphaFragmentOp1ATIJ
glAlphaFragmentOp2ATIJ
glAlphaFragmentOp3ATIJ
glAlphaFuncJ
glAreProgramsResidentNVJ
glAreTexturesResidentJ
glArrayElementJ
glArrayObjectATIJ
glAttachObjectARBJ
glAttachShaderJ
glBeginJ
glBeginConditionalRenderJ
glBeginConditionalRenderNVJ
glBeginFragmentShaderATIJ
glBeginOcclusionQueryNVJ
glBeginPerfMonitorAMDJ
glBeginQueryJ
glBeginQueryARBJ
glBeginQueryIndexedJ
glBeginTransformFeedbackJ
glBeginTransformFeedbackEXTJ
glBeginTransformFeedbackNVJ
glBeginVertexShaderEXTJ
glBeginVideoCaptureNVJ
glBindAttribLocationJ
glBindAttribLocationARBJ
glBindBufferJ
glBindBufferARBJ
glBindBufferBaseJ
glBindBufferBaseEXTJ
glBindBufferBaseNVJ
glBindBufferOffsetEXTJ
glBindBufferOffsetNVJ
glBindBufferRangeJ
glBindBufferRangeEXTJ
glBindBufferRangeNVJ
glBindBuffersBaseJ
glBindBuffersRangeJ
glBindFragDataLocationJ
glBindFragDataLocationEXTJ
glBindFragDataLocationIndexedJ
glBindFragmentShaderATIJ
glBindFramebufferJ
glBindFramebufferEXTJ
glBindImageTextureJ
glBindImageTextureEXTJ
glBindImageTexturesJ
glBindLightParameterEXTJ
glBindMaterialParameterEXTJ
glBindMultiTextureEXTJ
glBindParameterEXTJ
glBindProgramARBJ
glBindProgramNVJ
glBindProgramPipelineJ
glBindRenderbufferJ
glBindRenderbufferEXTJ
glBindSamplerJ
glBindSamplersJ
glBindTexGenParameterEXTJ
glBindTextureJ
glBindTextureUnitJ
glBindTextureUnitParameterEXTJ
glBindTexturesJ
glBindTransformFeedbackJ
glBindTransformFeedbackNVJ
glBindVertexArrayJ
glBindVertexArrayAPPLEJ
glBindVertexBufferJ
glBindVertexBuffersJ
glBindVertexShaderEXTJ
glBindVideoCaptureStreamBufferNVJ
glBindVideoCaptureStreamTextureNVJ
glBitmapJ
glBlendBarrierNVJ
glBlendColorJ
glBlendColorEXTJ
glBlendEquationJ
glBlendEquationEXTJ
glBlendEquationIndexedAMDJ
glBlendEquationSeparateJ
glBlendEquationSeparateEXTJ
glBlendEquationSeparateIndexedAMDJ
glBlendEquationSeparateiJ
glBlendEquationSeparateiARBJ
glBlendEquationiJ
glBlendEquationiARBJ
glBlendFuncJ
glBlendFuncIndexedAMDJ
glBlendFuncSeparateJ
glBlendFuncSeparateEXTJ
glBlendFuncSeparateIndexedAMDJ
glBlendFuncSeparateiJ
glBlendFuncSeparateiARBJ
glBlendFunciJ
glBlendFunciARBJ
glBlendParameteriNVJ
glBlitFramebufferJ
glBlitFramebufferEXTJ
glBlitNamedFramebufferJ
glBufferAddressRangeNVJ
glBufferDataJ
glBufferDataARBJ
glBufferPageCommitmentARBJ
glBufferParameteriAPPLEJ
glBufferStorageJ
glBufferSubDataJ
glBufferSubDataARBJ
glCallListJ
glCallListsJ
glCheckFramebufferStatusJ
glCheckFramebufferStatusEXTJ
glCheckNamedFramebufferStatusJ
glCheckNamedFramebufferStatusEXTJ
glClampColorJ
glClampColorARBJ
glClearJ
glClearAccumJ
glClearBufferDataJ
glClearBufferSubDataJ
glClearBufferfiJ
glClearBufferfvJ
glClearBufferivJ
glClearBufferuivJ
glClearColorJ
glClearColorIiEXTJ
glClearColorIuiEXTJ
glClearDepthJ
glClearDepthdNVJ
glClearDepthfJ
glClearNamedBufferDataJ
glClearNamedBufferDataEXTJ
glClearNamedBufferSubDataJ
glClearNamedBufferSubDataEXTJ
glClearNamedFramebufferfiJ
glClearNamedFramebufferfvJ
glClearNamedFramebufferivJ
glClearNamedFramebufferuivJ
glClearStencilJ
glClearTexImageJ
glClearTexSubImageJ
glClientActiveTextureJ
glClientActiveTextureARBJ
glClientActiveVertexStreamATIJ
glClientAttribDefaultEXTJ
glClientWaitSyncJ
glClipControlJ
glClipPlaneJ
glColor3bJ
glColor3dJ
glColor3fJ
glColor3hNVJ
glColor3ubJ
glColor4bJ
glColor4dJ
glColor4fJ
glColor4hNVJ
glColor4ubJ
glColorFormatNVJ
glColorFragmentOp1ATIJ
glColorFragmentOp2ATIJ
glColorFragmentOp3ATIJ
glColorMaskJ
glColorMaskIndexedEXTJ
glColorMaskiJ
glColorMaterialJ
glColorP3uiJ
glColorP3uivJ
glColorP4uiJ
glColorP4uivJ
glColorPointerJ
glColorSubTableJ
glColorSubTableEXTJ
glColorTableJ
glColorTableEXTJ
glColorTableParameterfvJ
glColorTableParameterivJ
glCombinerInputNVJ
glCombinerOutputNVJ
glCombinerParameterfNVJ
glCombinerParameterfvNVJ
glCombinerParameteriNVJ
glCombinerParameterivNVJ
glCombinerStageParameterfvNVJ
glCompileShaderJ
glCompileShaderARBJ
glCompileShaderIncludeARBJ
glCompressedMultiTexImage1DEXTJ
glCompressedMultiTexImage2DEXTJ
glCompressedMultiTexImage3DEXTJ
glCompressedMultiTexSubImage1DEXTJ
glCompressedMultiTexSubImage2DEXTJ
glCompressedMultiTexSubImage3DEXTJ
glCompressedTexImage1DJ
glCompressedTexImage1DARBJ
glCompressedTexImage2DJ
glCompressedTexImage2DARBJ
glCompressedTexImage3DJ
glCompressedTexImage3DARBJ
glCompressedTexSubImage1DJ
glCompressedTexSubImage1DARBJ
glCompressedTexSubImage2DJ
glCompressedTexSubImage2DARBJ
glCompressedTexSubImage3DJ
glCompressedTexSubImage3DARBJ
glCompressedTextureImage1DEXTJ
glCompressedTextureImage2DEXTJ
glCompressedTextureImage3DEXTJ
glCompressedTextureSubImage1DJ
glCompressedTextureSubImage1DEXTJ
glCompressedTextureSubImage2DJ
glCompressedTextureSubImage2DEXTJ
glCompressedTextureSubImage3DJ
glCompressedTextureSubImage3DEXTJ
glConvolutionFilter1DJ
glConvolutionFilter2DJ
glConvolutionParameterfJ
glConvolutionParameterfvJ
glConvolutionParameteriJ
glConvolutionParameterivJ
glCopyBufferSubDataJ
glCopyColorSubTableJ
glCopyColorTableJ
glCopyConvolutionFilter1DJ
glCopyConvolutionFilter2DJ
glCopyImageSubDataJ
glCopyImageSubDataNVJ
glCopyMultiTexImage1DEXTJ
glCopyMultiTexImage2DEXTJ
glCopyMultiTexSubImage1DEXTJ
glCopyMultiTexSubImage2DEXTJ
glCopyMultiTexSubImage3DEXTJ
glCopyNamedBufferSubDataJ
glCopyPathNVJ
glCopyPixelsJ
glCopyTexImage1DJ
glCopyTexImage2DJ
glCopyTexSubImage1DJ
glCopyTexSubImage2DJ
glCopyTexSubImage3DJ
glCopyTextureImage1DEXTJ
glCopyTextureImage2DEXTJ
glCopyTextureSubImage1DJ
glCopyTextureSubImage1DEXTJ
glCopyTextureSubImage2DJ
glCopyTextureSubImage2DEXTJ
glCopyTextureSubImage3DJ
glCopyTextureSubImage3DEXTJ
glCoverFillPathInstancedNVJ
glCoverFillPathNVJ
glCoverStrokePathInstancedNVJ
glCoverStrokePathNVJ
glCreateBuffersJ
glCreateFramebuffersJ
glCreateProgramJ
glCreateProgramObjectARBJ
glCreateProgramPipelinesJ
glCreateQueriesJ
glCreateRenderbuffersJ
glCreateSamplersJ
glCreateShaderJ
glCreateShaderObjectARBJ
glCreateShaderProgramEXTJ
glCreateShaderProgramvJ
glCreateSyncFromCLeventARBJ
glCreateTexturesJ
glCreateTransformFeedbacksJ
glCreateVertexArraysJ
glCullFaceJ
glCurrentPaletteMatrixARBJ
glDebugMessageCallbackJ
glDebugMessageCallbackAMDJ
glDebugMessageCallbackARBJ
glDebugMessageControlJ
glDebugMessageControlARBJ
glDebugMessageEnableAMDJ
glDebugMessageInsertJ
glDebugMessageInsertAMDJ
glDebugMessageInsertARBJ
glDeleteBuffersJ
glDeleteBuffersARBJ
glDeleteFencesAPPLEJ
glDeleteFencesNVJ
glDeleteFragmentShaderATIJ
glDeleteFramebuffersJ
glDeleteFramebuffersEXTJ
glDeleteListsJ
glDeleteNamedStringARBJ
glDeleteNamesAMDJ
glDeleteObjectARBJ
glDeleteOcclusionQueriesNVJ
glDeletePathsNVJ
glDeletePerfMonitorsAMDJ
glDeleteProgramJ
glDeleteProgramPipelinesJ
glDeleteProgramsARBJ
glDeleteProgramsNVJ
glDeleteQueriesJ
glDeleteQueriesARBJ
glDeleteRenderbuffersJ
glDeleteRenderbuffersEXTJ
glDeleteSamplersJ
glDeleteShaderJ
glDeleteSyncJ
glDeleteTexturesJ
glDeleteTransformFeedbacksJ
glDeleteTransformFeedbacksNVJ
glDeleteVertexArraysJ
glDeleteVertexArraysAPPLEJ
glDeleteVertexShaderEXTJ
glDepthBoundsEXTJ
glDepthBoundsdNVJ
glDepthFuncJ
glDepthMaskJ
glDepthRangeJ
glDepthRangeArrayvJ
glDepthRangeIndexedJ
glDepthRangedNVJ
glDepthRangefJ
glDetachObjectARBJ
glDetachShaderJ
glDisableJ
glDisableClientStateJ
glDisableClientStateIndexedEXTJ
glDisableClientStateiEXTJ
glDisableIndexedEXTJ
glDisableVariantClientStateEXTJ
glDisableVertexArrayAttribJ
glDisableVertexArrayAttribEXTJ
glDisableVertexArrayEXTJ
glDisableVertexAttribAPPLEJ
glDisableVertexAttribArrayJ
glDisableVertexAttribArrayARBJ
glDisableiJ
glDispatchComputeJ
glDispatchComputeGroupSizeARBJ
glDispatchComputeIndirectJ
glDrawArraysJ
glDrawArraysIndirectJ
glDrawArraysInstancedJ
glDrawArraysInstancedARBJ
glDrawArraysInstancedBaseInstanceJ
glDrawArraysInstancedEXTJ
glDrawBufferJ
glDrawBuffersJ
glDrawBuffersARBJ
glDrawBuffersATIJ
glDrawElementArrayAPPLEJ
glDrawElementArrayATIJ
glDrawElementsJ
glDrawElementsBaseVertexJ
glDrawElementsIndirectJ
glDrawElementsInstancedJ
glDrawElementsInstancedARBJ
glDrawElementsInstancedBaseInstanceJ
glDrawElementsInstancedBaseVertexJ
glDrawElementsInstancedBaseVertexBaseInstanceJ
glDrawElementsInstancedEXTJ
glDrawPixelsJ
glDrawRangeElementArrayAPPLEJ
glDrawRangeElementArrayATIJ
glDrawRangeElementsJ
glDrawRangeElementsBaseVertexJ
glDrawRangeElementsEXTJ
glDrawTextureNVJ
glDrawTransformFeedbackJ
glDrawTransformFeedbackInstancedJ
glDrawTransformFeedbackNVJ
glDrawTransformFeedbackStreamJ
glDrawTransformFeedbackStreamInstancedJ
glEdgeFlagJ
glEdgeFlagFormatNVJ
glEdgeFlagPointerJ
glElementPointerAPPLEJ
glElementPointerATIJ
glEnableJ
glEnableClientStateJ
glEnableClientStateIndexedEXTJ
glEnableClientStateiEXTJ
glEnableIndexedEXTJ
glEnableVariantClientStateEXTJ
glEnableVertexArrayAttribJ
glEnableVertexArrayAttribEXTJ
glEnableVertexArrayEXTJ
glEnableVertexAttribAPPLEJ
glEnableVertexAttribArrayJ
glEnableVertexAttribArrayARBJ
glEnableiJ
glEndJ
glEndConditionalRenderJ
glEndConditionalRenderNVJ
glEndFragmentShaderATIJ
glEndListJ
glEndOcclusionQueryNVJ
glEndPerfMonitorAMDJ
glEndQueryJ
glEndQueryARBJ
glEndQueryIndexedJ
glEndTransformFeedbackJ
glEndTransformFeedbackEXTJ
glEndTransformFeedbackNVJ
glEndVertexShaderEXTJ
glEndVideoCaptureNVJ
glEvalCoord1dJ
glEvalCoord1fJ
glEvalCoord2dJ
glEvalCoord2fJ
glEvalMapsNVJ
glEvalMesh1J
glEvalMesh2J
glEvalPoint1J
glEvalPoint2J
glExecuteProgramNVJ
glExtractComponentEXTJ
glFeedbackBufferJ
glFenceSyncJ
glFinalCombinerInputNVJ
glFinishJ
glFinishFenceAPPLEJ
glFinishFenceNVJ
glFinishObjectAPPLEJ
glFlushJ
glFlushMappedBufferRangeJ
glFlushMappedBufferRangeAPPLEJ
glFlushMappedNamedBufferRangeJ
glFlushMappedNamedBufferRangeEXTJ
glFlushPixelDataRangeNVJ
glFlushVertexArrayRangeAPPLEJ
glFlushVertexArrayRangeNVJ
glFogCoordFormatNVJ
glFogCoordPointerJ
glFogCoordPointerEXTJ
glFogCoorddJ
glFogCoorddEXTJ
glFogCoordfJ
glFogCoordfEXTJ
glFogCoordhNVJ
glFogfJ
glFogfvJ
glFogiJ
glFogivJ
glFrameTerminatorGREMEDYJ
glFramebufferDrawBufferEXTJ
glFramebufferDrawBuffersEXTJ
glFramebufferParameteriJ
glFramebufferReadBufferEXTJ
glFramebufferRenderbufferJ
glFramebufferRenderbufferEXTJ
glFramebufferTextureJ
glFramebufferTexture1DJ
glFramebufferTexture1DEXTJ
glFramebufferTexture2DJ
glFramebufferTexture2DEXTJ
glFramebufferTexture3DJ
glFramebufferTexture3DEXTJ
glFramebufferTextureARBJ
glFramebufferTextureEXTJ
glFramebufferTextureFaceARBJ
glFramebufferTextureFaceEXTJ
glFramebufferTextureLayerJ
glFramebufferTextureLayerARBJ
glFramebufferTextureLayerEXTJ
glFreeMemoryNVJ
glFreeObjectBufferATIJ
glFrontFaceJ
glFrustumJ
glGenBuffersJ
glGenBuffersARBJ
glGenFencesAPPLEJ
glGenFencesNVJ
glGenFragmentShadersATIJ
glGenFramebuffersJ
glGenFramebuffersEXTJ
glGenListsJ
glGenNamesAMDJ
glGenOcclusionQueriesNVJ
glGenPathsNVJ
glGenPerfMonitorsAMDJ
glGenProgramPipelinesJ
glGenProgramsARBJ
glGenProgramsNVJ
glGenQueriesJ
glGenQueriesARBJ
glGenRenderbuffersJ
glGenRenderbuffersEXTJ
glGenSamplersJ
glGenSymbolsEXTJ
glGenTexturesJ
glGenTransformFeedbacksJ
glGenTransformFeedbacksNVJ
glGenVertexArraysJ
glGenVertexArraysAPPLEJ
glGenVertexShadersEXTJ
glGenerateMipmapJ
glGenerateMipmapEXTJ
glGenerateMultiTexMipmapEXTJ
glGenerateTextureMipmapJ
glGenerateTextureMipmapEXTJ
glGetActiveAtomicCounterBufferivJ
glGetActiveAttribJ
glGetActiveAttribARBJ
glGetActiveSubroutineNameJ
glGetActiveSubroutineUniformNameJ
glGetActiveSubroutineUniformivJ
glGetActiveUniformJ
glGetActiveUniformARBJ
glGetActiveUniformBlockNameJ
glGetActiveUniformBlockivJ
glGetActiveUniformNameJ
glGetActiveUniformsivJ
glGetActiveVaryingNVJ
glGetArrayObjectfvATIJ
glGetArrayObjectivATIJ
glGetAttachedObjectsARBJ
glGetAttachedShadersJ
glGetAttribLocationJ
glGetAttribLocationARBJ
glGetBooleanIndexedvEXTJ
glGetBooleani_vJ
glGetBooleanvJ
glGetBufferParameteri64vJ
glGetBufferParameterivJ
glGetBufferParameterivARBJ
glGetBufferParameterui64vNVJ
glGetBufferPointervJ
glGetBufferPointervARBJ
glGetBufferSubDataJ
glGetBufferSubDataARBJ
glGetClipPlaneJ
glGetColorTableJ
glGetColorTableEXTJ
glGetColorTableParameterfvJ
glGetColorTableParameterfvEXTJ
glGetColorTableParameterivJ
glGetColorTableParameterivEXTJ
glGetCombinerInputParameterfvNVJ
glGetCombinerInputParameterivNVJ
glGetCombinerOutputParameterfvNVJ
glGetCombinerOutputParameterivNVJ
glGetCombinerStageParameterfvNVJ
glGetCompressedMultiTexImageEXTJ
glGetCompressedTexImageJ
glGetCompressedTexImageARBJ
glGetCompressedTextureImageJ
glGetCompressedTextureImageEXTJ
glGetCompressedTextureSubImageJ
glGetConvolutionFilterJ
glGetConvolutionParameterfvJ
glGetConvolutionParameterivJ
glGetDebugMessageLogJ
glGetDebugMessageLogAMDJ
glGetDebugMessageLogARBJ
glGetDoubleIndexedvEXTJ
glGetDoublei_vJ
glGetDoublei_vEXTJ
glGetDoublevJ
glGetErrorJ
glGetFenceivNVJ
glGetFinalCombinerInputParameterfvNVJ
glGetFinalCombinerInputParameterivNVJ
glGetFloatIndexedvEXTJ
glGetFloati_vJ
glGetFloati_vEXTJ
glGetFloatvJ
glGetFragDataIndexJ
glGetFragDataLocationJ
glGetFragDataLocationEXTJ
glGetFramebufferAttachmentParameterivJ
glGetFramebufferAttachmentParameterivEXTJ
glGetFramebufferParameterivJ
glGetFramebufferParameterivEXTJ
glGetGraphicsResetStatusJ
glGetGraphicsResetStatusARBJ
glGetHandleARBJ
glGetHistogramJ
glGetHistogramParameterfvJ
glGetHistogramParameterivJ
glGetImageHandleARBJ
glGetImageHandleNVJ
glGetInfoLogARBJ
glGetInteger64i_vJ
glGetInteger64vJ
glGetIntegerIndexedvEXTJ
glGetIntegeri_vJ
glGetIntegerui64i_vNVJ
glGetIntegerui64vNVJ
glGetIntegervJ
glGetInternalformati64vJ
glGetInternalformativJ
glGetInvariantBooleanvEXTJ
glGetInvariantFloatvEXTJ
glGetInvariantIntegervEXTJ
glGetLightfvJ
glGetLightivJ
glGetLocalConstantBooleanvEXTJ
glGetLocalConstantFloatvEXTJ
glGetLocalConstantIntegervEXTJ
glGetMapAttribParameterfvNVJ
glGetMapAttribParameterivNVJ
glGetMapControlPointsNVJ
glGetMapParameterfvNVJ
glGetMapParameterivNVJ
glGetMapdvJ
glGetMapfvJ
glGetMapivJ
glGetMaterialfvJ
glGetMaterialivJ
glGetMinmaxJ
glGetMinmaxParameterfvJ
glGetMinmaxParameterivJ
glGetMultiTexEnvfvEXTJ
glGetMultiTexEnvivEXTJ
glGetMultiTexGendvEXTJ
glGetMultiTexGenfvEXTJ
glGetMultiTexGenivEXTJ
glGetMultiTexImageEXTJ
glGetMultiTexLevelParameterfvEXTJ
glGetMultiTexLevelParameterivEXTJ
glGetMultiTexParameterIivEXTJ
glGetMultiTexParameterIuivEXTJ
glGetMultiTexParameterfvEXTJ
glGetMultiTexParameterivEXTJ
glGetMultisamplefvJ
glGetMultisamplefvNVJ
glGetNamedBufferParameteri64vJ
glGetNamedBufferParameterivJ
glGetNamedBufferParameterivEXTJ
glGetNamedBufferParameterui64vNVJ
glGetNamedBufferPointervJ
glGetNamedBufferPointervEXTJ
glGetNamedBufferSubDataJ
glGetNamedBufferSubDataEXTJ
glGetNamedFramebufferAttachmentParameterivJ
glGetNamedFramebufferAttachmentParameterivEXTJ
glGetNamedFramebufferParameterivJ
glGetNamedFramebufferParameterivEXTJ
glGetNamedProgramLocalParameterIivEXTJ
glGetNamedProgramLocalParameterIuivEXTJ
glGetNamedProgramLocalParameterdvEXTJ
glGetNamedProgramLocalParameterfvEXTJ
glGetNamedProgramStringEXTJ
glGetNamedProgramivEXTJ
glGetNamedRenderbufferParameterivJ
glGetNamedRenderbufferParameterivEXTJ
glGetNamedStringARBJ
glGetNamedStringivARBJ
glGetObjectBufferfvATIJ
glGetObjectBufferivATIJ
glGetObjectLabelJ
glGetObjectParameterfvARBJ
glGetObjectParameterivAPPLEJ
glGetObjectParameterivARBJ
glGetObjectPtrLabelJ
glGetOcclusionQueryivNVJ
glGetOcclusionQueryuivNVJ
glGetPathColorGenfvNVJ
glGetPathColorGenivNVJ
glGetPathCommandsNVJ
glGetPathCoordsNVJ
glGetPathDashArrayNVJ
glGetPathLengthNVJ
glGetPathMetricRangeNVJ
glGetPathMetricsNVJ
glGetPathParameterfvNVJ
glGetPathParameterivNVJ
glGetPathSpacingNVJ
glGetPathTexGenfvNVJ
glGetPathTexGenivNVJ
glGetPerfMonitorCounterDataAMDJ
glGetPerfMonitorCounterInfoAMDJ
glGetPerfMonitorCounterStringAMDJ
glGetPerfMonitorCountersAMDJ
glGetPerfMonitorGroupStringAMDJ
glGetPerfMonitorGroupsAMDJ
glGetPixelMapfvJ
glGetPixelMapuivJ
glGetPixelMapusvJ
glGetPointerIndexedvEXTJ
glGetPointeri_vEXTJ
glGetPointervJ
glGetPolygonStippleJ
glGetProgramBinaryJ
glGetProgramEnvParameterIivNVJ
glGetProgramEnvParameterIuivNVJ
glGetProgramEnvParameterdvARBJ
glGetProgramEnvParameterfvARBJ
glGetProgramInfoLogJ
glGetProgramInterfaceivJ
glGetProgramLocalParameterIivNVJ
glGetProgramLocalParameterIuivNVJ
glGetProgramLocalParameterdvARBJ
glGetProgramLocalParameterfvARBJ
glGetProgramNamedParameterdvNVJ
glGetProgramNamedParameterfvNVJ
glGetProgramParameterdvNVJ
glGetProgramParameterfvNVJ
glGetProgramPipelineInfoLogJ
glGetProgramPipelineivJ
glGetProgramResourceIndexJ
glGetProgramResourceLocationJ
glGetProgramResourceLocationIndexJ
glGetProgramResourceNameJ
glGetProgramResourceivJ
glGetProgramStageivJ
glGetProgramStringARBJ
glGetProgramStringNVJ
glGetProgramivJ
glGetProgramivARBJ
glGetProgramivNVJ
glGetQueryIndexedivJ
glGetQueryObjecti64vJ
glGetQueryObjecti64vEXTJ
glGetQueryObjectivJ
glGetQueryObjectivARBJ
glGetQueryObjectui64vJ
glGetQueryObjectui64vEXTJ
glGetQueryObjectuivJ
glGetQueryObjectuivARBJ
glGetQueryivJ
glGetQueryivARBJ
glGetRenderbufferParameterivJ
glGetRenderbufferParameterivEXTJ
glGetSamplerParameterIivJ
glGetSamplerParameterIuivJ
glGetSamplerParameterfvJ
glGetSamplerParameterivJ
glGetSeparableFilterJ
glGetShaderInfoLogJ
glGetShaderPrecisionFormatJ
glGetShaderSourceJ
glGetShaderSourceARBJ
glGetShaderivJ
glGetStringJ
glGetStringiJ
glGetSubroutineIndexJ
glGetSubroutineUniformLocationJ
glGetSyncivJ
glGetTexBumpParameterfvATIJ
glGetTexBumpParameterivATIJ
glGetTexEnvfvJ
glGetTexEnvivJ
glGetTexGendvJ
glGetTexGenfvJ
glGetTexGenivJ
glGetTexImageJ
glGetTexLevelParameterfvJ
glGetTexLevelParameterivJ
glGetTexParameterIivJ
glGetTexParameterIivEXTJ
glGetTexParameterIuivJ
glGetTexParameterIuivEXTJ
glGetTexParameterPointervAPPLEJ
glGetTexParameterfvJ
glGetTexParameterivJ
glGetTextureHandleARBJ
glGetTextureHandleNVJ
glGetTextureImageJ
glGetTextureImageEXTJ
glGetTextureLevelParameterfvJ
glGetTextureLevelParameterfvEXTJ
glGetTextureLevelParameterivJ
glGetTextureLevelParameterivEXTJ
glGetTextureParameterIivJ
glGetTextureParameterIivEXTJ
glGetTextureParameterIuivJ
glGetTextureParameterIuivEXTJ
glGetTextureParameterfvJ
glGetTextureParameterfvEXTJ
glGetTextureParameterivJ
glGetTextureParameterivEXTJ
glGetTextureSamplerHandleARBJ
glGetTextureSamplerHandleNVJ
glGetTextureSubImageJ
glGetTrackMatrixivNVJ
glGetTransformFeedbackVaryingJ
glGetTransformFeedbackVaryingEXTJ
glGetTransformFeedbackVaryingNVJ
glGetTransformFeedbacki64_vJ
glGetTransformFeedbacki_vJ
glGetTransformFeedbackivJ
glGetUniformBlockIndexJ
glGetUniformBufferSizeEXTJ
glGetUniformIndicesJ
glGetUniformLocationJ
glGetUniformLocationARBJ
glGetUniformOffsetEXTJ
glGetUniformSubroutineuivJ
glGetUniformdvJ
glGetUniformfvJ
glGetUniformfvARBJ
glGetUniformi64vNVJ
glGetUniformivJ
glGetUniformivARBJ
glGetUniformui64vNVJ
glGetUniformuivJ
glGetUniformuivEXTJ
glGetVariantArrayObjectfvATIJ
glGetVariantArrayObjectivATIJ
glGetVariantBooleanvEXTJ
glGetVariantFloatvEXTJ
glGetVariantIntegervEXTJ
glGetVariantPointervEXTJ
glGetVaryingLocationNVJ
glGetVertexArrayIndexed64ivJ
glGetVertexArrayIndexedivJ
glGetVertexArrayIntegeri_vEXTJ
glGetVertexArrayIntegervEXTJ
glGetVertexArrayPointeri_vEXTJ
glGetVertexArrayPointervEXTJ
glGetVertexArrayivJ
glGetVertexAttribArrayObjectfvATIJ
glGetVertexAttribArrayObjectivATIJ
glGetVertexAttribIivJ
glGetVertexAttribIivEXTJ
glGetVertexAttribIuivJ
glGetVertexAttribIuivEXTJ
glGetVertexAttribLdvJ
glGetVertexAttribLdvEXTJ
glGetVertexAttribLi64vNVJ
glGetVertexAttribLui64vARBJ
glGetVertexAttribLui64vNVJ
glGetVertexAttribPointervJ
glGetVertexAttribPointervARBJ
glGetVertexAttribPointervNVJ
glGetVertexAttribdvJ
glGetVertexAttribdvARBJ
glGetVertexAttribdvNVJ
glGetVertexAttribfvJ
glGetVertexAttribfvARBJ
glGetVertexAttribfvNVJ
glGetVertexAttribivJ
glGetVertexAttribivARBJ
glGetVertexAttribivNVJ
glGetVideoCaptureStreamdvNVJ
glGetVideoCaptureStreamfvNVJ
glGetVideoCaptureStreamivNVJ
glGetVideoCaptureivNVJ
glGetVideoi64vNVJ
glGetVideoivNVJ
glGetVideoui64vNVJ
glGetVideouivNVJ
glGetnColorTableARBJ
glGetnCompressedTexImageARBJ
glGetnConvolutionFilterARBJ
glGetnHistogramARBJ
glGetnMapdvARBJ
glGetnMapfvARBJ
glGetnMapivARBJ
glGetnMinmaxARBJ
glGetnPixelMapfvARBJ
glGetnPixelMapuivARBJ
glGetnPixelMapusvARBJ
glGetnPolygonStippleARBJ
glGetnSeparableFilterARBJ
glGetnTexImageARBJ
glGetnUniformdvARBJ
glGetnUniformfvJ
glGetnUniformfvARBJ
glGetnUniformivJ
glGetnUniformivARBJ
glGetnUniformuivJ
glGetnUniformuivARBJ
glHintJ
glHistogramJ
glIndexFormatNVJ
glInitNamesJ
glInsertComponentEXTJ
glInterleavedArraysJ
glInterpolatePathsNVJ
glInvalidateBufferDataJ
glInvalidateBufferSubDataJ
glInvalidateFramebufferJ
glInvalidateNamedFramebufferDataJ
glInvalidateNamedFramebufferSubDataJ
glInvalidateSubFramebufferJ
glInvalidateTexImageJ
glInvalidateTexSubImageJ
glIsBufferJ
glIsBufferARBJ
glIsBufferResidentNVJ
glIsEnabledJ
glIsEnabledIndexedEXTJ
glIsEnablediJ
glIsFenceAPPLEJ
glIsFenceNVJ
glIsFramebufferJ
glIsFramebufferEXTJ
glIsImageHandleResidentARBJ
glIsImageHandleResidentNVJ
glIsListJ
glIsNameAMDJ
glIsNamedBufferResidentNVJ
glIsNamedStringARBJ
glIsObjectBufferATIJ
glIsOcclusionQueryNVJ
glIsPathNVJ
glIsPointInFillPathNVJ
glIsPointInStrokePathNVJ
glIsProgramJ
glIsProgramARBJ
glIsProgramNVJ
glIsProgramPipelineJ
glIsQueryJ
glIsQueryARBJ
glIsRenderbufferJ
glIsRenderbufferEXTJ
glIsSamplerJ
glIsShaderJ
glIsSyncJ
glIsTextureJ
glIsTextureHandleResidentARBJ
glIsTextureHandleResidentNVJ
glIsTransformFeedbackJ
glIsTransformFeedbackNVJ
glIsVariantEnabledEXTJ
glIsVertexArrayJ
glIsVertexArrayAPPLEJ
glIsVertexAttribEnabledAPPLEJ
glLightModelfJ
glLightModelfvJ
glLightModeliJ
glLightModelivJ
glLightfJ
glLightfvJ
glLightiJ
glLightivJ
glLineStippleJ
glLineWidthJ
glLinkProgramJ
glLinkProgramARBJ
glListBaseJ
glLoadIdentityJ
glLoadMatrixdJ
glLoadMatrixfJ
glLoadNameJ
glLoadProgramNVJ
glLoadTransposeMatrixdJ
glLoadTransposeMatrixfJ
glLoadTransposeMatrixfARBJ
glLockArraysEXTJ
glLogicOpJ
glMakeBufferNonResidentNVJ
glMakeBufferResidentNVJ
glMakeImageHandleNonResidentARBJ
glMakeImageHandleNonResidentNVJ
glMakeImageHandleResidentARBJ
glMakeImageHandleResidentNVJ
glMakeNamedBufferNonResidentNVJ
glMakeNamedBufferResidentNVJ
glMakeTextureHandleNonResidentARBJ
glMakeTextureHandleNonResidentNVJ
glMakeTextureHandleResidentARBJ
glMakeTextureHandleResidentNVJ
glMap1dJ
glMap1fJ
glMap2dJ
glMap2fJ
glMapBufferJ
glMapBufferARBJ
glMapBufferRangeJ
glMapControlPointsNVJ
glMapGrid1dJ
glMapGrid1fJ
glMapGrid2dJ
glMapGrid2fJ
glMapNamedBufferJ
glMapNamedBufferEXTJ
glMapNamedBufferRangeJ
glMapNamedBufferRangeEXTJ
glMapObjectBufferATIJ
glMapParameterfvNVJ
glMapParameterivNVJ
glMapTexture2DINTELJ
glMapVertexAttrib1dAPPLEJ
glMapVertexAttrib1fAPPLEJ
glMapVertexAttrib2dAPPLEJ
glMapVertexAttrib2fAPPLEJ
glMaterialfJ
glMaterialfvJ
glMaterialiJ
glMaterialivJ
glMatrixFrustumEXTJ
glMatrixIndexPointerARBJ
glMatrixIndexubvARBJ
glMatrixIndexuivARBJ
glMatrixIndexusvARBJ
glMatrixLoadIdentityEXTJ
glMatrixLoadTransposedEXTJ
glMatrixLoadTransposefEXTJ
glMatrixLoaddEXTJ
glMatrixLoadfEXTJ
glMatrixModeJ
glMatrixMultTransposedEXTJ
glMatrixMultTransposefEXTJ
glMatrixMultdEXTJ
glMatrixMultfEXTJ
glMatrixOrthoEXTJ
glMatrixPopEXTJ
glMatrixPushEXTJ
glMatrixRotatedEXTJ
glMatrixRotatefEXTJ
glMatrixScaledEXTJ
glMatrixScalefEXTJ
glMatrixTranslatedEXTJ
glMatrixTranslatefEXTJ
glMemoryBarrierJ
glMemoryBarrierByRegionJ
glMemoryBarrierEXTJ
glMinSampleShadingJ
glMinSampleShadingARBJ
glMinmaxJ
glMultMatrixdJ
glMultMatrixfJ
glMultTransposeMatrixdJ
glMultTransposeMatrixfJ
glMultTransposeMatrixfARBJ
glMultiDrawArraysJ
glMultiDrawArraysEXTJ
glMultiDrawArraysIndirectJ
glMultiDrawArraysIndirectAMDJ
glMultiDrawArraysIndirectBindlessNVJ
glMultiDrawArraysIndirectCountARBJ
glMultiDrawElementArrayAPPLEJ
glMultiDrawElementsIndirectJ
glMultiDrawElementsIndirectAMDJ
glMultiDrawElementsIndirectBindlessNVJ
glMultiDrawElementsIndirectCountARBJ
glMultiDrawRangeElementArrayAPPLEJ
glMultiTexBufferEXTJ
glMultiTexCoord1dJ
glMultiTexCoord1dARBJ
glMultiTexCoord1fJ
glMultiTexCoord1fARBJ
glMultiTexCoord1hNVJ
glMultiTexCoord1iARBJ
glMultiTexCoord1sARBJ
glMultiTexCoord2dJ
glMultiTexCoord2dARBJ
glMultiTexCoord2fJ
glMultiTexCoord2fARBJ
glMultiTexCoord2hNVJ
glMultiTexCoord2iARBJ
glMultiTexCoord2sARBJ
glMultiTexCoord3dJ
glMultiTexCoord3dARBJ
glMultiTexCoord3fJ
glMultiTexCoord3fARBJ
glMultiTexCoord3hNVJ
glMultiTexCoord3iARBJ
glMultiTexCoord3sARBJ
glMultiTexCoord4dJ
glMultiTexCoord4dARBJ
glMultiTexCoord4fJ
glMultiTexCoord4fARBJ
glMultiTexCoord4hNVJ
glMultiTexCoord4iARBJ
glMultiTexCoord4sARBJ
glMultiTexCoordP1uiJ
glMultiTexCoordP1uivJ
glMultiTexCoordP2uiJ
glMultiTexCoordP2uivJ
glMultiTexCoordP3uiJ
glMultiTexCoordP3uivJ
glMultiTexCoordP4uiJ
glMultiTexCoordP4uivJ
glMultiTexCoordPointerEXTJ
glMultiTexEnvfEXTJ
glMultiTexEnvfvEXTJ
glMultiTexEnviEXTJ
glMultiTexEnvivEXTJ
glMultiTexGendEXTJ
glMultiTexGendvEXTJ
glMultiTexGenfEXTJ
glMultiTexGenfvEXTJ
glMultiTexGeniEXTJ
glMultiTexGenivEXTJ
glMultiTexImage1DEXTJ
glMultiTexImage2DEXTJ
glMultiTexImage3DEXTJ
glMultiTexParameterIivEXTJ
glMultiTexParameterIuivEXTJ
glMultiTexParameterfEXTJ
glMultiTexParameterfvEXTJ
glMultiTexParameteriEXTJ
glMultiTexParameterivEXTJ
glMultiTexRenderbufferEXTJ
glMultiTexSubImage1DEXTJ
glMultiTexSubImage2DEXTJ
glMultiTexSubImage3DEXTJ
glNamedBufferDataJ
glNamedBufferDataEXTJ
glNamedBufferStorageJ
glNamedBufferStorageEXTJ
glNamedBufferSubDataJ
glNamedBufferSubDataEXTJ
glNamedCopyBufferSubDataEXTJ
glNamedFramebufferDrawBufferJ
glNamedFramebufferDrawBuffersJ
glNamedFramebufferParameteriJ
glNamedFramebufferParameteriEXTJ
glNamedFramebufferReadBufferJ
glNamedFramebufferRenderbufferJ
glNamedFramebufferRenderbufferEXTJ
glNamedFramebufferTextureJ
glNamedFramebufferTexture1DEXTJ
glNamedFramebufferTexture2DEXTJ
glNamedFramebufferTexture3DEXTJ
glNamedFramebufferTextureEXTJ
glNamedFramebufferTextureFaceEXTJ
glNamedFramebufferTextureLayerJ
glNamedFramebufferTextureLayerEXTJ
glNamedProgramLocalParameter4dEXTJ
glNamedProgramLocalParameter4dvEXTJ
glNamedProgramLocalParameter4fEXTJ
glNamedProgramLocalParameter4fvEXTJ
glNamedProgramLocalParameterI4iEXTJ
glNamedProgramLocalParameterI4ivEXTJ
glNamedProgramLocalParameterI4uiEXTJ
glNamedProgramLocalParameterI4uivEXTJ
glNamedProgramLocalParameters4fvEXTJ
glNamedProgramLocalParametersI4ivEXTJ
glNamedProgramLocalParametersI4uivEXTJ
glNamedProgramStringEXTJ
glNamedRenderbufferStorageJ
glNamedRenderbufferStorageEXTJ
glNamedRenderbufferStorageMultisampleJ
glNamedRenderbufferStorageMultisampleCoverageEXTJ
glNamedRenderbufferStorageMultisampleEXTJ
glNamedStringARBJ
glNewListJ
glNewObjectBufferATIJ
glNormal3bJ
glNormal3dJ
glNormal3fJ
glNormal3hNVJ
glNormal3iJ
glNormalFormatNVJ
glNormalP3uiJ
glNormalP3uivJ
glNormalPointerJ
glNormalStream3bATIJ
glNormalStream3dATIJ
glNormalStream3fATIJ
glNormalStream3iATIJ
glNormalStream3sATIJ
glObjectLabelJ
glObjectPtrLabelJ
glObjectPurgeableAPPLEJ
glObjectUnpurgeableAPPLEJ
glOrthoJ
glPNTrianglesfATIJ
glPNTrianglesiATIJ
glPassTexCoordATIJ
glPassThroughJ
glPatchParameterfvJ
glPatchParameteriJ
glPathColorGenNVJ
glPathCommandsNVJ
glPathCoordsNVJ
glPathCoverDepthFuncNVJ
glPathDashArrayNVJ
glPathFogGenNVJ
glPathGlyphRangeNVJ
glPathGlyphsNVJ
glPathParameterfNVJ
glPathParameterfvNVJ
glPathParameteriNVJ
glPathParameterivNVJ
glPathStencilDepthOffsetNVJ
glPathStencilFuncNVJ
glPathStringNVJ
glPathSubCommandsNVJ
glPathSubCoordsNVJ
glPathTexGenNVJ
glPauseTransformFeedbackJ
glPauseTransformFeedbackNVJ
glPixelDataRangeNVJ
glPixelMapfvJ
glPixelMapuivJ
glPixelMapusvJ
glPixelStorefJ
glPixelStoreiJ
glPixelTransferfJ
glPixelTransferiJ
glPixelZoomJ
glPointAlongPathNVJ
glPointParameterfJ
glPointParameterfARBJ
glPointParameterfEXTJ
glPointParameterfvJ
glPointParameterfvARBJ
glPointParameterfvEXTJ
glPointParameteriJ
glPointParameteriNVJ
glPointParameterivJ
glPointParameterivNVJ
glPointSizeJ
glPolygonModeJ
glPolygonOffsetJ
glPolygonStippleJ
glPopAttribJ
glPopClientAttribJ
glPopDebugGroupJ
glPopMatrixJ
glPopNameJ
glPresentFrameDualFillNVJ
glPresentFrameKeyedNVJ
glPrimitiveRestartIndexJ
glPrimitiveRestartIndexNVJ
glPrimitiveRestartNVJ
glPrioritizeTexturesJ
glProgramBinaryJ
glProgramBufferParametersIivNVJ
glProgramBufferParametersIuivNVJ
glProgramBufferParametersfvNVJ
glProgramEnvParameter4dARBJ
glProgramEnvParameter4dvARBJ
glProgramEnvParameter4fARBJ
glProgramEnvParameter4fvARBJ
glProgramEnvParameterI4iNVJ
glProgramEnvParameterI4ivNVJ
glProgramEnvParameterI4uiNVJ
glProgramEnvParameterI4uivNVJ
glProgramEnvParameters4fvEXTJ
glProgramEnvParametersI4ivNVJ
glProgramEnvParametersI4uivNVJ
glProgramLocalParameter4dARBJ
glProgramLocalParameter4dvARBJ
glProgramLocalParameter4fARBJ
glProgramLocalParameter4fvARBJ
glProgramLocalParameterI4iNVJ
glProgramLocalParameterI4ivNVJ
glProgramLocalParameterI4uiNVJ
glProgramLocalParameterI4uivNVJ
glProgramLocalParameters4fvEXTJ
glProgramLocalParametersI4ivNVJ
glProgramLocalParametersI4uivNVJ
glProgramNamedParameter4dNVJ
glProgramNamedParameter4fNVJ
glProgramParameter4dNVJ
glProgramParameter4fNVJ
glProgramParameteriJ
glProgramParameteriARBJ
glProgramParameteriEXTJ
glProgramParameters4dvNVJ
glProgramParameters4fvNVJ
glProgramStringARBJ
glProgramUniform1dJ
glProgramUniform1dEXTJ
glProgramUniform1dvJ
glProgramUniform1dvEXTJ
glProgramUniform1fJ
glProgramUniform1fEXTJ
glProgramUniform1fvJ
glProgramUniform1fvEXTJ
glProgramUniform1iJ
glProgramUniform1i64NVJ
glProgramUniform1i64vNVJ
glProgramUniform1iEXTJ
glProgramUniform1ivJ
glProgramUniform1ivEXTJ
glProgramUniform1uiJ
glProgramUniform1ui64NVJ
glProgramUniform1ui64vNVJ
glProgramUniform1uiEXTJ
glProgramUniform1uivJ
glProgramUniform1uivEXTJ
glProgramUniform2dJ
glProgramUniform2dEXTJ
glProgramUniform2dvJ
glProgramUniform2dvEXTJ
glProgramUniform2fJ
glProgramUniform2fEXTJ
glProgramUniform2fvJ
glProgramUniform2fvEXTJ
glProgramUniform2iJ
glProgramUniform2i64NVJ
glProgramUniform2i64vNVJ
glProgramUniform2iEXTJ
glProgramUniform2ivJ
glProgramUniform2ivEXTJ
glProgramUniform2uiJ
glProgramUniform2ui64NVJ
glProgramUniform2ui64vNVJ
glProgramUniform2uiEXTJ
glProgramUniform2uivJ
glProgramUniform2uivEXTJ
glProgramUniform3dJ
glProgramUniform3dEXTJ
glProgramUniform3dvJ
glProgramUniform3dvEXTJ
glProgramUniform3fJ
glProgramUniform3fEXTJ
glProgramUniform3fvJ
glProgramUniform3fvEXTJ
glProgramUniform3iJ
glProgramUniform3i64NVJ
glProgramUniform3i64vNVJ
glProgramUniform3iEXTJ
glProgramUniform3ivJ
glProgramUniform3ivEXTJ
glProgramUniform3uiJ
glProgramUniform3ui64NVJ
glProgramUniform3ui64vNVJ
glProgramUniform3uiEXTJ
glProgramUniform3uivJ
glProgramUniform3uivEXTJ
glProgramUniform4dJ
glProgramUniform4dEXTJ
glProgramUniform4dvJ
glProgramUniform4dvEXTJ
glProgramUniform4fJ
glProgramUniform4fEXTJ
glProgramUniform4fvJ
glProgramUniform4fvEXTJ
glProgramUniform4iJ
glProgramUniform4i64NVJ
glProgramUniform4i64vNVJ
glProgramUniform4iEXTJ
glProgramUniform4ivJ
glProgramUniform4ivEXTJ
glProgramUniform4uiJ
glProgramUniform4ui64NVJ
glProgramUniform4ui64vNVJ
glProgramUniform4uiEXTJ
glProgramUniform4uivJ
glProgramUniform4uivEXTJ
glProgramUniformHandleui64ARBJ
glProgramUniformHandleui64NVJ
glProgramUniformHandleui64vARBJ
glProgramUniformHandleui64vNVJ
glProgramUniformMatrix2dvJ
glProgramUniformMatrix2dvEXTJ
glProgramUniformMatrix2fvJ
glProgramUniformMatrix2fvEXTJ
glProgramUniformMatrix2x3dvJ
glProgramUniformMatrix2x3dvEXTJ
glProgramUniformMatrix2x3fvJ
glProgramUniformMatrix2x3fvEXTJ
glProgramUniformMatrix2x4dvJ
glProgramUniformMatrix2x4dvEXTJ
glProgramUniformMatrix2x4fvJ
glProgramUniformMatrix2x4fvEXTJ
glProgramUniformMatrix3dvJ
glProgramUniformMatrix3dvEXTJ
glProgramUniformMatrix3fvJ
glProgramUniformMatrix3fvEXTJ
glProgramUniformMatrix3x2dvJ
glProgramUniformMatrix3x2dvEXTJ
glProgramUniformMatrix3x2fvJ
glProgramUniformMatrix3x2fvEXTJ
glProgramUniformMatrix3x4dvJ
glProgramUniformMatrix3x4dvEXTJ
glProgramUniformMatrix3x4fvJ
glProgramUniformMatrix3x4fvEXTJ
glProgramUniformMatrix4dvJ
glProgramUniformMatrix4dvEXTJ
glProgramUniformMatrix4fvJ
glProgramUniformMatrix4fvEXTJ
glProgramUniformMatrix4x2dvJ
glProgramUniformMatrix4x2dvEXTJ
glProgramUniformMatrix4x2fvJ
glProgramUniformMatrix4x2fvEXTJ
glProgramUniformMatrix4x3dvJ
glProgramUniformMatrix4x3dvEXTJ
glProgramUniformMatrix4x3fvJ
glProgramUniformMatrix4x3fvEXTJ
glProgramUniformui64NVJ
glProgramUniformui64vNVJ
glProgramVertexLimitNVJ
glProvokingVertexJ
glProvokingVertexEXTJ
glPushAttribJ
glPushClientAttribJ
glPushClientAttribDefaultEXTJ
glPushDebugGroupJ
glPushMatrixJ
glPushNameJ
glQueryCounterJ
glRasterPos2dJ
glRasterPos2fJ
glRasterPos2iJ
glRasterPos3dJ
glRasterPos3fJ
glRasterPos3iJ
glRasterPos4dJ
glRasterPos4fJ
glRasterPos4iJ
glReadBufferJ
glReadPixelsJ
glReadnPixelsJ
glReadnPixelsARBJ
glRectdJ
glRectfJ
glRectiJ
glReleaseShaderCompilerJ
glRenderModeJ
glRenderbufferStorageJ
glRenderbufferStorageEXTJ
glRenderbufferStorageMultisampleJ
glRenderbufferStorageMultisampleCoverageNVJ
glRenderbufferStorageMultisampleEXTJ
glRequestResidentProgramsNVJ
glResetHistogramJ
glResetMinmaxJ
glResumeTransformFeedbackJ
glResumeTransformFeedbackNVJ
glRotatedJ
glRotatefJ
glSampleCoverageJ
glSampleCoverageARBJ
glSampleMapATIJ
glSampleMaskIndexedNVJ
glSampleMaskiJ
glSamplerParameterIivJ
glSamplerParameterIuivJ
glSamplerParameterfJ
glSamplerParameterfvJ
glSamplerParameteriJ
glSamplerParameterivJ
glScaledJ
glScalefJ
glScissorJ
glScissorArrayvJ
glScissorIndexedJ
glScissorIndexedvJ
glSecondaryColor3bJ
glSecondaryColor3bEXTJ
glSecondaryColor3dJ
glSecondaryColor3dEXTJ
glSecondaryColor3fJ
glSecondaryColor3fEXTJ
glSecondaryColor3hNVJ
glSecondaryColor3ubJ
glSecondaryColor3ubEXTJ
glSecondaryColorFormatNVJ
glSecondaryColorP3uiJ
glSecondaryColorP3uivJ
glSecondaryColorPointerJ
glSecondaryColorPointerEXTJ
glSelectBufferJ
glSelectPerfMonitorCountersAMDJ
glSeparableFilter2DJ
glSetFenceAPPLEJ
glSetFenceNVJ
glSetFragmentShaderConstantATIJ
glSetInvariantEXTJ
glSetLocalConstantEXTJ
glSetMultisamplefvAMDJ
glShadeModelJ
glShaderBinaryJ
glShaderOp1EXTJ
glShaderOp2EXTJ
glShaderOp3EXTJ
glShaderSourceJ
glShaderSourceARBJ
glShaderStorageBlockBindingJ
glStencilClearTagEXTJ
glStencilFillPathInstancedNVJ
glStencilFillPathNVJ
glStencilFuncJ
glStencilFuncSeparateJ
glStencilFuncSeparateATIJ
glStencilMaskJ
glStencilMaskSeparateJ
glStencilOpJ
glStencilOpSeparateJ
glStencilOpSeparateATIJ
glStencilOpValueAMDJ
glStencilStrokePathInstancedNVJ
glStencilStrokePathNVJ
glStringMarkerGREMEDYJ
glSwizzleEXTJ
glSyncTextureINTELJ
glTessellationFactorAMDJ
glTessellationModeAMDJ
glTestFenceAPPLEJ
glTestFenceNVJ
glTestObjectAPPLEJ
glTexBufferJ
glTexBufferARBJ
glTexBufferEXTJ
glTexBufferRangeJ
glTexBumpParameterfvATIJ
glTexBumpParameterivATIJ
glTexCoord1dJ
glTexCoord1fJ
glTexCoord1hNVJ
glTexCoord2dJ
glTexCoord2fJ
glTexCoord2hNVJ
glTexCoord3dJ
glTexCoord3fJ
glTexCoord3hNVJ
glTexCoord4dJ
glTexCoord4fJ
glTexCoord4hNVJ
glTexCoordFormatNVJ
glTexCoordP1uiJ
glTexCoordP1uivJ
glTexCoordP2uiJ
glTexCoordP2uivJ
glTexCoordP3uiJ
glTexCoordP3uivJ
glTexCoordP4uiJ
glTexCoordP4uivJ
glTexCoordPointerJ
glTexEnvfJ
glTexEnvfvJ
glTexEnviJ
glTexEnvivJ
glTexGendJ
glTexGendvJ
glTexGenfJ
glTexGenfvJ
glTexGeniJ
glTexGenivJ
glTexImage1DJ
glTexImage2DJ
glTexImage2DMultisampleJ
glTexImage2DMultisampleCoverageNVJ
glTexImage3DJ
glTexImage3DMultisampleJ
glTexImage3DMultisampleCoverageNVJ
glTexPageCommitmentARBJ
glTexParameterIivJ
glTexParameterIivEXTJ
glTexParameterIuivJ
glTexParameterIuivEXTJ
glTexParameterfJ
glTexParameterfvJ
glTexParameteriJ
glTexParameterivJ
glTexRenderbufferNVJ
glTexStorage1DJ
glTexStorage2DJ
glTexStorage2DMultisampleJ
glTexStorage3DJ
glTexStorage3DMultisampleJ
glTexStorageSparseAMDJ
glTexSubImage1DJ
glTexSubImage2DJ
glTexSubImage3DJ
glTextureBarrierJ
glTextureBarrierNVJ
glTextureBufferJ
glTextureBufferEXTJ
glTextureBufferRangeJ
glTextureBufferRangeEXTJ
glTextureImage1DEXTJ
glTextureImage2DEXTJ
glTextureImage2DMultisampleCoverageNVJ
glTextureImage2DMultisampleNVJ
glTextureImage3DEXTJ
glTextureImage3DMultisampleCoverageNVJ
glTextureImage3DMultisampleNVJ
glTexturePageCommitmentEXTJ
glTextureParameterIivJ
glTextureParameterIivEXTJ
glTextureParameterIuivJ
glTextureParameterIuivEXTJ
glTextureParameterfJ
glTextureParameterfEXTJ
glTextureParameterfvJ
glTextureParameterfvEXTJ
glTextureParameteriJ
glTextureParameteriEXTJ
glTextureParameterivJ
glTextureParameterivEXTJ
glTextureRangeAPPLEJ
glTextureRenderbufferEXTJ
glTextureStorage1DJ
glTextureStorage1DEXTJ
glTextureStorage2DJ
glTextureStorage2DEXTJ
glTextureStorage2DMultisampleJ
glTextureStorage2DMultisampleEXTJ
glTextureStorage3DJ
glTextureStorage3DEXTJ
glTextureStorage3DMultisampleJ
glTextureStorage3DMultisampleEXTJ
glTextureStorageSparseAMDJ
glTextureSubImage1DJ
glTextureSubImage1DEXTJ
glTextureSubImage2DJ
glTextureSubImage2DEXTJ
glTextureSubImage3DJ
glTextureSubImage3DEXTJ
glTextureViewJ
glTrackMatrixNVJ
glTransformFeedbackAttribsNVJ
glTransformFeedbackBufferBaseJ
glTransformFeedbackBufferRangeJ
glTransformFeedbackVaryingsJ
glTransformFeedbackVaryingsEXTJ
glTransformFeedbackVaryingsNVJ
glTransformPathNVJ
glTranslatedJ
glTranslatefJ
glUniform1dJ
glUniform1dvJ
glUniform1fJ
glUniform1fARBJ
glUniform1fvJ
glUniform1fvARBJ
glUniform1iJ
glUniform1i64NVJ
glUniform1i64vNVJ
glUniform1iARBJ
glUniform1ivJ
glUniform1ivARBJ
glUniform1uiJ
glUniform1ui64NVJ
glUniform1ui64vNVJ
glUniform1uiEXTJ
glUniform1uivJ
glUniform1uivEXTJ
glUniform2dJ
glUniform2dvJ
glUniform2fJ
glUniform2fARBJ
glUniform2fvJ
glUniform2fvARBJ
glUniform2iJ
glUniform2i64NVJ
glUniform2i64vNVJ
glUniform2iARBJ
glUniform2ivJ
glUniform2ivARBJ
glUniform2uiJ
glUniform2ui64NVJ
glUniform2ui64vNVJ
glUniform2uiEXTJ
glUniform2uivJ
glUniform2uivEXTJ
glUniform3dJ
glUniform3dvJ
glUniform3fJ
glUniform3fARBJ
glUniform3fvJ
glUniform3fvARBJ
glUniform3iJ
glUniform3i64NVJ
glUniform3i64vNVJ
glUniform3iARBJ
glUniform3ivJ
glUniform3ivARBJ
glUniform3uiJ
glUniform3ui64NVJ
glUniform3ui64vNVJ
glUniform3uiEXTJ
glUniform3uivJ
glUniform3uivEXTJ
glUniform4dJ
glUniform4dvJ
glUniform4fJ
glUniform4fARBJ
glUniform4fvJ
glUniform4fvARBJ
glUniform4iJ
glUniform4i64NVJ
glUniform4i64vNVJ
glUniform4iARBJ
glUniform4ivJ
glUniform4ivARBJ
glUniform4uiJ
glUniform4ui64NVJ
glUniform4ui64vNVJ
glUniform4uiEXTJ
glUniform4uivJ
glUniform4uivEXTJ
glUniformBlockBindingJ
glUniformBufferEXTJ
glUniformHandleui64ARBJ
glUniformHandleui64NVJ
glUniformHandleui64vARBJ
glUniformHandleui64vNVJ
glUniformMatrix2dvJ
glUniformMatrix2fvJ
glUniformMatrix2fvARBJ
glUniformMatrix2x3dvJ
glUniformMatrix2x3fvJ
glUniformMatrix2x4dvJ
glUniformMatrix2x4fvJ
glUniformMatrix3dvJ
glUniformMatrix3fvJ
glUniformMatrix3fvARBJ
glUniformMatrix3x2dvJ
glUniformMatrix3x2fvJ
glUniformMatrix3x4dvJ
glUniformMatrix3x4fvJ
glUniformMatrix4dvJ
glUniformMatrix4fvJ
glUniformMatrix4fvARBJ
glUniformMatrix4x2dvJ
glUniformMatrix4x2fvJ
glUniformMatrix4x3dvJ
glUniformMatrix4x3fvJ
glUniformSubroutinesuivJ
glUniformui64NVJ
glUniformui64vNVJ
glUnlockArraysEXTJ
glUnmapBufferJ
glUnmapBufferARBJ
glUnmapNamedBufferJ
glUnmapNamedBufferEXTJ
glUnmapObjectBufferATIJ
glUnmapTexture2DINTELJ
glUpdateObjectBufferATIJ
glUseProgramJ
glUseProgramObjectARBJ
glUseProgramStagesJ
glUseShaderProgramEXTJ
glValidateProgramJ
glValidateProgramARBJ
glValidateProgramPipelineJ
glVariantArrayObjectATIJ
glVariantPointerEXTJ
glVariantbvEXTJ
glVariantdvEXTJ
glVariantfvEXTJ
glVariantivEXTJ
glVariantsvEXTJ
glVariantubvEXTJ
glVariantuivEXTJ
glVariantusvEXTJ
glVertex2dJ
glVertex2fJ
glVertex2hNVJ
glVertex2iJ
glVertex3dJ
glVertex3fJ
glVertex3hNVJ
glVertex3iJ
glVertex4dJ
glVertex4fJ
glVertex4hNVJ
glVertex4iJ
glVertexArrayAttribBindingJ
glVertexArrayAttribFormatJ
glVertexArrayAttribIFormatJ
glVertexArrayAttribLFormatJ
glVertexArrayBindingDivisorJ
glVertexArrayColorOffsetEXTJ
glVertexArrayEdgeFlagOffsetEXTJ
glVertexArrayElementBufferJ
glVertexArrayFogCoordOffsetEXTJ
glVertexArrayIndexOffsetEXTJ
glVertexArrayMultiTexCoordOffsetEXTJ
glVertexArrayNormalOffsetEXTJ
glVertexArrayParameteriAPPLEJ
glVertexArrayRangeAPPLEJ
glVertexArrayRangeNVJ
glVertexArraySecondaryColorOffsetEXTJ
glVertexArrayTexCoordOffsetEXTJ
glVertexArrayVertexAttribIOffsetEXTJ
glVertexArrayVertexAttribLOffsetEXTJ
glVertexArrayVertexAttribOffsetEXTJ
glVertexArrayVertexBufferJ
glVertexArrayVertexBuffersJ
glVertexArrayVertexOffsetEXTJ
glVertexAttrib1dJ
glVertexAttrib1dARBJ
glVertexAttrib1dNVJ
glVertexAttrib1fJ
glVertexAttrib1fARBJ
glVertexAttrib1fNVJ
glVertexAttrib1hNVJ
glVertexAttrib1sJ
glVertexAttrib1sARBJ
glVertexAttrib1sNVJ
glVertexAttrib2dJ
glVertexAttrib2dARBJ
glVertexAttrib2dNVJ
glVertexAttrib2fJ
glVertexAttrib2fARBJ
glVertexAttrib2fNVJ
glVertexAttrib2hNVJ
glVertexAttrib2sJ
glVertexAttrib2sARBJ
glVertexAttrib2sNVJ
glVertexAttrib3dJ
glVertexAttrib3dARBJ
glVertexAttrib3dNVJ
glVertexAttrib3fJ
glVertexAttrib3fARBJ
glVertexAttrib3fNVJ
glVertexAttrib3hNVJ
glVertexAttrib3sJ
glVertexAttrib3sARBJ
glVertexAttrib3sNVJ
glVertexAttrib4NubJ
glVertexAttrib4NubARBJ
glVertexAttrib4dJ
glVertexAttrib4dARBJ
glVertexAttrib4dNVJ
glVertexAttrib4fJ
glVertexAttrib4fARBJ
glVertexAttrib4fNVJ
glVertexAttrib4hNVJ
glVertexAttrib4sJ
glVertexAttrib4sARBJ
glVertexAttrib4sNVJ
glVertexAttrib4ubNVJ
glVertexAttribArrayObjectATIJ
glVertexAttribBindingJ
glVertexAttribDivisorJ
glVertexAttribDivisorARBJ
glVertexAttribFormatJ
glVertexAttribFormatNVJ
glVertexAttribI1iJ
glVertexAttribI1iEXTJ
glVertexAttribI1ivJ
glVertexAttribI1ivEXTJ
glVertexAttribI1uiJ
glVertexAttribI1uiEXTJ
glVertexAttribI1uivJ
glVertexAttribI1uivEXTJ
glVertexAttribI2iJ
glVertexAttribI2iEXTJ
glVertexAttribI2ivJ
glVertexAttribI2ivEXTJ
glVertexAttribI2uiJ
glVertexAttribI2uiEXTJ
glVertexAttribI2uivJ
glVertexAttribI2uivEXTJ
glVertexAttribI3iJ
glVertexAttribI3iEXTJ
glVertexAttribI3ivJ
glVertexAttribI3ivEXTJ
glVertexAttribI3uiJ
glVertexAttribI3uiEXTJ
glVertexAttribI3uivJ
glVertexAttribI3uivEXTJ
glVertexAttribI4bvJ
glVertexAttribI4bvEXTJ
glVertexAttribI4iJ
glVertexAttribI4iEXTJ
glVertexAttribI4ivJ
glVertexAttribI4ivEXTJ
glVertexAttribI4svJ
glVertexAttribI4svEXTJ
glVertexAttribI4ubvJ
glVertexAttribI4ubvEXTJ
glVertexAttribI4uiJ
glVertexAttribI4uiEXTJ
glVertexAttribI4uivJ
glVertexAttribI4uivEXTJ
glVertexAttribI4usvJ
glVertexAttribI4usvEXTJ
glVertexAttribIFormatJ
glVertexAttribIFormatNVJ
glVertexAttribIPointerJ
glVertexAttribIPointerEXTJ
glVertexAttribL1dJ
glVertexAttribL1dEXTJ
glVertexAttribL1dvJ
glVertexAttribL1dvEXTJ
glVertexAttribL1i64NVJ
glVertexAttribL1i64vNVJ
glVertexAttribL1ui64ARBJ
glVertexAttribL1ui64NVJ
glVertexAttribL1ui64vARBJ
glVertexAttribL1ui64vNVJ
glVertexAttribL2dJ
glVertexAttribL2dEXTJ
glVertexAttribL2dvJ
glVertexAttribL2dvEXTJ
glVertexAttribL2i64NVJ
glVertexAttribL2i64vNVJ
glVertexAttribL2ui64NVJ
glVertexAttribL2ui64vNVJ
glVertexAttribL3dJ
glVertexAttribL3dEXTJ
glVertexAttribL3dvJ
glVertexAttribL3dvEXTJ
glVertexAttribL3i64NVJ
glVertexAttribL3i64vNVJ
glVertexAttribL3ui64NVJ
glVertexAttribL3ui64vNVJ
glVertexAttribL4dJ
glVertexAttribL4dEXTJ
glVertexAttribL4dvJ
glVertexAttribL4dvEXTJ
glVertexAttribL4i64NVJ
glVertexAttribL4i64vNVJ
glVertexAttribL4ui64NVJ
glVertexAttribL4ui64vNVJ
glVertexAttribLFormatJ
glVertexAttribLFormatNVJ
glVertexAttribLPointerJ
glVertexAttribLPointerEXTJ
glVertexAttribP1uiJ
glVertexAttribP1uivJ
glVertexAttribP2uiJ
glVertexAttribP2uivJ
glVertexAttribP3uiJ
glVertexAttribP3uivJ
glVertexAttribP4uiJ
glVertexAttribP4uivJ
glVertexAttribParameteriAMDJ
glVertexAttribPointerJ
glVertexAttribPointerARBJ
glVertexAttribPointerNVJ
glVertexAttribs1dvNVJ
glVertexAttribs1fvNVJ
glVertexAttribs1hvNVJ
glVertexAttribs1svNVJ
glVertexAttribs2dvNVJ
glVertexAttribs2fvNVJ
glVertexAttribs2hvNVJ
glVertexAttribs2svNVJ
glVertexAttribs3dvNVJ
glVertexAttribs3fvNVJ
glVertexAttribs3hvNVJ
glVertexAttribs3svNVJ
glVertexAttribs4dvNVJ
glVertexAttribs4fvNVJ
glVertexAttribs4hvNVJ
glVertexAttribs4svNVJ
glVertexBindingDivisorJ
glVertexBlendARBJ
glVertexBlendEnvfATIJ
glVertexBlendEnviATIJ
glVertexFormatNVJ
glVertexP2uiJ
glVertexP2uivJ
glVertexP3uiJ
glVertexP3uivJ
glVertexP4uiJ
glVertexP4uivJ
glVertexPointerJ
glVertexStream2dATIJ
glVertexStream2fATIJ
glVertexStream2iATIJ
glVertexStream2sATIJ
glVertexStream3dATIJ
glVertexStream3fATIJ
glVertexStream3iATIJ
glVertexStream3sATIJ
glVertexStream4dATIJ
glVertexStream4fATIJ
glVertexStream4iATIJ
glVertexStream4sATIJ
glVertexWeightPointerEXTJ
glVertexWeightfEXTJ
glVertexWeighthNVJ
glVideoCaptureNVJ
glVideoCaptureStreamParameterdvNVJ
glVideoCaptureStreamParameterfvNVJ
glVideoCaptureStreamParameterivNVJ
glViewportJ
glViewportArrayvJ
glViewportIndexedfJ
glViewportIndexedfvJ
glWaitSyncJ
glWeightPathsNVJ
glWeightPointerARBJ
glWeightbvARBJ
glWeightdvARBJ
glWeightfvARBJ
glWeightivARBJ
glWeightsvARBJ
glWeightubvARBJ
glWeightuivARBJ
glWeightusvARBJ
glWindowPos2dJ
glWindowPos2dARBJ
glWindowPos2fJ
glWindowPos2fARBJ
glWindowPos2iJ
glWindowPos2iARBJ
glWindowPos2sARBJ
glWindowPos3dJ
glWindowPos3dARBJ
glWindowPos3fJ
glWindowPos3fARBJ
glWindowPos3iJ
glWindowPos3iARBJ
glWindowPos3sARBJ
glWriteMaskEXTJ
initAllStubs(Z)Ljava/util/Set;
remove(Ljava/util/Set;Ljava/lang/String;)V
trackerLorg/lwjgl/opengl/StateTracker;
unloadAllStubs()V
utilLorg/lwjgl/opengl/APIUtil;
```

#### `org.lwjgl.opengl.ContextGL`

```text
<init>(Lorg/lwjgl/opengl/PeerInfo;Lorg/lwjgl/opengl/ContextAttribs;Lorg/lwjgl/opengl/ContextGL;)V
canAccess()Z
checkAccess()V
checkDestroy()V
contextAttribsLorg/lwjgl/opengl/ContextAttribs;
createImplementation()Lorg/lwjgl/opengl/ContextImplementation;
current_context_localLjava/lang/ThreadLocal;
destroy()V
destroy_requestedZ
destroyedZ
forceDestroy()V
forwardCompatibleZ
getContextAttribs()Lorg/lwjgl/opengl/ContextAttribs;
getCurrentContext()Lorg/lwjgl/opengl/ContextGL;
getHandle()Ljava/nio/ByteBuffer;
getPeerInfo()Lorg/lwjgl/opengl/PeerInfo;
handleLjava/nio/ByteBuffer;
implementationLorg/lwjgl/opengl/ContextImplementation;
isCurrent()Z
makeCurrent()V
peer_infoLorg/lwjgl/opengl/PeerInfo;
releaseCurrent()V
releaseDrawable()V
setCLSharingProperties(Lorg/lwjgl/PointerBuffer;)V
setSwapInterval(I)V
swapBuffers()V
threadLjava/lang/Thread;
update()V
```

#### `org.lwjgl.opengl.ContextGLES`

```text
<init>(Lorg/lwjgl/opengl/DrawableGLES;Lorg/lwjgl/opengles/ContextAttribs;Lorg/lwjgl/opengl/ContextGLES;)V
canAccess()Z
checkAccess()V
checkDestroy()V
contextAttribsLorg/lwjgl/opengles/ContextAttribs;
current_context_localLjava/lang/ThreadLocal;
destroy()V
destroy_requestedZ
destroyedZ
drawableLorg/lwjgl/opengl/DrawableGLES;
eglContextLorg/lwjgl/opengles/EGLContext;
forceDestroy()V
getContextAttribs()Lorg/lwjgl/opengles/ContextAttribs;
getCurrentContext()Lorg/lwjgl/opengl/ContextGLES;
getEGLContext()Lorg/lwjgl/opengles/EGLContext;
isCurrent()Z
makeCurrent()V
releaseCurrent()V
setSwapInterval(I)V
swapBuffers()V
threadLjava/lang/Thread;
```

#### `org.lwjgl.opengl.ContextImplementation`

```text
create(Lorg/lwjgl/opengl/PeerInfo;Ljava/nio/IntBuffer;Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
destroy(Lorg/lwjgl/opengl/PeerInfo;Ljava/nio/ByteBuffer;)V
isCurrent(Ljava/nio/ByteBuffer;)Z
makeCurrent(Lorg/lwjgl/opengl/PeerInfo;Ljava/nio/ByteBuffer;)V
releaseCurrentContext()V
releaseDrawable(Ljava/nio/ByteBuffer;)V
setSwapInterval(I)V
swapBuffers()V
update(Ljava/nio/ByteBuffer;)V
```

#### `org.lwjgl.opengl.Display$1`

```text
<init>()V
```

#### `org.lwjgl.opengl.Display$2`

```text
<init>()V
```

#### `org.lwjgl.opengl.Display$3`

```text
<init>()V
```

#### `org.lwjgl.opengl.Display$4`

```text
<init>()V
```

#### `org.lwjgl.opengl.Display$5`

```text
<init>()V
```

#### `org.lwjgl.opengl.Display$6`

```text
<init>()V
```

#### `org.lwjgl.opengl.Display$7`

```text
<init>(Ljava/lang/String;)V
run()Ljava/lang/Boolean;
val$property_nameLjava/lang/String;
```

#### `org.lwjgl.opengl.Display`

```text
access$000()V
access$102(Z)Z
access$200()Ljava/lang/Thread;
access$300()V
access$400()V
access$502(I)I
access$602(I)I
access$702([Ljava/nio/ByteBuffer;)[Ljava/nio/ByteBuffer;
access$800()V
bF
cached_icons[Ljava/nio/ByteBuffer;
component_listenerLjava/awt/event/ComponentListener;
create()V
create(Lorg/lwjgl/opengl/PixelFormat;)V
create(Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;)V
create(Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/Drawable;Lorg/lwjgl/opengl/ContextAttribs;)V
create(Lorg/lwjgl/opengl/PixelFormatLWJGL;Lorg/lwjgl/opengl/Drawable;Lorg/lwjgl/opengles/ContextAttribs;)V
createDisplayImplementation()Lorg/lwjgl/opengl/DisplayImplementation;
createWindow()V
current_modeLorg/lwjgl/opengl/DisplayMode;
destroy()V
destroyWindow()V
display_implLorg/lwjgl/opengl/DisplayImplementation;
drawableLorg/lwjgl/opengl/DrawableLWJGL;
fullscreenZ
gF
getAvailableDisplayModes()[Lorg/lwjgl/opengl/DisplayMode;
getDesktopDisplayMode()Lorg/lwjgl/opengl/DisplayMode;
getDisplayMode()Lorg/lwjgl/opengl/DisplayMode;
getDrawable()Lorg/lwjgl/opengl/Drawable;
getEffectiveMode()Lorg/lwjgl/opengl/DisplayMode;
getHeight()I
getImplementation()Lorg/lwjgl/opengl/DisplayImplementation;
getParent()Ljava/awt/Canvas;
getPrivilegedBoolean(Ljava/lang/String;)Z
getPrivilegedString(Ljava/lang/String;)Ljava/lang/String;
getTitle()Ljava/lang/String;
getWidth()I
getWindowX()I
getWindowY()I
heightI
initContext()V
initControls()V
initial_modeLorg/lwjgl/opengl/DisplayMode;
isActive()Z
isCloseRequested()Z
isCreated()Z
isFullscreen()Z
isResizable()Z
makeCurrent()V
makeCurrentAndSetSwapInterval()V
parentLjava/awt/Canvas;
parent_resizedZ
pollDevices()V
processMessages()V
rF
registerShutdownHook()V
releaseDrawable()V
removeShutdownHook()V
reset()V
reshape()V
setDisplayMode(Lorg/lwjgl/opengl/DisplayMode;)V
setDisplayModeAndFullscreenInternal(ZLorg/lwjgl/opengl/DisplayMode;)V
setFullscreen(Z)V
setIcon([Ljava/nio/ByteBuffer;)I
setResizable(Z)V
setSwapInterval(I)V
setTitle(Ljava/lang/String;)V
setVSyncEnabled(Z)V
shutdown_hookLjava/lang/Thread;
swapBuffers()V
swap_intervalI
switchDisplayMode()V
sync(I)V
titleLjava/lang/String;
update()V
update(Z)V
wasResized()Z
widthI
window_createdZ
window_resizableZ
window_resizedZ
xI
yI
```

#### `org.lwjgl.opengl.DisplayImplementation`

```text
bindTexImageToPbuffer(Lorg/lwjgl/opengl/PeerInfo;I)V
createPbuffer(IILorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;)Lorg/lwjgl/opengl/PeerInfo;
createPeerInfo(Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;)Lorg/lwjgl/opengl/PeerInfo;
createWindow(Lorg/lwjgl/opengl/DrawableLWJGL;Lorg/lwjgl/opengl/DisplayMode;Ljava/awt/Canvas;II)V
destroyWindow()V
getAdapter()Ljava/lang/String;
getAvailableDisplayModes()[Lorg/lwjgl/opengl/DisplayMode;
getGammaRampLength()I
getHeight()I
getPbufferCapabilities()I
getPixelScaleFactor()F
getVersion()Ljava/lang/String;
getWidth()I
getX()I
getY()I
init()Lorg/lwjgl/opengl/DisplayMode;
isActive()Z
isBufferLost(Lorg/lwjgl/opengl/PeerInfo;)Z
isCloseRequested()Z
isDirty()Z
isVisible()Z
releaseTexImageFromPbuffer(Lorg/lwjgl/opengl/PeerInfo;I)V
resetDisplayMode()V
reshape(IIII)V
setGammaRamp(Ljava/nio/FloatBuffer;)V
setIcon([Ljava/nio/ByteBuffer;)I
setPbufferAttrib(Lorg/lwjgl/opengl/PeerInfo;II)V
setResizable(Z)V
setTitle(Ljava/lang/String;)V
switchDisplayMode(Lorg/lwjgl/opengl/DisplayMode;)V
update()V
wasResized()Z
```

#### `org.lwjgl.opengl.DisplayMode`

```text
<init>(II)V
<init>(IIII)V
<init>(IIIIZ)V
bppI
equals(Ljava/lang/Object;)Z
freqI
fullscreenZ
getBitsPerPixel()I
getFrequency()I
getHeight()I
getWidth()I
heightI
isFullscreenCapable()Z
widthI
```

#### `org.lwjgl.opengl.Drawable`

```text
setCLSharingProperties(Lorg/lwjgl/PointerBuffer;)V
```

#### `org.lwjgl.opengl.DrawableGL`

```text
<init>()V
checkDestroyed()V
checkGLError()V
contextLorg/lwjgl/opengl/ContextGL;
createSharedContext()Lorg/lwjgl/opengl/ContextGL;
destroy()V
getContext()Lorg/lwjgl/opengl/ContextGL;
getPixelFormat()Lorg/lwjgl/opengl/PixelFormatLWJGL;
initContext(FFF)V
isCurrent()Z
makeCurrent()V
peer_infoLorg/lwjgl/opengl/PeerInfo;
pixel_formatLorg/lwjgl/opengl/PixelFormat;
releaseContext()V
setCLSharingProperties(Lorg/lwjgl/PointerBuffer;)V
setPixelFormat(Lorg/lwjgl/opengl/PixelFormatLWJGL;)V
setPixelFormat(Lorg/lwjgl/opengl/PixelFormatLWJGL;Lorg/lwjgl/opengl/ContextAttribs;)V
setSwapInterval(I)V
swapBuffers()V
```

#### `org.lwjgl.opengl.DrawableGLES`

```text
<init>()V
checkDestroyed()V
contextLorg/lwjgl/opengl/ContextGLES;
createContext(Lorg/lwjgl/opengles/ContextAttribs;Lorg/lwjgl/opengl/Drawable;)V
destroy()V
eglConfigLorg/lwjgl/opengles/EGLConfig;
eglDisplayLorg/lwjgl/opengles/EGLDisplay;
eglSurfaceLorg/lwjgl/opengles/EGLSurface;
getContext()Lorg/lwjgl/opengl/ContextGLES;
getEGLConfig()Lorg/lwjgl/opengles/EGLConfig;
getEGLDisplay()Lorg/lwjgl/opengles/EGLDisplay;
getEGLSurface()Lorg/lwjgl/opengles/EGLSurface;
initialize(JJILorg/lwjgl/opengles/PixelFormat;)V
pixel_formatLorg/lwjgl/opengles/PixelFormat;
releaseContext()V
setPixelFormat(Lorg/lwjgl/opengl/PixelFormatLWJGL;)V
shared_drawableLorg/lwjgl/opengl/Drawable;
```

#### `org.lwjgl.opengl.DrawableLWJGL`

```text
checkGLError()V
createSharedContext()Lorg/lwjgl/opengl/Context;
destroy()V
getContext()Lorg/lwjgl/opengl/Context;
getPixelFormat()Lorg/lwjgl/opengl/PixelFormatLWJGL;
initContext(FFF)V
isCurrent()Z
makeCurrent()V
releaseContext()V
setSwapInterval(I)V
swapBuffers()V
```

#### `org.lwjgl.opengl.EXTAbgr`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTBgra`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTBindableUniform`

```text
nglGetUniformBufferSizeEXT(IIJ)I
nglGetUniformOffsetEXT(IIJ)J
nglUniformBufferEXT(IIIJ)V
```

#### `org.lwjgl.opengl.EXTBlendColor`

```text
nglBlendColorEXT(FFFFJ)V
```

#### `org.lwjgl.opengl.EXTBlendEquationSeparate`

```text
nglBlendEquationSeparateEXT(IIJ)V
```

#### `org.lwjgl.opengl.EXTBlendFuncSeparate`

```text
glBlendFuncSeparateEXT(IIII)V
nglBlendFuncSeparateEXT(IIIIJ)V
```

#### `org.lwjgl.opengl.EXTBlendMinmax`

```text
nglBlendEquationEXT(IJ)V
```

#### `org.lwjgl.opengl.EXTBlendSubtract`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTCgShader`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTCompiledVertexArray`

```text
nglLockArraysEXT(IIJ)V
nglUnlockArraysEXT(J)V
```

#### `org.lwjgl.opengl.EXTDepthBoundsTest`

```text
nglDepthBoundsEXT(DDJ)V
```

#### `org.lwjgl.opengl.EXTDirectStateAccess`

```text
glGetNamedBufferParameterEXT(II)I
glGetNamedProgramEXT(III)I
nglBindMultiTextureEXT(IIIJ)V
nglCheckNamedFramebufferStatusEXT(IIJ)I
nglClientAttribDefaultEXT(IJ)V
nglCompressedMultiTexImage1DEXT(IIIIIIIJJ)V
nglCompressedMultiTexImage1DEXTBO(IIIIIIIJJ)V
nglCompressedMultiTexImage2DEXT(IIIIIIIIJJ)V
nglCompressedMultiTexImage2DEXTBO(IIIIIIIIJJ)V
nglCompressedMultiTexImage3DEXT(IIIIIIIIIJJ)V
nglCompressedMultiTexImage3DEXTBO(IIIIIIIIIJJ)V
nglCompressedMultiTexSubImage1DEXT(IIIIIIIJJ)V
nglCompressedMultiTexSubImage1DEXTBO(IIIIIIIJJ)V
nglCompressedMultiTexSubImage2DEXT(IIIIIIIIIJJ)V
nglCompressedMultiTexSubImage2DEXTBO(IIIIIIIIIJJ)V
nglCompressedMultiTexSubImage3DEXT(IIIIIIIIIIIJJ)V
nglCompressedMultiTexSubImage3DEXTBO(IIIIIIIIIIIJJ)V
nglCompressedTextureImage1DEXT(IIIIIIIJJ)V
nglCompressedTextureImage1DEXTBO(IIIIIIIJJ)V
nglCompressedTextureImage2DEXT(IIIIIIIIJJ)V
nglCompressedTextureImage2DEXTBO(IIIIIIIIJJ)V
nglCompressedTextureImage3DEXT(IIIIIIIIIJJ)V
nglCompressedTextureImage3DEXTBO(IIIIIIIIIJJ)V
nglCompressedTextureSubImage1DEXT(IIIIIIIJJ)V
nglCompressedTextureSubImage1DEXTBO(IIIIIIIJJ)V
nglCompressedTextureSubImage2DEXT(IIIIIIIIIJJ)V
nglCompressedTextureSubImage2DEXTBO(IIIIIIIIIJJ)V
nglCompressedTextureSubImage3DEXT(IIIIIIIIIIIJJ)V
nglCompressedTextureSubImage3DEXTBO(IIIIIIIIIIIJJ)V
nglCopyMultiTexImage1DEXT(IIIIIIIIJ)V
nglCopyMultiTexImage2DEXT(IIIIIIIIIJ)V
nglCopyMultiTexSubImage1DEXT(IIIIIIIJ)V
nglCopyMultiTexSubImage2DEXT(IIIIIIIIIJ)V
nglCopyMultiTexSubImage3DEXT(IIIIIIIIIIJ)V
nglCopyTextureImage1DEXT(IIIIIIIIJ)V
nglCopyTextureImage2DEXT(IIIIIIIIIJ)V
nglCopyTextureSubImage1DEXT(IIIIIIIJ)V
nglCopyTextureSubImage2DEXT(IIIIIIIIIJ)V
nglCopyTextureSubImage3DEXT(IIIIIIIIIIJ)V
nglDisableClientStateIndexedEXT(IIJ)V
nglDisableClientStateiEXT(IIJ)V
nglDisableVertexArrayAttribEXT(IIJ)V
nglDisableVertexArrayEXT(IIJ)V
nglEnableClientStateIndexedEXT(IIJ)V
nglEnableClientStateiEXT(IIJ)V
nglEnableVertexArrayAttribEXT(IIJ)V
nglEnableVertexArrayEXT(IIJ)V
nglFlushMappedNamedBufferRangeEXT(IJJJ)V
nglFramebufferDrawBufferEXT(IIJ)V
nglFramebufferDrawBuffersEXT(IIJJ)V
nglFramebufferReadBufferEXT(IIJ)V
nglGenerateMultiTexMipmapEXT(IIJ)V
nglGenerateTextureMipmapEXT(IIJ)V
nglGetCompressedMultiTexImageEXT(IIIJJ)V
nglGetCompressedMultiTexImageEXTBO(IIIJJ)V
nglGetCompressedTextureImageEXT(IIIJJ)V
nglGetCompressedTextureImageEXTBO(IIIJJ)V
nglGetDoubleIndexedvEXT(IIJJ)V
nglGetDoublei_vEXT(IIJJ)V
nglGetFloatIndexedvEXT(IIJJ)V
nglGetFloati_vEXT(IIJJ)V
nglGetFramebufferParameterivEXT(IIJJ)V
nglGetMultiTexEnvfvEXT(IIIJJ)V
nglGetMultiTexEnvivEXT(IIIJJ)V
nglGetMultiTexGendvEXT(IIIJJ)V
nglGetMultiTexGenfvEXT(IIIJJ)V
nglGetMultiTexGenivEXT(IIIJJ)V
nglGetMultiTexImageEXT(IIIIIJJ)V
nglGetMultiTexImageEXTBO(IIIIIJJ)V
nglGetMultiTexLevelParameterfvEXT(IIIIJJ)V
nglGetMultiTexLevelParameterivEXT(IIIIJJ)V
nglGetMultiTexParameterIivEXT(IIIJJ)V
nglGetMultiTexParameterIuivEXT(IIIJJ)V
nglGetMultiTexParameterfvEXT(IIIJJ)V
nglGetMultiTexParameterivEXT(IIIJJ)V
nglGetNamedBufferParameterivEXT(IIJJ)V
nglGetNamedBufferPointervEXT(IIJJ)Ljava/nio/ByteBuffer;
nglGetNamedBufferSubDataEXT(IJJJJ)V
nglGetNamedFramebufferAttachmentParameterivEXT(IIIJJ)V
nglGetNamedProgramLocalParameterIivEXT(IIIJJ)V
nglGetNamedProgramLocalParameterIuivEXT(IIIJJ)V
nglGetNamedProgramLocalParameterdvEXT(IIIJJ)V
nglGetNamedProgramLocalParameterfvEXT(IIIJJ)V
nglGetNamedProgramStringEXT(IIIJJ)V
nglGetNamedProgramivEXT(IIIJJ)V
nglGetNamedRenderbufferParameterivEXT(IIJJ)V
nglGetPointerIndexedvEXT(IIJJ)Ljava/nio/ByteBuffer;
nglGetPointeri_vEXT(IIJJ)Ljava/nio/ByteBuffer;
nglGetTextureImageEXT(IIIIIJJ)V
nglGetTextureImageEXTBO(IIIIIJJ)V
nglGetTextureLevelParameterfvEXT(IIIIJJ)V
nglGetTextureLevelParameterivEXT(IIIIJJ)V
nglGetTextureParameterIivEXT(IIIJJ)V
nglGetTextureParameterIuivEXT(IIIJJ)V
nglGetTextureParameterfvEXT(IIIJJ)V
nglGetTextureParameterivEXT(IIIJJ)V
nglGetVertexArrayIntegeri_vEXT(IIIJJ)V
nglGetVertexArrayIntegervEXT(IIJJ)V
nglGetVertexArrayPointeri_vEXT(IIIJJ)Ljava/nio/ByteBuffer;
nglGetVertexArrayPointervEXT(IIJJ)Ljava/nio/ByteBuffer;
nglMapNamedBufferEXT(IIJLjava/nio/ByteBuffer;J)Ljava/nio/ByteBuffer;
nglMapNamedBufferRangeEXT(IJJILjava/nio/ByteBuffer;J)Ljava/nio/ByteBuffer;
nglMatrixFrustumEXT(IDDDDDDJ)V
nglMatrixLoadIdentityEXT(IJ)V
nglMatrixLoadTransposedEXT(IJJ)V
nglMatrixLoadTransposefEXT(IJJ)V
nglMatrixLoaddEXT(IJJ)V
nglMatrixLoadfEXT(IJJ)V
nglMatrixMultTransposedEXT(IJJ)V
nglMatrixMultTransposefEXT(IJJ)V
nglMatrixMultdEXT(IJJ)V
nglMatrixMultfEXT(IJJ)V
nglMatrixOrthoEXT(IDDDDDDJ)V
nglMatrixPopEXT(IJ)V
nglMatrixPushEXT(IJ)V
nglMatrixRotatedEXT(IDDDDJ)V
nglMatrixRotatefEXT(IFFFFJ)V
nglMatrixScaledEXT(IDDDJ)V
nglMatrixScalefEXT(IFFFJ)V
nglMatrixTranslatedEXT(IDDDJ)V
nglMatrixTranslatefEXT(IFFFJ)V
nglMultiTexBufferEXT(IIIIJ)V
nglMultiTexCoordPointerEXT(IIIIJJ)V
nglMultiTexCoordPointerEXTBO(IIIIJJ)V
nglMultiTexEnvfEXT(IIIFJ)V
nglMultiTexEnvfvEXT(IIIJJ)V
nglMultiTexEnviEXT(IIIIJ)V
nglMultiTexEnvivEXT(IIIJJ)V
nglMultiTexGendEXT(IIIDJ)V
nglMultiTexGendvEXT(IIIJJ)V
nglMultiTexGenfEXT(IIIFJ)V
nglMultiTexGenfvEXT(IIIJJ)V
nglMultiTexGeniEXT(IIIIJ)V
nglMultiTexGenivEXT(IIIJJ)V
nglMultiTexImage1DEXT(IIIIIIIIJJ)V
nglMultiTexImage1DEXTBO(IIIIIIIIJJ)V
nglMultiTexImage2DEXT(IIIIIIIIIJJ)V
nglMultiTexImage2DEXTBO(IIIIIIIIIJJ)V
nglMultiTexImage3DEXT(IIIIIIIIIIJJ)V
nglMultiTexImage3DEXTBO(IIIIIIIIIIJJ)V
nglMultiTexParameterIivEXT(IIIJJ)V
nglMultiTexParameterIuivEXT(IIIJJ)V
nglMultiTexParameterfEXT(IIIFJ)V
nglMultiTexParameterfvEXT(IIIJJ)V
nglMultiTexParameteriEXT(IIIIJ)V
nglMultiTexParameterivEXT(IIIJJ)V
nglMultiTexRenderbufferEXT(IIIJ)V
nglMultiTexSubImage1DEXT(IIIIIIIJJ)V
nglMultiTexSubImage1DEXTBO(IIIIIIIJJ)V
nglMultiTexSubImage2DEXT(IIIIIIIIIJJ)V
nglMultiTexSubImage2DEXTBO(IIIIIIIIIJJ)V
nglMultiTexSubImage3DEXT(IIIIIIIIIIIJJ)V
nglMultiTexSubImage3DEXTBO(IIIIIIIIIIIJJ)V
nglNamedBufferDataEXT(IJJIJ)V
nglNamedBufferSubDataEXT(IJJJJ)V
nglNamedCopyBufferSubDataEXT(IIJJJJ)V
nglNamedFramebufferRenderbufferEXT(IIIIJ)V
nglNamedFramebufferTexture1DEXT(IIIIIJ)V
nglNamedFramebufferTexture2DEXT(IIIIIJ)V
nglNamedFramebufferTexture3DEXT(IIIIIIJ)V
nglNamedFramebufferTextureEXT(IIIIJ)V
nglNamedFramebufferTextureFaceEXT(IIIIIJ)V
nglNamedFramebufferTextureLayerEXT(IIIIIJ)V
nglNamedProgramLocalParameter4dEXT(IIIDDDDJ)V
nglNamedProgramLocalParameter4dvEXT(IIIJJ)V
nglNamedProgramLocalParameter4fEXT(IIIFFFFJ)V
nglNamedProgramLocalParameter4fvEXT(IIIJJ)V
nglNamedProgramLocalParameterI4iEXT(IIIIIIIJ)V
nglNamedProgramLocalParameterI4ivEXT(IIIJJ)V
nglNamedProgramLocalParameterI4uiEXT(IIIIIIIJ)V
nglNamedProgramLocalParameterI4uivEXT(IIIJJ)V
nglNamedProgramLocalParameters4fvEXT(IIIIJJ)V
nglNamedProgramLocalParametersI4ivEXT(IIIIJJ)V
nglNamedProgramLocalParametersI4uivEXT(IIIIJJ)V
nglNamedProgramStringEXT(IIIIJJ)V
nglNamedRenderbufferStorageEXT(IIIIJ)V
nglNamedRenderbufferStorageMultisampleCoverageEXT(IIIIIIJ)V
nglNamedRenderbufferStorageMultisampleEXT(IIIIIJ)V
nglProgramUniform1fEXT(IIFJ)V
nglProgramUniform1fvEXT(IIIJJ)V
nglProgramUniform1iEXT(IIIJ)V
nglProgramUniform1ivEXT(IIIJJ)V
nglProgramUniform1uiEXT(IIIJ)V
nglProgramUniform1uivEXT(IIIJJ)V
nglProgramUniform2fEXT(IIFFJ)V
nglProgramUniform2fvEXT(IIIJJ)V
nglProgramUniform2iEXT(IIIIJ)V
nglProgramUniform2ivEXT(IIIJJ)V
nglProgramUniform2uiEXT(IIIIJ)V
nglProgramUniform2uivEXT(IIIJJ)V
nglProgramUniform3fEXT(IIFFFJ)V
nglProgramUniform3fvEXT(IIIJJ)V
nglProgramUniform3iEXT(IIIIIJ)V
nglProgramUniform3ivEXT(IIIJJ)V
nglProgramUniform3uiEXT(IIIIIJ)V
nglProgramUniform3uivEXT(IIIJJ)V
nglProgramUniform4fEXT(IIFFFFJ)V
nglProgramUniform4fvEXT(IIIJJ)V
nglProgramUniform4iEXT(IIIIIIJ)V
nglProgramUniform4ivEXT(IIIJJ)V
nglProgramUniform4uiEXT(IIIIIIJ)V
nglProgramUniform4uivEXT(IIIJJ)V
nglProgramUniformMatrix2fvEXT(IIIZJJ)V
nglProgramUniformMatrix2x3fvEXT(IIIZJJ)V
nglProgramUniformMatrix2x4fvEXT(IIIZJJ)V
nglProgramUniformMatrix3fvEXT(IIIZJJ)V
nglProgramUniformMatrix3x2fvEXT(IIIZJJ)V
nglProgramUniformMatrix3x4fvEXT(IIIZJJ)V
nglProgramUniformMatrix4fvEXT(IIIZJJ)V
nglProgramUniformMatrix4x2fvEXT(IIIZJJ)V
nglProgramUniformMatrix4x3fvEXT(IIIZJJ)V
nglPushClientAttribDefaultEXT(IJ)V
nglTextureBufferEXT(IIIIJ)V
nglTextureImage1DEXT(IIIIIIIIJJ)V
nglTextureImage1DEXTBO(IIIIIIIIJJ)V
nglTextureImage2DEXT(IIIIIIIIIJJ)V
nglTextureImage2DEXTBO(IIIIIIIIIJJ)V
nglTextureImage3DEXT(IIIIIIIIIIJJ)V
nglTextureImage3DEXTBO(IIIIIIIIIIJJ)V
nglTextureParameterIivEXT(IIIJJ)V
nglTextureParameterIuivEXT(IIIJJ)V
nglTextureParameterfEXT(IIIFJ)V
nglTextureParameterfvEXT(IIIJJ)V
nglTextureParameteriEXT(IIIIJ)V
nglTextureParameterivEXT(IIIJJ)V
nglTextureRenderbufferEXT(IIIJ)V
nglTextureSubImage1DEXT(IIIIIIIJJ)V
nglTextureSubImage1DEXTBO(IIIIIIIJJ)V
nglTextureSubImage2DEXT(IIIIIIIIIJJ)V
nglTextureSubImage2DEXTBO(IIIIIIIIIJJ)V
nglTextureSubImage3DEXT(IIIIIIIIIIIJJ)V
nglTextureSubImage3DEXTBO(IIIIIIIIIIIJJ)V
nglUnmapNamedBufferEXT(IJ)Z
nglVertexArrayColorOffsetEXT(IIIIIJJ)V
nglVertexArrayEdgeFlagOffsetEXT(IIIJJ)V
nglVertexArrayFogCoordOffsetEXT(IIIIJJ)V
nglVertexArrayIndexOffsetEXT(IIIIJJ)V
nglVertexArrayMultiTexCoordOffsetEXT(IIIIIIJJ)V
nglVertexArrayNormalOffsetEXT(IIIIJJ)V
nglVertexArraySecondaryColorOffsetEXT(IIIIIJJ)V
nglVertexArrayTexCoordOffsetEXT(IIIIIJJ)V
nglVertexArrayVertexAttribIOffsetEXT(IIIIIIJJ)V
nglVertexArrayVertexAttribOffsetEXT(IIIIIZIJJ)V
nglVertexArrayVertexOffsetEXT(IIIIIJJ)V
```

#### `org.lwjgl.opengl.EXTDrawBuffers2`

```text
glDisableIndexedEXT(II)V
glEnableIndexedEXT(II)V
glGetBooleanIndexedEXT(II)Z
glGetBooleanIndexedEXT(IILjava/nio/ByteBuffer;)V
glGetIntegerIndexedEXT(II)I
glGetIntegerIndexedEXT(IILjava/nio/IntBuffer;)V
glIsEnabledIndexedEXT(II)Z
nglColorMaskIndexedEXT(IZZZZJ)V
nglDisableIndexedEXT(IIJ)V
nglEnableIndexedEXT(IIJ)V
nglGetBooleanIndexedvEXT(IIJJ)V
nglGetIntegerIndexedvEXT(IIJJ)V
nglIsEnabledIndexedEXT(IIJ)Z
```

#### `org.lwjgl.opengl.EXTDrawInstanced`

```text
nglDrawArraysInstancedEXT(IIIIJ)V
nglDrawElementsInstancedEXT(IIIJIJ)V
nglDrawElementsInstancedEXTBO(IIIJIJ)V
```

#### `org.lwjgl.opengl.EXTDrawRangeElements`

```text
nglDrawRangeElementsEXT(IIIIIJJ)V
nglDrawRangeElementsEXTBO(IIIIIJJ)V
```

#### `org.lwjgl.opengl.EXTFogCoord`

```text
nglFogCoordPointerEXT(IIJJ)V
nglFogCoordPointerEXTBO(IIJJ)V
nglFogCoorddEXT(DJ)V
nglFogCoordfEXT(FJ)V
```

#### `org.lwjgl.opengl.EXTFramebufferBlit`

```text
nglBlitFramebufferEXT(IIIIIIIIIIJ)V
```

#### `org.lwjgl.opengl.EXTFramebufferMultisample`

```text
nglRenderbufferStorageMultisampleEXT(IIIIIJ)V
```

#### `org.lwjgl.opengl.EXTFramebufferMultisampleBlitScaled`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTFramebufferObject`

```text
glBindFramebufferEXT(II)V
glBindRenderbufferEXT(II)V
glCheckFramebufferStatusEXT(I)I
glDeleteFramebuffersEXT(I)V
glDeleteRenderbuffersEXT(I)V
glFramebufferRenderbufferEXT(IIII)V
glFramebufferTexture2DEXT(IIIII)V
glGenFramebuffersEXT()I
glGenRenderbuffersEXT()I
glGetFramebufferAttachmentParameteriEXT(III)I
glGetRenderbufferParameteriEXT(II)I
glIsFramebufferEXT(I)Z
glIsRenderbufferEXT(I)Z
glRenderbufferStorageEXT(IIII)V
nglBindFramebufferEXT(IIJ)V
nglBindRenderbufferEXT(IIJ)V
nglCheckFramebufferStatusEXT(IJ)I
nglDeleteFramebuffersEXT(IJJ)V
nglDeleteRenderbuffersEXT(IJJ)V
nglFramebufferRenderbufferEXT(IIIIJ)V
nglFramebufferTexture1DEXT(IIIIIJ)V
nglFramebufferTexture2DEXT(IIIIIJ)V
nglFramebufferTexture3DEXT(IIIIIIJ)V
nglGenFramebuffersEXT(IJJ)V
nglGenRenderbuffersEXT(IJJ)V
nglGenerateMipmapEXT(IJ)V
nglGetFramebufferAttachmentParameterivEXT(IIIJJ)V
nglGetRenderbufferParameterivEXT(IIJJ)V
nglIsFramebufferEXT(IJ)Z
nglIsRenderbufferEXT(IJ)Z
nglRenderbufferStorageEXT(IIIIJ)V
```

#### `org.lwjgl.opengl.EXTFramebufferSRGB`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTGeometryShader4`

```text
glFramebufferTextureEXT(IIII)V
glFramebufferTextureFaceEXT(IIIII)V
glFramebufferTextureLayerEXT(IIIII)V
nglFramebufferTextureEXT(IIIIJ)V
nglFramebufferTextureFaceEXT(IIIIIJ)V
nglFramebufferTextureLayerEXT(IIIIIJ)V
nglProgramParameteriEXT(IIIJ)V
```

#### `org.lwjgl.opengl.EXTGpuProgramParameters`

```text
nglProgramEnvParameters4fvEXT(IIIJJ)V
nglProgramLocalParameters4fvEXT(IIIJJ)V
```

#### `org.lwjgl.opengl.EXTGpuShader4`

```text
nglBindFragDataLocationEXT(IIJJ)V
nglGetFragDataLocationEXT(IJJ)I
nglGetUniformuivEXT(IIJJ)V
nglGetVertexAttribIivEXT(IIJJ)V
nglGetVertexAttribIuivEXT(IIJJ)V
nglUniform1uiEXT(IIJ)V
nglUniform1uivEXT(IIJJ)V
nglUniform2uiEXT(IIIJ)V
nglUniform2uivEXT(IIJJ)V
nglUniform3uiEXT(IIIIJ)V
nglUniform3uivEXT(IIJJ)V
nglUniform4uiEXT(IIIIIJ)V
nglUniform4uivEXT(IIJJ)V
nglVertexAttribI1iEXT(IIJ)V
nglVertexAttribI1ivEXT(IJJ)V
nglVertexAttribI1uiEXT(IIJ)V
nglVertexAttribI1uivEXT(IJJ)V
nglVertexAttribI2iEXT(IIIJ)V
nglVertexAttribI2ivEXT(IJJ)V
nglVertexAttribI2uiEXT(IIIJ)V
nglVertexAttribI2uivEXT(IJJ)V
nglVertexAttribI3iEXT(IIIIJ)V
nglVertexAttribI3ivEXT(IJJ)V
nglVertexAttribI3uiEXT(IIIIJ)V
nglVertexAttribI3uivEXT(IJJ)V
nglVertexAttribI4bvEXT(IJJ)V
nglVertexAttribI4iEXT(IIIIIJ)V
nglVertexAttribI4ivEXT(IJJ)V
nglVertexAttribI4svEXT(IJJ)V
nglVertexAttribI4ubvEXT(IJJ)V
nglVertexAttribI4uiEXT(IIIIIJ)V
nglVertexAttribI4uivEXT(IJJ)V
nglVertexAttribI4usvEXT(IJJ)V
nglVertexAttribIPointerEXT(IIIIJJ)V
nglVertexAttribIPointerEXTBO(IIIIJJ)V
```

#### `org.lwjgl.opengl.EXTMultiDrawArrays`

```text
nglMultiDrawArraysEXT(IJJIJ)V
```

#### `org.lwjgl.opengl.EXTPackedDepthStencil`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTPackedFloat`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTPackedPixels`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTPalettedTexture`

```text
nglColorSubTableEXT(IIIIIJJ)V
nglColorTableEXT(IIIIIJJ)V
nglGetColorTableEXT(IIIJJ)V
nglGetColorTableParameterfvEXT(IIJJ)V
nglGetColorTableParameterivEXT(IIJJ)V
```

#### `org.lwjgl.opengl.EXTPixelBufferObject`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTPointParameters`

```text
nglPointParameterfEXT(IFJ)V
nglPointParameterfvEXT(IJJ)V
```

#### `org.lwjgl.opengl.EXTProvokingVertex`

```text
nglProvokingVertexEXT(IJ)V
```

#### `org.lwjgl.opengl.EXTRescaleNormal`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTSecondaryColor`

```text
nglSecondaryColor3bEXT(BBBJ)V
nglSecondaryColor3dEXT(DDDJ)V
nglSecondaryColor3fEXT(FFFJ)V
nglSecondaryColor3ubEXT(BBBJ)V
nglSecondaryColorPointerEXT(IIIJJ)V
nglSecondaryColorPointerEXTBO(IIIJJ)V
```

#### `org.lwjgl.opengl.EXTSeparateShaderObjects`

```text
nglActiveProgramEXT(IJ)V
nglCreateShaderProgramEXT(IJJ)I
nglUseShaderProgramEXT(IIJ)V
```

#### `org.lwjgl.opengl.EXTSeparateSpecularColor`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTShaderImageLoadStore`

```text
nglBindImageTextureEXT(IIIZIIIJ)V
nglMemoryBarrierEXT(IJ)V
```

#### `org.lwjgl.opengl.EXTSharedTexturePalette`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTStencilClearTag`

```text
nglStencilClearTagEXT(IIJ)V
```

#### `org.lwjgl.opengl.EXTStencilTwoSide`

```text
nglActiveStencilFaceEXT(IJ)V
```

#### `org.lwjgl.opengl.EXTStencilWrap`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureArray`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureBufferObject`

```text
nglTexBufferEXT(IIIJ)V
```

#### `org.lwjgl.opengl.EXTTextureCompressionLATC`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureCompressionRGTC`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureCompressionS3TC`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureEnvCombine`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureEnvDot3`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureFilterAnisotropic`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureInteger`

```text
nglClearColorIiEXT(IIIIJ)V
nglClearColorIuiEXT(IIIIJ)V
nglGetTexParameterIivEXT(IIJJ)V
nglGetTexParameterIuivEXT(IIJJ)V
nglTexParameterIivEXT(IIJJ)V
nglTexParameterIuivEXT(IIJJ)V
```

#### `org.lwjgl.opengl.EXTTextureLODBias`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureMirrorClamp`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureRectangle`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureSRGB`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureSRGBDecode`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureSharedExponent`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureSnorm`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTextureSwizzle`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTTimerQuery`

```text
nglGetQueryObjecti64vEXT(IIJJ)V
nglGetQueryObjectui64vEXT(IIJJ)V
```

#### `org.lwjgl.opengl.EXTTransformFeedback`

```text
nglBeginTransformFeedbackEXT(IJ)V
nglBindBufferBaseEXT(IIIJ)V
nglBindBufferOffsetEXT(IIIJJ)V
nglBindBufferRangeEXT(IIIJJJ)V
nglEndTransformFeedbackEXT(J)V
nglGetTransformFeedbackVaryingEXT(IIIJJJJJ)V
nglTransformFeedbackVaryingsEXT(IIJIJ)V
```

#### `org.lwjgl.opengl.EXTVertexArrayBgra`

Identical byte redundancy.

#### `org.lwjgl.opengl.EXTVertexAttrib64bit`

```text
nglGetVertexAttribLdvEXT(IIJJ)V
nglVertexAttribL1dEXT(IDJ)V
nglVertexAttribL1dvEXT(IJJ)V
nglVertexAttribL2dEXT(IDDJ)V
nglVertexAttribL2dvEXT(IJJ)V
nglVertexAttribL3dEXT(IDDDJ)V
nglVertexAttribL3dvEXT(IJJ)V
nglVertexAttribL4dEXT(IDDDDJ)V
nglVertexAttribL4dvEXT(IJJ)V
nglVertexAttribLPointerEXT(IIIIJJ)V
nglVertexAttribLPointerEXTBO(IIIIJJ)V
```

#### `org.lwjgl.opengl.EXTVertexShader`

```text
nglBeginVertexShaderEXT(J)V
nglBindLightParameterEXT(IIJ)I
nglBindMaterialParameterEXT(IIJ)I
nglBindParameterEXT(IJ)I
nglBindTexGenParameterEXT(IIIJ)I
nglBindTextureUnitParameterEXT(IIJ)I
nglBindVertexShaderEXT(IJ)V
nglDeleteVertexShaderEXT(IJ)V
nglDisableVariantClientStateEXT(IJ)V
nglEnableVariantClientStateEXT(IJ)V
nglEndVertexShaderEXT(J)V
nglExtractComponentEXT(IIIJ)V
nglGenSymbolsEXT(IIIIJ)I
nglGenVertexShadersEXT(IJ)I
nglGetInvariantBooleanvEXT(IIJJ)V
nglGetInvariantFloatvEXT(IIJJ)V
nglGetInvariantIntegervEXT(IIJJ)V
nglGetLocalConstantBooleanvEXT(IIJJ)V
nglGetLocalConstantFloatvEXT(IIJJ)V
nglGetLocalConstantIntegervEXT(IIJJ)V
nglGetVariantBooleanvEXT(IIJJ)V
nglGetVariantFloatvEXT(IIJJ)V
nglGetVariantIntegervEXT(IIJJ)V
nglGetVariantPointervEXT(IIJJ)Ljava/nio/ByteBuffer;
nglInsertComponentEXT(IIIJ)V
nglIsVariantEnabledEXT(IIJ)Z
nglSetInvariantEXT(IIJJ)V
nglSetLocalConstantEXT(IIJJ)V
nglShaderOp1EXT(IIIJ)V
nglShaderOp2EXT(IIIIJ)V
nglShaderOp3EXT(IIIIIJ)V
nglSwizzleEXT(IIIIIIJ)V
nglVariantPointerEXT(IIIJJ)V
nglVariantPointerEXTBO(IIIJJ)V
nglVariantbvEXT(IJJ)V
nglVariantdvEXT(IJJ)V
nglVariantfvEXT(IJJ)V
nglVariantivEXT(IJJ)V
nglVariantsvEXT(IJJ)V
nglVariantubvEXT(IJJ)V
nglVariantuivEXT(IJJ)V
nglVariantusvEXT(IJJ)V
nglWriteMaskEXT(IIIIIIJ)V
```

#### `org.lwjgl.opengl.EXTVertexWeighting`

```text
nglVertexWeightPointerEXT(IIIJJ)V
nglVertexWeightPointerEXTBO(IIIJJ)V
nglVertexWeightfEXT(FJ)V
```

#### `org.lwjgl.opengl.EventQueue`

```text
<init>(I)V
clearEvents()V
copyEvents(Ljava/nio/ByteBuffer;)V
event_sizeI
putEvent(Ljava/nio/ByteBuffer;)Z
queueLjava/nio/ByteBuffer;
```

#### `org.lwjgl.opengl.FastIntMap$Entry`

```text
<init>(ILjava/lang/Object;Lorg/lwjgl/opengl/FastIntMap$Entry;)V
keyI
nextLorg/lwjgl/opengl/FastIntMap$Entry;
valueLjava/lang/Object;
```

#### `org.lwjgl.opengl.FastIntMap$EntryIterator`

```text
<init>(Lorg/lwjgl/opengl/FastIntMap;)V
currentLorg/lwjgl/opengl/FastIntMap$Entry;
next()Lorg/lwjgl/opengl/FastIntMap$Entry;
nextIndexI
reset()V
this$0Lorg/lwjgl/opengl/FastIntMap;
```

#### `org.lwjgl.opengl.FastIntMap`

```text
<init>()V
<init>(IF)V
access$000(Lorg/lwjgl/opengl/FastIntMap;)[Lorg/lwjgl/opengl/FastIntMap$Entry;
capacityI
containsKey(I)Z
get(I)Ljava/lang/Object;
index(I)I
index(II)I
iterator()Lorg/lwjgl/opengl/FastIntMap$EntryIterator;
maskI
put(ILjava/lang/Object;)Ljava/lang/Object;
rehash([Lorg/lwjgl/opengl/FastIntMap$Entry;)V
remove(I)Ljava/lang/Object;
sizeI
table[Lorg/lwjgl/opengl/FastIntMap$Entry;
thresholdI
```

#### `org.lwjgl.opengl.GL11`

```text
glAlphaFunc(IF)V
glBegin(I)V
glBindTexture(II)V
glBlendFunc(II)V
glCallList(I)V
glCallLists(Ljava/nio/IntBuffer;)V
glClear(I)V
glClearColor(FFFF)V
glClearDepth(D)V
glColor3f(FFF)V
glColor4d(DDDD)V
glColor4f(FFFF)V
glColor4ub(BBBB)V
glColorMask(ZZZZ)V
glColorMaterial(II)V
glColorPointer(IIIJ)V
glColorPointer(IIILjava/nio/ByteBuffer;)V
glCopyTexSubImage2D(IIIIIIII)V
glCullFace(I)V
glDeleteLists(II)V
glDeleteTextures(I)V
glDepthFunc(I)V
glDepthMask(Z)V
glDisable(I)V
glDisableClientState(I)V
glDrawArrays(III)V
glDrawBuffer(I)V
glEnable(I)V
glEnableClientState(I)V
glEnd()V
glEndList()V
glFinish()V
glFlush()V
glFog(ILjava/nio/FloatBuffer;)V
glFogf(IF)V
glFogi(II)V
glGenLists(I)I
glGenTextures()I
glGenTextures(Ljava/nio/IntBuffer;)V
glGetBoolean(I)Z
glGetBoolean(ILjava/nio/ByteBuffer;)V
glGetError()I
glGetFloat(I)F
glGetFloat(ILjava/nio/FloatBuffer;)V
glGetInteger(I)I
glGetInteger(ILjava/nio/IntBuffer;)V
glGetString(I)Ljava/lang/String;
glGetTexImage(IIIILjava/nio/IntBuffer;)V
glGetTexLevelParameteri(III)I
glHint(II)V
glIsEnabled(I)Z
glIsTexture(I)Z
glLight(IILjava/nio/FloatBuffer;)V
glLightModel(ILjava/nio/FloatBuffer;)V
glLineWidth(F)V
glLoadIdentity()V
glLoadMatrix(Ljava/nio/FloatBuffer;)V
glLogicOp(I)V
glMatrixMode(I)V
glMultMatrix(Ljava/nio/FloatBuffer;)V
glNewList(II)V
glNormal3f(FFF)V
glNormalPointer(IIJ)V
glNormalPointer(IILjava/nio/ByteBuffer;)V
glOrtho(DDDDDD)V
glPixelStorei(II)V
glPointSize(F)V
glPolygonOffset(FF)V
glPopAttrib()V
glPopMatrix()V
glPushAttrib(I)V
glPushMatrix()V
glReadBuffer(I)V
glReadPixels(IIIIIILjava/nio/FloatBuffer;)V
glReadPixels(IIIIIILjava/nio/IntBuffer;)V
glRotated(DDDD)V
glRotatef(FFFF)V
glScaled(DDD)V
glScalef(FFF)V
glScissor(IIII)V
glShadeModel(I)V
glStencilFunc(III)V
glStencilOp(III)V
glTexCoord2f(FF)V
glTexCoordPointer(IIIJ)V
glTexCoordPointer(IIILjava/nio/ByteBuffer;)V
glTexEnv(IILjava/nio/FloatBuffer;)V
glTexEnvi(III)V
glTexGen(IILjava/nio/FloatBuffer;)V
glTexGeni(III)V
glTexImage1D(IIIIIIILjava/nio/ByteBuffer;)V
glTexImage2D(IIIIIIIILjava/nio/ByteBuffer;)V
glTexImage2D(IIIIIIIILjava/nio/FloatBuffer;)V
glTexImage2D(IIIIIIIILjava/nio/IntBuffer;)V
glTexParameterf(IIF)V
glTexParameteri(III)V
glTexSubImage2D(IIIIIIIILjava/nio/ByteBuffer;)V
glTexSubImage2D(IIIIIIIILjava/nio/IntBuffer;)V
glTranslated(DDD)V
glTranslatef(FFF)V
glVertex2d(DD)V
glVertex2f(FF)V
glVertex3d(DDD)V
glVertex3f(FFF)V
glVertexPointer(IIIJ)V
glVertexPointer(IIILjava/nio/ByteBuffer;)V
glViewport(IIII)V
nglAccum(IFJ)V
nglAlphaFunc(IFJ)V
nglAreTexturesResident(IJJJ)Z
nglArrayElement(IJ)V
nglBegin(IJ)V
nglBindTexture(IIJ)V
nglBitmap(IIFFFFJJ)V
nglBitmapBO(IIFFFFJJ)V
nglBlendFunc(IIJ)V
nglCallList(IJ)V
nglCallLists(IIJJ)V
nglClear(IJ)V
nglClearAccum(FFFFJ)V
nglClearColor(FFFFJ)V
nglClearDepth(DJ)V
nglClearStencil(IJ)V
nglClipPlane(IJJ)V
nglColor3b(BBBJ)V
nglColor3d(DDDJ)V
nglColor3f(FFFJ)V
nglColor3ub(BBBJ)V
nglColor4b(BBBBJ)V
nglColor4d(DDDDJ)V
nglColor4f(FFFFJ)V
nglColor4ub(BBBBJ)V
nglColorMask(ZZZZJ)V
nglColorMaterial(IIJ)V
nglColorPointer(IIIJJ)V
nglColorPointerBO(IIIJJ)V
nglCopyPixels(IIIIIJ)V
nglCopyTexImage1D(IIIIIIIJ)V
nglCopyTexImage2D(IIIIIIIIJ)V
nglCopyTexSubImage1D(IIIIIIJ)V
nglCopyTexSubImage2D(IIIIIIIIJ)V
nglCullFace(IJ)V
nglDeleteLists(IIJ)V
nglDeleteTextures(IJJ)V
nglDepthFunc(IJ)V
nglDepthMask(ZJ)V
nglDepthRange(DDJ)V
nglDisable(IJ)V
nglDisableClientState(IJ)V
nglDrawArrays(IIIJ)V
nglDrawBuffer(IJ)V
nglDrawElements(IIIJJ)V
nglDrawElementsBO(IIIJJ)V
nglDrawPixels(IIIIJJ)V
nglDrawPixelsBO(IIIIJJ)V
nglEdgeFlag(ZJ)V
nglEdgeFlagPointer(IJJ)V
nglEdgeFlagPointerBO(IJJ)V
nglEnable(IJ)V
nglEnableClientState(IJ)V
nglEnd(J)V
nglEndList(J)V
nglEvalCoord1d(DJ)V
nglEvalCoord1f(FJ)V
nglEvalCoord2d(DDJ)V
nglEvalCoord2f(FFJ)V
nglEvalMesh1(IIIJ)V
nglEvalMesh2(IIIIIJ)V
nglEvalPoint1(IJ)V
nglEvalPoint2(IIJ)V
nglFeedbackBuffer(IIJJ)V
nglFinish(J)V
nglFlush(J)V
nglFogf(IFJ)V
nglFogfv(IJJ)V
nglFogi(IIJ)V
nglFogiv(IJJ)V
nglFrontFace(IJ)V
nglFrustum(DDDDDDJ)V
nglGenLists(IJ)I
nglGenTextures(IJJ)V
nglGetBooleanv(IJJ)V
nglGetClipPlane(IJJ)V
nglGetDoublev(IJJ)V
nglGetError(J)I
nglGetFloatv(IJJ)V
nglGetIntegerv(IJJ)V
nglGetLightfv(IIJJ)V
nglGetLightiv(IIJJ)V
nglGetMapdv(IIJJ)V
nglGetMapfv(IIJJ)V
nglGetMapiv(IIJJ)V
nglGetMaterialfv(IIJJ)V
nglGetMaterialiv(IIJJ)V
nglGetPixelMapfv(IJJ)V
nglGetPixelMapfvBO(IJJ)V
nglGetPixelMapuiv(IJJ)V
nglGetPixelMapuivBO(IJJ)V
nglGetPixelMapusv(IJJ)V
nglGetPixelMapusvBO(IJJ)V
nglGetPointerv(IJJ)Ljava/nio/ByteBuffer;
nglGetPolygonStipple(JJ)V
nglGetPolygonStippleBO(JJ)V
nglGetString(IJ)Ljava/lang/String;
nglGetTexEnvfv(IIJJ)V
nglGetTexEnviv(IIJJ)V
nglGetTexGendv(IIJJ)V
nglGetTexGenfv(IIJJ)V
nglGetTexGeniv(IIJJ)V
nglGetTexImage(IIIIJJ)V
nglGetTexImageBO(IIIIJJ)V
nglGetTexLevelParameterfv(IIIJJ)V
nglGetTexLevelParameteriv(IIIJJ)V
nglGetTexParameterfv(IIJJ)V
nglGetTexParameteriv(IIJJ)V
nglHint(IIJ)V
nglInitNames(J)V
nglInterleavedArrays(IIJJ)V
nglInterleavedArraysBO(IIJJ)V
nglIsEnabled(IJ)Z
nglIsList(IJ)Z
nglIsTexture(IJ)Z
nglLightModelf(IFJ)V
nglLightModelfv(IJJ)V
nglLightModeli(IIJ)V
nglLightModeliv(IJJ)V
nglLightf(IIFJ)V
nglLightfv(IIJJ)V
nglLighti(IIIJ)V
nglLightiv(IIJJ)V
nglLineStipple(ISJ)V
nglLineWidth(FJ)V
nglListBase(IJ)V
nglLoadIdentity(J)V
nglLoadMatrixd(JJ)V
nglLoadMatrixf(JJ)V
nglLoadName(IJ)V
nglLogicOp(IJ)V
nglMap1d(IDDIIJJ)V
nglMap1f(IFFIIJJ)V
nglMap2d(IDDIIDDIIJJ)V
nglMap2f(IFFIIFFIIJJ)V
nglMapGrid1d(IDDJ)V
nglMapGrid1f(IFFJ)V
nglMapGrid2d(IDDIDDJ)V
nglMapGrid2f(IFFIFFJ)V
nglMaterialf(IIFJ)V
nglMaterialfv(IIJJ)V
nglMateriali(IIIJ)V
nglMaterialiv(IIJJ)V
nglMatrixMode(IJ)V
nglMultMatrixd(JJ)V
nglMultMatrixf(JJ)V
nglNewList(IIJ)V
nglNormal3b(BBBJ)V
nglNormal3d(DDDJ)V
nglNormal3f(FFFJ)V
nglNormal3i(IIIJ)V
nglNormalPointer(IIJJ)V
nglNormalPointerBO(IIJJ)V
nglOrtho(DDDDDDJ)V
nglPassThrough(FJ)V
nglPixelMapfv(IIJJ)V
nglPixelMapfvBO(IIJJ)V
nglPixelMapuiv(IIJJ)V
nglPixelMapuivBO(IIJJ)V
nglPixelMapusv(IIJJ)V
nglPixelMapusvBO(IIJJ)V
nglPixelStoref(IFJ)V
nglPixelStorei(IIJ)V
nglPixelTransferf(IFJ)V
nglPixelTransferi(IIJ)V
nglPixelZoom(FFJ)V
nglPointSize(FJ)V
nglPolygonMode(IIJ)V
nglPolygonOffset(FFJ)V
nglPolygonStipple(JJ)V
nglPolygonStippleBO(JJ)V
nglPopAttrib(J)V
nglPopClientAttrib(J)V
nglPopMatrix(J)V
nglPopName(J)V
nglPrioritizeTextures(IJJJ)V
nglPushAttrib(IJ)V
nglPushClientAttrib(IJ)V
nglPushMatrix(J)V
nglPushName(IJ)V
nglRasterPos2d(DDJ)V
nglRasterPos2f(FFJ)V
nglRasterPos2i(IIJ)V
nglRasterPos3d(DDDJ)V
nglRasterPos3f(FFFJ)V
nglRasterPos3i(IIIJ)V
nglRasterPos4d(DDDDJ)V
nglRasterPos4f(FFFFJ)V
nglRasterPos4i(IIIIJ)V
nglReadBuffer(IJ)V
nglReadPixels(IIIIIIJJ)V
nglReadPixelsBO(IIIIIIJJ)V
nglRectd(DDDDJ)V
nglRectf(FFFFJ)V
nglRecti(IIIIJ)V
nglRenderMode(IJ)I
nglRotated(DDDDJ)V
nglRotatef(FFFFJ)V
nglScaled(DDDJ)V
nglScalef(FFFJ)V
nglScissor(IIIIJ)V
nglSelectBuffer(IJJ)V
nglShadeModel(IJ)V
nglStencilFunc(IIIJ)V
nglStencilMask(IJ)V
nglStencilOp(IIIJ)V
nglTexCoord1d(DJ)V
nglTexCoord1f(FJ)V
nglTexCoord2d(DDJ)V
nglTexCoord2f(FFJ)V
nglTexCoord3d(DDDJ)V
nglTexCoord3f(FFFJ)V
nglTexCoord4d(DDDDJ)V
nglTexCoord4f(FFFFJ)V
nglTexCoordPointer(IIIJJ)V
nglTexCoordPointerBO(IIIJJ)V
nglTexEnvf(IIFJ)V
nglTexEnvfv(IIJJ)V
nglTexEnvi(IIIJ)V
nglTexEnviv(IIJJ)V
nglTexGend(IIDJ)V
nglTexGendv(IIJJ)V
nglTexGenf(IIFJ)V
nglTexGenfv(IIJJ)V
nglTexGeni(IIIJ)V
nglTexGeniv(IIJJ)V
nglTexImage1D(IIIIIIIJJ)V
nglTexImage1DBO(IIIIIIIJJ)V
nglTexImage2D(IIIIIIIIJJ)V
nglTexImage2DBO(IIIIIIIIJJ)V
nglTexParameterf(IIFJ)V
nglTexParameterfv(IIJJ)V
nglTexParameteri(IIIJ)V
nglTexParameteriv(IIJJ)V
nglTexSubImage1D(IIIIIIJJ)V
nglTexSubImage1DBO(IIIIIIJJ)V
nglTexSubImage2D(IIIIIIIIJJ)V
nglTexSubImage2DBO(IIIIIIIIJJ)V
nglTranslated(DDDJ)V
nglTranslatef(FFFJ)V
nglVertex2d(DDJ)V
nglVertex2f(FFJ)V
nglVertex2i(IIJ)V
nglVertex3d(DDDJ)V
nglVertex3f(FFFJ)V
nglVertex3i(IIIJ)V
nglVertex4d(DDDDJ)V
nglVertex4f(FFFFJ)V
nglVertex4i(IIIIJ)V
nglVertexPointer(IIIJJ)V
nglVertexPointerBO(IIIJJ)V
nglViewport(IIIIJ)V
```

#### `org.lwjgl.opengl.GL12`

```text
glTexImage3D(IIIIIIIIILjava/nio/ByteBuffer;)V
nglCopyTexSubImage3D(IIIIIIIIIJ)V
nglDrawRangeElements(IIIIIJJ)V
nglDrawRangeElementsBO(IIIIIJJ)V
nglTexImage3D(IIIIIIIIIJJ)V
nglTexImage3DBO(IIIIIIIIIJJ)V
nglTexSubImage3D(IIIIIIIIIIJJ)V
nglTexSubImage3DBO(IIIIIIIIIIJJ)V
```

#### `org.lwjgl.opengl.GL13`

```text
glActiveTexture(I)V
glClientActiveTexture(I)V
glMultiTexCoord2f(IFF)V
nglActiveTexture(IJ)V
nglClientActiveTexture(IJ)V
nglCompressedTexImage1D(IIIIIIJJ)V
nglCompressedTexImage1DBO(IIIIIIJJ)V
nglCompressedTexImage2D(IIIIIIIJJ)V
nglCompressedTexImage2DBO(IIIIIIIJJ)V
nglCompressedTexImage3D(IIIIIIIIJJ)V
nglCompressedTexImage3DBO(IIIIIIIIJJ)V
nglCompressedTexSubImage1D(IIIIIIJJ)V
nglCompressedTexSubImage1DBO(IIIIIIJJ)V
nglCompressedTexSubImage2D(IIIIIIIIJJ)V
nglCompressedTexSubImage2DBO(IIIIIIIIJJ)V
nglCompressedTexSubImage3D(IIIIIIIIIIJJ)V
nglCompressedTexSubImage3DBO(IIIIIIIIIIJJ)V
nglGetCompressedTexImage(IIJJ)V
nglGetCompressedTexImageBO(IIJJ)V
nglLoadTransposeMatrixd(JJ)V
nglLoadTransposeMatrixf(JJ)V
nglMultTransposeMatrixd(JJ)V
nglMultTransposeMatrixf(JJ)V
nglMultiTexCoord1d(IDJ)V
nglMultiTexCoord1f(IFJ)V
nglMultiTexCoord2d(IDDJ)V
nglMultiTexCoord2f(IFFJ)V
nglMultiTexCoord3d(IDDDJ)V
nglMultiTexCoord3f(IFFFJ)V
nglMultiTexCoord4d(IDDDDJ)V
nglMultiTexCoord4f(IFFFFJ)V
nglSampleCoverage(FZJ)V
```

#### `org.lwjgl.opengl.GL14`

```text
glBlendColor(FFFF)V
glBlendEquation(I)V
glBlendFuncSeparate(IIII)V
glMultiDrawArrays(ILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)V
nglBlendColor(FFFFJ)V
nglBlendEquation(IJ)V
nglBlendFuncSeparate(IIIIJ)V
nglFogCoordPointer(IIJJ)V
nglFogCoordPointerBO(IIJJ)V
nglFogCoordd(DJ)V
nglFogCoordf(FJ)V
nglMultiDrawArrays(IJJIJ)V
nglPointParameterf(IFJ)V
nglPointParameterfv(IJJ)V
nglPointParameteri(IIJ)V
nglPointParameteriv(IJJ)V
nglSecondaryColor3b(BBBJ)V
nglSecondaryColor3d(DDDJ)V
nglSecondaryColor3f(FFFJ)V
nglSecondaryColor3ub(BBBJ)V
nglSecondaryColorPointer(IIIJJ)V
nglSecondaryColorPointerBO(IIIJJ)V
nglWindowPos2d(DDJ)V
nglWindowPos2f(FFJ)V
nglWindowPos2i(IIJ)V
nglWindowPos3d(DDDJ)V
nglWindowPos3f(FFFJ)V
nglWindowPos3i(IIIJ)V
```

#### `org.lwjgl.opengl.GL15`

```text
glBindBuffer(II)V
glBufferData(IJI)V
glBufferData(ILjava/nio/ByteBuffer;I)V
glBufferSubData(IJLjava/nio/ByteBuffer;)V
glDeleteBuffers(I)V
glGenBuffers()I
glGetBufferParameteri(II)I
glGetQueryi(II)I
nglBeginQuery(IIJ)V
nglBindBuffer(IIJ)V
nglBufferData(IJJIJ)V
nglBufferSubData(IJJJJ)V
nglDeleteBuffers(IJJ)V
nglDeleteQueries(IJJ)V
nglEndQuery(IJ)V
nglGenBuffers(IJJ)V
nglGenQueries(IJJ)V
nglGetBufferParameteriv(IIJJ)V
nglGetBufferPointerv(IIJJ)Ljava/nio/ByteBuffer;
nglGetBufferSubData(IJJJJ)V
nglGetQueryObjectiv(IIJJ)V
nglGetQueryObjectuiv(IIJJ)V
nglGetQueryiv(IIJJ)V
nglIsBuffer(IJ)Z
nglIsQuery(IJ)Z
nglMapBuffer(IIJLjava/nio/ByteBuffer;J)Ljava/nio/ByteBuffer;
nglUnmapBuffer(IJ)Z
```

#### `org.lwjgl.opengl.GL20`

```text
glAttachShader(II)V
glCompileShader(I)V
glCreateProgram()I
glCreateShader(I)I
glDeleteProgram(I)V
glDeleteShader(I)V
glDetachShader(II)V
glDisableVertexAttribArray(I)V
glDrawBuffers(I)V
glDrawBuffers(Ljava/nio/IntBuffer;)V
glEnableVertexAttribArray(I)V
glGetAttribLocation(ILjava/lang/CharSequence;)I
glGetProgramInfoLog(II)Ljava/lang/String;
glGetProgrami(II)I
glGetShaderInfoLog(II)Ljava/lang/String;
glGetShaderi(II)I
glGetUniformLocation(ILjava/lang/CharSequence;)I
glIsProgram(I)Z
glLinkProgram(I)V
glShaderSource(ILjava/lang/CharSequence;)V
glShaderSource(ILjava/nio/ByteBuffer;)V
glUniform1(ILjava/nio/FloatBuffer;)V
glUniform1(ILjava/nio/IntBuffer;)V
glUniform1f(IF)V
glUniform1i(II)V
glUniform2(ILjava/nio/FloatBuffer;)V
glUniform2(ILjava/nio/IntBuffer;)V
glUniform2f(IFF)V
glUniform2i(III)V
glUniform3(ILjava/nio/FloatBuffer;)V
glUniform3(ILjava/nio/IntBuffer;)V
glUniform3f(IFFF)V
glUniform4(ILjava/nio/FloatBuffer;)V
glUniform4(ILjava/nio/IntBuffer;)V
glUniform4f(IFFFF)V
glUniformMatrix2(IZLjava/nio/FloatBuffer;)V
glUniformMatrix3(IZLjava/nio/FloatBuffer;)V
glUniformMatrix4(IZLjava/nio/FloatBuffer;)V
glUseProgram(I)V
glVertexAttribPointer(IIIZIJ)V
glVertexAttribPointer(IIIZILjava/nio/ByteBuffer;)V
nglAttachShader(IIJ)V
nglBindAttribLocation(IIJJ)V
nglBlendEquationSeparate(IIJ)V
nglCompileShader(IJ)V
nglCreateProgram(J)I
nglCreateShader(IJ)I
nglDeleteProgram(IJ)V
nglDeleteShader(IJ)V
nglDetachShader(IIJ)V
nglDisableVertexAttribArray(IJ)V
nglDrawBuffers(IJJ)V
nglEnableVertexAttribArray(IJ)V
nglGetActiveAttrib(IIIJJJJJ)V
nglGetActiveUniform(IIIJJJJJ)V
nglGetAttachedShaders(IIJJJ)V
nglGetAttribLocation(IJJ)I
nglGetProgramInfoLog(IIJJJ)V
nglGetProgramiv(IIJJ)V
nglGetShaderInfoLog(IIJJJ)V
nglGetShaderSource(IIJJJ)V
nglGetShaderiv(IIJJ)V
nglGetUniformLocation(IJJ)I
nglGetUniformfv(IIJJ)V
nglGetUniformiv(IIJJ)V
nglGetVertexAttribPointerv(IIJJ)Ljava/nio/ByteBuffer;
nglGetVertexAttribPointerv2(IIJJ)V
nglGetVertexAttribdv(IIJJ)V
nglGetVertexAttribfv(IIJJ)V
nglGetVertexAttribiv(IIJJ)V
nglIsProgram(IJ)Z
nglIsShader(IJ)Z
nglLinkProgram(IJ)V
nglShaderSource(IIJIJ)V
nglShaderSource3(IIJJJ)V
nglStencilFuncSeparate(IIIIJ)V
nglStencilMaskSeparate(IIJ)V
nglStencilOpSeparate(IIIIJ)V
nglUniform1f(IFJ)V
nglUniform1fv(IIJJ)V
nglUniform1i(IIJ)V
nglUniform1iv(IIJJ)V
nglUniform2f(IFFJ)V
nglUniform2fv(IIJJ)V
nglUniform2i(IIIJ)V
nglUniform2iv(IIJJ)V
nglUniform3f(IFFFJ)V
nglUniform3fv(IIJJ)V
nglUniform3i(IIIIJ)V
nglUniform3iv(IIJJ)V
nglUniform4f(IFFFFJ)V
nglUniform4fv(IIJJ)V
nglUniform4i(IIIIIJ)V
nglUniform4iv(IIJJ)V
nglUniformMatrix2fv(IIZJJ)V
nglUniformMatrix3fv(IIZJJ)V
nglUniformMatrix4fv(IIZJJ)V
nglUseProgram(IJ)V
nglValidateProgram(IJ)V
nglVertexAttrib1d(IDJ)V
nglVertexAttrib1f(IFJ)V
nglVertexAttrib1s(ISJ)V
nglVertexAttrib2d(IDDJ)V
nglVertexAttrib2f(IFFJ)V
nglVertexAttrib2s(ISSJ)V
nglVertexAttrib3d(IDDDJ)V
nglVertexAttrib3f(IFFFJ)V
nglVertexAttrib3s(ISSSJ)V
nglVertexAttrib4Nub(IBBBBJ)V
nglVertexAttrib4d(IDDDDJ)V
nglVertexAttrib4f(IFFFFJ)V
nglVertexAttrib4s(ISSSSJ)V
nglVertexAttribPointer(IIIZIJJ)V
nglVertexAttribPointerBO(IIIZIJJ)V
```

#### `org.lwjgl.opengl.GL21`

```text
nglUniformMatrix2x3fv(IIZJJ)V
nglUniformMatrix2x4fv(IIZJJ)V
nglUniformMatrix3x2fv(IIZJJ)V
nglUniformMatrix3x4fv(IIZJJ)V
nglUniformMatrix4x2fv(IIZJJ)V
nglUniformMatrix4x3fv(IIZJJ)V
```

#### `org.lwjgl.opengl.GL30`

```text
glBindBufferBase(III)V
glBindBufferRange(IIIJJ)V
glBindFramebuffer(II)V
glBindRenderbuffer(II)V
glBindVertexArray(I)V
glBlitFramebuffer(IIIIIIIIII)V
glCheckFramebufferStatus(I)I
glDeleteFramebuffers(I)V
glDeleteFramebuffers(Ljava/nio/IntBuffer;)V
glDeleteRenderbuffers(I)V
glDeleteRenderbuffers(Ljava/nio/IntBuffer;)V
glDeleteVertexArrays(I)V
glDeleteVertexArrays(Ljava/nio/IntBuffer;)V
glFlushMappedBufferRange(IJJ)V
glFramebufferRenderbuffer(IIII)V
glFramebufferTexture1D(IIIII)V
glFramebufferTexture2D(IIIII)V
glFramebufferTexture3D(IIIIII)V
glFramebufferTextureLayer(IIIII)V
glGenFramebuffers()I
glGenFramebuffers(Ljava/nio/IntBuffer;)V
glGenRenderbuffers()I
glGenRenderbuffers(Ljava/nio/IntBuffer;)V
glGenVertexArrays()I
glGenVertexArrays(Ljava/nio/IntBuffer;)V
glGenerateMipmap(I)V
glGetFramebufferAttachmentParameter(IIILjava/nio/IntBuffer;)V
glGetFramebufferAttachmentParameteri(III)I
glGetInteger(II)I
glGetInteger(IILjava/nio/IntBuffer;)V
glGetRenderbufferParameter(IILjava/nio/IntBuffer;)V
glGetRenderbufferParameteri(II)I
glGetStringi(II)Ljava/lang/String;
glIsFramebuffer(I)Z
glIsRenderbuffer(I)Z
glIsVertexArray(I)Z
glMapBufferRange(IJJILjava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
glRenderbufferStorage(IIII)V
glRenderbufferStorageMultisample(IIIII)V
nglBeginConditionalRender(IIJ)V
nglBeginTransformFeedback(IJ)V
nglBindBufferBase(IIIJ)V
nglBindBufferRange(IIIJJJ)V
nglBindFragDataLocation(IIJJ)V
nglBindFramebuffer(IIJ)V
nglBindRenderbuffer(IIJ)V
nglBindVertexArray(IJ)V
nglBlitFramebuffer(IIIIIIIIIIJ)V
nglCheckFramebufferStatus(IJ)I
nglClampColor(IIJ)V
nglClearBufferfi(IIFIJ)V
nglClearBufferfv(IIJJ)V
nglClearBufferiv(IIJJ)V
nglClearBufferuiv(IIJJ)V
nglColorMaski(IZZZZJ)V
nglDeleteFramebuffers(IJJ)V
nglDeleteRenderbuffers(IJJ)V
nglDeleteVertexArrays(IJJ)V
nglDisablei(IIJ)V
nglEnablei(IIJ)V
nglEndConditionalRender(J)V
nglEndTransformFeedback(J)V
nglFlushMappedBufferRange(IJJJ)V
nglFramebufferRenderbuffer(IIIIJ)V
nglFramebufferTexture1D(IIIIIJ)V
nglFramebufferTexture2D(IIIIIJ)V
nglFramebufferTexture3D(IIIIIIJ)V
nglFramebufferTextureLayer(IIIIIJ)V
nglGenFramebuffers(IJJ)V
nglGenRenderbuffers(IJJ)V
nglGenVertexArrays(IJJ)V
nglGenerateMipmap(IJ)V
nglGetBooleani_v(IIJJ)V
nglGetFragDataLocation(IJJ)I
nglGetFramebufferAttachmentParameteriv(IIIJJ)V
nglGetIntegeri_v(IIJJ)V
nglGetRenderbufferParameteriv(IIJJ)V
nglGetStringi(IIJ)Ljava/lang/String;
nglGetTexParameterIiv(IIJJ)V
nglGetTexParameterIuiv(IIJJ)V
nglGetTransformFeedbackVarying(IIIJJJJJ)V
nglGetUniformuiv(IIJJ)V
nglGetVertexAttribIiv(IIJJ)V
nglGetVertexAttribIuiv(IIJJ)V
nglIsEnabledi(IIJ)Z
nglIsFramebuffer(IJ)Z
nglIsRenderbuffer(IJ)Z
nglIsVertexArray(IJ)Z
nglMapBufferRange(IJJILjava/nio/ByteBuffer;J)Ljava/nio/ByteBuffer;
nglRenderbufferStorage(IIIIJ)V
nglRenderbufferStorageMultisample(IIIIIJ)V
nglTexParameterIiv(IIJJ)V
nglTexParameterIuiv(IIJJ)V
nglTransformFeedbackVaryings(IIJIJ)V
nglUniform1ui(IIJ)V
nglUniform1uiv(IIJJ)V
nglUniform2ui(IIIJ)V
nglUniform2uiv(IIJJ)V
nglUniform3ui(IIIIJ)V
nglUniform3uiv(IIJJ)V
nglUniform4ui(IIIIIJ)V
nglUniform4uiv(IIJJ)V
nglVertexAttribI1i(IIJ)V
nglVertexAttribI1iv(IJJ)V
nglVertexAttribI1ui(IIJ)V
nglVertexAttribI1uiv(IJJ)V
nglVertexAttribI2i(IIIJ)V
nglVertexAttribI2iv(IJJ)V
nglVertexAttribI2ui(IIIJ)V
nglVertexAttribI2uiv(IJJ)V
nglVertexAttribI3i(IIIIJ)V
nglVertexAttribI3iv(IJJ)V
nglVertexAttribI3ui(IIIIJ)V
nglVertexAttribI3uiv(IJJ)V
nglVertexAttribI4bv(IJJ)V
nglVertexAttribI4i(IIIIIJ)V
nglVertexAttribI4iv(IJJ)V
nglVertexAttribI4sv(IJJ)V
nglVertexAttribI4ubv(IJJ)V
nglVertexAttribI4ui(IIIIIJ)V
nglVertexAttribI4uiv(IJJ)V
nglVertexAttribI4usv(IJJ)V
nglVertexAttribIPointer(IIIIJJ)V
nglVertexAttribIPointerBO(IIIIJJ)V
```

#### `org.lwjgl.opengl.GL31`

```text
glCopyBufferSubData(IIJJJ)V
glGetActiveUniformBlock(IIILjava/nio/IntBuffer;)V
glGetActiveUniformBlockName(III)Ljava/lang/String;
glGetActiveUniformBlockName(IILjava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
glGetActiveUniformBlocki(III)I
glGetActiveUniformName(III)Ljava/lang/String;
glGetActiveUniformName(IILjava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
glGetActiveUniforms(ILjava/nio/IntBuffer;ILjava/nio/IntBuffer;)V
glGetActiveUniformsi(III)I
glGetUniformBlockIndex(ILjava/lang/CharSequence;)I
glGetUniformBlockIndex(ILjava/nio/ByteBuffer;)I
glGetUniformIndices(ILjava/nio/ByteBuffer;Ljava/nio/IntBuffer;)V
glGetUniformIndices(I[Ljava/lang/CharSequence;Ljava/nio/IntBuffer;)V
glUniformBlockBinding(III)V
nglCopyBufferSubData(IIJJJJ)V
nglDrawArraysInstanced(IIIIJ)V
nglDrawElementsInstanced(IIIJIJ)V
nglDrawElementsInstancedBO(IIIJIJ)V
nglGetActiveUniformBlockName(IIIJJJ)V
nglGetActiveUniformBlockiv(IIIJJ)V
nglGetActiveUniformName(IIIJJJ)V
nglGetActiveUniformsiv(IIJIJJ)V
nglGetUniformBlockIndex(IJJ)I
nglGetUniformIndices(IIJJJ)V
nglPrimitiveRestartIndex(IJ)V
nglTexBuffer(IIIJ)V
nglUniformBlockBinding(IIIJ)V
```

#### `org.lwjgl.opengl.GL32`

```text
glClientWaitSync(Lorg/lwjgl/opengl/GLSync;IJ)I
glDeleteSync(Lorg/lwjgl/opengl/GLSync;)V
glDrawElementsBaseVertex(IIIJI)V
glDrawElementsBaseVertex(ILjava/nio/ByteBuffer;I)V
glDrawElementsBaseVertex(ILjava/nio/IntBuffer;I)V
glDrawElementsBaseVertex(ILjava/nio/ShortBuffer;I)V
glDrawElementsInstancedBaseVertex(IIIJII)V
glDrawElementsInstancedBaseVertex(ILjava/nio/ByteBuffer;II)V
glDrawElementsInstancedBaseVertex(ILjava/nio/IntBuffer;II)V
glDrawElementsInstancedBaseVertex(ILjava/nio/ShortBuffer;II)V
glDrawRangeElementsBaseVertex(IIIIIJI)V
glDrawRangeElementsBaseVertex(IIILjava/nio/ByteBuffer;I)V
glDrawRangeElementsBaseVertex(IIILjava/nio/IntBuffer;I)V
glDrawRangeElementsBaseVertex(IIILjava/nio/ShortBuffer;I)V
glFenceSync(II)Lorg/lwjgl/opengl/GLSync;
glGetBufferParameteri64(II)J
glGetInteger64(I)J
glGetInteger64(ILjava/nio/LongBuffer;)V
glGetMultisample(IILjava/nio/FloatBuffer;)V
glGetSync(Lorg/lwjgl/opengl/GLSync;ILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)V
glGetSynci(Lorg/lwjgl/opengl/GLSync;I)I
glIsSync(Lorg/lwjgl/opengl/GLSync;)Z
glProvokingVertex(I)V
glSampleMaski(II)V
glTexImage2DMultisample(IIIIIZ)V
glTexImage3DMultisample(IIIIIIZ)V
glWaitSync(Lorg/lwjgl/opengl/GLSync;IJ)V
nglClientWaitSync(JIJJ)I
nglDeleteSync(JJ)V
nglDrawElementsBaseVertex(IIIJIJ)V
nglDrawElementsBaseVertexBO(IIIJIJ)V
nglDrawElementsInstancedBaseVertex(IIIJIIJ)V
nglDrawElementsInstancedBaseVertexBO(IIIJIIJ)V
nglDrawRangeElementsBaseVertex(IIIIIJIJ)V
nglDrawRangeElementsBaseVertexBO(IIIIIJIJ)V
nglFenceSync(IIJ)J
nglFramebufferTexture(IIIIJ)V
nglGetBufferParameteri64v(IIJJ)V
nglGetInteger64i_v(IIJJ)V
nglGetInteger64v(IJJ)V
nglGetMultisamplefv(IIJJ)V
nglGetSynciv(JIIJJJ)V
nglIsSync(JJ)Z
nglProvokingVertex(IJ)V
nglSampleMaski(IIJ)V
nglTexImage2DMultisample(IIIIIZJ)V
nglTexImage3DMultisample(IIIIIIZJ)V
nglWaitSync(JIJJ)V
```

#### `org.lwjgl.opengl.GL33`

```text
glBindFragDataLocationIndexed(IIILjava/lang/CharSequence;)V
glBindFragDataLocationIndexed(IIILjava/nio/ByteBuffer;)V
glBindSampler(II)V
glColorP3u(ILjava/nio/IntBuffer;)V
glColorP3ui(II)V
glColorP4u(ILjava/nio/IntBuffer;)V
glColorP4ui(II)V
glDeleteSamplers(I)V
glDeleteSamplers(Ljava/nio/IntBuffer;)V
glGenSamplers()I
glGenSamplers(Ljava/nio/IntBuffer;)V
glGetFragDataIndex(ILjava/lang/CharSequence;)I
glGetFragDataIndex(ILjava/nio/ByteBuffer;)I
glGetQueryObject(IILjava/nio/LongBuffer;)V
glGetQueryObjecti64(II)J
glGetQueryObjectu(IILjava/nio/LongBuffer;)V
glGetQueryObjectui64(II)J
glGetSamplerParameter(IILjava/nio/FloatBuffer;)V
glGetSamplerParameter(IILjava/nio/IntBuffer;)V
glGetSamplerParameterI(IILjava/nio/IntBuffer;)V
glGetSamplerParameterIi(II)I
glGetSamplerParameterIu(IILjava/nio/IntBuffer;)V
glGetSamplerParameterIui(II)I
glGetSamplerParameterf(II)F
glGetSamplerParameteri(II)I
glIsSampler(I)Z
glMultiTexCoordP1u(IILjava/nio/IntBuffer;)V
glMultiTexCoordP1ui(III)V
glMultiTexCoordP2u(IILjava/nio/IntBuffer;)V
glMultiTexCoordP2ui(III)V
glMultiTexCoordP3u(IILjava/nio/IntBuffer;)V
glMultiTexCoordP3ui(III)V
glMultiTexCoordP4u(IILjava/nio/IntBuffer;)V
glMultiTexCoordP4ui(III)V
glNormalP3u(ILjava/nio/IntBuffer;)V
glNormalP3ui(II)V
glQueryCounter(II)V
glSamplerParameter(IILjava/nio/FloatBuffer;)V
glSamplerParameter(IILjava/nio/IntBuffer;)V
glSamplerParameterI(IILjava/nio/IntBuffer;)V
glSamplerParameterIu(IILjava/nio/IntBuffer;)V
glSamplerParameterf(IIF)V
glSamplerParameteri(III)V
glSecondaryColorP3u(ILjava/nio/IntBuffer;)V
glSecondaryColorP3ui(II)V
glTexCoordP1u(ILjava/nio/IntBuffer;)V
glTexCoordP1ui(II)V
glTexCoordP2u(ILjava/nio/IntBuffer;)V
glTexCoordP2ui(II)V
glTexCoordP3u(ILjava/nio/IntBuffer;)V
glTexCoordP3ui(II)V
glTexCoordP4u(ILjava/nio/IntBuffer;)V
glTexCoordP4ui(II)V
glVertexAttribP1u(IIZLjava/nio/IntBuffer;)V
glVertexAttribP1ui(IIZI)V
glVertexAttribP2u(IIZLjava/nio/IntBuffer;)V
glVertexAttribP2ui(IIZI)V
glVertexAttribP3u(IIZLjava/nio/IntBuffer;)V
glVertexAttribP3ui(IIZI)V
glVertexAttribP4u(IIZLjava/nio/IntBuffer;)V
glVertexAttribP4ui(IIZI)V
glVertexP2u(ILjava/nio/IntBuffer;)V
glVertexP2ui(II)V
glVertexP3u(ILjava/nio/IntBuffer;)V
glVertexP3ui(II)V
glVertexP4u(ILjava/nio/IntBuffer;)V
glVertexP4ui(II)V
nglBindFragDataLocationIndexed(IIIJJ)V
nglBindSampler(IIJ)V
nglColorP3ui(IIJ)V
nglColorP3uiv(IJJ)V
nglColorP4ui(IIJ)V
nglColorP4uiv(IJJ)V
nglDeleteSamplers(IJJ)V
nglGenSamplers(IJJ)V
nglGetFragDataIndex(IJJ)I
nglGetQueryObjecti64v(IIJJ)V
nglGetQueryObjectui64v(IIJJ)V
nglGetSamplerParameterIiv(IIJJ)V
nglGetSamplerParameterIuiv(IIJJ)V
nglGetSamplerParameterfv(IIJJ)V
nglGetSamplerParameteriv(IIJJ)V
nglIsSampler(IJ)Z
nglMultiTexCoordP1ui(IIIJ)V
nglMultiTexCoordP1uiv(IIJJ)V
nglMultiTexCoordP2ui(IIIJ)V
nglMultiTexCoordP2uiv(IIJJ)V
nglMultiTexCoordP3ui(IIIJ)V
nglMultiTexCoordP3uiv(IIJJ)V
nglMultiTexCoordP4ui(IIIJ)V
nglMultiTexCoordP4uiv(IIJJ)V
nglNormalP3ui(IIJ)V
nglNormalP3uiv(IJJ)V
nglQueryCounter(IIJ)V
nglSamplerParameterIiv(IIJJ)V
nglSamplerParameterIuiv(IIJJ)V
nglSamplerParameterf(IIFJ)V
nglSamplerParameterfv(IIJJ)V
nglSamplerParameteri(IIIJ)V
nglSamplerParameteriv(IIJJ)V
nglSecondaryColorP3ui(IIJ)V
nglSecondaryColorP3uiv(IJJ)V
nglTexCoordP1ui(IIJ)V
nglTexCoordP1uiv(IJJ)V
nglTexCoordP2ui(IIJ)V
nglTexCoordP2uiv(IJJ)V
nglTexCoordP3ui(IIJ)V
nglTexCoordP3uiv(IJJ)V
nglTexCoordP4ui(IIJ)V
nglTexCoordP4uiv(IJJ)V
nglVertexAttribDivisor(IIJ)V
nglVertexAttribP1ui(IIZIJ)V
nglVertexAttribP1uiv(IIZJJ)V
nglVertexAttribP2ui(IIZIJ)V
nglVertexAttribP2uiv(IIZJJ)V
nglVertexAttribP3ui(IIZIJ)V
nglVertexAttribP3uiv(IIZJJ)V
nglVertexAttribP4ui(IIZIJ)V
nglVertexAttribP4uiv(IIZJJ)V
nglVertexP2ui(IIJ)V
nglVertexP2uiv(IJJ)V
nglVertexP3ui(IIJ)V
nglVertexP3uiv(IJJ)V
nglVertexP4ui(IIJ)V
nglVertexP4uiv(IJJ)V
```

#### `org.lwjgl.opengl.GL40`

```text
glBeginQueryIndexed(III)V
glBindTransformFeedback(II)V
glDeleteTransformFeedbacks(I)V
glDeleteTransformFeedbacks(Ljava/nio/IntBuffer;)V
glDrawArraysIndirect(IJ)V
glDrawArraysIndirect(ILjava/nio/ByteBuffer;)V
glDrawArraysIndirect(ILjava/nio/IntBuffer;)V
glDrawElementsIndirect(IIJ)V
glDrawElementsIndirect(IILjava/nio/ByteBuffer;)V
glDrawElementsIndirect(IILjava/nio/IntBuffer;)V
glDrawTransformFeedback(II)V
glDrawTransformFeedbackStream(III)V
glEndQueryIndexed(II)V
glGenTransformFeedbacks()I
glGenTransformFeedbacks(Ljava/nio/IntBuffer;)V
glGetActiveSubroutineName(IIII)Ljava/lang/String;
glGetActiveSubroutineName(IIILjava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
glGetActiveSubroutineUniform(IIIILjava/nio/IntBuffer;)V
glGetActiveSubroutineUniformName(IIII)Ljava/lang/String;
glGetActiveSubroutineUniformName(IIILjava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
glGetActiveSubroutineUniformi(IIII)I
glGetProgramStage(IIILjava/nio/IntBuffer;)V
glGetProgramStagei(III)I
glGetQueryIndexed(IIILjava/nio/IntBuffer;)V
glGetQueryIndexedi(III)I
glGetSubroutineIndex(IILjava/lang/CharSequence;)I
glGetSubroutineIndex(IILjava/nio/ByteBuffer;)I
glGetSubroutineUniformLocation(IILjava/lang/CharSequence;)I
glGetSubroutineUniformLocation(IILjava/nio/ByteBuffer;)I
glGetUniform(IILjava/nio/DoubleBuffer;)V
glGetUniformSubroutineu(IILjava/nio/IntBuffer;)V
glGetUniformSubroutineui(II)I
glIsTransformFeedback(I)Z
glPatchParameter(ILjava/nio/FloatBuffer;)V
glPatchParameteri(II)V
glPauseTransformFeedback()V
glResumeTransformFeedback()V
glUniform1(ILjava/nio/DoubleBuffer;)V
glUniform1d(ID)V
glUniform2(ILjava/nio/DoubleBuffer;)V
glUniform2d(IDD)V
glUniform3(ILjava/nio/DoubleBuffer;)V
glUniform3d(IDDD)V
glUniform4(ILjava/nio/DoubleBuffer;)V
glUniform4d(IDDDD)V
glUniformMatrix2(IZLjava/nio/DoubleBuffer;)V
glUniformMatrix2x3(IZLjava/nio/DoubleBuffer;)V
glUniformMatrix2x4(IZLjava/nio/DoubleBuffer;)V
glUniformMatrix3(IZLjava/nio/DoubleBuffer;)V
glUniformMatrix3x2(IZLjava/nio/DoubleBuffer;)V
glUniformMatrix3x4(IZLjava/nio/DoubleBuffer;)V
glUniformMatrix4(IZLjava/nio/DoubleBuffer;)V
glUniformMatrix4x2(IZLjava/nio/DoubleBuffer;)V
glUniformMatrix4x3(IZLjava/nio/DoubleBuffer;)V
glUniformSubroutinesu(ILjava/nio/IntBuffer;)V
nglBeginQueryIndexed(IIIJ)V
nglBindTransformFeedback(IIJ)V
nglBlendEquationSeparatei(IIIJ)V
nglBlendEquationi(IIJ)V
nglBlendFuncSeparatei(IIIIIJ)V
nglBlendFunci(IIIJ)V
nglDeleteTransformFeedbacks(IJJ)V
nglDrawArraysIndirect(IJJ)V
nglDrawArraysIndirectBO(IJJ)V
nglDrawElementsIndirect(IIJJ)V
nglDrawElementsIndirectBO(IIJJ)V
nglDrawTransformFeedback(IIJ)V
nglDrawTransformFeedbackStream(IIIJ)V
nglEndQueryIndexed(IIJ)V
nglGenTransformFeedbacks(IJJ)V
nglGetActiveSubroutineName(IIIIJJJ)V
nglGetActiveSubroutineUniformName(IIIIJJJ)V
nglGetActiveSubroutineUniformiv(IIIIJJ)V
nglGetProgramStageiv(IIIJJ)V
nglGetQueryIndexediv(IIIJJ)V
nglGetSubroutineIndex(IIJJ)I
nglGetSubroutineUniformLocation(IIJJ)I
nglGetUniformSubroutineuiv(IIJJ)V
nglGetUniformdv(IIJJ)V
nglIsTransformFeedback(IJ)Z
nglMinSampleShading(FJ)V
nglPatchParameterfv(IJJ)V
nglPatchParameteri(IIJ)V
nglPauseTransformFeedback(J)V
nglResumeTransformFeedback(J)V
nglUniform1d(IDJ)V
nglUniform1dv(IIJJ)V
nglUniform2d(IDDJ)V
nglUniform2dv(IIJJ)V
nglUniform3d(IDDDJ)V
nglUniform3dv(IIJJ)V
nglUniform4d(IDDDDJ)V
nglUniform4dv(IIJJ)V
nglUniformMatrix2dv(IIZJJ)V
nglUniformMatrix2x3dv(IIZJJ)V
nglUniformMatrix2x4dv(IIZJJ)V
nglUniformMatrix3dv(IIZJJ)V
nglUniformMatrix3x2dv(IIZJJ)V
nglUniformMatrix3x4dv(IIZJJ)V
nglUniformMatrix4dv(IIZJJ)V
nglUniformMatrix4x2dv(IIZJJ)V
nglUniformMatrix4x3dv(IIZJJ)V
nglUniformSubroutinesuiv(IIJJ)V
```

#### `org.lwjgl.opengl.GL41`

```text
glActiveShaderProgram(II)V
glBindProgramPipeline(I)V
glClearDepthf(F)V
glCreateShaderProgram(IILjava/nio/ByteBuffer;)I
glCreateShaderProgram(ILjava/lang/CharSequence;)I
glCreateShaderProgram(ILjava/nio/ByteBuffer;)I
glCreateShaderProgram(I[Ljava/lang/CharSequence;)I
glCreateShaderProgram(I[Ljava/nio/ByteBuffer;)I
glDeleteProgramPipelines(I)V
glDeleteProgramPipelines(Ljava/nio/IntBuffer;)V
glDepthRangeArray(ILjava/nio/DoubleBuffer;)V
glDepthRangeIndexed(IDD)V
glDepthRangef(FF)V
glGenProgramPipelines()I
glGenProgramPipelines(Ljava/nio/IntBuffer;)V
glGetDouble(II)D
glGetDouble(IILjava/nio/DoubleBuffer;)V
glGetFloat(II)F
glGetFloat(IILjava/nio/FloatBuffer;)V
glGetProgramBinary(ILjava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
glGetProgramPipeline(IILjava/nio/IntBuffer;)V
glGetProgramPipelineInfoLog(II)Ljava/lang/String;
glGetProgramPipelineInfoLog(ILjava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
glGetProgramPipelinei(II)I
glGetShaderPrecisionFormat(IILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)V
glGetVertexAttribL(IILjava/nio/DoubleBuffer;)V
glIsProgramPipeline(I)Z
glProgramBinary(IILjava/nio/ByteBuffer;)V
glProgramParameteri(III)V
glProgramUniform1(IILjava/nio/DoubleBuffer;)V
glProgramUniform1(IILjava/nio/FloatBuffer;)V
glProgramUniform1(IILjava/nio/IntBuffer;)V
glProgramUniform1d(IID)V
glProgramUniform1f(IIF)V
glProgramUniform1i(III)V
glProgramUniform1u(IILjava/nio/IntBuffer;)V
glProgramUniform1ui(III)V
glProgramUniform2(IILjava/nio/DoubleBuffer;)V
glProgramUniform2(IILjava/nio/FloatBuffer;)V
glProgramUniform2(IILjava/nio/IntBuffer;)V
glProgramUniform2d(IIDD)V
glProgramUniform2f(IIFF)V
glProgramUniform2i(IIII)V
glProgramUniform2u(IILjava/nio/IntBuffer;)V
glProgramUniform2ui(IIII)V
glProgramUniform3(IILjava/nio/DoubleBuffer;)V
glProgramUniform3(IILjava/nio/FloatBuffer;)V
glProgramUniform3(IILjava/nio/IntBuffer;)V
glProgramUniform3d(IIDDD)V
glProgramUniform3f(IIFFF)V
glProgramUniform3i(IIIII)V
glProgramUniform3u(IILjava/nio/IntBuffer;)V
glProgramUniform3ui(IIIII)V
glProgramUniform4(IILjava/nio/DoubleBuffer;)V
glProgramUniform4(IILjava/nio/FloatBuffer;)V
glProgramUniform4(IILjava/nio/IntBuffer;)V
glProgramUniform4d(IIDDDD)V
glProgramUniform4f(IIFFFF)V
glProgramUniform4i(IIIIII)V
glProgramUniform4u(IILjava/nio/IntBuffer;)V
glProgramUniform4ui(IIIIII)V
glProgramUniformMatrix2(IIZLjava/nio/DoubleBuffer;)V
glProgramUniformMatrix2(IIZLjava/nio/FloatBuffer;)V
glProgramUniformMatrix2x3(IIZLjava/nio/DoubleBuffer;)V
glProgramUniformMatrix2x3(IIZLjava/nio/FloatBuffer;)V
glProgramUniformMatrix2x4(IIZLjava/nio/DoubleBuffer;)V
glProgramUniformMatrix2x4(IIZLjava/nio/FloatBuffer;)V
glProgramUniformMatrix3(IIZLjava/nio/DoubleBuffer;)V
glProgramUniformMatrix3(IIZLjava/nio/FloatBuffer;)V
glProgramUniformMatrix3x2(IIZLjava/nio/DoubleBuffer;)V
glProgramUniformMatrix3x2(IIZLjava/nio/FloatBuffer;)V
glProgramUniformMatrix3x4(IIZLjava/nio/DoubleBuffer;)V
glProgramUniformMatrix3x4(IIZLjava/nio/FloatBuffer;)V
glProgramUniformMatrix4(IIZLjava/nio/DoubleBuffer;)V
glProgramUniformMatrix4(IIZLjava/nio/FloatBuffer;)V
glProgramUniformMatrix4x2(IIZLjava/nio/DoubleBuffer;)V
glProgramUniformMatrix4x2(IIZLjava/nio/FloatBuffer;)V
glProgramUniformMatrix4x3(IIZLjava/nio/DoubleBuffer;)V
glProgramUniformMatrix4x3(IIZLjava/nio/FloatBuffer;)V
glReleaseShaderCompiler()V
glScissorArray(ILjava/nio/IntBuffer;)V
glScissorIndexed(IIIII)V
glScissorIndexed(ILjava/nio/IntBuffer;)V
glShaderBinary(Ljava/nio/IntBuffer;ILjava/nio/ByteBuffer;)V
glUseProgramStages(III)V
glValidateProgramPipeline(I)V
glVertexAttribL1(ILjava/nio/DoubleBuffer;)V
glVertexAttribL1d(ID)V
glVertexAttribL2(ILjava/nio/DoubleBuffer;)V
glVertexAttribL2d(IDD)V
glVertexAttribL3(ILjava/nio/DoubleBuffer;)V
glVertexAttribL3d(IDDD)V
glVertexAttribL4(ILjava/nio/DoubleBuffer;)V
glVertexAttribL4d(IDDDD)V
glVertexAttribLPointer(IIIJ)V
glVertexAttribLPointer(IIILjava/nio/DoubleBuffer;)V
glViewportArray(ILjava/nio/FloatBuffer;)V
glViewportIndexed(ILjava/nio/FloatBuffer;)V
glViewportIndexedf(IFFFF)V
nglActiveShaderProgram(IIJ)V
nglBindProgramPipeline(IJ)V
nglClearDepthf(FJ)V
nglCreateShaderProgramv(IIJJ)I
nglCreateShaderProgramv2(IIJJ)I
nglCreateShaderProgramv3(II[Ljava/nio/ByteBuffer;J)I
nglDeleteProgramPipelines(IJJ)V
nglDepthRangeArrayv(IIJJ)V
nglDepthRangeIndexed(IDDJ)V
nglDepthRangef(FFJ)V
nglGenProgramPipelines(IJJ)V
nglGetDoublei_v(IIJJ)V
nglGetFloati_v(IIJJ)V
nglGetProgramBinary(IIJJJJ)V
nglGetProgramPipelineInfoLog(IIJJJ)V
nglGetProgramPipelineiv(IIJJ)V
nglGetShaderPrecisionFormat(IIJJJ)V
nglGetVertexAttribLdv(IIJJ)V
nglIsProgramPipeline(IJ)Z
nglProgramBinary(IIJIJ)V
nglProgramParameteri(IIIJ)V
nglProgramUniform1d(IIDJ)V
nglProgramUniform1dv(IIIJJ)V
nglProgramUniform1f(IIFJ)V
nglProgramUniform1fv(IIIJJ)V
nglProgramUniform1i(IIIJ)V
nglProgramUniform1iv(IIIJJ)V
nglProgramUniform1ui(IIIJ)V
nglProgramUniform1uiv(IIIJJ)V
nglProgramUniform2d(IIDDJ)V
nglProgramUniform2dv(IIIJJ)V
nglProgramUniform2f(IIFFJ)V
nglProgramUniform2fv(IIIJJ)V
nglProgramUniform2i(IIIIJ)V
nglProgramUniform2iv(IIIJJ)V
nglProgramUniform2ui(IIIIJ)V
nglProgramUniform2uiv(IIIJJ)V
nglProgramUniform3d(IIDDDJ)V
nglProgramUniform3dv(IIIJJ)V
nglProgramUniform3f(IIFFFJ)V
nglProgramUniform3fv(IIIJJ)V
nglProgramUniform3i(IIIIIJ)V
nglProgramUniform3iv(IIIJJ)V
nglProgramUniform3ui(IIIIIJ)V
nglProgramUniform3uiv(IIIJJ)V
nglProgramUniform4d(IIDDDDJ)V
nglProgramUniform4dv(IIIJJ)V
nglProgramUniform4f(IIFFFFJ)V
nglProgramUniform4fv(IIIJJ)V
nglProgramUniform4i(IIIIIIJ)V
nglProgramUniform4iv(IIIJJ)V
nglProgramUniform4ui(IIIIIIJ)V
nglProgramUniform4uiv(IIIJJ)V
nglProgramUniformMatrix2dv(IIIZJJ)V
nglProgramUniformMatrix2fv(IIIZJJ)V
nglProgramUniformMatrix2x3dv(IIIZJJ)V
nglProgramUniformMatrix2x3fv(IIIZJJ)V
nglProgramUniformMatrix2x4dv(IIIZJJ)V
nglProgramUniformMatrix2x4fv(IIIZJJ)V
nglProgramUniformMatrix3dv(IIIZJJ)V
nglProgramUniformMatrix3fv(IIIZJJ)V
nglProgramUniformMatrix3x2dv(IIIZJJ)V
nglProgramUniformMatrix3x2fv(IIIZJJ)V
nglProgramUniformMatrix3x4dv(IIIZJJ)V
nglProgramUniformMatrix3x4fv(IIIZJJ)V
nglProgramUniformMatrix4dv(IIIZJJ)V
nglProgramUniformMatrix4fv(IIIZJJ)V
nglProgramUniformMatrix4x2dv(IIIZJJ)V
nglProgramUniformMatrix4x2fv(IIIZJJ)V
nglProgramUniformMatrix4x3dv(IIIZJJ)V
nglProgramUniformMatrix4x3fv(IIIZJJ)V
nglReleaseShaderCompiler(J)V
nglScissorArrayv(IIJJ)V
nglScissorIndexed(IIIIIJ)V
nglScissorIndexedv(IJJ)V
nglShaderBinary(IJIJIJ)V
nglUseProgramStages(IIIJ)V
nglValidateProgramPipeline(IJ)V
nglVertexAttribL1d(IDJ)V
nglVertexAttribL1dv(IJJ)V
nglVertexAttribL2d(IDDJ)V
nglVertexAttribL2dv(IJJ)V
nglVertexAttribL3d(IDDDJ)V
nglVertexAttribL3dv(IJJ)V
nglVertexAttribL4d(IDDDDJ)V
nglVertexAttribL4dv(IJJ)V
nglVertexAttribLPointer(IIIIJJ)V
nglVertexAttribLPointerBO(IIIIJJ)V
nglViewportArrayv(IIJJ)V
nglViewportIndexedf(IFFFFJ)V
nglViewportIndexedfv(IJJ)V
```

#### `org.lwjgl.opengl.GL42`

```text
glBindImageTexture(IIIZIII)V
glDrawArraysInstancedBaseInstance(IIIII)V
glDrawElementsInstancedBaseInstance(IIIJII)V
glDrawElementsInstancedBaseInstance(ILjava/nio/ByteBuffer;II)V
glDrawElementsInstancedBaseInstance(ILjava/nio/IntBuffer;II)V
glDrawElementsInstancedBaseInstance(ILjava/nio/ShortBuffer;II)V
glDrawElementsInstancedBaseVertexBaseInstance(IIIJIII)V
glDrawElementsInstancedBaseVertexBaseInstance(ILjava/nio/ByteBuffer;III)V
glDrawElementsInstancedBaseVertexBaseInstance(ILjava/nio/IntBuffer;III)V
glDrawElementsInstancedBaseVertexBaseInstance(ILjava/nio/ShortBuffer;III)V
glDrawTransformFeedbackInstanced(III)V
glDrawTransformFeedbackStreamInstanced(IIII)V
glGetActiveAtomicCounterBuffer(III)I
glGetActiveAtomicCounterBuffer(IIILjava/nio/IntBuffer;)V
glGetInternalformat(III)I
glGetInternalformat(IIILjava/nio/IntBuffer;)V
glMemoryBarrier(I)V
glTexStorage1D(IIII)V
glTexStorage2D(IIIII)V
glTexStorage3D(IIIIII)V
nglBindImageTexture(IIIZIIIJ)V
nglDrawArraysInstancedBaseInstance(IIIIIJ)V
nglDrawElementsInstancedBaseInstance(IIIJIIJ)V
nglDrawElementsInstancedBaseInstanceBO(IIIJIIJ)V
nglDrawElementsInstancedBaseVertexBaseInstance(IIIJIIIJ)V
nglDrawElementsInstancedBaseVertexBaseInstanceBO(IIIJIIIJ)V
nglDrawTransformFeedbackInstanced(IIIJ)V
nglDrawTransformFeedbackStreamInstanced(IIIIJ)V
nglGetActiveAtomicCounterBufferiv(IIIJJ)V
nglGetInternalformativ(IIIIJJ)V
nglMemoryBarrier(IJ)V
nglTexStorage1D(IIIIJ)V
nglTexStorage2D(IIIIIJ)V
nglTexStorage3D(IIIIIIJ)V
```

#### `org.lwjgl.opengl.GLChecks`

```text
calculateBytesPerCharCode(I)I
calculateBytesPerPathName(I)I
calculateBytesPerPixel(II)I
calculateImageStorage(IIIII)I
calculateImageStorage(Ljava/nio/Buffer;IIIII)I
calculateMetricsSize(II)I
calculatePathColorGenCoeffsCount(II)I
calculatePathGenCoeffsPerComponent(I)I
calculatePathTextGenCoeffsPerComponent(Ljava/nio/FloatBuffer;I)I
calculateTexImage1DStorage(III)I
calculateTexImage1DStorage(Ljava/nio/Buffer;III)I
calculateTexImage2DStorage(IIII)I
calculateTexImage2DStorage(Ljava/nio/Buffer;IIII)I
calculateTexImage3DStorage(IIIII)I
calculateTexImage3DStorage(Ljava/nio/Buffer;IIIII)I
calculateTransformPathValues(I)I
ensureArrayVBOdisabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
ensureArrayVBOenabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
ensureElementVBOdisabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
ensureElementVBOenabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
ensureIndirectBOdisabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
ensureIndirectBOenabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
ensurePackPBOdisabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
ensurePackPBOenabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
ensureUnpackPBOdisabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
ensureUnpackPBOenabled(Lorg/lwjgl/opengl/ContextCapabilities;)V
```

#### `org.lwjgl.opengl.GLContext$1`

```text
<init>()V
run()Ljava/lang/String;
```

#### `org.lwjgl.opengl.GLContext$2`

```text
<init>(Ljava/lang/Class;)V
val$extension_classLjava/lang/Class;
```

#### `org.lwjgl.opengl.GLContext$CapabilitiesCacheEntry`

```text
<init>()V
<init>(Lorg/lwjgl/opengl/GLContext$1;)V
capabilitiesLorg/lwjgl/opengl/ContextCapabilities;
ownerLjava/lang/Thread;
```

#### `org.lwjgl.opengl.GLContext`

```text
capability_cacheLjava/util/Map;
current_capabilitiesLjava/lang/ThreadLocal;
did_auto_loadZ
fast_path_cacheLorg/lwjgl/opengl/GLContext$CapabilitiesCacheEntry;
getCapabilities()Lorg/lwjgl/opengl/ContextCapabilities;
getCapabilities(Ljava/lang/Object;)Lorg/lwjgl/opengl/ContextCapabilities;
getCapabilitiesImpl()Lorg/lwjgl/opengl/ContextCapabilities;
getFunctionAddress(Ljava/lang/String;)J
getFunctionAddress([Ljava/lang/String;)J
getPlatformSpecificFunctionAddress(Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)J
getSupportedExtensions(Ljava/util/Set;)I
getThreadLocalCapabilities()Lorg/lwjgl/opengl/ContextCapabilities;
gl_ref_countI
loadOpenGLLibrary()V
nLoadOpenGLLibrary()V
nUnloadOpenGLLibrary()V
ngetFunctionAddress(J)J
resetNativeStubs(Ljava/lang/Class;)V
setCapabilities(Lorg/lwjgl/opengl/ContextCapabilities;)V
thread_cache_entriesLjava/lang/ThreadLocal;
unloadOpenGLLibrary()V
useContext(Ljava/lang/Object;)V
useContext(Ljava/lang/Object;Z)V
```

#### `org.lwjgl.opengl.GLSync`

```text
<init>(J)V
getPointer()J
```

#### `org.lwjgl.opengl.GLUConstants`

Identical byte redundancy.

#### `org.lwjgl.opengl.GREMEDYStringMarker`

```text
nglStringMarkerGREMEDY(IJJ)V
```

#### `org.lwjgl.opengl.GlobalLock`

```text
lockLjava/lang/Object;
```

#### `org.lwjgl.opengl.HPOcclusionTest`

Identical byte redundancy.

#### `org.lwjgl.opengl.IBMRasterposClip`

Identical byte redundancy.

#### `org.lwjgl.opengl.InputImplementation`

```text
createCursor(IIIIILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)Ljava/lang/Object;
createKeyboard()V
createMouse()V
destroyCursor(Ljava/lang/Object;)V
destroyKeyboard()V
destroyMouse()V
getButtonCount()I
getMaxCursorSize()I
getMinCursorSize()I
getNativeCursorCapabilities()I
grabMouse(Z)V
hasWheel()Z
isInsideWindow()Z
pollKeyboard(Ljava/nio/ByteBuffer;)V
pollMouse(Ljava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
readKeyboard(Ljava/nio/ByteBuffer;)V
readMouse(Ljava/nio/ByteBuffer;)V
setCursorPosition(II)V
setNativeCursor(Ljava/lang/Object;)V
```

#### `org.lwjgl.opengl.KeyboardEventQueue`

```text
<init>(Ljava/awt/Component;)V
KEY_MAP[I
componentLjava/awt/Component;
copyEvents(Ljava/nio/ByteBuffer;)V
deferred_characterI
deferred_key_codeI
deferred_key_locationI
deferred_key_stateB
deferred_nanosJ
eventLjava/nio/ByteBuffer;
flushDeferredEvent()V
getMappedKeyCode(II)I
handleKey(IIBIJ)V
has_deferred_eventZ
key_states[B
poll(Ljava/nio/ByteBuffer;)V
putEvent(Ljava/nio/ByteBuffer;)Z
putKeyEvent(IIBIJZ)V
putKeyboardEvent(IBIJZ)V
register()V
unregister()V
```

#### `org.lwjgl.opengl.LinuxAWTGLCanvasPeerInfo`

```text
<init>(Ljava/awt/Canvas;)V
awt_surfaceLorg/lwjgl/opengl/AWTSurfaceLock;
componentLjava/awt/Canvas;
getHandle()Ljava/nio/ByteBuffer;
getScreenFromSurfaceInfo(Ljava/nio/ByteBuffer;)I
nInitHandle(ILjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V
screenI
```

#### `org.lwjgl.opengl.LinuxCanvasImplementation$1`

```text
<init>(Ljava/awt/GraphicsDevice;)V
run()Ljava/lang/reflect/Method;
val$deviceLjava/awt/GraphicsDevice;
```

#### `org.lwjgl.opengl.LinuxCanvasImplementation$2`

```text
<init>(Ljava/awt/GraphicsConfiguration;)V
run()Ljava/lang/reflect/Method;
val$configurationLjava/awt/GraphicsConfiguration;
```

#### `org.lwjgl.opengl.LinuxCanvasImplementation`

```text
<init>()V
findVisualIDFromFormat(ILorg/lwjgl/opengl/PixelFormat;)I
getScreenFromDevice(Ljava/awt/GraphicsDevice;)I
getVisualIDFromConfiguration(Ljava/awt/GraphicsConfiguration;)I
nFindVisualIDFromFormat(JILorg/lwjgl/opengl/PixelFormat;)I
```

#### `org.lwjgl.opengl.LinuxContextImplementation`

```text
<init>()V
getDisplay(Ljava/nio/ByteBuffer;)J
getGLXContext(Ljava/nio/ByteBuffer;)J
nCreate(Ljava/nio/ByteBuffer;Ljava/nio/IntBuffer;Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
nDestroy(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V
nIsCurrent(Ljava/nio/ByteBuffer;)Z
nMakeCurrent(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V
nReleaseCurrentContext(Ljava/nio/ByteBuffer;)V
nSetSwapInterval(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;I)V
nSwapBuffers(Ljava/nio/ByteBuffer;)V
```

#### `org.lwjgl.opengl.LinuxDisplay$1`

```text
<init>(Lorg/lwjgl/opengl/LinuxDisplay;)V
this$0Lorg/lwjgl/opengl/LinuxDisplay;
```

#### `org.lwjgl.opengl.LinuxDisplay$2`

```text
<init>(Lorg/lwjgl/opengl/LinuxDisplay;)V
this$0Lorg/lwjgl/opengl/LinuxDisplay;
```

#### `org.lwjgl.opengl.LinuxDisplay$3`

```text
<init>(Lorg/lwjgl/opengl/LinuxDisplay;)V
run()Lorg/lwjgl/opengl/DisplayMode;
this$0Lorg/lwjgl/opengl/LinuxDisplay;
```

#### `org.lwjgl.opengl.LinuxDisplay$4`

```text
<init>(Lorg/lwjgl/opengl/LinuxDisplay;)V
this$0Lorg/lwjgl/opengl/LinuxDisplay;
```

#### `org.lwjgl.opengl.LinuxDisplay$Compiz$1$1`

```text
<init>(Lorg/lwjgl/opengl/LinuxDisplay$Compiz$1;)V
this$0Lorg/lwjgl/opengl/LinuxDisplay$Compiz$1;
```

#### `org.lwjgl.opengl.LinuxDisplay$Compiz$1$2`

```text
<init>(Lorg/lwjgl/opengl/LinuxDisplay$Compiz$1;)V
this$0Lorg/lwjgl/opengl/LinuxDisplay$Compiz$1;
```

#### `org.lwjgl.opengl.LinuxDisplay$Compiz$1`

```text
<init>()V
```

#### `org.lwjgl.opengl.LinuxDisplay$Compiz$2`

```text
<init>(Z)V
val$enabledZ
```

#### `org.lwjgl.opengl.LinuxDisplay$Compiz$Provider`

```text
hasLegacyFullscreenSupport()Z
setLegacyFullscreenSupport(Z)V
```

#### `org.lwjgl.opengl.LinuxDisplay$Compiz`

```text
access$200(Ljava/lang/String;)Z
access$300()Lorg/lwjgl/opengl/LinuxDisplay$Compiz$Provider;
access$302(Lorg/lwjgl/opengl/LinuxDisplay$Compiz$Provider;)Lorg/lwjgl/opengl/LinuxDisplay$Compiz$Provider;
access$400([Ljava/lang/String;)Ljava/util/List;
access$502(Z)Z
applyFixZ
init()V
isProcessActive(Ljava/lang/String;)Z
providerLorg/lwjgl/opengl/LinuxDisplay$Compiz$Provider;
run([Ljava/lang/String;)Ljava/util/List;
setLegacyFullscreenSupport(Z)V
```

#### `org.lwjgl.opengl.LinuxDisplay`

```text
<init>()V
access$002(Lorg/lwjgl/opengl/LinuxDisplay;Z)Z
access$102(Lorg/lwjgl/opengl/LinuxDisplay;Z)Z
acquireInput()V
blank_cursorJ
callErrorHandler(JJJ)I
checkInput()V
close_requestedZ
convertIcons([Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
convertToNativeRamp(Ljava/nio/FloatBuffer;)Ljava/nio/ByteBuffer;
createBlankCursor()J
current_cursorJ
current_displaymode_extensionI
current_gammaLjava/nio/ByteBuffer;
current_modeLorg/lwjgl/opengl/DisplayMode;
current_windowJ
current_window_modeI
decDisplay()V
delete_atomJ
dirtyZ
displayJ
display_connection_usage_countI
doSetGamma(Ljava/nio/ByteBuffer;)V
event_bufferLorg/lwjgl/opengl/LinuxEvent;
focus_listenerLjava/awt/event/FocusListener;
focusedZ
getAvailableDisplayModes()[Lorg/lwjgl/opengl/DisplayMode;
getBestDisplayModeExtension()I
getChildCount(JJ)I
getCurrentGammaRamp()Ljava/nio/ByteBuffer;
getCursorHandle(Ljava/lang/Object;)J
getDefaultScreen()I
getDisplay()J
getErrorText(JJ)Ljava/lang/String;
getHandle(Ljava/awt/Canvas;)J
getParentWindow(JJ)J
getRootWindow(JI)J
getWindow()J
getWindowMode(Z)I
grabZ
grabKeyboard()V
grabPointer()V
hasProperty(JJJ)Z
incDisplay()V
input_releasedZ
internAtom(Ljava/lang/String;Z)J
isAncestorXEmbedded(J)Z
isFullscreen()Z
isLegacyFullscreen()Z
isNetWMFullscreenSupported()Z
isParentWindowActive(J)Z
isXF86VidModeSupported()Z
isXrandrSupported()Z
keyboardLorg/lwjgl/opengl/LinuxKeyboard;
keyboard_grabbedZ
last_window_focusJ
lockAWT()V
mapRaised(JJ)V
minimizedZ
mouseLorg/lwjgl/opengl/LinuxMouse;
mouseInsideZ
nConvertToNativeRamp(Ljava/nio/FloatBuffer;II)Ljava/nio/ByteBuffer;
nCreateBlankCursor(JJ)J
nCreateCursor(JIIIIILjava/nio/IntBuffer;ILjava/nio/IntBuffer;I)J
nCreateWindow(JILjava/nio/ByteBuffer;Lorg/lwjgl/opengl/DisplayMode;IIIZJZ)J
nDefineCursor(JJJ)V
nDestroyCursor(JJ)V
nDestroyWindow(JJ)V
nGetAvailableDisplayModes(JII)[Lorg/lwjgl/opengl/DisplayMode;
nGetCurrentGammaRamp(JI)Ljava/nio/ByteBuffer;
nGetCurrentXRandrMode(JI)Lorg/lwjgl/opengl/DisplayMode;
nGetDefaultScreen(J)I
nGetGammaRampLength(JI)I
nGetHeight(JJ)I
nGetMaxCursorSize(JJ)I
nGetMinCursorSize(JJ)I
nGetNativeCursorCapabilities(J)I
nGetPbufferCapabilities(JI)I
nGetWidth(JJ)I
nGetX(JJ)I
nGetY(JJ)I
nGrabKeyboard(JJ)I
nGrabPointer(JJJ)I
nIconifyWindow(JJI)V
nInternAtom(JLjava/lang/String;Z)J
nIsNetWMFullscreenSupported(JI)Z
nIsXF86VidModeSupported(J)Z
nIsXrandrSupported(J)Z
nLockAWT()V
nReshape(JJIIII)V
nSetClassHint(JJJJ)V
nSetGammaRamp(JILjava/nio/ByteBuffer;)V
nSetInputFocus(JJJ)V
nSetTitle(JJJI)V
nSetViewPort(JJI)V
nSetWindowIcon(JJLjava/nio/ByteBuffer;I)V
nSetWindowSize(JJIIZ)V
nSwitchDisplayMode(JIILorg/lwjgl/opengl/DisplayMode;)V
nSync(JZ)V
nUngrabKeyboard(J)I
nUngrabPointer(J)I
nUnlockAWT()V
openDisplay()J
parentLjava/awt/Canvas;
parent_focus_changedZ
parent_focusedZ
parent_proxy_focus_windowJ
parent_windowJ
peer_infoLorg/lwjgl/opengl/PeerInfo;
pointer_grabbedZ
processEvents()V
relayEventToParent(Lorg/lwjgl/opengl/LinuxEvent;)V
relayEventToParent(Lorg/lwjgl/opengl/LinuxEvent;I)V
releaseInput()V
resizableZ
resizedZ
saved_error_handlerJ
saved_gammaLjava/nio/ByteBuffer;
saved_modeLorg/lwjgl/opengl/DisplayMode;
setClassHint(Ljava/lang/String;Ljava/lang/String;)V
setErrorHandler()J
setFocused(ZI)V
setGammaRampOnTmpDisplay(Ljava/nio/ByteBuffer;)V
setInputFocusUnsafe(J)V
setNativeCursor(Ljava/lang/Object;)V
shouldGrab()Z
shouldWarpPointer()Z
switchDisplayMode(Lorg/lwjgl/opengl/DisplayMode;)V
switchDisplayModeOnTmpDisplay(Lorg/lwjgl/opengl/DisplayMode;)V
tmp_event_bufferLorg/lwjgl/opengl/LinuxEvent;
ungrabKeyboard()V
ungrabPointer()V
unlockAWT()V
updateCursor()V
updateInputGrab()V
updateKeyboardGrab()V
updatePointerGrab()V
window_heightI
window_widthI
window_xI
window_yI
wm_classLjava/lang/String;
xembeddedZ
```

#### `org.lwjgl.opengl.LinuxDisplayPeerInfo`

```text
<init>()V
<init>(Lorg/lwjgl/opengl/PixelFormat;)V
eglZ
getHandle()Ljava/nio/ByteBuffer;
initDefaultPeerInfo(JILjava/nio/ByteBuffer;Lorg/lwjgl/opengl/PixelFormat;)V
initDrawable(JLjava/nio/ByteBuffer;)V
```

#### `org.lwjgl.opengl.LinuxEvent`

```text
<init>()V
copyFrom(Lorg/lwjgl/opengl/LinuxEvent;)V
createEventBuffer()Ljava/nio/ByteBuffer;
event_bufferLjava/nio/ByteBuffer;
filterEvent(J)Z
getButtonButton()I
getButtonRoot()J
getButtonTime()J
getButtonType()I
getButtonX()I
getButtonXRoot()I
getButtonY()I
getButtonYRoot()I
getClientData(I)I
getClientFormat()I
getClientMessageType()J
getFocusDetail()I
getKeyAddress()J
getKeyKeyCode()I
getKeyState()I
getKeyTime()J
getKeyType()I
getPending(J)I
getType()I
getWindow()J
nFilterEvent(Ljava/nio/ByteBuffer;J)Z
nGetButtonButton(Ljava/nio/ByteBuffer;)I
nGetButtonRoot(Ljava/nio/ByteBuffer;)J
nGetButtonState(Ljava/nio/ByteBuffer;)I
nGetButtonTime(Ljava/nio/ByteBuffer;)J
nGetButtonType(Ljava/nio/ByteBuffer;)I
nGetButtonX(Ljava/nio/ByteBuffer;)I
nGetButtonXRoot(Ljava/nio/ByteBuffer;)I
nGetButtonY(Ljava/nio/ByteBuffer;)I
nGetButtonYRoot(Ljava/nio/ByteBuffer;)I
nGetClientData(Ljava/nio/ByteBuffer;I)I
nGetClientFormat(Ljava/nio/ByteBuffer;)I
nGetClientMessageType(Ljava/nio/ByteBuffer;)J
nGetFocusDetail(Ljava/nio/ByteBuffer;)I
nGetFocusMode(Ljava/nio/ByteBuffer;)I
nGetKeyAddress(Ljava/nio/ByteBuffer;)J
nGetKeyKeyCode(Ljava/nio/ByteBuffer;)I
nGetKeyState(Ljava/nio/ByteBuffer;)I
nGetKeyTime(Ljava/nio/ByteBuffer;)I
nGetKeyType(Ljava/nio/ByteBuffer;)I
nGetType(Ljava/nio/ByteBuffer;)I
nGetWindow(Ljava/nio/ByteBuffer;)J
nNextEvent(JLjava/nio/ByteBuffer;)V
nSendEvent(Ljava/nio/ByteBuffer;JJZJ)V
nSetWindow(Ljava/nio/ByteBuffer;J)V
nextEvent(J)V
sendEvent(JJZJ)V
setWindow(J)V
```

#### `org.lwjgl.opengl.LinuxKeyboard`

```text
<init>(JJ)V
allocateComposeStatus()Ljava/nio/ByteBuffer;
caps_lock_maskI
char_bufferLjava/nio/CharBuffer;
closeIM(J)V
compose_statusLjava/nio/ByteBuffer;
createIC(JJ)J
deferred_event_keycodeI
deferred_key_stateB
deferred_keycodeI
deferred_nanosJ
destroy(J)V
destroyIC(J)V
event_queueLorg/lwjgl/opengl/EventQueue;
filterEvent(Lorg/lwjgl/opengl/LinuxEvent;)Z
flushDeferredEvent()V
freeModifierMapping(J)V
getKeyState(I)B
getKeySym(JII)J
getKeycode(JI)I
getMaxKeyPerMod(J)I
getModifierMapping(J)J
handleKeyEvent(JJIII)V
has_deferred_eventZ
isKeypadKeysym(J)Z
isNoSymbolOrVendorSpecific(J)Z
key_down_buffer[B
keycodeToKeySym(JI)J
lookupKeysym(JI)J
lookupModifierMap(JI)I
lookupString(JLjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)I
lookupString(J[I)I
lookupStringISO88591(J[I)I
lookupStringUnicode(J[I)I
mapEventToKeySym(JI)J
modeswitch_maskI
nSetDetectableKeyRepeat(JZ)Z
native_translation_bufferLjava/nio/ByteBuffer;
numlock_maskI
openIM(J)J
poll(Ljava/nio/ByteBuffer;)V
putKeyboardEvent(IBIJZ)V
read(Ljava/nio/ByteBuffer;)V
releaseAll()V
setDetectableKeyRepeat(JZ)V
setupIMEventMask(JJJ)V
shift_lock_maskI
temp_translation_buffer[I
tmp_eventLjava/nio/ByteBuffer;
toUpper(J)J
translateEvent(JIBJZ)V
utf8LookupString(JJLjava/nio/ByteBuffer;II)I
utf8_decoderLjava/nio/charset/CharsetDecoder;
xicJ
ximJ
```

#### `org.lwjgl.opengl.LinuxKeycodes`

```text
mapKeySymToLWJGLKeyCode(J)I
```

#### `org.lwjgl.opengl.LinuxMouse`

```text
<init>(JJJ)V
accum_dxI
accum_dyI
accum_dzI
button_countI
buttons[B
changeGrabbed(ZZ)V
displayJ
doHandlePointerMotion(ZZJIIIIJ)V
doWarpPointer(II)V
event_bufferLjava/nio/ByteBuffer;
event_queueLorg/lwjgl/opengl/EventQueue;
filterEvent(ZZLorg/lwjgl/opengl/LinuxEvent;)Z
getButtonCount()I
handleButton(ZIBJ)V
handleButtonEvent(ZJIB)V
handleButtonPress(ZBJ)V
handlePointerMotion(ZZJJIIII)V
handleWarpEvent(II)V
input_windowJ
last_event_nanosJ
last_xI
last_yI
nGetButtonCount(J)I
nGetWindowHeight(JJ)I
nGetWindowWidth(JJ)I
nQueryPointer(JJLjava/nio/IntBuffer;)J
nSendWarpEvent(JJJII)V
nWarpCursor(JJII)V
poll(ZLjava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
putMouseEvent(ZBBIJ)V
putMouseEventWithCoords(BBIIIJ)V
query_pointer_bufferLjava/nio/IntBuffer;
read(Ljava/nio/ByteBuffer;)V
reset(ZZ)V
resetCursor(II)V
setCursorPos(ZIIJ)V
setCursorPosition(II)V
transformY(I)I
warp_atomJ
windowJ
```

#### `org.lwjgl.opengl.LinuxPbufferPeerInfo`

```text
<init>(IILorg/lwjgl/opengl/PixelFormat;)V
getHandle()Ljava/nio/ByteBuffer;
nDestroy(Ljava/nio/ByteBuffer;)V
nInitHandle(JILjava/nio/ByteBuffer;IILorg/lwjgl/opengl/PixelFormat;)V
```

#### `org.lwjgl.opengl.LinuxPeerInfo`

```text
<init>()V
createHandle()Ljava/nio/ByteBuffer;
destroy()V
getDrawable()J
getHandle()Ljava/nio/ByteBuffer;
lockAndGetHandle()Ljava/nio/ByteBuffer;
nGetDisplay(Ljava/nio/ByteBuffer;)J
nGetDrawable(Ljava/nio/ByteBuffer;)J
unlock()V
```

#### `org.lwjgl.opengl.MacOSXAWTGLCanvasPeerInfo`

```text
<init>(Ljava/awt/Canvas;Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;Z)V
componentLjava/awt/Canvas;
initHandle(Ljava/awt/Canvas;)V
```

#### `org.lwjgl.opengl.MacOSXCanvasImplementation`

```text
<init>()V
```

#### `org.lwjgl.opengl.MacOSXCanvasListener$1`

```text
<init>(Lorg/lwjgl/opengl/MacOSXCanvasListener;)V
this$0Lorg/lwjgl/opengl/MacOSXCanvasListener;
```

#### `org.lwjgl.opengl.MacOSXCanvasListener`

```text
access$000(Lorg/lwjgl/opengl/MacOSXCanvasListener;)Ljava/awt/Canvas;
canvasLjava/awt/Canvas;
context_updateZ
heightI
resizedZ
setUpdate()V
widthI
```

#### `org.lwjgl.opengl.MacOSXCanvasPeerInfo`

```text
<init>(Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;Z)V
access$000(Ljava/awt/Canvas;Ljava/nio/ByteBuffer;)V
addComponentListener(Ljava/awt/Canvas;)V
awt_surfaceLorg/lwjgl/opengl/AWTSurfaceLock;
doUnlock()V
getHandle()Ljava/nio/ByteBuffer;
getInsets(Ljava/awt/Canvas;)Ljava/awt/Insets;
nInitHandle(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;ZZII)Ljava/nio/ByteBuffer;
nSetLayerBounds(Ljava/nio/ByteBuffer;IIII)V
reSetLayerBounds(Ljava/awt/Canvas;Ljava/nio/ByteBuffer;)V
window_handleLjava/nio/ByteBuffer;
```

#### `org.lwjgl.opengl.MacOSXContextImplementation`

```text
<init>()V
clearDrawable(Ljava/nio/ByteBuffer;)V
getCGLShareGroup(Ljava/nio/ByteBuffer;)J
nCreate(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
nDestroy(Ljava/nio/ByteBuffer;)V
nIsCurrent(Ljava/nio/ByteBuffer;)Z
nMakeCurrent(Ljava/nio/ByteBuffer;)V
nReleaseCurrentContext()V
nSetSwapInterval(Ljava/nio/ByteBuffer;I)V
nSwapBuffers(Ljava/nio/ByteBuffer;)V
nUpdate(Ljava/nio/ByteBuffer;)V
setView(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V
```

#### `org.lwjgl.opengl.MacOSXDisplay`

```text
<init>()V
access$000(Lorg/lwjgl/opengl/MacOSXDisplay;)V -> MISSING
access$100(Lorg/lwjgl/opengl/MacOSXDisplay;)Lorg/lwjgl/opengl/MacOSXFrame; -> MISSING
canvasLjava/awt/Canvas;
close_requestedZ
currentNativeCursorJ
current_viewportLjava/nio/IntBuffer;
destroyWindow()V
enableHighDPIZ
equals(Lorg/lwjgl/opengl/DisplayMode;Lorg/lwjgl/opengl/DisplayMode;)Z
getAvailableDisplayModes()[Lorg/lwjgl/opengl/DisplayMode;
getCanvas()Ljava/awt/Canvas;
getCursorHandle(Ljava/lang/Object;)J
getHeight()I
getWidth()I
grabMouse(Z)V
isUndecorated()Z
keyboardLorg/lwjgl/opengl/MacOSXNativeKeyboard;
keyboard_queueLorg/lwjgl/opengl/KeyboardEventQueue;
mouseLorg/lwjgl/opengl/MacOSXNativeMouse;
mouseInsideWindowZ
mouse_queueLorg/lwjgl/opengl/MacOSXMouseEventQueue;
nCreateWindow(IIIIZZZZZZLjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
nDestroyCALayer(Ljava/nio/ByteBuffer;)V
nDestroyWindow(Ljava/nio/ByteBuffer;)V
nGetCurrentDisplayMode()Ljava/lang/Object;
nGetDisplayModes(Ljava/lang/Object;)V
nGetHeight(Ljava/nio/ByteBuffer;)I
nGetWidth(Ljava/nio/ByteBuffer;)I
nGetX(Ljava/nio/ByteBuffer;)I
nGetY(Ljava/nio/ByteBuffer;)I
nIsFocused(Ljava/nio/ByteBuffer;)Z
nIsNativeMode(Ljava/nio/ByteBuffer;)Z
nSetResizable(Ljava/nio/ByteBuffer;Z)V
nSetTitle(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V
nWasResized(Ljava/nio/ByteBuffer;)Z
native_modeZ
requested_modeLorg/lwjgl/opengl/DisplayMode;
restoreGamma()V
robotLjava/awt/Robot;
scaleFactorF
setNativeCursor(Ljava/lang/Object;)V
skipViewportValueZ
updateNativeCursorZ
windowLjava/nio/ByteBuffer;
```

#### `org.lwjgl.opengl.MacOSXDisplayPeerInfo`

```text
<init>(Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;Z)V
initHandle(Ljava/awt/Canvas;)V
lockedZ
```

#### `org.lwjgl.opengl.MacOSXGLCanvas`

```text
<init>()V
canvas_paintedZ
dirtyZ
paint(Ljava/awt/Graphics;)V
requestFocus()V
setFocusTraversalKeysEnabled(Z)V
```

#### `org.lwjgl.opengl.MacOSXMouseEventQueue`

```text
<init>(Ljava/awt/Component;)V
addDelta(II)V
copyEvents(Ljava/nio/ByteBuffer;)V
delta_bufferLjava/nio/IntBuffer;
getComponent()Ljava/awt/Component;
getMouseDeltas(Ljava/nio/IntBuffer;)V
grabMouse(Z)V
isGrabbed()Z
is_grabbedZ
nGrabMouse(Z)V
nWarpCursor(II)V
poll(Ljava/nio/IntBuffer;Ljava/nio/ByteBuffer;)V
putMouseEventWithCoords(BBIIIJ)V
register()V
setGrabbed(Z)V
skip_eventZ
unregister()V
warpCursor()V
```

#### `org.lwjgl.opengl.MacOSXPbufferPeerInfo`

```text
<init>(IILorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;)V
getHandle()Ljava/nio/ByteBuffer;
nCreate(Ljava/nio/ByteBuffer;II)V
nDestroy(Ljava/nio/ByteBuffer;)V
```

#### `org.lwjgl.opengl.MacOSXPeerInfo`

```text
<init>(Lorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;ZZZZ)V
choosePixelFormat(Lorg/lwjgl/opengl/PixelFormat;ZZZZZ)V
createHandle()Ljava/nio/ByteBuffer;
getHandle()Ljava/nio/ByteBuffer;
nChoosePixelFormat(Ljava/nio/ByteBuffer;Lorg/lwjgl/opengl/PixelFormat;ZZZZZ)V
nDestroy(Ljava/nio/ByteBuffer;)V
```

#### `org.lwjgl.opengl.MouseEventQueue`

```text
<init>(Ljava/awt/Component;)V
accum_dxI
accum_dyI
accum_dzI
addDelta(II)V
buttons[B
clearEvents()V
componentLjava/awt/Component;
eventLjava/nio/ByteBuffer;
grabbedZ
handleButton(Ljava/awt/event/MouseEvent;)V
handleMotion(Ljava/awt/event/MouseEvent;)V
handleWheel(IJ)V
last_xI
last_yI
putEvent(Ljava/nio/ByteBuffer;)Z
putMouseEvent(BBIJ)V
putMouseEventWithCoords(BBIIIJ)V
resetCursorToCenter()V
saved_control_stateZ
setButton(BBJ)V
setCursorPos(IIJ)V
setGrabbed(Z)V
transformY(I)I
updateDeltas(J)V
```

#### `org.lwjgl.opengl.NVConditionalRender`

```text
nglBeginConditionalRenderNV(IIJ)V
nglEndConditionalRenderNV(J)V
```

#### `org.lwjgl.opengl.NVCopyDepthToColor`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVCopyImage`

```text
nglCopyImageSubDataNV(IIIIIIIIIIIIIIIJ)V
```

#### `org.lwjgl.opengl.NVDepthBufferFloat`

```text
nglClearDepthdNV(DJ)V
nglDepthBoundsdNV(DDJ)V
nglDepthRangedNV(DDJ)V
```

#### `org.lwjgl.opengl.NVDepthClamp`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVEvaluators`

```text
nglEvalMapsNV(IIJ)V
nglGetMapAttribParameterfvNV(IIIJJ)V
nglGetMapAttribParameterivNV(IIIJJ)V
nglGetMapControlPointsNV(IIIIIZJJ)V
nglGetMapParameterfvNV(IIJJ)V
nglGetMapParameterivNV(IIJJ)V
nglMapControlPointsNV(IIIIIIIZJJ)V
nglMapParameterfvNV(IIJJ)V
nglMapParameterivNV(IIJJ)V
```

#### `org.lwjgl.opengl.NVExplicitMultisample`

```text
nglGetMultisamplefvNV(IIJJ)V
nglSampleMaskIndexedNV(IIJ)V
nglTexRenderbufferNV(IIJ)V
```

#### `org.lwjgl.opengl.NVFence`

```text
nglDeleteFencesNV(IJJ)V
nglFinishFenceNV(IJ)V
nglGenFencesNV(IJJ)V
nglGetFenceivNV(IIJJ)V
nglIsFenceNV(IJ)Z
nglSetFenceNV(IIJ)V
nglTestFenceNV(IJ)Z
```

#### `org.lwjgl.opengl.NVFloatBuffer`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVFogDistance`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVFragmentProgram`

```text
nglGetProgramNamedParameterdvNV(IIJJJ)V
nglGetProgramNamedParameterfvNV(IIJJJ)V
nglProgramNamedParameter4dNV(IIJDDDDJ)V
nglProgramNamedParameter4fNV(IIJFFFFJ)V
```

#### `org.lwjgl.opengl.NVFragmentProgram2`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVFramebufferMultisampleCoverage`

```text
nglRenderbufferStorageMultisampleCoverageNV(IIIIIIJ)V
```

#### `org.lwjgl.opengl.NVGeometryProgram4`

```text
nglProgramVertexLimitNV(IIJ)V
```

#### `org.lwjgl.opengl.NVGpuProgram4`

```text
nglGetProgramEnvParameterIivNV(IIJJ)V
nglGetProgramEnvParameterIuivNV(IIJJ)V
nglGetProgramLocalParameterIivNV(IIJJ)V
nglGetProgramLocalParameterIuivNV(IIJJ)V
nglProgramEnvParameterI4iNV(IIIIIIJ)V
nglProgramEnvParameterI4ivNV(IIJJ)V
nglProgramEnvParameterI4uiNV(IIIIIIJ)V
nglProgramEnvParameterI4uivNV(IIJJ)V
nglProgramEnvParametersI4ivNV(IIIJJ)V
nglProgramEnvParametersI4uivNV(IIIJJ)V
nglProgramLocalParameterI4iNV(IIIIIIJ)V
nglProgramLocalParameterI4ivNV(IIJJ)V
nglProgramLocalParameterI4uiNV(IIIIIIJ)V
nglProgramLocalParameterI4uivNV(IIJJ)V
nglProgramLocalParametersI4ivNV(IIIJJ)V
nglProgramLocalParametersI4uivNV(IIIJJ)V
```

#### `org.lwjgl.opengl.NVGpuProgram5`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVGpuShader5`

```text
glGetUniformuNV(IILjava/nio/LongBuffer;)V
nglGetUniformi64vNV(IIJJ)V
nglGetUniformui64vNV(IIJJ)V
nglProgramUniform1i64NV(IIJJ)V
nglProgramUniform1i64vNV(IIIJJ)V
nglProgramUniform1ui64NV(IIJJ)V
nglProgramUniform1ui64vNV(IIIJJ)V
nglProgramUniform2i64NV(IIJJJ)V
nglProgramUniform2i64vNV(IIIJJ)V
nglProgramUniform2ui64NV(IIJJJ)V
nglProgramUniform2ui64vNV(IIIJJ)V
nglProgramUniform3i64NV(IIJJJJ)V
nglProgramUniform3i64vNV(IIIJJ)V
nglProgramUniform3ui64NV(IIJJJJ)V
nglProgramUniform3ui64vNV(IIIJJ)V
nglProgramUniform4i64NV(IIJJJJJ)V
nglProgramUniform4i64vNV(IIIJJ)V
nglProgramUniform4ui64NV(IIJJJJJ)V
nglProgramUniform4ui64vNV(IIIJJ)V
nglUniform1i64NV(IJJ)V
nglUniform1i64vNV(IIJJ)V
nglUniform1ui64NV(IJJ)V
nglUniform1ui64vNV(IIJJ)V
nglUniform2i64NV(IJJJ)V
nglUniform2i64vNV(IIJJ)V
nglUniform2ui64NV(IJJJ)V
nglUniform2ui64vNV(IIJJ)V
nglUniform3i64NV(IJJJJ)V
nglUniform3i64vNV(IIJJ)V
nglUniform3ui64NV(IJJJJ)V
nglUniform3ui64vNV(IIJJ)V
nglUniform4i64NV(IJJJJJ)V
nglUniform4i64vNV(IIJJ)V
nglUniform4ui64NV(IJJJJJ)V
nglUniform4ui64vNV(IIJJ)V
```

#### `org.lwjgl.opengl.NVHalfFloat`

```text
nglColor3hNV(SSSJ)V
nglColor4hNV(SSSSJ)V
nglFogCoordhNV(SJ)V
nglMultiTexCoord1hNV(ISJ)V
nglMultiTexCoord2hNV(ISSJ)V
nglMultiTexCoord3hNV(ISSSJ)V
nglMultiTexCoord4hNV(ISSSSJ)V
nglNormal3hNV(SSSJ)V
nglSecondaryColor3hNV(SSSJ)V
nglTexCoord1hNV(SJ)V
nglTexCoord2hNV(SSJ)V
nglTexCoord3hNV(SSSJ)V
nglTexCoord4hNV(SSSSJ)V
nglVertex2hNV(SSJ)V
nglVertex3hNV(SSSJ)V
nglVertex4hNV(SSSSJ)V
nglVertexAttrib1hNV(ISJ)V
nglVertexAttrib2hNV(ISSJ)V
nglVertexAttrib3hNV(ISSSJ)V
nglVertexAttrib4hNV(ISSSSJ)V
nglVertexAttribs1hvNV(IIJJ)V
nglVertexAttribs2hvNV(IIJJ)V
nglVertexAttribs3hvNV(IIJJ)V
nglVertexAttribs4hvNV(IIJJ)V
nglVertexWeighthNV(SJ)V
```

#### `org.lwjgl.opengl.NVLightMaxExponent`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVMultisampleCoverage`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVMultisampleFilterHint`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVOcclusionQuery`

```text
nglBeginOcclusionQueryNV(IJ)V
nglDeleteOcclusionQueriesNV(IJJ)V
nglEndOcclusionQueryNV(J)V
nglGenOcclusionQueriesNV(IJJ)V
nglGetOcclusionQueryivNV(IIJJ)V
nglGetOcclusionQueryuivNV(IIJJ)V
nglIsOcclusionQueryNV(IJ)Z
```

#### `org.lwjgl.opengl.NVPackedDepthStencil`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVParameterBufferObject`

```text
nglProgramBufferParametersIivNV(IIIIJJ)V
nglProgramBufferParametersIuivNV(IIIIJJ)V
nglProgramBufferParametersfvNV(IIIIJJ)V
```

#### `org.lwjgl.opengl.NVPathRendering`

```text
nglCopyPathNV(IIJ)V
nglCoverFillPathInstancedNV(IIJIIIJJ)V
nglCoverFillPathNV(IIJ)V
nglCoverStrokePathInstancedNV(IIJIIIJJ)V
nglCoverStrokePathNV(IIJ)V
nglDeletePathsNV(IIJ)V
nglGenPathsNV(IJ)I
nglGetPathColorGenfvNV(IIJJ)V
nglGetPathColorGenivNV(IIJJ)V
nglGetPathCommandsNV(IJJ)V
nglGetPathCoordsNV(IJJ)V
nglGetPathDashArrayNV(IJJ)V
nglGetPathLengthNV(IIIJ)F
nglGetPathMetricRangeNV(IIIIJJ)V
nglGetPathMetricsNV(IIIJIIJJ)V
nglGetPathParameterfvNV(IIJJ)V
nglGetPathParameterivNV(IIJJ)V
nglGetPathSpacingNV(IIIJIFFIJJ)V
nglGetPathTexGenfvNV(IIJJ)V
nglGetPathTexGenivNV(IIJJ)V
nglInterpolatePathsNV(IIIFJ)V
nglIsPathNV(IJ)Z
nglIsPointInFillPathNV(IIFFJ)Z
nglIsPointInStrokePathNV(IFFJ)Z
nglPathColorGenNV(IIIJJ)V
nglPathCommandsNV(IIJIIJJ)V
nglPathCoordsNV(IIIJJ)V
nglPathCoverDepthFuncNV(IJ)V
nglPathDashArrayNV(IIJJ)V
nglPathFogGenNV(IJ)V
nglPathGlyphRangeNV(IIJIIIIIFJ)V
nglPathGlyphsNV(IIJIIIJIIFJ)V
nglPathParameterfNV(IIFJ)V
nglPathParameterfvNV(IIJJ)V
nglPathParameteriNV(IIIJ)V
nglPathParameterivNV(IIJJ)V
nglPathStencilDepthOffsetNV(FIJ)V
nglPathStencilFuncNV(IIIJ)V
nglPathStringNV(IIIJJ)V
nglPathSubCommandsNV(IIIIJIIJJ)V
nglPathSubCoordsNV(IIIIJJ)V
nglPathTexGenNV(IIIJJ)V
nglPointAlongPathNV(IIIFJJJJJ)Z
nglStencilFillPathInstancedNV(IIJIIIIJJ)V
nglStencilFillPathNV(IIIJ)V
nglStencilStrokePathInstancedNV(IIJIIIIJJ)V
nglStencilStrokePathNV(IIIJ)V
nglTransformPathNV(IIIJJ)V
nglWeightPathsNV(IIJJJ)V
```

#### `org.lwjgl.opengl.NVPixelDataRange`

```text
nglFlushPixelDataRangeNV(IJ)V
nglPixelDataRangeNV(IIJJ)V
```

#### `org.lwjgl.opengl.NVPointSprite`

```text
nglPointParameteriNV(IIJ)V
nglPointParameterivNV(IJJ)V
```

#### `org.lwjgl.opengl.NVPresentVideo`

```text
nglGetVideoi64vNV(IIJJ)V
nglGetVideoivNV(IIJJ)V
nglGetVideoui64vNV(IIJJ)V
nglGetVideouivNV(IIJJ)V
nglPresentFrameDualFillNV(IJIIIIIIIIIIIJ)V
nglPresentFrameKeyedNV(IJIIIIIIIIIJ)V
```

#### `org.lwjgl.opengl.NVPresentVideoUtil`

```text
checkExtension()V
getPeerInfo()Ljava/nio/ByteBuffer;
nglBindVideoDeviceNV(Ljava/nio/ByteBuffer;IJLjava/nio/IntBuffer;I)Z
nglEnumerateVideoDevicesNV(Ljava/nio/ByteBuffer;Ljava/nio/LongBuffer;I)I
nglQueryContextNV(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;ILjava/nio/IntBuffer;I)Z
```

#### `org.lwjgl.opengl.NVPrimitiveRestart`

```text
nglPrimitiveRestartIndexNV(IJ)V
nglPrimitiveRestartNV(J)V
```

#### `org.lwjgl.opengl.NVProgram`

```text
<init>()V
glGetProgramiNV(II)I
nglAreProgramsResidentNV(IJJJ)Z
nglBindProgramNV(IIJ)V
nglDeleteProgramsNV(IJJ)V
nglGenProgramsNV(IJJ)V
nglGetProgramStringNV(IIJJ)V
nglGetProgramivNV(IIJJ)V
nglIsProgramNV(IJ)Z
nglLoadProgramNV(IIIJJ)V
nglRequestResidentProgramsNV(IJJ)V
```

#### `org.lwjgl.opengl.NVRegisterCombiners`

```text
nglCombinerInputNV(IIIIIIJ)V
nglCombinerOutputNV(IIIIIIIZZZJ)V
nglCombinerParameterfNV(IFJ)V
nglCombinerParameterfvNV(IJJ)V
nglCombinerParameteriNV(IIJ)V
nglCombinerParameterivNV(IJJ)V
nglFinalCombinerInputNV(IIIIJ)V
nglGetCombinerInputParameterfvNV(IIIIJJ)V
nglGetCombinerInputParameterivNV(IIIIJJ)V
nglGetCombinerOutputParameterfvNV(IIIJJ)V
nglGetCombinerOutputParameterivNV(IIIJJ)V
nglGetFinalCombinerInputParameterfvNV(IIJJ)V
nglGetFinalCombinerInputParameterivNV(IIJJ)V
```

#### `org.lwjgl.opengl.NVRegisterCombiners2`

```text
nglCombinerStageParameterfvNV(IIJJ)V
nglGetCombinerStageParameterfvNV(IIJJ)V
```

#### `org.lwjgl.opengl.NVShaderBufferLoad`

```text
nglGetBufferParameterui64vNV(IIJJ)V
nglGetIntegerui64vNV(IJJ)V
nglGetNamedBufferParameterui64vNV(IIJJ)V
nglIsBufferResidentNV(IJ)Z
nglIsNamedBufferResidentNV(IJ)Z
nglMakeBufferNonResidentNV(IJ)V
nglMakeBufferResidentNV(IIJ)V
nglMakeNamedBufferNonResidentNV(IJ)V
nglMakeNamedBufferResidentNV(IIJ)V
nglProgramUniformui64NV(IIJJ)V
nglProgramUniformui64vNV(IIIJJ)V
nglUniformui64NV(IJJ)V
nglUniformui64vNV(IIJJ)V
```

#### `org.lwjgl.opengl.NVShaderBufferStore`

No incoming member constant found; dynamic/native path remains unexecuted.

#### `org.lwjgl.opengl.NVTessellationProgram5`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVTexgenReflection`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVTextureBarrier`

```text
nglTextureBarrierNV(J)V
```

#### `org.lwjgl.opengl.NVTextureCompressionVTC`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVTextureEnvCombine4`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVTextureExpandNormal`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVTextureMultisample`

```text
nglTexImage2DMultisampleCoverageNV(IIIIIIZJ)V
nglTexImage3DMultisampleCoverageNV(IIIIIIIZJ)V
nglTextureImage2DMultisampleCoverageNV(IIIIIIIZJ)V
nglTextureImage2DMultisampleNV(IIIIIIZJ)V
nglTextureImage3DMultisampleCoverageNV(IIIIIIIIZJ)V
nglTextureImage3DMultisampleNV(IIIIIIIZJ)V
```

#### `org.lwjgl.opengl.NVTextureRectangle`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVTextureShader`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVTextureShader2`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVTextureShader3`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVTransformFeedback`

```text
nglActiveVaryingNV(IJJ)V
nglBeginTransformFeedbackNV(IJ)V
nglBindBufferBaseNV(IIIJ)V
nglBindBufferOffsetNV(IIIJJ)V
nglBindBufferRangeNV(IIIJJJ)V
nglEndTransformFeedbackNV(J)V
nglGetActiveVaryingNV(IIIJJJJJ)V
nglGetTransformFeedbackVaryingNV(IIJJ)V
nglGetVaryingLocationNV(IJJ)I
nglTransformFeedbackAttribsNV(IJIJ)V
nglTransformFeedbackVaryingsNV(IIJIJ)V
```

#### `org.lwjgl.opengl.NVTransformFeedback2`

```text
nglBindTransformFeedbackNV(IIJ)V
nglDeleteTransformFeedbacksNV(IJJ)V
nglDrawTransformFeedbackNV(IIJ)V
nglGenTransformFeedbacksNV(IJJ)V
nglIsTransformFeedbackNV(IJ)Z
nglPauseTransformFeedbackNV(J)V
nglResumeTransformFeedbackNV(J)V
```

#### `org.lwjgl.opengl.NVVertexArrayRange`

```text
nglAllocateMemoryNV(IFFFJJ)Ljava/nio/ByteBuffer;
nglFlushVertexArrayRangeNV(J)V
nglFreeMemoryNV(JJ)V
nglVertexArrayRangeNV(IJJ)V
```

#### `org.lwjgl.opengl.NVVertexArrayRange2`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVVertexAttribInteger64bit`

```text
nglGetVertexAttribLi64vNV(IIJJ)V
nglGetVertexAttribLui64vNV(IIJJ)V
nglVertexAttribL1i64NV(IJJ)V
nglVertexAttribL1i64vNV(IJJ)V
nglVertexAttribL1ui64NV(IJJ)V
nglVertexAttribL1ui64vNV(IJJ)V
nglVertexAttribL2i64NV(IJJJ)V
nglVertexAttribL2i64vNV(IJJ)V
nglVertexAttribL2ui64NV(IJJJ)V
nglVertexAttribL2ui64vNV(IJJ)V
nglVertexAttribL3i64NV(IJJJJ)V
nglVertexAttribL3i64vNV(IJJ)V
nglVertexAttribL3ui64NV(IJJJJ)V
nglVertexAttribL3ui64vNV(IJJ)V
nglVertexAttribL4i64NV(IJJJJJ)V
nglVertexAttribL4i64vNV(IJJ)V
nglVertexAttribL4ui64NV(IJJJJJ)V
nglVertexAttribL4ui64vNV(IJJ)V
nglVertexAttribLFormatNV(IIIIJ)V
```

#### `org.lwjgl.opengl.NVVertexBufferUnifiedMemory`

```text
nglBufferAddressRangeNV(IIJJJ)V
nglColorFormatNV(IIIJ)V
nglEdgeFlagFormatNV(IJ)V
nglFogCoordFormatNV(IIJ)V
nglGetIntegerui64i_vNV(IIJJ)V
nglIndexFormatNV(IIJ)V
nglNormalFormatNV(IIJ)V
nglSecondaryColorFormatNV(IIIJ)V
nglTexCoordFormatNV(IIIJ)V
nglVertexAttribFormatNV(IIIZIJ)V
nglVertexAttribIFormatNV(IIIIJ)V
nglVertexFormatNV(IIIJ)V
```

#### `org.lwjgl.opengl.NVVertexProgram`

```text
nglExecuteProgramNV(IIJJ)V
nglGetProgramParameterdvNV(IIIJJ)V
nglGetProgramParameterfvNV(IIIJJ)V
nglGetTrackMatrixivNV(IIIJJ)V
nglGetVertexAttribPointervNV(IIJJ)Ljava/nio/ByteBuffer;
nglGetVertexAttribdvNV(IIJJ)V
nglGetVertexAttribfvNV(IIJJ)V
nglGetVertexAttribivNV(IIJJ)V
nglProgramParameter4dNV(IIDDDDJ)V
nglProgramParameter4fNV(IIFFFFJ)V
nglProgramParameters4dvNV(IIIJJ)V
nglProgramParameters4fvNV(IIIJJ)V
nglTrackMatrixNV(IIIIJ)V
nglVertexAttrib1dNV(IDJ)V
nglVertexAttrib1fNV(IFJ)V
nglVertexAttrib1sNV(ISJ)V
nglVertexAttrib2dNV(IDDJ)V
nglVertexAttrib2fNV(IFFJ)V
nglVertexAttrib2sNV(ISSJ)V
nglVertexAttrib3dNV(IDDDJ)V
nglVertexAttrib3fNV(IFFFJ)V
nglVertexAttrib3sNV(ISSSJ)V
nglVertexAttrib4dNV(IDDDDJ)V
nglVertexAttrib4fNV(IFFFFJ)V
nglVertexAttrib4sNV(ISSSSJ)V
nglVertexAttrib4ubNV(IBBBBJ)V
nglVertexAttribPointerNV(IIIIJJ)V
nglVertexAttribPointerNVBO(IIIIJJ)V
nglVertexAttribs1dvNV(IIJJ)V
nglVertexAttribs1fvNV(IIJJ)V
nglVertexAttribs1svNV(IIJJ)V
nglVertexAttribs2dvNV(IIJJ)V
nglVertexAttribs2fvNV(IIJJ)V
nglVertexAttribs2svNV(IIJJ)V
nglVertexAttribs3dvNV(IIJJ)V
nglVertexAttribs3fvNV(IIJJ)V
nglVertexAttribs3svNV(IIJJ)V
nglVertexAttribs4dvNV(IIJJ)V
nglVertexAttribs4fvNV(IIJJ)V
nglVertexAttribs4svNV(IIJJ)V
```

#### `org.lwjgl.opengl.NVVertexProgram2Option`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVVertexProgram3`

Identical byte redundancy.

#### `org.lwjgl.opengl.NVVideoCapture`

```text
nglBeginVideoCaptureNV(IJ)V
nglBindVideoCaptureStreamBufferNV(IIIJJ)V
nglBindVideoCaptureStreamTextureNV(IIIIIJ)V
nglEndVideoCaptureNV(IJ)V
nglGetVideoCaptureStreamdvNV(IIIJJ)V
nglGetVideoCaptureStreamfvNV(IIIJJ)V
nglGetVideoCaptureStreamivNV(IIIJJ)V
nglGetVideoCaptureivNV(IIJJ)V
nglVideoCaptureNV(IJJJ)I
nglVideoCaptureStreamParameterdvNV(IIIJJ)V
nglVideoCaptureStreamParameterfvNV(IIIJJ)V
nglVideoCaptureStreamParameterivNV(IIIJJ)V
```

#### `org.lwjgl.opengl.NVVideoCaptureUtil`

```text
checkExtension()V
getPeerInfo()Ljava/nio/ByteBuffer;
nglBindVideoCaptureDeviceNV(Ljava/nio/ByteBuffer;IJ)Z
nglEnumerateVideoCaptureDevicesNV(Ljava/nio/ByteBuffer;Ljava/nio/LongBuffer;I)I
nglLockVideoCaptureDeviceNV(Ljava/nio/ByteBuffer;J)Z
nglQueryVideoCaptureDeviceNV(Ljava/nio/ByteBuffer;JILjava/nio/IntBuffer;I)Z
nglReleaseVideoCaptureDeviceNV(Ljava/nio/ByteBuffer;J)Z
```

#### `org.lwjgl.opengl.NVXGpuMemoryInfo`

Identical byte redundancy.

#### `org.lwjgl.opengl.OpenGLException`

```text
<init>(I)V
<init>(Ljava/lang/String;)V
createErrorMessage(I)Ljava/lang/String;
getMessage()Ljava/lang/String;
```

#### `org.lwjgl.opengl.Pbuffer`

```text
<init>(IILorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/RenderTexture;Lorg/lwjgl/opengl/Drawable;)V
<init>(IILorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/RenderTexture;Lorg/lwjgl/opengl/Drawable;Lorg/lwjgl/opengl/ContextAttribs;)V
checkDestroyed()V
contextLorg/lwjgl/opengl/ContextGL;
createPbuffer(IILorg/lwjgl/opengl/PixelFormat;Lorg/lwjgl/opengl/ContextAttribs;Lorg/lwjgl/opengl/RenderTexture;)Lorg/lwjgl/opengl/PeerInfo;
heightI
peer_infoLorg/lwjgl/opengl/PeerInfo;
widthI
```

#### `org.lwjgl.opengl.PeerInfo`

```text
<init>(Ljava/nio/ByteBuffer;)V
destroy()V
doLockAndInitHandle()V
doUnlock()V
getHandle()Ljava/nio/ByteBuffer;
handleLjava/nio/ByteBuffer;
lockAndGetHandle()Ljava/nio/ByteBuffer;
lock_countI
locking_threadLjava/lang/Thread;
unlock()V
```

#### `org.lwjgl.opengl.PixelFormat`

```text
<init>()V
<init>(III)V
<init>(IIII)V
<init>(IIIII)V
<init>(IIIIIIIIZ)V
<init>(IIIIIIIIZZ)V
<init>(Lorg/lwjgl/opengl/PixelFormat;)V
accum_alphaI
accum_bppI
alphaI
bppI
colorSamplesI
depthI
floating_pointZ
floating_point_packedZ
num_aux_buffersI
sRGBZ
samplesI
stencilI
stereoZ
withCoverageSamples(II)Lorg/lwjgl/opengl/PixelFormat;
withDepthBits(I)Lorg/lwjgl/opengl/PixelFormat;
withSamples(I)Lorg/lwjgl/opengl/PixelFormat;
```

#### `org.lwjgl.opengl.PixelFormatLWJGL`

Identical byte redundancy.

#### `org.lwjgl.opengl.References`

```text
<init>(Lorg/lwjgl/opengl/ContextCapabilities;)V
ARB_matrix_palette_glMatrixIndexPointerARB_pPointerLjava/nio/Buffer;
ARB_vertex_blend_glWeightPointerARB_pPointerLjava/nio/Buffer;
EXT_fog_coord_glFogCoordPointerEXT_dataLjava/nio/Buffer;
EXT_secondary_color_glSecondaryColorPointerEXT_pPointerLjava/nio/Buffer;
EXT_vertex_shader_glVariantPointerEXT_pAddrLjava/nio/Buffer;
EXT_vertex_weighting_glVertexWeightPointerEXT_pPointerLjava/nio/Buffer;
GL11_glColorPointer_pointerLjava/nio/Buffer;
GL11_glEdgeFlagPointer_pointerLjava/nio/Buffer;
GL11_glNormalPointer_pointerLjava/nio/Buffer;
GL11_glVertexPointer_pointerLjava/nio/Buffer;
GL14_glFogCoordPointer_dataLjava/nio/Buffer;
arrayBufferI
clear()V
copy(Lorg/lwjgl/opengl/References;I)V
glClientActiveTextureI
glTexCoordPointer_buffer[Ljava/nio/Buffer;
glVertexAttribPointer_buffer[Ljava/nio/Buffer;
indirectBufferI
pixelPackBufferI
pixelUnpackBufferI
vertexArrayObjectI
```

#### `org.lwjgl.opengl.ReferencesStack`

```text
<init>()V
getReferences()Lorg/lwjgl/opengl/References;
growStack()V
popState(I)Lorg/lwjgl/opengl/References;
pushState()V
references_stack[Lorg/lwjgl/opengl/References;
stack_posI
```

#### `org.lwjgl.opengl.RenderTexture`

```text
pBufferAttribsLjava/nio/IntBuffer;
pixelFormatCapsLjava/nio/IntBuffer;
```

#### `org.lwjgl.opengl.SGISGenerateMipmap`

Identical byte redundancy.

#### `org.lwjgl.opengl.SGISTextureLOD`

Identical byte redundancy.

#### `org.lwjgl.opengl.SUNSliceAccum`

Identical byte redundancy.

#### `org.lwjgl.opengl.SharedDrawable`

```text
contextLorg/lwjgl/opengl/ContextGL;
createSharedContext()Lorg/lwjgl/opengl/ContextGL;
```

#### `org.lwjgl.opengl.StateStack`

```text
<init>(I)V
growState()V
popState()I
pushState(I)V
stack_posI
state_stack[I
```

#### `org.lwjgl.opengl.StateTracker$1`

Identical byte redundancy.

#### `org.lwjgl.opengl.StateTracker$VAOState`

```text
<init>()V
<init>(Lorg/lwjgl/opengl/StateTracker$1;)V
elementArrayBufferI
```

#### `org.lwjgl.opengl.StateTracker`

```text
<init>()V
attrib_stackLorg/lwjgl/opengl/StateStack;
bindBuffer(Lorg/lwjgl/opengl/ContextCapabilities;II)V
bindVAO(Lorg/lwjgl/opengl/ContextCapabilities;I)V
deleteVAO(Lorg/lwjgl/opengl/ContextCapabilities;I)V
deleteVAO(Lorg/lwjgl/opengl/ContextCapabilities;Ljava/nio/IntBuffer;)V
doPopAttrib()V
doPushAttrib(I)V
getElementArrayBufferBound(Lorg/lwjgl/opengl/ContextCapabilities;)I
getReferences(Lorg/lwjgl/opengl/ContextCapabilities;)Lorg/lwjgl/opengl/References;
init()V
insideBeginEndZ
popAttrib(Lorg/lwjgl/opengl/ContextCapabilities;)V
pushAttrib(Lorg/lwjgl/opengl/ContextCapabilities;I)V
references_stackLorg/lwjgl/opengl/ReferencesStack;
vaoMapLorg/lwjgl/opengl/FastIntMap;
```

#### `org.lwjgl.opengl.Util`

```text
checkGLError()V
translateGLErrorString(I)Ljava/lang/String;
```

#### `org.lwjgl.opengl.WindowsAWTGLCanvasPeerInfo`

```text
<init>(Ljava/awt/Canvas;Lorg/lwjgl/opengl/PixelFormat;)V
awt_surfaceLorg/lwjgl/opengl/AWTSurfaceLock;
choosePixelFormat(JIILorg/lwjgl/opengl/PixelFormat;Ljava/nio/IntBuffer;ZZZZ)I
componentLjava/awt/Canvas;
getHandle()Ljava/nio/ByteBuffer;
getHdc()J
has_pixel_formatZ
nInitHandle(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V
pixel_formatLorg/lwjgl/opengl/PixelFormat;
setPixelFormat(JI)V
```

#### `org.lwjgl.opengl.WindowsCanvasImplementation$1`

```text
<init>()V
```

#### `org.lwjgl.opengl.WindowsCanvasImplementation`

```text
<init>()V
```

#### `org.lwjgl.opengl.WindowsContextImplementation`

```text
<init>()V
getHDC(Ljava/nio/ByteBuffer;)J
getHGLRC(Ljava/nio/ByteBuffer;)J
nCreate(Ljava/nio/ByteBuffer;Ljava/nio/IntBuffer;Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
nDestroy(Ljava/nio/ByteBuffer;)V
nIsCurrent(Ljava/nio/ByteBuffer;)Z
nMakeCurrent(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V
nReleaseCurrentContext()V
nSetSwapInterval(I)Z
nSwapBuffers(Ljava/nio/ByteBuffer;)V
```

#### `org.lwjgl.opengl.WindowsDisplay$1`

```text
<init>(Lorg/lwjgl/opengl/WindowsDisplay;)V
this$0Lorg/lwjgl/opengl/WindowsDisplay;
```

#### `org.lwjgl.opengl.WindowsDisplay$Rect`

```text
<init>()V
<init>(Lorg/lwjgl/opengl/WindowsDisplay$1;)V
bottomI
copyFromBuffer(Ljava/nio/IntBuffer;)V
copyToBuffer(Ljava/nio/IntBuffer;)V
leftI
offset(II)V
rightI
topI
```

#### `org.lwjgl.opengl.WindowsDisplay`

```text
<init>()V
access$100(Lorg/lwjgl/opengl/WindowsDisplay;)Ljava/util/concurrent/atomic/AtomicBoolean;
access$200(Lorg/lwjgl/opengl/WindowsDisplay;)V
access$300(Lorg/lwjgl/opengl/WindowsDisplay;)Ljava/awt/Canvas;
adjustWindowRectEx(Ljava/nio/IntBuffer;IZI)Z
appActivate(ZJ)V
captureMouseI
centerCursor(J)V
clearAWTFocus()V
clientToScreen(JLjava/nio/IntBuffer;)V
clipCursor(Ljava/nio/IntBuffer;)V
close_requestedZ
convertToNativeRamp(Ljava/nio/FloatBuffer;)Ljava/nio/ByteBuffer;
createIcon(IILjava/nio/IntBuffer;)J
current_cursorLjava/lang/Object;
current_displayLorg/lwjgl/opengl/WindowsDisplay;
current_gammaLjava/nio/ByteBuffer;
current_modeLorg/lwjgl/opengl/DisplayMode;
cursor_clippedZ
defWindowProc(JIJJ)J
destroyIcon(J)V
doCreateCursor(IIIIILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)Ljava/lang/Object;
doDestroyCursor(Ljava/lang/Object;)V
doHandleMessage(JIJJJ)J
doSetGammaRamp(Ljava/nio/ByteBuffer;)V
freeLargeIcon()V
freeSmallIcon()V
getAdapter()Ljava/lang/String;
getClientRect(JLjava/nio/IntBuffer;)V
getCurrentDisplayMode()Lorg/lwjgl/opengl/DisplayMode;
getCurrentGammaRamp()Ljava/nio/ByteBuffer;
getDC(J)J
getForegroundWindow()J
getGlobalClientRect(JLorg/lwjgl/opengl/WindowsDisplay$Rect;)V
getHdc()J
getHwnd()J
getHwnd(Ljava/awt/Canvas;)J
getSystemMetrics(I)I
getWindowLongPtr(JI)J
getWindowRect(JLjava/nio/IntBuffer;)Z
grabFocus()V
handleChar(JJJ)V
handleKeyButton(JJJ)V
handleMouseButton(IIJ)V
handleMouseScrolled(IJ)V
hasParentZ
hdcJ
heightI
hwndJ
iconsLoadedZ
inAppActivateZ
isActive()Z
isFocusedZ
isMinimizedZ
isUndecorated()Z
isVisible()Z
is_dirtyZ
keyboardLorg/lwjgl/opengl/WindowsKeyboard;
large_iconJ
mode_setZ
mouseLorg/lwjgl/opengl/WindowsMouse;
mouseInsideZ
nCreateCursor(IIIIILjava/nio/IntBuffer;ILjava/nio/IntBuffer;I)Ljava/nio/ByteBuffer;
nCreateWindow(IIIIZZJ)J
nDestroyWindow(J)V
nGetPbufferCapabilities(Lorg/lwjgl/opengl/PixelFormat;)I
nGetVersion(Ljava/lang/String;)Lorg/lwjgl/opengl/WindowsFileVersion;
nReleaseCapture()Z
nReleaseDC(JJ)V
nResetDisplayMode()V
nReshape(JIIIIZZ)V
nSetCapture(J)J
nSetCursorPosition(II)V
nSetGammaRamp(Ljava/nio/ByteBuffer;)V
nSetNativeCursor(JLjava/lang/Object;)V
nSetTitle(JJ)V
nSwitchDisplayMode(Lorg/lwjgl/opengl/DisplayMode;)V
nTrackMouseEvent(J)Z
nUpdate()V
parentLjava/awt/Canvas;
parent_focus_trackerLjava/awt/event/FocusAdapter;
parent_focusedLjava/util/concurrent/atomic/AtomicBoolean;
parent_hwndJ
peer_infoLorg/lwjgl/opengl/WindowsDisplayPeerInfo;
rectLorg/lwjgl/opengl/WindowsDisplay$Rect;
rect_bufferLjava/nio/IntBuffer;
redoMakeContextCurrentZ
resetCursorClipping()V
resetDisplayMode()V
resizableZ
resizedZ
restoreDisplayMode()V
saved_gammaLjava/nio/ByteBuffer;
sendMessage(JJJJ)J
setFocus(J)V
setForegroundWindow(J)V
setMinimized(Z)V
setMousePosition(II)V
setResizable(Z)V
setWindowLongPtr(JIJ)J
setWindowPos(JJIIIIJ)Z
setWindowProc(Ljava/lang/reflect/Method;)V
setupCursorClipping(J)V
shouldGrab()Z
showWindow(JI)V
small_iconJ
transformY(JI)I
updateClipping()V
updateCursor()V
updateWidthAndHeight()V
widthI
xI
yI
```

#### `org.lwjgl.opengl.WindowsDisplayPeerInfo`

```text
<init>(Z)V
eglZ
getHandle()Ljava/nio/ByteBuffer;
initDC(JJ)V
nInitDC(Ljava/nio/ByteBuffer;JJ)V
```

#### `org.lwjgl.opengl.WindowsFileVersion`

```text
product_version_lsI
product_version_msI
toString()Ljava/lang/String;
```

#### `org.lwjgl.opengl.WindowsKeyboard`

```text
<init>()V
GetAsyncKeyState(I)S
event_queueLorg/lwjgl/opengl/EventQueue;
flushRetained()V
handleChar(IJZ)V
handleKey(IIZBJZ)V
has_retained_eventZ
isKeyDown(I)Z
isKeyPressed(I)Z
isKeyPressedAsync(I)Z
key_down_buffer[B
poll(Ljava/nio/ByteBuffer;)V
putEvent(IBIJZ)V
read(Ljava/nio/ByteBuffer;)V
releaseAll(J)V
retained_charI
retained_key_codeI
retained_millisJ
retained_repeatZ
retained_stateB
tmp_eventLjava/nio/ByteBuffer;
translateExtended(IIZ)I
virt_key_down_buffer[B
```

#### `org.lwjgl.opengl.WindowsKeycodes`

```text
mapVirtualKeyToLWJGLCode(I)I
```

#### `org.lwjgl.opengl.WindowsMouse`

```text
<init>(J)V
accum_dwheelI
accum_dxI
accum_dyI
blank_cursorLjava/lang/Object;
button_states[B
createBlankCursor()Ljava/lang/Object;
destroy()V
event_queueLorg/lwjgl/opengl/EventQueue;
getBlankCursor()Ljava/lang/Object;
getButtonCount()I
grab(Z)V
handleMouseButton(BBJ)V
handleMouseMoved(IIJ)V
handleMouseScrolled(IJ)V
hasWheel()Z
has_wheelZ
hwndJ
isGrabbed()Z
last_xI
last_yI
mouse_button_countI
mouse_eventLjava/nio/ByteBuffer;
mouse_grabbedZ
poll(Ljava/nio/IntBuffer;Ljava/nio/ByteBuffer;Lorg/lwjgl/opengl/WindowsDisplay;)V
putMouseEvent(BBIJ)V
putMouseEventWithCoords(BBIIIJ)V
read(Ljava/nio/ByteBuffer;)V
setPosition(II)V
```

#### `org.lwjgl.opengl.WindowsPbufferPeerInfo`

```text
<init>(IILorg/lwjgl/opengl/PixelFormat;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;)V
bindTexImageToPbuffer(I)V
getHandle()Ljava/nio/ByteBuffer;
isBufferLost()Z
nBindTexImageToPbuffer(Ljava/nio/ByteBuffer;I)V
nCreate(Ljava/nio/ByteBuffer;IILorg/lwjgl/opengl/PixelFormat;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;)V
nDestroy(Ljava/nio/ByteBuffer;)V
nIsBufferLost(Ljava/nio/ByteBuffer;)Z
nReleaseTexImageFromPbuffer(Ljava/nio/ByteBuffer;I)V
nSetPbufferAttrib(Ljava/nio/ByteBuffer;II)V
releaseTexImageFromPbuffer(I)V
setPbufferAttrib(II)V
```

#### `org.lwjgl.opengl.WindowsPeerInfo`

```text
<init>()V
choosePixelFormat(JIILorg/lwjgl/opengl/PixelFormat;Ljava/nio/IntBuffer;ZZZZ)I
createHandle()Ljava/nio/ByteBuffer;
destroy()V
getHandle()Ljava/nio/ByteBuffer;
getHwnd()J
lockAndGetHandle()Ljava/nio/ByteBuffer;
nChoosePixelFormat(JIILorg/lwjgl/opengl/PixelFormat;Ljava/nio/IntBuffer;ZZZZ)I
nGetHdc(Ljava/nio/ByteBuffer;)J
nGetHwnd(Ljava/nio/ByteBuffer;)J
setPixelFormat(JI)V
unlock()V
```

#### `org.lwjgl.opengl.WindowsRegistry`

```text
nQueryRegistrationKey(ILjava/lang/String;Ljava/lang/String;)Ljava/lang/String;
queryRegistrationKey(ILjava/lang/String;Ljava/lang/String;)Ljava/lang/String;
```

#### `org.lwjgl.opengl.XRandR$Screen`

```text
<init>(Ljava/lang/String;IIIII)V
access$000(Lorg/lwjgl/opengl/XRandR$Screen;Ljava/util/List;)V
freqI
getArgs(Ljava/util/List;)V
heightI
nameLjava/lang/String;
widthI
xPosI
yPosI
```

#### `org.lwjgl.opengl.XRandR`

```text
DisplayModetoScreen(Lorg/lwjgl/opengl/DisplayMode;)Lorg/lwjgl/opengl/XRandR$Screen;
FREQ_PATTERNLjava/util/regex/Pattern;
SCREEN_HEADER_PATTERNLjava/util/regex/Pattern;
SCREEN_MODELINE_PATTERNLjava/util/regex/Pattern;
ScreentoDisplayMode([Lorg/lwjgl/opengl/XRandR$Screen;)Lorg/lwjgl/opengl/DisplayMode;
WHITESPACE_PATTERNLjava/util/regex/Pattern;
current[Lorg/lwjgl/opengl/XRandR$Screen;
findPrimary([Lorg/lwjgl/opengl/XRandR$Screen;)Lorg/lwjgl/opengl/XRandR$Screen;
getConfiguration()[Lorg/lwjgl/opengl/XRandR$Screen;
getResolutions(Ljava/lang/String;)[Lorg/lwjgl/opengl/XRandR$Screen;
getScreenNames()[Ljava/lang/String;
parseScreenHeader([ILjava/lang/String;)V
parseScreenModeline(Ljava/util/List;Ljava/util/List;Ljava/lang/String;II[Ljava/lang/String;[I)V
populate()V
primaryScreenIdentifierLjava/lang/String;
restoreConfiguration()V
saveConfiguration()V
savedConfiguration[Lorg/lwjgl/opengl/XRandR$Screen;
screensLjava/util/Map;
setConfiguration(Z[Lorg/lwjgl/opengl/XRandR$Screen;)V
```

#### `org.lwjgl.opengles.ContextAttribs`

```text
<init>(I)V
getAttribList()Ljava/nio/IntBuffer;
versionI
```

#### `paulscode.sound.Channel`

```text
<init>(I)V
attachedSourceLpaulscode/sound/Source;
buffersProcessed()I
buffersUnqueuedI
channelTypeI
cleanup()V
close()V
feedRawAudioData([B)I
flush()V
getClassName()Ljava/lang/String;
libraryTypeLjava/lang/Class;
loggerLpaulscode/sound/SoundSystemLogger;
millisecondsPlayed()F
pause()V
play()V
playing()Z
preLoadBuffers(Ljava/util/LinkedList;)Z
processBuffer()Z
queueBuffer([B)Z
rewind()V
setAudioFormat(Ljavax/sound/sampled/AudioFormat;)V
stop()V
```

#### `paulscode.sound.CommandObject`

```text
<init>(I)V
<init>(IF)V
<init>(IFFF)V
<init>(IFFFFFF)V
<init>(ILjava/lang/Class;)V
<init>(ILjava/lang/Object;)V
<init>(ILjava/lang/Object;ZLjava/lang/String;FFFIF)V
<init>(ILjava/lang/String;)V
<init>(ILjava/lang/String;F)V
<init>(ILjava/lang/String;FFF)V
<init>(ILjava/lang/String;I)V
<init>(ILjava/lang/String;Ljava/lang/Object;)V
<init>(ILjava/lang/String;Ljava/lang/Object;J)V
<init>(ILjava/lang/String;Ljava/lang/Object;JJ)V
<init>(ILjava/lang/String;Ljava/lang/String;)V
<init>(ILjava/lang/String;Z)V
<init>(ILjava/lang/String;[B)V
<init>(IZZZLjava/lang/String;Ljava/lang/Object;FFFIF)V
<init>(IZZZLjava/lang/String;Ljava/lang/Object;FFFIFZ)V
CommandI
boolArgs[Z
buffer[B
classArgs[Ljava/lang/Class;
floatArgs[F
intArgs[I
longArgs[J
objectArgs[Ljava/lang/Object;
stringArgs[Ljava/lang/String;
```

#### `paulscode.sound.CommandThread`

```text
<init>(Lpaulscode/sound/SoundSystem;)V
alive()Z
classNameLjava/lang/String;
cleanup()V
dying()Z
errorMessage(Ljava/lang/String;I)V
interrupt()V
kill()V
loggerLpaulscode/sound/SoundSystemLogger;
snooze(J)V
soundSystemLpaulscode/sound/SoundSystem;
start()V
```

#### `paulscode.sound.FilenameURL`

```text
<init>(Ljava/lang/String;)V
<init>(Ljava/net/URL;Ljava/lang/String;)V
errorMessage(Ljava/lang/String;)V
filenameLjava/lang/String;
getFilename()Ljava/lang/String;
getURL()Ljava/net/URL;
loggerLpaulscode/sound/SoundSystemLogger;
printStackTrace(Ljava/lang/Exception;)V
urlLjava/net/URL;
```

#### `paulscode.sound.ICodec`

```text
cleanup()V
endOfStream()Z
getAudioFormat()Ljavax/sound/sampled/AudioFormat;
initialize(Ljava/net/URL;)Z
read()Lpaulscode/sound/SoundBuffer;
readAll()Lpaulscode/sound/SoundBuffer;
reverseByteOrder(Z)V
```

#### `paulscode.sound.IStreamListener`

```text
endOfStream(Ljava/lang/String;I)V
```

#### `paulscode.sound.Library`

```text
<init>()V
activate(Ljava/lang/String;)V
bufferMapLjava/util/HashMap;
checkFadeVolumes()V
cleanup()V
copySources(Ljava/util/HashMap;)V
createChannel(I)Lpaulscode/sound/Channel;
cull(Ljava/lang/String;)V
dequeueSound(Ljava/lang/String;Ljava/lang/String;)V
dopplerChanged()V
errorMessage(Ljava/lang/String;)V
fadeOut(Ljava/lang/String;Lpaulscode/sound/FilenameURL;J)V
fadeOutIn(Ljava/lang/String;Lpaulscode/sound/FilenameURL;JJ)V
feedRawAudioData(Ljava/lang/String;[B)I
feedRawAudioData(Lpaulscode/sound/Source;[B)I
flush(Ljava/lang/String;)V
getClassName()Ljava/lang/String;
getListenerData()Lpaulscode/sound/ListenerData;
getMidiChannel()Lpaulscode/sound/MidiChannel;
getNextChannel(Lpaulscode/sound/Source;)Lpaulscode/sound/Channel;
getPitch(Ljava/lang/String;)F
getSources()Ljava/util/HashMap;
getVolume(Ljava/lang/String;)F
init()V
listenerLpaulscode/sound/ListenerData;
loadMidi(ZLjava/lang/String;Lpaulscode/sound/FilenameURL;)V
loadSound(Lpaulscode/sound/FilenameURL;)Z
loadSound(Lpaulscode/sound/SoundBuffer;Ljava/lang/String;)Z
loggerLpaulscode/sound/SoundSystemLogger;
message(Ljava/lang/String;)V
midiChannelLpaulscode/sound/MidiChannel;
midiSourcename(Ljava/lang/String;)Z
millisecondsPlayed(Ljava/lang/String;)F
moveListener(FFF)V
newSource(ZZZLjava/lang/String;Lpaulscode/sound/FilenameURL;FFFIF)V
nextNormalChannelI
nextStreamingChannelI
normalChannelSourceNames[Ljava/lang/String;
normalChannelsLjava/util/List;
pause(Ljava/lang/String;)V
play(Ljava/lang/String;)V
play(Lpaulscode/sound/Source;)V
queueSound(Ljava/lang/String;Lpaulscode/sound/FilenameURL;)V
quickPlay(ZZZLjava/lang/String;Lpaulscode/sound/FilenameURL;FFFIFZ)V
rawDataStream(Ljavax/sound/sampled/AudioFormat;ZLjava/lang/String;FFFIF)V
removeSource(Ljava/lang/String;)V
removeTemporarySources()V
replaySources()V
reverseByteOrderZ
rewind(Ljava/lang/String;)V
setAttenuation(Ljava/lang/String;I)V
setDistOrRoll(Ljava/lang/String;F)V
setListenerAngle(F)V
setListenerData(Lpaulscode/sound/ListenerData;)V
setListenerOrientation(FFFFFF)V
setListenerPosition(FFF)V
setListenerVelocity(FFF)V
setLooping(Ljava/lang/String;Z)V
setMasterVolume(F)V
setMidiChannel(Lpaulscode/sound/MidiChannel;)V
setPitch(Ljava/lang/String;F)V
setPosition(Ljava/lang/String;FFF)V
setPriority(Ljava/lang/String;Z)V
setTemporary(Ljava/lang/String;Z)V
setVelocity(Ljava/lang/String;FFF)V
setVolume(Ljava/lang/String;F)V
sourceMapLjava/util/HashMap;
stop(Ljava/lang/String;)V
streamThreadLpaulscode/sound/StreamThread;
streamingChannelSourceNames[Ljava/lang/String;
streamingChannelsLjava/util/List;
turnListener(F)V
unloadSound(Ljava/lang/String;)V
```

#### `paulscode.sound.ListenerData`

```text
<init>(FFFFFFFFFF)V
angleF
lookAtLpaulscode/sound/Vector3D;
positionLpaulscode/sound/Vector3D;
setAngle(F)V
setData(Lpaulscode/sound/ListenerData;)V
setOrientation(FFFFFF)V
setPosition(FFF)V
setVelocity(FFF)V
upLpaulscode/sound/Vector3D;
velocityLpaulscode/sound/Vector3D;
```

#### `paulscode.sound.MidiChannel$1`

Identical byte redundancy.

#### `paulscode.sound.MidiChannel$FadeThread`

```text
<init>(Lpaulscode/sound/MidiChannel;)V
<init>(Lpaulscode/sound/MidiChannel;Lpaulscode/sound/MidiChannel$1;)V
alive()Z
cleanup()V
dying()Z
interrupt()V
kill()V
snooze(J)V
start()V
this$0Lpaulscode/sound/MidiChannel;
```

#### `paulscode.sound.MidiChannel`

```text
<init>(ZLjava/lang/String;Lpaulscode/sound/FilenameURL;)V
access$100(Lpaulscode/sound/MidiChannel;)Z
checkFadeOut()Z
cleanup()V
dequeueSound(Ljava/lang/String;)V
errorMessage(Ljava/lang/String;)V
fadeInGainF
fadeInMilisJ
fadeOut(Lpaulscode/sound/FilenameURL;J)V
fadeOutGainF
fadeOutIn(Lpaulscode/sound/FilenameURL;JJ)V
fadeOutMilisJ
fadeThreadLpaulscode/sound/MidiChannel$FadeThread;
filenameURL(ZLpaulscode/sound/FilenameURL;)Lpaulscode/sound/FilenameURL;
filenameURLLpaulscode/sound/FilenameURL;
gainF
getFilenameURL()Lpaulscode/sound/FilenameURL;
getLooping()Z
getSequencer()V
getSourcename()Ljava/lang/String;
getSynthesizer()V
getVolume()F
incrementSequence()Z
init()V
lastFadeCheckJ
loading()Z
loading(ZZ)Z
loadingZ
loggerLpaulscode/sound/SoundSystemLogger;
message(Ljava/lang/String;)V
openMidiDevice(Ljava/lang/String;)Ljavax/sound/midi/MidiDevice;
openSequencer(Ljava/lang/String;)Ljavax/sound/midi/Sequencer;
pause()V
play()V
printStackTrace(Ljava/lang/Exception;)V
queueSound(Lpaulscode/sound/FilenameURL;)V
reset()V
resetGain()V
rewind()V
sequenceLjavax/sound/midi/Sequence;
sequenceQueueLjava/util/LinkedList;
sequenceQueueLockLjava/lang/Object;
sequencerLjavax/sound/midi/Sequencer;
setLooping(Z)V
setSequence(Ljava/net/URL;)V
setVolume(F)V
sourcename(ZLjava/lang/String;)Ljava/lang/String;
sourcenameLjava/lang/String;
stop()V
switchSource(ZLjava/lang/String;Lpaulscode/sound/FilenameURL;)V
synthDeviceLjavax/sound/midi/MidiDevice;
synthesizerLjavax/sound/midi/Synthesizer;
toLoop(ZZ)Z
toLoopZ
```

#### `paulscode.sound.SimpleThread$1`

```text
<init>(Lpaulscode/sound/SimpleThread;)V
start()V
this$0Lpaulscode/sound/SimpleThread;
```

#### `paulscode.sound.SimpleThread`

```text
<init>()V
access$000(Lpaulscode/sound/SimpleThread;)V
alive(ZZ)Z
aliveZ
cleanup()V
kill(ZZ)Z
killZ
rerun()V
run()V
snooze(J)V
```

#### `paulscode.sound.SoundBuffer`

```text
<init>([BLjavax/sound/sampled/AudioFormat;)V
audioData[B
audioFormatLjavax/sound/sampled/AudioFormat;
cleanup()V
trimData(I)V
```

#### `paulscode.sound.SoundSystem`

```text
<init>()V
CommandActivate(Ljava/lang/String;)V
CommandChangeDopplerFactor(F)V
CommandChangeDopplerVelocity(F)V
CommandCheckFadeVolumes()V
CommandCull(Ljava/lang/String;)V
CommandDequeueSound(Ljava/lang/String;Ljava/lang/String;)V
CommandFadeOut(Ljava/lang/String;Lpaulscode/sound/FilenameURL;J)V
CommandFadeOutIn(Ljava/lang/String;Lpaulscode/sound/FilenameURL;JJ)V
CommandFeedRawAudioData(Ljava/lang/String;[B)V
CommandFlush(Ljava/lang/String;)V
CommandInitialize()V
CommandLoadSound(Lpaulscode/sound/FilenameURL;)V
CommandLoadSound(Lpaulscode/sound/SoundBuffer;Ljava/lang/String;)V
CommandMoveListener(FFF)V
CommandNewLibrary(Ljava/lang/Class;)V
CommandNewSource(ZZZLjava/lang/String;Lpaulscode/sound/FilenameURL;FFFIF)V
CommandPause(Ljava/lang/String;)V
CommandPlay(Ljava/lang/String;)V
CommandQueue(Lpaulscode/sound/CommandObject;)Z
CommandQueueSound(Ljava/lang/String;Lpaulscode/sound/FilenameURL;)V
CommandQuickPlay(ZZZLjava/lang/String;Lpaulscode/sound/FilenameURL;FFFIFZ)V
CommandRawDataStream(Ljavax/sound/sampled/AudioFormat;ZLjava/lang/String;FFFIF)V
CommandRemoveSource(Ljava/lang/String;)V
CommandRewind(Ljava/lang/String;)V
CommandSetAttenuation(Ljava/lang/String;I)V
CommandSetDistOrRoll(Ljava/lang/String;F)V
CommandSetListenerAngle(F)V
CommandSetListenerOrientation(FFFFFF)V
CommandSetListenerPosition(FFF)V
CommandSetListenerVelocity(FFF)V
CommandSetLooping(Ljava/lang/String;Z)V
CommandSetMasterVolume(F)V
CommandSetPitch(Ljava/lang/String;F)V
CommandSetPosition(Ljava/lang/String;FFF)V
CommandSetPriority(Ljava/lang/String;Z)V
CommandSetTemporary(Ljava/lang/String;Z)V
CommandSetVelocity(Ljava/lang/String;FFF)V
CommandSetVolume(Ljava/lang/String;F)V
CommandStop(Ljava/lang/String;)V
CommandTurnListener(F)V
CommandUnloadSound(Ljava/lang/String;)V
ManageSources()V
classNameLjava/lang/String;
commandQueueLjava/util/List;
commandThreadLpaulscode/sound/CommandThread;
copySources(Ljava/util/HashMap;)Ljava/util/HashMap;
currentLibrary(ZLjava/lang/Class;)Ljava/lang/Class;
currentLibraryLjava/lang/Class;
errorCheck(ZLjava/lang/String;I)Z
errorMessage(Ljava/lang/String;I)V
getLastException()Lpaulscode/sound/SoundSystemException;
importantMessage(Ljava/lang/String;I)V
init(Ljava/lang/Class;)V
initialized(ZZ)Z
initializedZ
lastException(ZLpaulscode/sound/SoundSystemException;)Lpaulscode/sound/SoundSystemException;
lastExceptionLpaulscode/sound/SoundSystemException;
linkDefaultLibrariesAndCodecs()V
loggerLpaulscode/sound/SoundSystemLogger;
message(Ljava/lang/String;I)V
newLibrary(Ljava/lang/Class;)Z
randomNumberGeneratorLjava/util/Random;
removeTemporarySources()V
setException(Lpaulscode/sound/SoundSystemException;)V
snooze(J)V
soundLibraryLpaulscode/sound/Library;
sourcePlayListLjava/util/List;
```

#### `paulscode.sound.SoundSystemConfig$1`

```text
<init>(Ljava/lang/String;I)V
start()V
val$qSizeI
val$srcNameLjava/lang/String;
```

#### `paulscode.sound.SoundSystemConfig$Codec`

```text
<init>(Ljava/lang/String;Ljava/lang/Class;)V
extensionRegXLjava/lang/String;
getInstance()Lpaulscode/sound/ICodec;
iCodecClassLjava/lang/Class;
instantiationErrorMessage()V
```

#### `paulscode.sound.SoundSystemConfig`

```text
EXTENSION_MIDILjava/lang/String;
PREFIX_URLLjava/lang/String;
THREAD_SYNCLjava/lang/Object;
access$000()Ljava/lang/Object;
access$100()Ljava/util/LinkedList;
access$200(Ljava/lang/String;)V
addLibrary(Ljava/lang/Class;)V
codecsLjava/util/LinkedList;
defaultAttenuationModelI
defaultFadeDistanceF
defaultRolloffFactorF
dopplerFactorF
dopplerVelocityF
errorMessage(Ljava/lang/String;)V
fileChunkSizeI
getCodec(Ljava/lang/String;)Lpaulscode/sound/ICodec;
getDopplerFactor()F
getDopplerVelocity()F
getFileChunkSize()I
getLibraries()Ljava/util/LinkedList;
getLibraryDescription(Ljava/lang/Class;)Ljava/lang/String;
getLibraryTitle(Ljava/lang/Class;)Ljava/lang/String;
getLogger()Lpaulscode/sound/SoundSystemLogger;
getMasterGain()F
getMaxFileSize()I
getNumberNormalChannels()I
getNumberStreamingBuffers()I
getNumberStreamingChannels()I
getOverrideMIDISynthesizer()Ljava/lang/String;
getSoundFilesPackage()Ljava/lang/String;
getStreamQueueFormatsMatch()Z
getStreamingBufferSize()I
librariesLjava/util/LinkedList;
libraryCompatible(Ljava/lang/Class;)Z
loggerLpaulscode/sound/SoundSystemLogger;
masterGainF
maxFileSizeI
midiCodec()Z
midiCodecZ
notifyEOS(Ljava/lang/String;I)V
numberNormalChannelsI
numberStreamingBuffersI
numberStreamingChannelsI
overrideMIDISynthesizerLjava/lang/String;
reverseByteOrder(Ljava/lang/Class;)Z
runMethod(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;
setCodec(Ljava/lang/String;Ljava/lang/Class;)V
setDopplerFactor(F)V
setDopplerVelocity(F)V
setLogger(Lpaulscode/sound/SoundSystemLogger;)V
setMasterGain(F)V
soundFilesPackageLjava/lang/String;
streamListenersLjava/util/LinkedList;
streamListenersLockLjava/lang/Object;
streamQueueFormatsMatchZ
streamingBufferSizeI
```

#### `paulscode.sound.SoundSystemException`

```text
<init>(Ljava/lang/String;)V
<init>(Ljava/lang/String;I)V
myTypeI
```

#### `paulscode.sound.SoundSystemLogger`

```text
<init>()V
errorCheck(ZLjava/lang/String;Ljava/lang/String;I)Z
errorMessage(Ljava/lang/String;Ljava/lang/String;I)V
importantMessage(Ljava/lang/String;I)V
message(Ljava/lang/String;I)V
printExceptionMessage(Ljava/lang/Exception;I)V
printStackTrace(Ljava/lang/Exception;I)V
```

#### `paulscode.sound.Source`

```text
<init>(Ljavax/sound/sampled/AudioFormat;ZLjava/lang/String;FFFIF)V
<init>(Lpaulscode/sound/Source;Lpaulscode/sound/SoundBuffer;)V
<init>(ZZZLjava/lang/String;Lpaulscode/sound/FilenameURL;Lpaulscode/sound/SoundBuffer;FFFIFZ)V
activate()V
active()Z
active(ZZ)Z
activeZ
attModelI
changeSource(ZZZLjava/lang/String;Lpaulscode/sound/FilenameURL;Lpaulscode/sound/SoundBuffer;FFFIFZ)V
channelLpaulscode/sound/Channel;
checkFadeOut()Z
cleanup()V
codecLpaulscode/sound/ICodec;
cull()V
dequeueSound(Ljava/lang/String;)V
distOrRollF
distanceFromListenerF
errorMessage(Ljava/lang/String;)V
fadeInGainF
fadeInMilisJ
fadeOut(Lpaulscode/sound/FilenameURL;J)V
fadeOutGainF
fadeOutIn(Lpaulscode/sound/FilenameURL;JJ)V
fadeOutMilisJ
feedRawAudioData(Lpaulscode/sound/Channel;[B)I
filenameURLLpaulscode/sound/FilenameURL;
flush()V
gainF
getClassName()Ljava/lang/String;
getPitch()F
getSoundSequenceQueueSize()I
incrementSoundSequence()Z
lastFadeCheckJ
libraryTypeLjava/lang/Class;
listenerMoved()V
loggerLpaulscode/sound/SoundSystemLogger;
millisecondsPlayed()F
nextBuffersLjava/util/LinkedList;
nextCodecLpaulscode/sound/ICodec;
pause()V
paused()Z
paused(ZZ)Z
pausedZ
pitchF
play(Lpaulscode/sound/Channel;)V
playing()Z
positionLpaulscode/sound/Vector3D;
positionChanged()V
preLoad()Z
preLoadZ
priorityZ
queueSound(Lpaulscode/sound/FilenameURL;)V
rawDataFormatLjavax/sound/sampled/AudioFormat;
rawDataStreamZ
readBuffersFromNextSoundInSequence()Z
rewind()V
setAttenuation(I)V
setDistOrRoll(F)V
setLooping(Z)V
setPitch(F)V
setPosition(FFF)V
setPriority(Z)V
setTemporary(Z)V
setVelocity(FFF)V
soundBufferLpaulscode/sound/SoundBuffer;
soundSequenceLockLjava/lang/Object;
soundSequenceQueueLjava/util/LinkedList;
sourceVolumeF
sourcenameLjava/lang/String;
stop()V
stopped()Z
stopped(ZZ)Z
stoppedZ
stream()Z
temporaryZ
toLoopZ
toPlayZ
toStreamZ
velocityLpaulscode/sound/Vector3D;
```

#### `paulscode.sound.StreamThread`

```text
<init>()V
alive()Z
cleanup()V
dying()Z
interrupt()V
kill()V
listLockLjava/lang/Object;
loggerLpaulscode/sound/SoundSystemLogger;
snooze(J)V
start()V
streamingSourcesLjava/util/List;
watch(Lpaulscode/sound/Source;)V
```

#### `paulscode.sound.Vector3D`

```text
<init>(FFF)V
clone()Lpaulscode/sound/Vector3D;
cross(Lpaulscode/sound/Vector3D;)Lpaulscode/sound/Vector3D;
dot(Lpaulscode/sound/Vector3D;)F
dot(Lpaulscode/sound/Vector3D;Lpaulscode/sound/Vector3D;)F
length()F
normalize()V
subtract(Lpaulscode/sound/Vector3D;)Lpaulscode/sound/Vector3D;
xF
yF
zF
```

## Reproduktion und Belege

[Dependencybaum](F:/neutron-rel-p02-evidence/runtime-dependency-tree.txt), [ABI-Inventar](F:/neutron-rel-p02-evidence/dependency-abi-audit.json), [breite Reflexion](F:/neutron-rel-p02-evidence/reflection-surface.log), [Netty-Bytecode](F:/neutron-rel-p02-evidence/netty-bootstrap-javap.txt), [Patchy-Bytecode](F:/neutron-rel-p02-evidence/patchy-bootstrap-javap.txt), [Display-Bytecode](F:/neutron-rel-p02-evidence/lwjgl-display-javap.txt). Externe Python-/Runnerquellen stehen im Evidenzordner; Mavenaufrufe sind durchgehend offline. Neue Tests und exakte finalen Läufe sind in RESULTS gebunden.

**STOPP nach REL-P02.**
