# RankManager – Änderungen

Datum: 2026-06-08

## Nametag über dem Skin + Chat-Format (mit optionaler ClanSystem-Anbindung)

**Neu (CHANGE 1) – Nametag über dem Spieler-Skin:** Über dem Kopf jedes Spielers
steht jetzt `[Rang] Spielername [ClanTag]`. Der Rang-Präfix nutzt den Farbverlauf
aus der `config.yml`, der Clan-Tag die Clan-Farbe (siehe unten). Umgesetzt über
Scoreboard-Team-`prefix`/`suffix`; aktualisiert beim Join, bei Rang-Wechsel und
bei `/rank reload`.

**Neu (CHANGE 2) – Chat-Format:** Chat wird als
`[Rang] Spielername [ClanTag]: Nachricht` gerendert (Paper `AsyncChatEvent`). Das
Layout ist über `chat.format` in der `config.yml` frei konfigurierbar
(Platzhalter `<rank> <player> <clan> <message>`). Der Clan-Tag erscheint nur, wenn
ClanSystem ihn liefert, sonst entfällt er rückstandslos. Spieler-Text wird als
Component eingesetzt und **niemals** als MiniMessage geparst (keine Farb-/Tag-
Injektion).

**ClanSystem-Anbindung (optional, keine harte Abhängigkeit):** RankManager liest
den Clan-Tag entkoppelt aus zwei Bukkit-Metadaten am Spieler:
`clan_tag` (Tag-Text) und `clan_color` (Hex `#rrggbb` oder Farbname, optional,
Standard Weiß). Ist ClanSystem nicht installiert, sind die Metadaten nie gesetzt –
der Clan-Tag wird **still übersprungen**, nichts stürzt ab. Jeder Zugriff ist
defensiv (`try/catch`) gekapselt, damit ein fehlerhaftes Clan-Plugin RankManager
nicht beeinträchtigen kann.

### Wichtige Design-Entscheidung: ein Team pro Spieler statt pro Rang
Der Nametag über dem Kopf wird aus dem **einen** Scoreboard-Team des Spielers
gerendert (Präfix + Name + Suffix). Ein Spieler kann nur in genau einem Team sein,
also muss dasselbe Team sowohl den Rang-Präfix als auch den **spielerindividuellen**
Clan-Suffix tragen – ein gemeinsames Team pro Rang kann das nicht. Deshalb bekommt
jeder Spieler sein eigenes Team, benannt als `rm_<priority>_<id>`. Die Tab-Listen-
Sortierung bleibt dadurch **unverändert** nach Rang-Priorität gruppiert; wir
gewinnen nur einen pro-Spieler-Suffix-Slot für den Clan-Tag.

### Geänderte/neue Dateien
- **`Rank.java`** – Farb-Logik in `colorize(...)` zusammengeführt; neue Methoden
  `nametagPrefix()` (Über-Kopf-Präfix `[Rang] `) und `chatPrefix()` (`[Rang]`).
- **`ClanHook.java`** *(neu)* – entkoppelte ClanSystem-Brücke: liest `clan_tag`/
  `clan_color` aus Metadaten, liefert Nametag-Suffix bzw. Chat-Tag als Component
  (leer ohne Clan); voll fehlertolerant.
- **`TabListManager.java`** – pro-Spieler-Team mit `prefix(Rang)` + `suffix(Clan)`;
  leere Alt-Teams werden aufgeräumt. Über-Kopf-Nametag ergänzt.
- **`listener/ChatListener.java`** *(neu)* – `AsyncChatEvent`-Renderer nach
  `chat.format`.
- **`RankManager.java`** – `ChatListener` registriert; neue öffentliche Methode
  `refreshDisplay(Player)` (Einstiegspunkt für ClanSystem nach Clan-Wechsel).
- **`config.yml`** – Abschnitt `chat.format` plus Doku des ClanSystem-Metadaten-
  Vertrags (`clan_tag`/`clan_color`).

Build (`./gradlew build`) erfolgreich. Fertiges Plugin in `Fertige Plugins/`
(Vorgängerversion nach `alte Versionen/` archiviert).

---

# RankManager – Änderungen

Datum: 2026-06-07

## Externe Permission-Nodes pro Rang (`extra-permissions`)

**Neu:** Jeder Rang in `config.yml` unterstützt jetzt eine optionale Liste
`extra-permissions` mit zusätzlichen Permission-Nodes. Diese werden zusätzlich zu
den eingebauten Fähigkeiten des Rangs vergeben – ideal, um Nodes anderer Plugins
(z. B. `coreprotect.inspect`, `coreprotect.lookup`, `coreprotect.rollback`,
`coreprotect.restore`) ohne externes Permission-Plugin zuzuteilen.

**Umsetzung:** Wie die übrigen Rechte werden sie über das Bukkit
`PermissionAttachment` gesetzt – beim Join und bei jedem Rang-Wechsel
(`PermissionManager.apply`).

### Geänderte Dateien
- **`Rank.java`** – Record um Feld `List<String> extraPermissions` erweitert.
- **`RankManager.java`** – `loadRanks()` liest `extra-permissions` per
  `getStringList(...)` und gibt sie an den `Rank`-Konstruktor weiter.
- **`PermissionManager.java`** – `apply(...)` setzt zusätzlich jeden Node aus
  `rank.extraPermissions()` (leere/blanke Werte werden übersprungen).
