# Orbis

Ein modernes Wallet für deine eigene (Spiel-)Währung – als Android-App.

**Download:** https://github.com/OWNER/REPO/releases/latest/download/orbis.apk

Jeder Push auf `main` baut automatisch eine neue APK (GitHub Actions) und veröffentlicht sie als Release.

## Features

- **Konten & Sicherheit:** mehrere Konten pro Gerät, Konto-IDs wie `OR-90AAB`, 4-stellige PIN (gehasht), Fingerabdruck, Sperre nach 5 Fehlversuchen, Auto-Sperre, PIN-Bestätigung für Zahlungen, Tageslimit, Screenshot-Schutz
- **Senden:** Empfänger aus Kontakten, Favoriten, letzten Partnern oder per Konto-ID, eigenes Ziffernfeld, Schnellbeträge, Notiz und Kategorie, Bestätigungsseite, Erfolgsanimation, Beleg teilen
- **Anfordern:** Geld von anderen Konten anfordern, Anfragen annehmen oder ablehnen, zurückziehen, Zahlungslink teilen
- **Rechnung teilen:** Gesamtbetrag gleichmäßig auf mehrere Personen aufteilen
- **Aufladen:** Guthaben einzahlen
- **Verlauf:** Suche, Filter (Eingang/Ausgang/Sparen/Daueraufträge), Kategorien, nach Tagen gruppiert, Detailansicht mit Beleg
- **Live-Transaktionen:** Echtzeit-Feed aller Zahlungen auf dem Gerät (optional anonym)
- **Statistik:** Einnahmen/Ausgaben pro Monat, Kategorien-Donut, Wochen- und 7-Tage-Diagramme, Top-Empfänger
- **Budgets:** Monatslimits pro Kategorie mit Fortschrittsbalken
- **Sparziele:** mit Emoji, Farbe, Fortschrittsring, Ein- und Auszahlungen
- **Daueraufträge:** täglich, wöchentlich oder monatlich; verpasste Läufe werden nachgeholt
- **Kontakte:** Favoriten, bearbeiten, direkt senden oder anfordern
- **QR-Code & Zahlungslinks:** `orbis://pay?to=…&amount=…` öffnet direkt das Senden
- **Design:** Dunkel, Hell, AMOLED oder System, 6 Akzent-Verläufe, schwebende Navigationsleiste, Animationen, haptisches Feedback
- **Währung:** Symbol, Name, Position und ID-Präfix frei wählbar
- **Daten:** CSV-Export, Backup und Wiederherstellung als JSON, Konto löschen, alles zurücksetzen

Alle Daten bleiben lokal auf dem Gerät (`bank.json`).

## Bauen

```
./gradlew assembleRelease
```
