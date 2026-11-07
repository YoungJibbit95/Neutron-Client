import type { Locale, Scenario } from './model';

export const de = {
  startingClient: 'Clientstart wird vorbereitet', retryConnection: 'Verbindung erneut prÃ¼fen',
  launch: 'Client starten', options: 'Startoptionen', settings: 'Launcher-Einstellungen', update: 'Updates',
  launchIntro: 'Dein nÃ¤chstes Spiel beginnt hier.', optionsIntro: 'Deine Laufzeit. Dein Setup.', settingsIntro: 'So fÃ¼hlt sich Neutron nach dir an.', updateIntro: 'Versionen mit einer klaren Herkunft.',
  prototype: 'Interaktiver Prototyp', mockNotice: 'Beispieldaten. Kein echter Client, Build oder Download wird gestartet.',
  prototypeControls: 'BeispielzustÃ¤nde', scenario: 'Zustand', simulateClose: 'SchlieÃŸen simulieren', nextPhase: 'NÃ¤chste Phase', simulateError: 'Buildfehler simulieren', logLoad: '2.000 Beispielzeilen',
  ready: 'Spielbereit', firstRun: 'Einrichtung', empty: 'Noch kein Build', offline: 'Offline bereit', updateAvailable: 'Update verfÃ¼gbar', prepare: 'Vorbereiten', download: 'Herunterladen', build: 'Bauen', verify: 'PrÃ¼fen', cancelled: 'Abgebrochen', error: 'Build fehlgeschlagen', running: 'Client lÃ¤uft', crash: 'Client abgestÃ¼rzt',
  online: 'Online', offlineLabel: 'Offline', development: 'Development', buildDate: 'Build vom', commit: 'Commit', minecraft: 'Minecraft 1.8.9', source: 'Quelle', branch: 'Branch', localReady: 'Lokal verfÃ¼gbar',
  launchButton: 'Neutron starten', stop: 'Client beenden', checkUpdate: 'Auf Updates prÃ¼fen', viewUpdate: 'Update ansehen', buildLatest: 'Neueste Version bauen', buildPlay: 'Bauen und spielen', cancel: 'Abbrechen',
  loginHint: 'Die Minecraft-Anmeldung bleibt im Client.', offlineHint: 'Du bist offline. Dein installierter Build bleibt spielbar.',
  updateHint: 'Ein neuer Beispiel-Build ist verfÃ¼gbar. Dein installierter Build bleibt erhalten.', cancelHint: 'Der Vorgang wurde abgebrochen. Installierte Versionen bleiben unverÃ¤ndert.', errorHint: 'Der Beispiel-Build konnte nicht erstellt werden. Starte die letzte Version oder versuche es erneut.',
  crashHint: 'Der Beispiel-Client wurde unerwartet beendet.', exitCode: 'Exitcode', restart: 'Erneut starten', report: 'Problem melden', showReport: 'Bericht ansehen',
  firstBuildError: 'Simulation: Der erste Beispiel-Build ist fehlgeschlagen. Es ist noch keine Version installiert. Versuche es erneut.', firstBuildCancelled: 'Simulation: Der erste Build wurde abgebrochen. Es ist noch keine Version installiert. Du kannst den Build erneut starten.', olderLogsDiscarded: 'Ã„ltere Ausgabe verworfen. Anzeige ab der Ã¤ltesten verfÃ¼gbaren Zeile.',
  progressHint: 'Die vorherige Version bleibt bis zur erfolgreichen PrÃ¼fung aktiv.', downloadProgress: '18,5 von 50 MB Â· Beispiel', indeterminate: 'Fortschritt wird ermittelt', done: 'Bereit', retry: 'Erneut versuchen',
  setupTitle: 'Mach Neutron startklar.', setupIntro: 'WÃ¤hle einen Speicherort. Die benÃ¶tigten Werkzeuge bleiben lokal bei deinem Launcher.', storage: 'Installationsordner', browse: 'AuswÃ¤hlen', exampleFolder: 'Beispielordner wÃ¤hlen', setupDownloads: 'BenÃ¶tigte Downloads', setupEstimate: 'GrÃ¶ÃŸen sind SchÃ¤tzwerte fÃ¼r diesen Prototyp.', jdkDownload: 'Java 8 JDK Â· 64 Bit', mavenDownload: 'Maven', assetDownload: 'Minecraft-Assets & Natives', setupButton: 'Einrichtung simulieren',
  emptyTitle: 'Ein Platz fÃ¼r deinen ersten Build.', emptyHint: 'Baue eine Version aus main. Nach erfolgreicher PrÃ¼fung kannst du sie auch offline starten.',
  memory: 'Arbeitsspeicher', memoryHint: 'BeispielgerÃ¤t: 16 GiB RAM. Empfehlung: 4 GiB; maximal 8 GiB im Prototyp.', recommended: 'Empfohlen', reset: 'ZurÃ¼cksetzen', directory: 'Spielverzeichnis', directoryHint: 'Hier liegen deine Welten, Einstellungen und Crash-Berichte.',
  java: 'Java-Laufzeit', managed: 'Vom Launcher verwaltet', customJava: 'Eigene Java-Installation', javaHint: 'Java 8 Â· 64 Bit', javaPath: 'Pfad zur Java-Anwendung', display: 'Spielfenster', width: 'Breite', height: 'HÃ¶he', fullscreen: 'Im Vollbild starten', advanced: 'Erweitert', jvmArgs: 'ZusÃ¤tzliche JVM-Argumente', jvmHint: 'RAM stellst du oben ein. Keine zusÃ¤tzlichen -Xmx- oder -Xms-Angaben.',
  savedMemory: 'Ã„nderungen gelten nur fÃ¼r diesen Prototyp und werden nicht auf dem Rechner gespeichert.', optionErrors: 'Bitte korrigiere die markierten Startoptionen.', errorRam: 'WÃ¤hle eine ganze MiB-Zahl zwischen 1.024 und 8.192.', errorDirectory: 'Gib einen absoluten Ordnerpfad an.', errorJava: 'Gib einen absoluten Pfad zur Java-Anwendung an.', errorWindow: 'WÃ¤hle eine ganzzahlige FenstergrÃ¶ÃŸe zwischen 640Ã—480 und 7680Ã—4320.', errorHeap: 'Entferne widersprÃ¼chliche Heap-Angaben aus den zusÃ¤tzlichen Argumenten.',
  appearance: 'Dein Look', themeHint: 'Ein Akzent. Vier Stimmungen.', language: 'Sprache', languageHint: 'Die Sprache Ã¤ndert sich sofort.', scale: 'UI-Skalierung', effects: 'EffektstÃ¤rke', effectsHint: 'Steuert Glow und dekorative FlÃ¤chen.', reduced: 'Bewegung reduzieren', reducedHint: 'Die Systemeinstellung wird zusÃ¤tzlich berÃ¼cksichtigt.', systemMotion: 'SystemprÃ¤ferenz berÃ¼cksichtigt',
  current: 'AusgewÃ¤hlt', installed: 'Installierte Builds', installedHint: 'WÃ¤hle eine Version fÃ¼r deinen nÃ¤chsten Start.', github: 'GitHub-Stand', githubHint: 'Beispiel fÃ¼r main Â· keine Live-Abfrage', checked: 'Zuletzt geprÃ¼ft', select: 'AuswÃ¤hlen', remove: 'Entfernen', protected: 'AusgewÃ¤hlte und laufende Builds sind geschÃ¼tzt.', noBuilds: 'Noch keine Version installiert.', size: 'GrÃ¶ÃŸe',
  console: 'Konsole', clientLogs: 'Client', buildLogs: 'Build', search: 'Logs durchsuchen', allLevels: 'Alle Stufen', info: 'Info', warn: 'Warnungen', errors: 'Fehler', copy: 'Kopieren', export: 'Exportieren', copied: 'Kopiert', copyFailed: 'Kopieren nicht verfÃ¼gbar', exported: 'Beispiel-Logs exportiert', noLogs: 'Keine passenden Logzeilen.', autoScroll: 'Automatisch scrollen', paused: 'Scrollen pausiert', newLines: '{count} neue Zeilen', resume: 'Zu den neuesten Zeilen', collapse: 'Konsole einklappen', expand: 'Konsole Ã¶ffnen', resize: 'KonsolenhÃ¶he Ã¤ndern', consoleHeight: 'KonsolenhÃ¶he',
  removeTitle: 'Diesen Beispiel-Build entfernen?', removeHint: 'Die ausgewÃ¤hlte und die laufende Version bleiben geschÃ¼tzt.', keep: 'Behalten',
  closeTitle: 'Der Beispiel-Client lÃ¤uft noch.', closeHint: 'WÃ¤hle, wie Neutron beim SchlieÃŸen reagieren soll.', minimize: 'In den Hintergrund', quitBoth: 'Client und Launcher beenden', stay: 'ZurÃ¼ck zum Launcher', closeSimulation: 'Nur die Auswahl wird simuliert. Es wird kein Fenster geschlossen.',
  reportTitle: 'Problembericht prÃ¼fen', reportHint: 'Diese Vorschau enthÃ¤lt nur Beispieldaten. Erkennbare Tokens und persÃ¶nliche Pfade werden entfernt.', issuePreview: 'GitHub-Issue-Vorschau', issueHint: 'Im Prototyp wird keine Website geÃ¶ffnet und nichts verÃ¶ffentlicht.', close: 'SchlieÃŸen', demoStopped: 'Beispiel-Client beendet. Das Launcherfenster bleibt im Prototyp offen.',
  profile: 'Profil', example: 'Beispiel', noCredentials: 'Keine echten Zugangsdaten oder persÃ¶nlichen Spieldaten.',
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
  progressHint: 'The previous version stays active until verification succeeds.', downloadProgress: '18.5 of 50 MB Â· example', indeterminate: 'Determining progress', done: 'Ready', retry: 'Try again',
  setupTitle: 'Get Neutron ready.', setupIntro: 'Choose a location. Required tools stay local to your launcher.', storage: 'Installation folder', browse: 'Choose', exampleFolder: 'Choose example folder', setupDownloads: 'Required downloads', setupEstimate: 'Sizes are illustrative estimates for this prototype.', jdkDownload: 'Java 8 JDK Â· 64 bit', mavenDownload: 'Maven', assetDownload: 'Minecraft assets & natives', setupButton: 'Simulate setup',
  emptyTitle: 'A place for your first build.', emptyHint: 'Build a version from main. After verification, you can launch it offline too.',
  memory: 'Memory', memoryHint: 'Example device: 16 GiB RAM. Recommended: 4 GiB; prototype maximum: 8 GiB.', recommended: 'Recommended', reset: 'Reset', directory: 'Game directory', directoryHint: 'Your worlds, settings and crash reports live here.',
  java: 'Java runtime', managed: 'Managed by the launcher', customJava: 'Custom Java installation', javaHint: 'Java 8 Â· 64 bit', javaPath: 'Path to Java executable', display: 'Game window', width: 'Width', height: 'Height', fullscreen: 'Launch in fullscreen', advanced: 'Advanced', jvmArgs: 'Additional JVM arguments', jvmHint: 'Set memory above. Do not add -Xmx or -Xms arguments.',
  savedMemory: 'Changes apply to this prototype session and are not saved to your computer.', optionErrors: 'Please correct the highlighted launch options.', errorRam: 'Choose a whole MiB value between 1,024 and 8,192.', errorDirectory: 'Enter an absolute directory path.', errorJava: 'Enter an absolute path to the Java executable.', errorWindow: 'Choose whole window dimensions between 640Ã—480 and 7680Ã—4320.', errorHeap: 'Remove conflicting heap options from the additional arguments.',
  appearance: 'Your look', themeHint: 'One accent. Four moods.', language: 'Language', languageHint: 'The language changes immediately.', scale: 'UI scale', effects: 'Effect intensity', effectsHint: 'Controls glow and decorative surfaces.', reduced: 'Reduce motion', reducedHint: 'The system preference is respected as well.', systemMotion: 'System preference respected',
  current: 'Selected', installed: 'Installed builds', installedHint: 'Choose a version for your next launch.', github: 'GitHub status', githubHint: 'Example for main Â· no live request', checked: 'Last checked', select: 'Select', remove: 'Remove', protected: 'Selected and running builds are protected.', noBuilds: 'No version installed yet.', size: 'Size',
  console: 'Console', clientLogs: 'Client', buildLogs: 'Build', search: 'Search logs', allLevels: 'All levels', info: 'Info', warn: 'Warnings', errors: 'Errors', copy: 'Copy', export: 'Export', copied: 'Copied', copyFailed: 'Copy unavailable', exported: 'Example logs exported', noLogs: 'No matching log lines.', autoScroll: 'Auto-scroll', paused: 'Scrolling paused', newLines: '{count} new lines', resume: 'Jump to latest lines', collapse: 'Collapse console', expand: 'Open console', resize: 'Resize console', consoleHeight: 'Console height',
  removeTitle: 'Remove this example build?', removeHint: 'Selected and running versions stay protected.', keep: 'Keep build',
  closeTitle: 'The example client is still running.', closeHint: 'Choose how Neutron should respond when closing.', minimize: 'Keep in background', quitBoth: 'Stop client and launcher', stay: 'Back to launcher', closeSimulation: 'Only the choice is simulated. No window will close.',
  reportTitle: 'Review problem report', reportHint: 'This preview contains example data only. Recognizable tokens and personal paths are removed.', issuePreview: 'GitHub issue preview', issueHint: 'The prototype will not open a website or publish anything.', close: 'Close', demoStopped: 'Example client stopped. The launcher stays open in this prototype.',
  profile: 'Profile', example: 'example', noCredentials: 'No real credentials or personal game data.',
};
export const dictionaries = { de, en };
const liveCopy: Record<Locale, Partial<Record<CopyKey, string>>> = {
  de: {
    prototype: 'Stable', development: 'Neutron 0.2.1', mockNotice: 'Neutron 0.2.1 · Dein installierter Client startet lokal. Anmeldung im Client.',
    setupButton: 'Neutron vollstÃ¤ndig einrichten', exampleFolder: 'AuswÃ¤hlen', setupEstimate: 'Downloads werden geprÃ¼ft. FÃ¼r Einrichtung und Build mindestens 3 GiB freien Platz einplanen.',
    storage: 'Spielverzeichnis', setupIntro: 'WÃ¤hle deinen Spielordner. Der gepinnte Neutron-Client und seine Laufzeit werden lokal vorbereitet. Danach startet Spielen ohne Build.', errorRam: 'WÃ¤hle eine ganze MiB-Zahl innerhalb der angezeigten RAM-Grenze.',
    progressHint: 'Eine vorhandene Version bleibt bis zur erfolgreichen PrÃ¼fung erhalten.', downloadProgress: 'Download lÃ¤uft',
    firstBuildError: 'Der erste Build ist fehlgeschlagen. Es ist noch keine Version installiert. PrÃ¼fe die Build-Ausgabe und versuche es erneut.', firstBuildCancelled: 'Der Build wurde abgebrochen. Es ist noch keine Version installiert. Du kannst ihn erneut starten.',
    errorHint: 'Der Vorgang ist fehlgeschlagen. PrÃ¼fe die Build-Ausgabe. Eine vorhandene Version bleibt verfÃ¼gbar.', cancelHint: 'Vorgang abgebrochen. Installierte Versionen bleiben erhalten.', crashHint: 'Der Client wurde unerwartet beendet.',
    updateHint: 'Ein neuer Commit ist verfÃ¼gbar. Die installierte Version bleibt erhalten.', githubHint: 'Fest aufgelÃ¶ster GitHub-Commit Â· main',
    savedMemory: 'Einstellungen werden lokal gespeichert.', memoryHint: 'RAM-Auswahl mit Reserve fÃ¼r dein Betriebssystem. Die tatsÃ¤chliche Grenze steht am Regler.',
    jvmHint: 'RAM Ã¼ber den Regler. ZusÃ¤tzlich erlaubt: -XX:+UseG1GC, -XX:+UseConcMarkSweepGC, -XX:+UseSerialGC, -XX:+UseParallelGC, -XX:+DisableExplicitGC und -XX:+AlwaysPreTouch (auch mit -).',
    removeTitle: 'Diesen Build aus der Liste entfernen?', installedHint: 'WÃ¤hle eine geprÃ¼fte Version fÃ¼r den nÃ¤chsten Start. Entfernte EintrÃ¤ge behalten ihre Dateien zur Wiederherstellung.',
    closeTitle: 'Neutron ist noch aktiv.', closeHint: 'Eine Aufgabe oder der Client lÃ¤uft. WÃ¤hle, wie Neutron schlieÃŸen soll.', closeSimulation: 'Beenden bricht eine Aufgabe ab und stoppt den gesamten Client-Prozessbaum.',
    reportHint: 'Lokaler Diagnosebericht. Erkennbare Zugangsdaten und persÃ¶nliche Pfade werden entfernt. PrÃ¼fe den Text vor dem Teilen.', issueHint: 'Kopiere den geprÃ¼ften Bericht bei Bedarf. Es wird nichts automatisch verÃ¶ffentlicht.', exported: 'Logs exportiert',
  },
  en: {
    prototype: 'Stable', development: 'Neutron 0.2.1', mockNotice: 'Neutron 0.2.1 · Your installed client starts locally. Sign in inside the client.',
    setupButton: 'Complete Neutron setup', exampleFolder: 'Browse', setupEstimate: 'Downloads are verified. Allow at least 3 GiB free space for setup and builds.',
    storage: 'Game directory', setupIntro: 'Choose your game folder. The pinned Neutron client and runtime are prepared locally. Afterwards, Play starts without a build.', errorRam: 'Choose a whole MiB value within the displayed memory limit.',
    progressHint: 'An installed version is retained until verification succeeds.', downloadProgress: 'Downloading',
    firstBuildError: 'The first build failed. No version is installed yet. Check Build output and try again.', firstBuildCancelled: 'The build was cancelled. No version is installed yet. You can try again.',
    errorHint: 'The operation failed. Check Build output. An installed version remains available.', cancelHint: 'Operation cancelled. Installed versions are retained.', crashHint: 'The client exited unexpectedly.',
    updateHint: 'A newer commit is available. Your installed version is retained.', githubHint: 'Resolved GitHub commit Â· main',
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
