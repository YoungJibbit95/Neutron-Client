import type { Locale, Scenario } from './model';

export const de = {
  releaseChoice: 'Release-Version', newestStable: 'Neueste stabile Version', newestPrerelease: 'Neueste Vorabversion', publishedOn: 'Veröffentlicht am', prerelease: 'Vorabversion', stable: 'Stabil', downloadSelected: 'Version herunterladen', noReleases: 'Noch keine verfügbaren Releases geladen.', setupRequired: 'Richte zuerst Java und die Minecraft-Ressourcen ein.', releaseFiles: 'JAR + JSON', localInstallation: 'Lokale Installation', catalogCount: '{count} verfügbare Versionen',
  startingClient: 'Clientstart wird vorbereitet', retryConnection: 'Verbindung erneut prüfen',
  launch: 'Client starten', options: 'Startoptionen', settings: 'Launcher-Einstellungen', update: 'Versionen',
  launchIntro: 'Dein nächstes Spiel beginnt hier.', optionsIntro: 'Deine Laufzeit. Dein Setup.', settingsIntro: 'So fühlt sich Neutron nach dir an.', updateIntro: 'Versionen mit einer klaren Herkunft.',
  prototype: 'Interaktiver Prototyp', mockNotice: 'Beispieldaten. Kein echter Client oder Download wird gestartet.',
  prototypeControls: 'Beispielzustände', scenario: 'Zustand', simulateClose: 'Schließen simulieren', nextPhase: 'Nächste Phase', simulateError: 'Installationsfehler simulieren', logLoad: '2.000 Beispielzeilen',
  ready: 'Spielbereit', firstRun: 'Einrichtung', empty: 'Noch keine Version', offline: 'Offline bereit', updateAvailable: 'Update verfügbar', prepare: 'Vorbereiten', download: 'Herunterladen', verify: 'Prüfen', cancelled: 'Abgebrochen', error: 'Installation fehlgeschlagen', running: 'Client läuft', crash: 'Client abgestürzt',
  online: 'Online', offlineLabel: 'Offline', legacyVersion: 'Lokale Version', installedOn: 'Installiert am', version: 'Version', minecraft: 'Minecraft 1.8.9', source: 'Quelle', localReady: 'Lokal verfügbar',
  launchButton: 'Neutron starten', stop: 'Client beenden', checkUpdate: 'Auf Updates prüfen', viewUpdate: 'Update ansehen', downloadLatest: 'Neueste Version herunterladen', downloadPlay: 'Herunterladen und spielen', cancel: 'Abbrechen',
  loginHint: 'Die Minecraft-Anmeldung bleibt im Client.', offlineHint: 'Du bist offline. Installierte Versionen bleiben spielbar; die Release-Liste wird beim nächsten Abruf aktualisiert.',
  updateHint: 'Eine neue Beispiel-Version ist verfügbar. Deine installierte Version bleibt erhalten.', cancelHint: 'Der Vorgang wurde abgebrochen. Installierte Versionen bleiben unverändert.', errorHint: 'Die Beispiel-Installation ist fehlgeschlagen. Starte die letzte Version oder versuche es erneut.',
  crashHint: 'Der Beispiel-Client wurde unerwartet beendet.', exitCode: 'Exitcode', restart: 'Erneut starten', report: 'Problem melden', showReport: 'Bericht ansehen',
  firstInstallError: 'Simulation: Die erste Installation ist fehlgeschlagen. Es ist noch keine Version installiert. Versuche es erneut.', firstInstallCancelled: 'Simulation: Die erste Installation wurde abgebrochen. Es ist noch keine Version installiert. Du kannst sie erneut starten.', olderLogsDiscarded: 'Ältere Ausgabe verworfen. Anzeige ab der ältesten verfügbaren Zeile.',
  progressHint: 'Die vorherige Version bleibt bis zur erfolgreichen Prüfung aktiv.', downloadProgress: '18,5 von 50 MB · Beispiel', indeterminate: 'Fortschritt wird ermittelt', done: 'Bereit', retry: 'Erneut versuchen',
  setupTitle: 'Mach Neutron startklar.', setupIntro: 'Wähle deinen Spielordner. Die neueste Neutron-Release-Version und die benötigte Laufzeit werden heruntergeladen.', storage: 'Installationsordner', browse: 'Auswählen', exampleFolder: 'Beispielordner wählen', setupDownloads: 'Benötigte Downloads', setupEstimate: 'Größen sind Schätzwerte für diesen Prototyp.', jdkDownload: 'Java 8 · 64 Bit', clientDownload: 'Neutron · JAR + JSON', assetDownload: 'Minecraft-Assets & Natives', setupButton: 'Einrichtung simulieren',
  emptyTitle: 'Bereit für deine erste Version.', emptyHint: 'Lade eine veröffentlichte Neutron-Version herunter. Nach erfolgreicher Prüfung kannst du sie auch offline starten.',
  memory: 'Arbeitsspeicher', memoryHint: 'Beispielgerät: 16 GiB RAM. Empfehlung: 4 GiB; maximal 8 GiB im Prototyp.', recommended: 'Empfohlen', reset: 'Zurücksetzen', directory: 'Spielverzeichnis', directoryHint: 'Hier liegen deine Welten, Einstellungen und Crash-Berichte.',
  java: 'Java-Laufzeit', managed: 'Vom Launcher verwaltet', customJava: 'Eigene Java-Installation', javaHint: 'Java 8 · 64 Bit', javaPath: 'Pfad zur Java-Anwendung', display: 'Spielfenster', width: 'Breite', height: 'Höhe', fullscreen: 'Im Vollbild starten', advanced: 'Erweitert', jvmArgs: 'Zusätzliche JVM-Argumente', jvmHint: 'RAM stellst du oben ein. Keine zusätzlichen -Xmx- oder -Xms-Angaben.',
  savedMemory: 'Änderungen gelten nur für diesen Prototyp und werden nicht auf dem Rechner gespeichert.', optionErrors: 'Bitte korrigiere die markierten Startoptionen.', errorRam: 'Wähle eine ganze MiB-Zahl zwischen 1.024 und 8.192.', errorDirectory: 'Gib einen absoluten Ordnerpfad an.', errorJava: 'Gib einen absoluten Pfad zur Java-Anwendung an.', errorWindow: 'Wähle eine ganzzahlige Fenstergröße zwischen 640×480 und 7680×4320.', errorHeap: 'Entferne widersprüchliche Heap-Angaben aus den zusätzlichen Argumenten.',
  appearance: 'Dein Look', themeHint: 'Ein Akzent. Vier Stimmungen.', language: 'Sprache', languageHint: 'Die Sprache ändert sich sofort.', scale: 'UI-Skalierung', effects: 'Effektstärke', effectsHint: 'Steuert Glow und dekorative Flächen.', reduced: 'Bewegung reduzieren', reducedHint: 'Die Systemeinstellung wird zusätzlich berücksichtigt.', systemMotion: 'Systempräferenz berücksichtigt',
  current: 'Ausgewählt', installed: 'Installierte Versionen', installedHint: 'Wähle eine Version für deinen nächsten Start.', github: 'GitHub Releases', githubHint: 'Beispiel-Releases · keine Live-Abfrage', checked: 'Zuletzt geprüft', select: 'Auswählen', remove: 'Entfernen', protected: 'Ausgewählte und laufende Versionen sind geschützt.', noBuilds: 'Noch keine Version installiert.', size: 'Größe',
  console: 'Konsole', clientLogs: 'Client', installationLogs: 'Installation', search: 'Logs durchsuchen', allLevels: 'Alle Stufen', info: 'Info', warn: 'Warnungen', errors: 'Fehler', copy: 'Kopieren', export: 'Exportieren', copied: 'Kopiert', copyFailed: 'Kopieren nicht verfügbar', exported: 'Beispiel-Logs exportiert', noLogs: 'Keine passenden Logzeilen.', autoScroll: 'Automatisch scrollen', paused: 'Scrollen pausiert', newLines: '{count} neue Zeilen', resume: 'Zu den neuesten Zeilen', collapse: 'Konsole einklappen', expand: 'Konsole öffnen', resize: 'Konsolenhöhe ändern', consoleHeight: 'Konsolenhöhe',
  removeTitle: 'Diese Beispiel-Version entfernen?', removeHint: 'Die ausgewählte und die laufende Version bleiben geschützt.', keep: 'Behalten',
  closeTitle: 'Der Beispiel-Client läuft noch.', closeHint: 'Wähle, wie Neutron beim Schließen reagieren soll.', minimize: 'In den Hintergrund', quitBoth: 'Client und Launcher beenden', stay: 'Zurück zum Launcher', closeSimulation: 'Nur die Auswahl wird simuliert. Es wird kein Fenster geschlossen.',
  reportTitle: 'Problembericht prüfen', reportHint: 'Diese Vorschau enthält nur Beispieldaten. Erkennbare Tokens und persönliche Pfade werden entfernt.', issuePreview: 'GitHub-Issue-Vorschau', issueHint: 'Im Prototyp wird keine Website geöffnet und nichts veröffentlicht.', close: 'Schließen', demoStopped: 'Beispiel-Client beendet. Das Launcherfenster bleibt im Prototyp offen.',
  profile: 'Profil', example: 'Beispiel', noCredentials: 'Keine echten Zugangsdaten oder persönlichen Spieldaten.',
} as const;
export type CopyKey = keyof typeof de;
export const en: Record<CopyKey, string> = {
  releaseChoice: 'Release version', newestStable: 'Latest stable version', newestPrerelease: 'Latest pre-release', publishedOn: 'Published on', prerelease: 'Pre-release', stable: 'Stable', downloadSelected: 'Download version', noReleases: 'No available releases loaded yet.', setupRequired: 'Set up Java and Minecraft resources first.', releaseFiles: 'JAR + JSON', localInstallation: 'Local installation', catalogCount: '{count} available versions',
  startingClient: 'Preparing client launch', retryConnection: 'Check connection again',
  launch: 'Launch Client', options: 'Launch Options', settings: 'Launcher Settings', update: 'Versions',
  launchIntro: 'Your next game starts here.', optionsIntro: 'Your runtime. Your setup.', settingsIntro: 'Make Neutron feel like yours.', updateIntro: 'Versions with a clear origin.',
  prototype: 'Interactive prototype', mockNotice: 'Example data. No real client or download will start.',
  prototypeControls: 'Example states', scenario: 'State', simulateClose: 'Simulate closing', nextPhase: 'Next phase', simulateError: 'Simulate installation failure', logLoad: '2,000 example lines',
  ready: 'Ready to play', firstRun: 'Setup', empty: 'No version yet', offline: 'Ready offline', updateAvailable: 'Update available', prepare: 'Preparing', download: 'Downloading', verify: 'Verifying', cancelled: 'Cancelled', error: 'Installation failed', running: 'Client running', crash: 'Client crashed',
  online: 'Online', offlineLabel: 'Offline', legacyVersion: 'Local version', installedOn: 'Installed on', version: 'Version', minecraft: 'Minecraft 1.8.9', source: 'Source', localReady: 'Available locally',
  launchButton: 'Launch Neutron', stop: 'Stop client', checkUpdate: 'Check for updates', viewUpdate: 'View update', downloadLatest: 'Download latest version', downloadPlay: 'Download and play', cancel: 'Cancel',
  loginHint: 'Minecraft sign-in stays inside the client.', offlineHint: 'You are offline. Installed versions remain playable; the release list refreshes when you reconnect.',
  updateHint: 'A new example version is available. Your installed version is kept.', cancelHint: 'The operation was cancelled. Installed versions are unchanged.', errorHint: 'The example installation failed. Launch the last version or try again.',
  crashHint: 'The example client stopped unexpectedly.', exitCode: 'Exit code', restart: 'Launch again', report: 'Report a problem', showReport: 'View report',
  firstInstallError: 'Simulation: The first installation failed. No version is installed yet. Try again.', firstInstallCancelled: 'Simulation: The first installation was cancelled. No version is installed yet. You can start it again.', olderLogsDiscarded: 'Older output discarded. Showing the oldest available line.',
  progressHint: 'The previous version stays active until verification succeeds.', downloadProgress: '18.5 of 50 MB · example', indeterminate: 'Determining progress', done: 'Ready', retry: 'Try again',
  setupTitle: 'Get Neutron ready.', setupIntro: 'Choose your game folder. The latest Neutron release and its required runtime will be downloaded.', storage: 'Installation folder', browse: 'Choose', exampleFolder: 'Choose example folder', setupDownloads: 'Required downloads', setupEstimate: 'Sizes are illustrative estimates for this prototype.', jdkDownload: 'Java 8 · 64 bit', clientDownload: 'Neutron · JAR + JSON', assetDownload: 'Minecraft assets & natives', setupButton: 'Simulate setup',
  emptyTitle: 'Ready for your first version.', emptyHint: 'Download a published Neutron version. After verification, you can launch it offline too.',
  memory: 'Memory', memoryHint: 'Example device: 16 GiB RAM. Recommended: 4 GiB; prototype maximum: 8 GiB.', recommended: 'Recommended', reset: 'Reset', directory: 'Game directory', directoryHint: 'Your worlds, settings and crash reports live here.',
  java: 'Java runtime', managed: 'Managed by the launcher', customJava: 'Custom Java installation', javaHint: 'Java 8 · 64 bit', javaPath: 'Path to Java executable', display: 'Game window', width: 'Width', height: 'Height', fullscreen: 'Launch in fullscreen', advanced: 'Advanced', jvmArgs: 'Additional JVM arguments', jvmHint: 'Set memory above. Do not add -Xmx or -Xms arguments.',
  savedMemory: 'Changes apply to this prototype session and are not saved to your computer.', optionErrors: 'Please correct the highlighted launch options.', errorRam: 'Choose a whole MiB value between 1,024 and 8,192.', errorDirectory: 'Enter an absolute directory path.', errorJava: 'Enter an absolute path to the Java executable.', errorWindow: 'Choose whole window dimensions between 640×480 and 7680×4320.', errorHeap: 'Remove conflicting heap options from the additional arguments.',
  appearance: 'Your look', themeHint: 'One accent. Four moods.', language: 'Language', languageHint: 'The language changes immediately.', scale: 'UI scale', effects: 'Effect intensity', effectsHint: 'Controls glow and decorative surfaces.', reduced: 'Reduce motion', reducedHint: 'The system preference is respected as well.', systemMotion: 'System preference respected',
  current: 'Selected', installed: 'Installed versions', installedHint: 'Choose a version for your next launch.', github: 'GitHub releases', githubHint: 'Example releases · no live request', checked: 'Last checked', select: 'Select', remove: 'Remove', protected: 'Selected and running versions are protected.', noBuilds: 'No version installed yet.', size: 'Size',
  console: 'Console', clientLogs: 'Client', installationLogs: 'Installation', search: 'Search logs', allLevels: 'All levels', info: 'Info', warn: 'Warnings', errors: 'Errors', copy: 'Copy', export: 'Export', copied: 'Copied', copyFailed: 'Copy unavailable', exported: 'Example logs exported', noLogs: 'No matching log lines.', autoScroll: 'Auto-scroll', paused: 'Scrolling paused', newLines: '{count} new lines', resume: 'Jump to latest lines', collapse: 'Collapse console', expand: 'Open console', resize: 'Resize console', consoleHeight: 'Console height',
  removeTitle: 'Remove this example version?', removeHint: 'Selected and running versions stay protected.', keep: 'Keep version',
  closeTitle: 'The example client is still running.', closeHint: 'Choose how Neutron should respond when closing.', minimize: 'Keep in background', quitBoth: 'Stop client and launcher', stay: 'Back to launcher', closeSimulation: 'Only the choice is simulated. No window will close.',
  reportTitle: 'Review problem report', reportHint: 'This preview contains example data only. Recognizable tokens and personal paths are removed.', issuePreview: 'GitHub issue preview', issueHint: 'The prototype will not open a website or publish anything.', close: 'Close', demoStopped: 'Example client stopped. The launcher stays open in this prototype.',
  profile: 'Profile', example: 'example', noCredentials: 'No real credentials or personal game data.',
};
export const dictionaries = { de, en };
const liveCopy: Record<Locale, Partial<Record<CopyKey, string>>> = {
  de: {
    prototype: 'Stable', mockNotice: 'Veröffentlichte Neutron-Versionen direkt herunterladen und lokal starten. Anmeldung im Client.',
    setupButton: 'Neutron vollständig einrichten', exampleFolder: 'Auswählen', setupEstimate: 'Downloads werden geprüft. Für Einrichtung und Updates mindestens 3 GiB freien Platz einplanen.',
    storage: 'Spielverzeichnis', setupIntro: 'Wähle deinen Spielordner. Die aktuelle Neutron-Release-Version wird als JAR und JSON heruntergeladen und mit ihrer Laufzeit eingerichtet.', errorRam: 'Wähle eine ganze MiB-Zahl innerhalb der angezeigten RAM-Grenze.',
    progressHint: 'Eine vorhandene Version bleibt bis zur erfolgreichen Prüfung erhalten.', downloadProgress: 'Download läuft',
    firstInstallError: 'Die erste Installation ist fehlgeschlagen. Es ist noch keine Version installiert. Prüfe die Installationsausgabe und versuche es erneut.', firstInstallCancelled: 'Die Installation wurde abgebrochen. Es ist noch keine Version installiert. Du kannst sie erneut starten.',
    errorHint: 'Der Vorgang ist fehlgeschlagen. Prüfe die Installationsausgabe. Eine vorhandene Version bleibt verfügbar.', cancelHint: 'Vorgang abgebrochen. Installierte Versionen bleiben erhalten.', crashHint: 'Der Client wurde unerwartet beendet.',
    updateHint: 'Eine neuere Release-Version ist verfügbar. Die installierte Version bleibt erhalten.', githubHint: 'Veröffentlichte Neutron-Versionen mit JAR und JSON',
    savedMemory: 'Einstellungen werden lokal gespeichert.', memoryHint: 'RAM-Auswahl mit Reserve für dein Betriebssystem. Die tatsächliche Grenze steht am Regler.',
    jvmHint: 'RAM über den Regler. Zusätzlich erlaubt: -XX:+UseG1GC, -XX:+UseConcMarkSweepGC, -XX:+UseSerialGC, -XX:+UseParallelGC, -XX:+DisableExplicitGC und -XX:+AlwaysPreTouch (auch mit -).',
    removeTitle: 'Diese Version aus der Liste entfernen?', installedHint: 'Wähle eine geprüfte Version für den nächsten Start. Entfernte Einträge behalten ihre Dateien zur Wiederherstellung.',
    closeTitle: 'Neutron ist noch aktiv.', closeHint: 'Eine Aufgabe oder der Client läuft. Wähle, wie Neutron schließen soll.', closeSimulation: 'Beenden bricht eine Aufgabe ab und stoppt den gesamten Client-Prozessbaum.',
    reportHint: 'Lokaler Diagnosebericht. Erkennbare Zugangsdaten und persönliche Pfade werden entfernt. Prüfe den Text vor dem Teilen.', issueHint: 'Kopiere den geprüften Bericht bei Bedarf. Es wird nichts automatisch veröffentlicht.', exported: 'Logs exportiert',
  },
  en: {
    prototype: 'Stable', mockNotice: 'Download published Neutron versions directly and launch locally. Sign in inside the client.',
    setupButton: 'Complete Neutron setup', exampleFolder: 'Browse', setupEstimate: 'Downloads are verified. Allow at least 3 GiB free space for setup and updates.',
    storage: 'Game directory', setupIntro: 'Choose your game folder. The current Neutron release is downloaded as a JAR and JSON and installed with its runtime.', errorRam: 'Choose a whole MiB value within the displayed memory limit.',
    progressHint: 'An installed version is retained until verification succeeds.', downloadProgress: 'Downloading',
    firstInstallError: 'The first installation failed. No version is installed yet. Check Installation output and try again.', firstInstallCancelled: 'The installation was cancelled. No version is installed yet. You can try again.',
    errorHint: 'The operation failed. Check Installation output. An installed version remains available.', cancelHint: 'Operation cancelled. Installed versions are retained.', crashHint: 'The client exited unexpectedly.',
    updateHint: 'A newer release is available. Your installed version is retained.', githubHint: 'Published Neutron versions with a JAR and JSON',
    savedMemory: 'Settings are saved locally.', memoryHint: 'Leave memory for the operating system. The slider shows the actual limit.',
    jvmHint: 'Set RAM with the slider. Also supported: -XX:+UseG1GC, -XX:+UseConcMarkSweepGC, -XX:+UseSerialGC, -XX:+UseParallelGC, -XX:+DisableExplicitGC and -XX:+AlwaysPreTouch (minus also allowed).',
    removeTitle: 'Remove this version from the list?', installedHint: 'Select a verified version for the next launch. Removed entries retain files for recovery.',
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
  ready: 'ready', 'first-run': 'firstRun', empty: 'empty', offline: 'offline', update: 'updateAvailable', prepare: 'prepare', download: 'download', verify: 'verify', cancelled: 'cancelled', error: 'error', running: 'running', crash: 'crash',
};
export function formatDate(value: string, locale: Locale) {
  return new Intl.DateTimeFormat(locale === 'de' ? 'de-DE' : 'en-GB', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Europe/Berlin' }).format(new Date(value));
}
