# Änderungen

Deutsche Fassung von CHANGELOG.md für die "Was ist neu"-Box der App. Bei jeder Version
zusammen mit CHANGELOG.md aktualisieren.

## 0.3.3 – 18.10.2026
### Geändert
- **Neue Package-ID** `io.github.mircodhd.rpgmplayermv`: Diese Version installiert sich als
  neue App. Deinstalliere die alte und füge deine Spiele neu hinzu; Spielstände in den
  Spielordnern bleiben erhalten.
- **Kein Zugriff auf alle Dateien mehr.** Jeder Spielordner wird über die Ordnerauswahl des
  Systems freigegeben (Lese- und Schreibzugriff nur auf diesen Ordner); Logs landen ohne
  Berechtigung in `Documents/RPGM Player/logs/`.
- Kurzname **RPGM Player - MV**. Der Titel bleibt oben in der Leiste neben dem
  Einstellungssymbol, statt wegzuscrollen.
- Spieleinstellungen haben keine eigenen "Standard verwenden"-Einträge mehr: Die Option, die
  der globalen Einstellung entspricht, trägt den Hinweis **Standard**; wer sie wählt, folgt
  wieder der globalen Einstellung.
- Bildschirmsteuerung: Beschriftungen wachsen mit der Taste und bleiben mittig; die
  Layout-Vorschauen zeigen Deckkraft, Größe und Linkshändermodus live.
### Neu
- Layout-Editor: Größenanzeige am ausgewählten Element und ein Feld zum Eintippen der
  Größe, Elemente hinzufügen und löschen, Tastenbeschriftungen umbenennen (nur pro Spiel)
  und eine Werkzeugleiste, die sich zur Seite ziehen lässt.
- Beim Einschalten der Vibration gibt es einen kurzen Impuls, damit man sie sofort spürt.
- "Was ist neu"-Box nach einem Update; auch unter Einstellungen → Info abrufbar.
- Einstellungen exportieren und importieren: globale Einstellungen, eigene Layouts und
  Spieleinstellungen.
- Eine kleine Überraschung in den Einstellungen für Neugierige.

## 0.3.2 – 18.10.2026
### Behoben
- Ein Spiel erneut zu starten, nachdem man eines über "Spiel verlassen" beendet hat, hängt
  nicht mehr beim Laden, bis die App ganz geschlossen wird. Das Verlassen hatte die
  JavaScript-Timer der ganzen App angehalten.

## 0.3.1 – 18.10.2026
### Geändert
- Die App heißt nicht mehr "MV Player", sondern **RobustPlainGameMaker Player -
  MyVersion**; Startbildschirm und Bibliothek zeigen die Kurzform **RPGM Player**.
- Logs werden jetzt in `Documents/RPGM Player/logs/` geschrieben.
- Der Bereich "Info" zeigt den vollen App-Namen mit Version.
- APK-Dateien heißen `RPGM-Player-<Version>-<debug|release>.apk`.
### Neu
- Release-APKs sind signiert.

## 0.3.0 – 18.10.2026 (Phase A: Eingabe)
### Neu
- Einstellungen auf zwei Ebenen: globale Standards und Abweichungen pro Spiel, erreichbar
  über die Infokarte des Spiels und im Spiel.
- Controller-Belegung für alle Spielaktionen und das Schnellmenü, global oder pro Spiel;
  mehrere Tasten und Tastenkombinationen pro Aktion; Hinweis bei Konflikten.
- Bildschirm-Gamepad mit fünf Vorlagen: Multitouch, gleitendes Steuerkreuz, Analogstick,
  Vibration, Deckkraft, Größe, Linkshändermodus; erscheint automatisch ohne Controller.
- Vorlagen-Galerie mit Vorschau und ein Layout-Editor (verschieben, Größe ändern,
  ausblenden, zurücksetzen; für ein Spiel oder alle speichern).
- Schnellmenü im Spiel (Select + Start, Zurück-Geste oder ☰-Taste): fortsetzen,
  Bildschirmsteuerung, Layout, Bildrate, Spieleinstellungen, beenden. Pausiert das Spiel.
- Namenseingaben öffnen die Android-Tastatur.
- Bildrate pro Spiel (60 fps oder unbegrenzt, experimentell).
### Behoben
- Pausieren für ein Menü und Pausieren der App stören sich nicht mehr gegenseitig.

## 0.2.0 – 17.10.2026
### Neu
- Material-3-Oberfläche mit dynamischen Farben: Spielebibliothek mit Titelbild, Ordner,
  Spielzeit und Play-Taste.
- Infokarte pro Spiel: MV- und PIXI-Version, Plugins, Größe, Dateien, Verschlüsselung;
  Spiele aus der Bibliothek entfernen (Dateien bleiben unberührt).
- Einstellungen: 60-fps-Begrenzung, Diagnose-Logs abschaltbar, Logs teilen, Version,
  Entwickler-Link, Lizenz.
- Spielzeit wird von der App gemessen.
- Lizenz: GPL-3.0-or-later.

## 0.1.4 – 17.10.2026
### Behoben
- Auf 120-Hz-Bildschirmen lief die Spielzeit doppelt so schnell: Die Bildrate ist jetzt
  standardmäßig auf 60 fps begrenzt.
### Geändert
- Wiederholte Log-Zeilen werden zusammengefasst; Version auf dem Startbildschirm.

## 0.1.3 – 17.10.2026
### Neu
- "Logs teilen" exportiert alle Logs als Zip-Datei.

## 0.1.2 – 17.10.2026
### Neu
- Ausführliche Diagnose-Logs in Sitzungsdateien.

## 0.1.1 – 17.10.2026
### Behoben
- Der Spielton konnte stumm bleiben, nachdem die App im Hintergrund war (z. B. beim
  Koppeln eines Controllers).

## 0.1.0 – 17.10.2026
### Neu
- Erste Version: spielt RPG-Maker-MV-Spiele aus einem Ordner im Vollbild, mit lokal
  bereitgestellten Spieldateien (keine Internetberechtigung), kleinen
  Kompatibilitäts-Patches, PC-kompatiblen Spielständen, Controller-Unterstützung und einem
  Steam-Ersatz.
