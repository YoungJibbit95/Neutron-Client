import type { Locale, Scenario } from './model';

export const de = {
  startingClient: 'Clientstart wird vorbereitet', retryConnection: 'Verbindung erneut prüfen',
  launch: 'Client starten', options: 'Startoptionen', settings: 'Launcher-Einstellungen', update: 'Updates',
  launchIntro: 'Dein nächstes Spiel beginnt hier.', optionsIntro: 'Deine Laufzeit. Dein Setup.', settingsIntro: 'So fühlt sich Neutron nach dir an.', updateIntro: 'Versionen mit einer klaren Herkunft.',
  prototype: 'Interaktiver Prototyp', mockNotice: 'Beispieldaten. Kein echter Client, Build oder Download wird gestartet.',
  prototypeControls: 'Beispielzustände', scenario: 'Zustand', simulateClose: 'Schließen simulieren', nextPhase: 'Nächste Phase', simulateError: 'Buildfehler simulieren', logLoad: '2.000 Beispielzeilen',
  ready: 'Spielbereit', firstRun: 'Einrichtung', empty: 'Noch kein Build', offline: 'Offline bereit', updateAvailable: 'Update verfügbar', prepare: 'Vorbereiten', download: 'Herunterladen', build: 'Bauen', verify: 'Prüfen', cancelled: 'Abgebrochen', error: 'Build fehlgeschlagen', running: 'Client läuft', crash: 'Client abgestürzt',
  online: 'Online', offlineLabel: 'Offline', development: 'Development', buildDate: 'Build vom', commit: 'Commit', minecraft: 'Minecraft 1.8.9', source: 'Quelle', branch: 'Branch', localReady: 'Lokal verfügbar',
  launchButton: 'Neutron starten', stop: 'Client beenden', checkUpdate: 'Auf Updates prüfen', viewUpdate: 'Update ansehen', buildLatest: 'Neueste Version bauen', buildPlay: 'Bauen und spielen', cancel: 'Abbrechen',
  loginHint: 'Die Minecraft-Anmeldung bleibt im Client.', offlineHint: 'Du bist offline. Dein installierter Build bleibt spielbar.',
  updateHint: 'Ein neuer Beispiel-Build ist verfügbar. Dein installierter Build bleibt erhalten.', cancelHint: 'Der Vorgang wurde abgebrochen. Installierte Versionen bleiben unverändert.', errorHint: 'Der Beispiel-Build konnte nicht erstellt werden. Starte die letzte Version oder versuche es erneut.',
  crashHint: 'Der Beispiel-Client wurde unerwartet beendet.', exitCode: 'Exitcode', restart: 'Erneut starten', report: 'Problem melden', showReport: 'Bericht ansehen',
  firstBuildError: 'Simulation: Der erste Beispiel-Build ist fehlgeschlagen. Es ist noch keine Version installiert. Versuche es erneut.', firstBuildCancelled: 'Simulation: Der erste Build wurde abgebrochen. Es ist noch keine Version installiert. Du kannst den Build erneut starten.', olderLogsDiscarded: 'Ältere Ausgabe verworfen. Anzeige ab der ältesten verfügbaren Zeile.',
  progressHint: 'Die vorherige Version bleibt bis zur erfolgreichen Prüfung aktiv.', downloadProgress: '18,5 von 50 MB · Beispiel', indeterminate: 'Fortschritt wird ermittelt', done: 'Bereit', retry: 'Erneut versuchen',
  setupTitle: 'Mach Neutron startklar.', setupIntro: 'Wähle einen Speicherort. Die benötigten Werkzeuge bleiben lokal bei deinem Launcher.', storage: 'Installationsordner', browse: 'Auswählen', exampleFolder: 'Beispielordner wählen', setupDownloads: 'Benötigte Downloads', setupEstimate: 'Größen sind Schätzwerte für diesen Prototyp.', jdkDownload: 'Java 8 JDK · 64 Bit', mavenDownload: 'Maven', assetDownload: 'Minecraft-Assets & Natives', setupButton: 'Einrichtung simulieren',
  emptyTitle: 'Ein Platz für deinen ersten Build.', emptyHint: 'Baue eine Version aus main. Nach erfolgreicher Prüfung kannst du sie auch offline starten.',
  memory: 'Arbeitsspeicher', memoryHint: 'Beispielgerät: 16 GiB RAM. Empfehlung: 4 GiB; maximal 8 GiB im Prototyp.', recommended: 'Empfohlen', reset: 'Zurücksetzen', directory: 'Spielverzeichnis', directoryHint: 'Hier liegen deine Welten, Einstellungen und Crash-Berichte.',
  java: 'Java-Laufzeit', managed: 'Vom Launcher verwaltet', customJava: 'Eigene Java-Installation', javaHint: 'Java 8 · 64 Bit', javaPath: 'Pfad zur Java-Anwendung', display: 'Spielfenster', width: 'Breite', height: 'Höhe', fullscreen: 'Im Vollbild starten', advanced: 'Erweitert', jvmArgs: 'Zusätzliche JVM-Argumente', jvmHint: 'RAM stellst du oben ein. Keine zusätzlichen -Xmx- oder -Xms-Angaben.',
  savedMemory: 'Änderungen gelten nur für diesen Prototyp und werden nicht auf dem Rechner gespeichert.', optionErrors: 'Bitte korrigiere die markierten Startoptionen.', errorRam: 'Wähle eine ganze MiB-Zahl zwischen 1.024 und 8.192.', errorDirectory: 'Gib einen absoluten Ordnerpfad an.', errorJava: 'Gib einen absoluten Pfad zur Java-Anwendung an.', errorWindow: 'Wähle eine ganzzahlige Fenstergröße zwischen 640×480 und 7680×4320.', errorHeap: 'Entferne widersprüchliche Heap-Angaben aus den zusätzlichen Argumenten.',
  appearance: 'Dein Look', themeHint: 'Ein Akzent. Vier Stimmungen.', language: 'Sprache', languageHint: 'Die Sprache ändert sich sofort.', scale: 'UI-Skalierung', effects: 'Effektstärke', effectsHint: 'Steuert Glow und dekorative Flächen.', reduced: 'Bewegung reduzieren', reducedHint: 'Die Systemeinstellung wird zusätzlich berücksichtigt.', systemMotion: 'Systempräferenz berücksichtigt',
  current: 'Ausgewählt', installed: 'Installierte Builds', installedHint: 'Wähle eine Version für deinen nächsten Start.', github: 'GitHub-Stand', githubHint: 'Beispiel für main · keine Live-Abfrage', checked: 'Zuletzt geprüft', select: 'Auswählen', remove: 'Entfernen', protected: 'Ausgewählte und laufende Builds sind geschützt.', noBuilds: 'Noch keine Version installiert.', size: 'Größe',
  console: 'Konsole', clientLogs: 'Client', buildLogs: 'Build', search: 'Logs durchsuchen', allLevels: 'Alle Stufen', info: 'Info', warn: 'Warnungen', errors: 'Fehler', copy: 'Kopieren', export: 'Exportieren', copied: 'Kopiert', copyFailed: 'Kopieren nicht verfügbar', exported: 'Beispiel-Logs exportiert', noLogs: 'Keine passenden Logzeilen.', autoScroll: 'Automatisch scrollen', paused: 'Scrollen pausiert', newLines: '{count} neue Zeilen', resume: 'Zu den neuesten Zeilen', collapse: 'Konsole einklappen', expand: 'Konsole öffnen', resize: 'Konsolenhöhe ändern', consoleHeight: 'Konsolenhöhe',
  removeTitle: 'Diesen Beispiel-Build entfernen?', removeHint: 'Die ausgewählte und die laufende Version bleiben geschützt.', keep: 'Behalten',
  closeTitle: 'Der Beispiel-Client läuft noch.', closeHint: 'Wähle, wie Neutron beim Schließen reagieren soll.', minimize: 'In den Hintergrund', quitBoth: 'Client und Launcher beenden', stay: 'Zurück zum Launcher', closeSimulation: 'Nur die Auswahl wird simuliert. Es wird kein Fenster geschlossen.',
  reportTitle: 'Problembericht prüfen', reportHint: 'Diese Vorschau enthält nur Beispieldaten. Erkennbare Tokens und persönliche Pfade werden entfernt.', issuePreview: 'GitHub-Issue-Vorschau', issueHint: 'Im Prototyp wird keine Website geöffnet und nichts veröffentlicht.', close: 'Schließen', demoStopped: 'Beispiel-Client beendet. Das Launcherfenster bleibt im Prototyp offen.',
  profile: 'Profil', example: 'Beispiel', noCredentials: 'Keine echten Zugangsdaten oder persönlichen Spieldaten.',
} as const;
export type CopyKey = keyof typeof de;
export const en: Record<CopyKey, string> = {
  startingClient: 'Preparing client launch', retryConnection: 'Check connection again',
  launch: 'Launch Client', options: 'Launch Options', settings: 'Launcher Settings', update: 'Updates',
  launchIntro: 'Your next game starts here.', optionsIntro: 'Your runtime. Your setup.', settingsIntro: 'Make Neutron feel like yours.', updateIntro: 'Versions with a clear origin.',
  prototype: 'Interactive prototype', mockNotice: 'Example data. No real client, build or download will start.',
  prototypeControls: 'Example states', scenario: 'State', simulateClose: 'Simulate closing', nextPhase: 'Next phase', simulateError: 'Simulate build failure', logLoad: '2,000 example lines',
  ready: 'Ready to play', firstRun: 'Setup', empty: 'No build yet', offline: 'Ready offline', updateAvailable: 'Update available', prepare: 'Preparing', download: 'Downloading', build: 'Building', verify: 'Verifying', cancelled: 'Cancelled', error: 'Build failed', running: 'Client running', crash: 'Client crashed',
  online: 'Online', offlineLabel: 'Offline', development: 'Development', buildDate: 'Built on', commit: 'Commit', minecraft: 'Minecraft 1.8.9', source: 'Source', branch: 'Branch', localReady: 'Available locally',
  launchButton: 'Launch Neutron', stop: 'Stop client', checkUpdate: 'Check for updates', viewUpdate: 'View update', buildLatest: 'Build latest version', buildPlay: 'Build and play', cancel: 'Cancel',
  loginHint: 'Minecraft sign-in stays inside the client.', offlineHint: 'You are offline. Your installed build is still playable.',
  updateHint: 'A new example build is available. Your installed build is kept.', cancelHint: 'The operation was cancelled. Installed versions are unchanged.', errorHint: 'The example build could not be created. Launch the last version or try again.',
  crashHint: 'The example client stopped unexpectedly.', exitCode: 'Exit code', restart: 'Launch again', report: 'Report a problem', showReport: 'View report',
  firstBuildError: 'Simulation: The first example build failed. No version is installed yet. Try again.', firstBuildCancelled: 'Simulation: The first build was cancelled. No version is installed yet. You can start the build again.', olderLogsDiscarded: 'Older output discarded. Showing the oldest available line.',
  progressHint: 'The previous version stays active until verification succeeds.', downloadProgress: '18.5 of 50 MB · example', indeterminate: 'Determining progress', done: 'Ready', retry: 'Try again',
  setupTitle: 'Get Neutron ready.', setupIntro: 'Choose a location. Required tools stay local to your launcher.', storage: 'Installation folder', browse: 'Choose', exampleFolder: 'Choose example folder', setupDownloads: 'Required downloads', setupEstimate: 'Sizes are illustrative estimates for this prototype.', jdkDownload: 'Java 8 JDK · 64 bit', mavenDownload: 'Maven', assetDownload: 'Minecraft assets & natives', setupButton: 'Simulate setup',
  emptyTitle: 'A place for your first build.', emptyHint: 'Build a version from main. After verification, you can launch it offline too.',
  memory: 'Memory', memoryHint: 'Example device: 16 GiB RAM. Recommended: 4 GiB; prototype maximum: 8 GiB.', recommended: 'Recommended', reset: 'Reset', directory: 'Game directory', directoryHint: 'Your worlds, settings and crash reports live here.',
  java: 'Java runtime', managed: 'Managed by the launcher', customJava: 'Custom Java installation', javaHint: 'Java 8 · 64 bit', javaPath: 'Path to Java executable', display: 'Game window', width: 'Width', height: 'Height', fullscreen: 'Launch in fullscreen', advanced: 'Advanced', jvmArgs: 'Additional JVM arguments', jvmHint: 'Set memory above. Do not add -Xmx or -Xms arguments.',
  savedMemory: 'Changes apply to this prototype session and are not saved to your computer.', optionErrors: 'Please correct the highlighted launch options.', errorRam: 'Choose a whole MiB value between 1,024 and 8,192.', errorDirectory: 'Enter an absolute directory path.', errorJava: 'Enter an absolute path to the Java executable.', errorWindow: 'Choose whole window dimensions between 640×480 and 7680×4320.', errorHeap: 'Remove conflicting heap options from the additional arguments.',
  appearance: 'Your look', themeHint: 'One accent. Four moods.', language: 'Language', languageHint: 'The language changes immediately.', scale: 'UI scale', effects: 'Effect intensity', effectsHint: 'Controls glow and decorative surfaces.', reduced: 'Reduce motion', reducedHint: 'The system preference is respected as well.', systemMotion: 'System preference respected',
  current: 'Selected', installed: 'Installed builds', installedHint: 'Choose a version for your next launch.', github: 'GitHub status', githubHint: 'Example for main · no live request', checked: 'Last checked', select: 'Select', remove: 'Remove', protected: 'Selected and running builds are protected.', noBuilds: 'No version installed yet.', size: 'Size',
  console: 'Console', clientLogs: 'Client', buildLogs: 'Build', search: 'Search logs', allLevels: 'All levels', info: 'Info', warn: 'Warnings', errors: 'Errors', copy: 'Copy', export: 'Export', copied: 'Copied', copyFailed: 'Copy unavailable', exported: 'Example logs exported', noLogs: 'No matching log lines.', autoScroll: 'Auto-scroll', paused: 'Scrolling paused', newLines: '{count} new lines', resume: 'Jump to latest lines', collapse: 'Collapse console', expand: 'Open console', resize: 'Resize console', consoleHeight: 'Console height',
  removeTitle: 'Remove this example build?', removeHint: 'Selected and running versions stay protected.', keep: 'Keep build',
  closeTitle: 'The example client is still running.', closeHint: 'Choose how Neutron should respond when closing.', minimize: 'Keep in background', quitBoth: 'Stop client and launcher', stay: 'Back to launcher', closeSimulation: 'Only the choice is simulated. No window will close.',
  reportTitle: 'Review problem report', reportHint: 'This preview contains example data only. Recognizable tokens and personal paths are removed.', issuePreview: 'GitHub issue preview', issueHint: 'The prototype will not open a website or publish anything.', close: 'Close', demoStopped: 'Example client stopped. The launcher stays open in this prototype.',
  profile: 'Profile', example: 'example', noCredentials: 'No real credentials or personal game data.',
};
export const dictionaries = { de, en };
const liveCopy: Record<Locale, Partial<Record<CopyKey, string>>> = {
  de: {
    prototype: 'Alpha', development: 'Neutron Alpha', mockNotice: 'Neutron Alpha · Dein installierter Client startet lokal. Anmeldung im Client.',
    setupButton: 'Neutron vollständig einrichten', exampleFolder: 'Auswählen', setupEstimate: 'Downloads werden geprüft. Für Einrichtung und Build mindestens 3 GiB freien Platz einplanen.',
    storage: 'Spielverzeichnis', setupIntro: 'Wähle deinen Spielordner. Der gepinnte Alpha-Client und seine Laufzeit werden lokal vorbereitet. Danach startet Spielen ohne Build.', errorRam: 'Wähle eine ganze MiB-Zahl innerhalb der angezeigten RAM-Grenze.',
    progressHint: 'Eine vorhandene Version bleibt bis zur erfolgreichen Prüfung erhalten.', downloadProgress: 'Download läuft',
    firstBuildError: 'Der erste Build ist fehlgeschlagen. Es ist noch keine Version installiert. Prüfe die Build-Ausgabe und versuche es erneut.', firstBuildCancelled: 'Der Build wurde abgebrochen. Es ist noch keine Version installiert. Du kannst ihn erneut starten.',
    errorHint: 'Der Vorgang ist fehlgeschlagen. Prüfe die Build-Ausgabe. Eine vorhandene Version bleibt verfügbar.', cancelHint: 'Vorgang abgebrochen. Installierte Versionen bleiben erhalten.', crashHint: 'Der Client wurde unerwartet beendet.',
    updateHint: 'Ein neuer Commit ist verfügbar. Die installierte Version bleibt erhalten.', githubHint: 'Fest aufgelöster GitHub-Commit · main',
    savedMemory: 'Einstellungen werden lokal gespeichert.', memoryHint: 'RAM-Auswahl mit Reserve für dein Betriebssystem. Die tatsächliche Grenze steht am Regler.',
    jvmHint: 'RAM über den Regler. Zusätzlich erlaubt: -XX:+UseG1GC, -XX:+UseConcMarkSweepGC, -XX:+UseSerialGC, -XX:+UseParallelGC, -XX:+DisableExplicitGC und -XX:+AlwaysPreTouch (auch mit -).',
    removeTitle: 'Diesen Build aus der Liste entfernen?', installedHint: 'Wähle eine geprüfte Version für den nächsten Start. Entfernte Einträge behalten ihre Dateien zur Wiederherstellung.',
    closeTitle: 'Neutron ist noch aktiv.', closeHint: 'Eine Aufgabe oder der Client läuft. Wähle, wie Neutron schließen soll.', closeSimulation: 'Beenden bricht eine Aufgabe ab und stoppt den gesamten Client-Prozessbaum.',
    reportHint: 'Lokaler Diagnosebericht. Erkennbare Zugangsdaten und persönliche Pfade werden entfernt. Prüfe den Text vor dem Teilen.', issueHint: 'Kopiere den geprüften Bericht bei Bedarf. Es wird nichts automatisch veröffentlicht.', exported: 'Logs exportiert',
  },
  en: {
    prototype: 'Alpha', development: 'Neutron Alpha', mockNotice: 'Neutron Alpha · Your installed client starts locally. Sign in inside the client.',
    setupButton: 'Complete Neutron setup', exampleFolder: 'Browse', setupEstimate: 'Downloads are verified. Allow at least 3 GiB free space for setup and builds.',
    storage: 'Game directory', setupIntro: 'Choose your game folder. The pinned Alpha client and runtime are prepared locally. Afterwards, Play starts without a build.', errorRam: 'Choose a whole MiB value within the displayed memory limit.',
    progressHint: 'An installed version is retained until verification succeeds.', downloadProgress: 'Downloading',
    firstBuildError: 'The first build failed. No version is installed yet. Check Build output and try again.', firstBuildCancelled: 'The build was cancelled. No version is installed yet. You can try again.',
    errorHint: 'The operation failed. Check Build output. An installed version remains available.', cancelHint: 'Operation cancelled. Installed versions are retained.', crashHint: 'The client exited unexpectedly.',
    updateHint: 'A newer commit is available. Your installed version is retained.', githubHint: 'Resolved GitHub commit · main',
    savedMemory: 'Settings are saved locally.', memoryHint: 'Leave memory for the operating system. The slider shows the actual limit.',
    jvmHint: 'Set RAM with the slider. Also supported: -XX:+UseG1GC, -XX:+UseConcMarkSweepGC, -XX:+UseSerialGC, -XX:+UseParallelGC, -XX:+DisableExplicitGC and -XX:+AlwaysPreTouch (minus also allowed).',
    removeTitle: 'Remove this build from the list?', installedHint: 'Select a verified version for the next launch. Removed entries retain files for recovery.',
    closeTitle: 'Neutron is still active.', closeHint: 'A task or the client is running. Choose how Neutron should close.', closeSimulation: 'Quitting cancels an active task and stops the whole client process tree.',
    reportHint: 'Local diagnostic report. Recognizable credentials and personal paths are removed. Review before sharing.', issueHint: 'Copy the reviewed report if needed. Nothing is published automatically.', exported: 'Logs exported',
  },
};
export function translator(locale: Locale, live = false) {
  return (key: CopyKey, params?: Record<string, string | number>): string => {
    let value: string = (live ? liveCopy[locale][key] : undefined) ?? dictionaries[locale][key];
    for (const [name, replacement] of Object.entries(params ?? {})) value = value.replaceAll(`{${name}}`, String(replacement));
    return value;
  };
}
export const scenarioKey: Record<Scenario, CopyKey> = {
  ready: 'ready', 'first-run': 'firstRun', empty: 'empty', offline: 'offline', update: 'updateAvailable', prepare: 'prepare', download: 'download', build: 'build', verify: 'verify', cancelled: 'cancelled', error: 'error', running: 'running', crash: 'crash',
};
export function formatDate(value: string, locale: Locale) {
  return new Intl.DateTimeFormat(locale === 'de' ? 'de-DE' : 'en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Berlin' }).format(new Date(value));
}
