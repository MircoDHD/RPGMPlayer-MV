# Änderungen

Deutsche Fassung von CHANGELOG.md für die "Was ist neu"-Box der App. Bei jeder Version
zusammen mit CHANGELOG.md aktualisieren.

## 0.4.3 – 21.10.2026
### Behoben
- **Dünne Linien zwischen Kartenkacheln** auf manchen Grafikchips (Mali, wie in vielen
  MediaTek- und Exynos-Geräten): Shader rechnen jetzt mit hoher Genauigkeit, wo der Chip
  das kann.
- **Haptisches Feedback** blieb stumm, wenn die Touch-Vibration von Android aus war. Es
  steuert jetzt direkt den Vibrationsmotor an, für die Bildschirmsteuerung und für den
  kurzen Impuls beim Einschalten.
- **Speichern- und Laden-Menü öffnen viel schneller** auf langsamem Speicher: Gerade
  gelesene oder geschriebene Spielstände bleiben im Speicher, statt für jeden Slot neu
  gelesen zu werden. Auch das Speichern geht schneller.
- Text mit ungültiger Ausrichtung füllt das Log nicht mehr mit Browser-Warnungen.
### Geändert
- **Der Frame-Graph ist ein eigenes HUD-Modul**, damit die Frame-Zeit auch ohne ihn
  angezeigt werden kann. Bestehende HUD-Einstellungen behalten den Graph.
- Ein Spiel direkt nach dem Scan der Bibliothek zu starten, überspringt den zweiten Scan.
- Diagnose-Logs halten einmal pro Minute Bildrate und Gerätezustand fest (Akku, Wärme,
  Speicher), um Einbrüche in langen Sitzungen zu finden.

## 0.4.2 – 19.10.2026
### Neu
- HUD-Farben: Bildrate, Frame-Zeit (auch der Graph) und 1 %-Low werden grün, gelb oder
  rot, gemessen an der gewählten Bildrate (bei 30 fps sind 30 grün, gelb erst unter 24);
  andere Werte werden nur nahe einer Grenze gelb oder rot (wenig Akku, heißer Akku,
  Wärme, JavaScript-Speicher oder -Zeit, langsame Spiellogik).

## 0.4.1 – 19.10.2026 (Phase B, Teil 2: Bildraten)
### Neu
- **Bildraten-Auswahl** global und pro Spiel: 30 fps (Akku sparen), 60 fps (Standard) und
  die Display-Rate (*experimentell*). Gilt sofort, auch mitten im Spiel.
- ⓘ-Symbole bei der Bildrate und beim Renderer erklären jede Option in einfachen Worten:
  wann sie sinnvoll ist, Vorteile und Nachteile.
### Behoben
- Die Spielzeit im Spiel stimmt jetzt bei jeder Bildrate. MV zählte gezeichnete Bilder,
  daher lief die Spielzeit bei 120 Hz doppelt so schnell und wäre bei 30 fps nur halb so
  schnell gelaufen; jedes Bild zählt jetzt die Logikschritte, die es zeigt.
### Geändert
- Der globale Schalter "Auf 60 fps begrenzen" ist jetzt eine Bildraten-Auswahl.
- Die Kurzbeschreibungen von Canvas und WebGL 2 sagen jetzt, wofür sie gut sind.

## 0.4.0 – 19.10.2026 (Phase B, Teil 1: Renderer und Performance-HUD)
### Neu
- **Renderer-Wahl**, global und pro Spiel: WebGL (Standard), Canvas oder WebGL 2
  (*experimentell*; weicht auf WebGL aus, wenn es nicht startet). Eine Änderung während
  des Spiels bietet einen Neustart an, weil der Renderer beim Spielstart festgelegt wird.
- **Performance-HUD** über dem Spiel, einschaltbar in den Einstellungen, im Schnellmenü
  oder per zugewiesener Controller-Taste. Module: FPS, Frame-Zeit mit Graph, 1 %-Low und
  langsame Bilder, Logik-Schritte und Bildwiederholrate, JavaScript-Zeit pro Bild,
  JS-Heap, Bilder-Cache, App-Speicher, Akku (Ladestand, Laden, Temperatur), thermischer
  Status, Renderer und Grafikchip, Uhr und Sitzungszeit sowie die Kosten des HUD selbst.
- HUD-Einstellungen: Vorlagen (Minimal, Standard, Voll), Module zum Anhaken und
  Umsortieren per Ziehen, Ecke, Größe, Deckkraft, kompakte oder ausführliche Darstellung,
  Breite, bei der die kompakte Zeile umbricht (50, 75 oder 100 % des Bildschirms),
  ausgeschriebene oder abgekürzte Beschriftung (FPS, HEAP, BATT … wie bei verbreiteten
  PC-Overlays), Aktualisierungsrate, mit Live-Vorschau. Das ⓘ-Symbol erklärt jeden Wert. Im Spiel wechselt ein Tipp aufs HUD zwischen
  kompakt und ausführlich, und per Ziehen lässt es sich dorthin schieben, wo es am wenigsten
  stört; die Bildschirmsteuerung darüber behält Vorrang.
### Geändert
- Auswahldialoge zeigen eine kurze Beschreibung unter jeder Option und bleiben im
  Querformat vollständig sichtbar.
### Behoben
- Die Renderer-Einstellung des Spiels selbst (zum Beispiel im Plugin Community_Basic)
  konnte die Wahl des Spielers überschreiben.

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