- **`config.yml`** – `extra-permissions` für die Ränge dokumentiert und für
  Spieler bis Owner mit den CoreProtect-Nodes vorbelegt.

Build (`./gradlew build`) erfolgreich.

---

# RankManager – Änderungen

Datum: 2026-06-05

## Supporter darf jetzt zwischen Survival und Spectator wechseln

**Geändert:** Supporter sind nicht mehr fest auf Spectator gesperrt. Sie dürfen
ihren eigenen Gamemode selbst zwischen **Spectator** und **Survival** wechseln;
Creative/Adventure bleiben gesperrt.

**Umsetzung:** `RankAbilities.allowedGamemodes("supporter")` ist jetzt
`{SPECTATOR, SURVIVAL}`. Der frühere Spezialfall `lockedInSpectator(...)` wurde
entfernt und der harte Gamemode-Lock (`PlayerGameModeChangeEvent`) verallgemeinert:
ein gamemode-beschränkter Rang (Supporter, Moderator) darf – egal über welchen
Weg (Befehl, Plugin, API) – nur in seine erlaubten Modi; ein Moderator+ kann ihn
weiterhin per Befehl umstellen (Vorautorisierung für einen Tick). Zusätzlich wurde
geschlossen, dass ein Supporter den Gamemode *anderer* Spieler ändern konnte –
das bleibt Moderator+ vorbehalten. Build (`./gradlew build`) erfolgreich.

---

# RankManager – Bugfixes

Datum: 2026-06-03

Behebung von zwei gemeldeten Bugs. Build (`./gradlew compileJava`) erfolgreich.

---

## BUG 1 – Supporter konnte aus dem Spectator-Modus wechseln

**Problem:** Die Gamemode-Sperre griff nur beim getippten `/gamemode`-Befehl.
Änderungen auf anderem Weg (Plugins, API, usw.) wurden nicht abgefangen, und es
gab keinen Weg für Moderator+, einen Supporter umzustellen.

**Lösung:** Supporter sind jetzt im Spectator-Modus gesperrt – egal wodurch die
Änderung ausgelöst wird. Nur Moderator+ darf einen Supporter umstellen.

### Geänderte Dateien

**`RankAbilities.java`**
- Neue Methode `lockedInSpectator(String rankId)` → Rang ist auf Spectator
  gesperrt (Supporter).
- Neue Methode `canChangeOthersGamemode(String rankId)` → darf fremde Gamemodes
  ändern (Moderator und höher, via `isStaff`).

**`RankManager.java`**
- Import `java.util.HashSet` und `java.util.Set` ergänzt.
- Neues Feld `authorizedGamemodeChanges` (Set<UUID>).
- Neue Methode `authorizeGamemodeChange(UUID)` → vergibt einen einmaligen
  Bypass-Token; wird am Tick-Ende automatisch entfernt, falls ungenutzt.
- Neue Methode `consumeGamemodeChange(UUID)` → verbraucht den Token einmalig.

**`CommandListener.java`**
- Import `PlayerGameModeChangeEvent` ergänzt; Klassen-Doku erweitert.
- `onPlayerCommand`: Gamemode-Logik in neue Methode `handleGamemodeCommand`
  ausgelagert.
- Neue Methode `handleGamemodeCommand(...)`:
  - Executor darf nur Gamemodes seines eigenen Rangs setzen (z. B. Moderator
    weiterhin kein Creative) – für sich selbst und für andere.
  - Wird ein gesperrter Supporter umgestellt, muss der Executor Moderator+ sein;
    sonst Abbruch mit `gamemode-denied`. Bei erlaubtem Wechsel wird der
    Bypass-Token gesetzt.
- Neue Methode `gamemodeTargetName(...)` → liest das Ziel-Spieler-Argument.
- Neuer Event-Handler `onGameModeChange(PlayerGameModeChangeEvent)`:
  - Bricht jeden Wechsel eines gesperrten Rangs (Supporter) weg von Spectator
    ab – unabhängig von der Quelle – und sendet `gamemode-denied`.
  - Wechsel *zu* Spectator immer erlaubt.
  - Erlaubt genau einen Wechsel, wenn ein Moderator+-Token vorliegt.
- `onConsoleCommand`: Konsole (volle Rechte) autorisiert das Umstellen eines
  gesperrten Supporters vor.

---

## BUG 2 – Rechte nach Server-Neustart verloren

**Problem:** Nach einem Neustart war der Rang in der Tab-Liste sichtbar, aber die
Rechte schienen nicht angewendet. `reapply()` (beim Join) und `setRank` riefen
zwar `PermissionManager.apply` auf, doch die erteilten Vanilla-Befehlsrechte
(`minecraft.command.*`) wurden nie an den Client-Command-Tree weitergegeben.

**Lösung:**

**`PermissionManager.java`**
- In `apply(...)` nach `recalculatePermissions()` zusätzlich
  `player.updateCommands()` aufgerufen. Damit greift das vollständige
  Rechte-Set sofort beim Join, nach dem Neustart und nach jedem `/rank set`.

---

## Hinweis
- In `README.md` besteht ein vorbestehender, ungelöster Merge-Konflikt
  (nicht Teil dieser Änderungen) – unangetastet gelassen.
