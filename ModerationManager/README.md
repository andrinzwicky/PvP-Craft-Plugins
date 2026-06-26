# ModerationManager

Ein erweiterbares Moderations-Plugin für Paper **1.21.11**. Bringt `/warn` und
`/warns` mit und führt eine gemeinsame Historie aller Verwarnungen, Kicks und
Bans pro Spieler. Keine externe Datenbank nötig — alles in `history.yml`.

## Befehle

| Befehl | Beschreibung |
|---|---|
| `/warn <spieler> <grund>` | Verwarnt einen Spieler. Sichtbar **nur** für den verwarnten Spieler und das gesamte Team. |
| `/unwarn <spieler> [nummer]` | Entfernt eine Verwarnung (neueste, oder die `#nummer` aus `/warns`). Kicks/Bans bleiben. |
| `/mute <spieler> <dauer> [grund]` | Schaltet einen Spieler in Chat **und** privaten Nachrichten (`/msg`, `/tell`, `/r`, `/me`) stumm. Dauer z.B. `30m`, `2h`, `1d`, `perm`. |
| `/unmute <spieler>` | Hebt die Stummschaltung auf. |
| `/warns <spieler>` | Zeigt die komplette Historie (Warns, Mutes, Kicks, Bans). Aliase: `/history`, `/modlog`. |

Stummschaltungen sind zeitbasiert, überdauern einen Neustart (`mutes.yml`) und laufen
automatisch ab. Ein gemuteter Spieler bekommt beim Schreibversuch den Hinweis
„Du bist noch X stummgeschaltet. Schreibe ein Ticket, wenn es ungerechtfertigt ist."

## Logging privater Nachrichten

Alle zugestellten privaten Nachrichten (`/msg`, `/tell`, `/r` …) werden in eine
Datei geschrieben (Standard: `private-messages.log`). Konfigurierbar bzw.
abschaltbar unter `message-logging` in der `config.yml`.

> **Datenschutz:** Private Nachrichten sind personenbezogene Daten. Das Mitloggen
> auf dem eigenen Server ist zulässig, **muss aber offengelegt werden** (Serverregeln
> / Datenschutzhinweis), z.B. „Chat- und Privatnachrichten werden zu
> Moderationszwecken gespeichert." Zugriff nur fürs Team.

## Sichtbarkeit & Berechtigung

- **Team = Supporter und höher** (Supporter, Moderator, Developer, Admin, Owner).
  Builder, Marketing und Spieler sind **kein** Team — sie sehen Verwarnungen nicht
  und dürfen die Befehle nicht nutzen.
- Eine Verwarnung sieht der verwarnte Spieler (persönliche Nachricht) plus das
  gesamte Online-Team. Normale Spieler sehen nichts.
- Genutzt werden darf `/warn` / `/warns` von jedem mit der Permission
  `moderation.use` (default: `op`) **oder** jedem Team-Rang — die Erkennung läuft
  über die `rankmanager.rank.*`-Permissions aus dem RankManager-Plugin.

## Kicks & Bans in der Historie

Vanilla-`/kick`, `/ban` und `/ban-ip` werden automatisch mitgeloggt (über
Command-Abfang), sodass sie zusammen mit den Verwarnungen unter `/warns`
auftauchen. Geloggt wird nur, wenn der Ausführende die passende Permission
besitzt und der Zielname bekannt ist (filtert Tippfehler/Berechtigungsfehler).

> Hinweis: Das Logging hängt sich an die Befehlsausführung, nicht an einen
> Server-internen Bann-Event. Kicks/Bans durch andere Plugins werden daher nur
> erfasst, wenn sie über die genannten Befehle laufen.

## Erweitern

Das Plugin ist auf weitere Moderationsbefehle ausgelegt:

- Neue Aktionstypen (z. B. `MUTE`, `TEMPBAN`) in `history/ActionType.java`
  ergänzen — Speicherung und `/warns`-Anzeige verstehen sie automatisch.
- Neue Befehle registrieren in `ModerationManager.onEnable()` und dabei
  `history()` (Speichern), `notifyTeam(...)` (Team-Broadcast) und `msg(...)`
  (Nachrichten aus `config.yml`) wiederverwenden.

## Bauen

Benötigt ein JDK **21+**.

```bash
./gradlew build
```

Das Plugin-Jar entsteht unter `build/libs/ModerationManager-1.0.0.jar`. Es nutzt
ausschließlich die Paper-API (kein NMS), ist Mojang-mapped und kompatibel mit
`-Dpaper.disablePluginRemapping=true`. Jar in den `plugins/`-Ordner legen und
Paper 1.21.11 starten.
