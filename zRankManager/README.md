# RankManager

A Paper **1.21.11** plugin that shows each player's rank as a gradient prefix in
the tab list — `[Rank] Playername` — where the prefix **and** the player name
share one continuous gradient. It also enforces rank-based permissions entirely
internally (no LuckPerms or other permission plugin required).

## Features

- **Tab list:** `[Rank] Playername` rendered with MiniMessage. The whole string
  uses the rank's color/gradient as a single continuous gradient.
- **Sorted by priority** (lowest number = top) via scoreboard sort teams.
- **Configurable header/footer** in `config.yml` (`tablist.header` / `tablist.footer`).
- **Op = Owner:** any player with `/op` automatically gets the Owner rank
  (checked on join, on a periodic op poll, and immediately after `/op` / `/deop`).
  This is an override and is *not* persisted — losing op restores the stored rank.
- **UUID-based storage** in `players.yml`. New players get the default rank (`Spieler`).
- **Internal permission enforcement** through Bukkit permission attachments plus
  command interception for the fine-grained rules vanilla nodes can't express.

## Commands

All under permission `rankmanager.admin` (Owner/Admin/Developer have it; Moderators
may still run `set`/`get`/`list` for the ranks they're allowed to manage).

| Command | Description |
|---|---|
| `/rank set <player> <rank>` | Assign a rank (checks the executor may grant it) |
| `/rank get <player>` | Show a player's current rank |
| `/rank list` | List all ranks with their colors |
| `/rank reload` | Reload config and reapply every tab list entry |

## Ranks & abilities

Ranks (colors, priority, permission, default flag) live in `config.yml`. The
**abilities** below are keyed by the rank id (the config key) in
`RankAbilities.java`:

| Rank | Assign ranks | Gamemodes | /tp | kick/ban | /op |
|---|---|---|---|---|---|
| Owner | all | all | always | yes | yes |
| Admin | all except owner | all | always | yes | no |
| Developer | all except owner | all | always | yes | no |
| Moderator | supporter, builder, marketing, spieler | survival, adventure, spectator | always | yes | no |
| Supporter | – | survival, spectator | only while spectating | no | no |
| Builder | – | – | – | – | no |
| Marketing | – | – | – | – | no |
| Spieler | – | – | – | – | no |

Renaming a rank's config key keeps it as a cosmetic rank but removes its special
abilities (the id no longer matches the ability table).

## Building

Requires a JDK **21+** (JDK 25 is fine — the build targets `--release 21`).

```bash
./gradlew build
```

The plugin jar is produced at `build/libs/RankManager-1.0.0.jar`.

This project uses **only the Paper API** (no NMS), so:
- it does **not** use `reobfJar` / paperweight,
- the jar is plain, Mojang-mapped, and
- it is compatible with `-Dpaper.disablePluginRemapping=true`.

Drop the jar in your server's `plugins/` folder and start Paper 1.21.11.
