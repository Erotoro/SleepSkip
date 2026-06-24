![Platform](https://img.shields.io/badge/platform-Paper%20%7C%20Folia-green.svg)
![Java](https://img.shields.io/badge/java-bytecode%2017-orange.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%2B-red.svg)
![Compatible](https://img.shields.io/badge/Compatible-1.20.1%20--%2026.1.x-green.svg)
![Tested](https://img.shields.io/badge/Tested-Paper%20%7C%20Folia-blue.svg)
[![Support me](https://img.shields.io/badge/Support%20me-Ko--fi-ff5f5f?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/erotoro)

# SleepSkipUltra

SleepSkipUltra is a Minecraft sleep plugin for Paper and Folia-compatible servers running 1.20.1+.
The plugin jar is built as Java 17 bytecode; the Java runtime requirement follows your server version
(for example, Paper/Folia 1.21.x requires Java 21, and 26.1.x requires Java 25+).
It replaces rough instant night skips with a cleaner sleep flow: players go to bed, the night accelerates, and the world transitions into morning with overlay/bossbar feedback, sunrise titles, and day tracking.

If you want a Paper sleep plugin or Folia sleep plugin that feels polished instead of abrupt, this is the goal of the project.

> Verified in this repository: **Paper 1.21.1 / 1.21.11** and **Folia 1.20.1 / 1.20.6 / 1.21.11 / 26.1.2**.

> Declared compatibility target: **Paper/Folia 1.20.1 - 26.1.x**.

> The plugin keeps `api-version: '1.20'` and Folia-safe scheduler paths for Paper/Folia-compatible 1.20.1+ servers. Validate your exact server build before production rollout.

> Built on Adventure APIs, so a Paper-based server (Paper, Folia, Purpur, etc.) is required. Pure Spigot is not supported.

> Plugin folder: `plugins/SleepSkip`

## Features

- Smooth sunrise transition instead of a hard instant time jump
- Unified sleep flow: `IDLE -> ACCELERATING -> FULL_SKIP`
- Configurable night acceleration
- Overlay (title) progress UI
- Bossbar UI with configurable color/style, server toggle, and per-player toggle (`/sleep bossbar`)
- Day counter with sunrise title and `%sleepskip_day_count%`
- AFK-aware sleep counting with Essentials and CMI hooks
- Thunderstorm daytime sleep support
- Per-world support
- PlaceholderAPI integration (status, progress, state, speed, day count)
- Anti-troll limits: minimum players online, per-world skip cooldown, world whitelist/blacklist
- Weighted sleep via `sleepskip.weight.<n>` (a player can count as several sleepers)
- Optional sleep rewards (potions, heal, feed, XP, commands, message) for players who slept
- Developer API: `SleepSkipStartEvent` (cancellable), `SleepSkipCompleteEvent`, `SleepSkipCancelEvent`
- Built-in update checker (GitHub Releases) with admin join notifications
- Locales: `en`, `ru`, `ua`, `de`, `es`, `fr`, `pl`, `pt`, `zh`, `it`, `cs`, `hi`, `tr`, `id`, `fi`
- Paper/Folia verified runtime with Folia-safe scheduling paths (1.20.1+)

## Installation

1. Download the latest release from [Releases](https://github.com/Erotoro/SleepSkip/releases)
2. Put `SleepSkip-1.10.0.jar` into your server `plugins` folder
3. Restart the server
4. Configure `plugins/SleepSkip/config.yml`

## Why SleepSkipUltra

- Better sleep UX for SMP servers
- Smooth night skipping with visible progress
- Folia-safe support for modern server setups
- Flexible config, placeholders, and per-world behavior

## Commands

| Command | Description |
|---|---|
| `/sleep reload` | Reload config and locale files |
| `/sleep status` | Show current sleep status |
| `/sleep broadcaststatus` | Broadcast current sleep progress |
| `/sleep forceskip [world] [--instant]` | Force a smooth or instant skip |
| `/sleep bossbar <on\|off\|toggle\|status>` | Toggle your own sleep bossbar |

Admin commands require `sleepskip.admin`. `/sleep bossbar` only needs `sleepskip.bossbar.toggle`.

## Permissions

- `sleepskip.admin` - access to admin commands
- `sleepskip.bypass` - exclude a player from sleep counters
- `sleepskip.bossbar.toggle` - allows a player to toggle their own sleep bossbar (default: false)
- `sleepskip.weight.<n>` - counts the player as `n` sleepers when `limits.weighted-sleep` is enabled

## PlaceholderAPI

| Placeholder | Description |
|---|---|
| `%sleepskip_sleeping%` | Current sleeping players |
| `%sleepskip_needed%` | Required players to skip |
| `%sleepskip_active_players%` | Counted active players |
| `%sleepskip_remaining%` | Players still needed to skip |
| `%sleepskip_percent%` | Progress toward the requirement (0-100) |
| `%sleepskip_state%` | Night state: `IDLE`, `ACCELERATING`, `FULL_SKIP` |
| `%sleepskip_speed%` | Current night acceleration multiplier |
| `%sleepskip_is_night%` | `true` if it is currently night |
| `%sleepskip_world%` | Current world name |
| `%sleepskip_day_count%` | Current world day count |

## Anti-troll limits (optional)

Guards against a single player skipping nights solo, and per-world world filtering. All default to
off, so behaviour is unchanged unless you opt in. Read live, so `/sleep reload` applies them.

```yaml
limits:
  min-players-online: 0     # require at least N eligible players online before a natural skip (0 = off)
  skip-cooldown-seconds: 0  # minimum seconds between skips per world (0 = off); /sleep forceskip ignores it
  weighted-sleep: false     # honor sleepskip.weight.<n> (a player counts as N sleepers)
  world-whitelist: []       # if non-empty, ONLY these worlds are managed (by name)
  world-blacklist: []       # these worlds are never managed (wins over the whitelist)
```

With `weighted-sleep: true`, grant e.g. `sleepskip.weight.3` to make a VIP count as three sleepers
(the highest numeric node a player holds wins). Admin `/sleep forceskip` bypasses the limits.

## Sleep rewards (optional)

An optional reward layer for the players who actually slept when a skip completes. **Disabled by
default** — it does nothing until you opt in. Rewards can include potion effects, full heal, full
feed, experience, a personal message, and console commands (with `{player}`). Everything is read
once and re-read on `/sleep reload`, and is applied Folia-safely on each player's region thread.

```yaml
rewards:
  enabled: false                # master switch (OFF by default)
  only-natural-skips: false     # true = no rewards when an admin used /sleep forceskip
  heal: false                   # restore each sleeper to full health
  feed: false                   # restore each sleeper's hunger and saturation
  experience: 0                 # experience points granted to each sleeper (0 = none)
  message: ""                   # optional MiniMessage sent to each rewarded sleeper
  potion-effects: []            # e.g. - { type: REGENERATION, duration-seconds: 10, amplifier: 0 }
  commands: []                  # e.g. - "give {player} minecraft:bread 4"
```

## Update checker

On startup (and every `interval-hours` afterwards) the plugin asks the GitHub Releases API whether a
newer version exists, fully off the main thread. When an update is found it logs a notice and, on
join, messages players with `sleepskip.admin`. Everything is configurable under `update-checker`:

```yaml
update-checker:
  enabled: true                 # check GitHub Releases for a newer version on startup
  notify-admins-on-join: true   # message players with sleepskip.admin when an update is available
  interval-hours: 6             # how often to re-check while running (0 disables periodic re-checks)
```

## Developer API

SleepSkipUltra fires Bukkit events around every skip so other plugins can react (rewards, logging,
Discord relays, etc.). Events are fired on the thread that owns the affected world (main thread on
Paper, the owning region/global thread on Folia), so handlers may touch that world's state directly.

| Event | When | Notes |
|---|---|---|
| `SleepSkipStartEvent` | A skip is about to begin | `Cancellable`; exposes sleeping/required counts, recipients, and `isForced()` |
| `SleepSkipCompleteEvent` | A skip finished (morning reached) | Exposes `getSleepers()` (reward targets) and `isInstant()` |
| `SleepSkipCancelEvent` | An in-progress skip was aborted | Exposes a `Reason` |

```java
@EventHandler
public void onSleepComplete(SleepSkipCompleteEvent event) {
    for (UUID id : event.getSleepers()) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0));
        }
    }
}
```

Events live in `me.Erotoro.sleepskip.api.event`; the public `SleepSkipType` enum is in
`me.Erotoro.sleepskip.api`. Add SleepSkipUltra as a `softdepend`/`depend` in your `plugin.yml` and
compile against the plugin jar.

## Compatibility

SleepSkipUltra targets Paper-compatible server APIs and requires a modern Java runtime. Declared
compatibility is **Paper/Folia 1.20.1 - 26.1.x**. The current codebase is compiled against the
minimum Folia API 1.20.1 with Java 17 bytecode and keeps `api-version: '1.20'` for broad 1.20.1+
compatibility.

Do not treat compatibility as a promise that every fork/build combination is identical. Test the
exact server build you deploy, especially on Folia or forks with custom sleep behavior.

| Platform | Support |
|---|---|
| Paper/Folia 1.20.1 - 26.1.x | Declared compatibility target |
| Paper 1.21.1 | Verified with automated tests and local server smoke testing |
| Paper 1.21.11 | Verified with local server startup, plugin load, reload, OP/RCON, and bot sleep smoke testing |
| Folia 1.20.1 | Verified startup, plugin load, `/plugins`, `/sleep reload`, `/sleep status`, and shutdown through console stdin |
| Folia 1.20.6 | Verified startup, plugin load, RCON `/plugins`, `/sleep reload`, `/sleep status`, and shutdown |
| Folia 1.21.11 | Verified startup, plugin load, RCON `/plugins`, `/sleep reload`, `/sleep status`, and shutdown |
| Folia 26.1.2 | Verified startup on JDK 25, plugin load, RCON `/plugins`, `/sleep reload`, `/sleep status`, and shutdown |
| Paper-compatible 1.20.1+ | Designed to run via `api-version: '1.20'`; validate your exact build |
| Folia-compatible 1.20.1+ | `folia-supported: true` with Folia-safe scheduling paths; validate your exact build |
| Spigot | Not supported; use Paper or a Paper-compatible fork |

## Verification

Latest local verification pass: **2026-06-24**.

- `./gradlew clean test shadowJar`
- Paper 1.21.11 build 69 startup with the built plugin jar
- Folia 1.20.1 build 17, Folia 1.20.6 build 6, Folia 1.21.11 build 6, and Folia 26.1.2 build 8 startup/load smoke tests
- Folia 26.1.2 build 8 was tested on JDK 25; Java 21 is rejected by that server line before plugin loading
- `/sleep reload` smoke test without bundled-locale save warnings
- Natural sleep skip smoke test with bots, including sleeper reward targeting
- RCON administration smoke test (`op` command) against the 1.21.11 test server

## Notes

- Paper 1.21.x requires Java 21 at runtime; older 1.20.1-compatible servers may run on their own supported Java baseline.
- Folia/Paper 26.1.x requires Java 25+ at runtime.
- The plugin jar is built as Java 17 bytecode so old Folia 1.20.1 remapping can read it.
- Main verified targets in this pass: Paper 1.21.1/1.21.11 and Folia 1.20.1/1.20.6/1.21.11/26.1.2
- Plugin data folder: `plugins/SleepSkip`
- Bossbar mode is set via `overlay.mode` (`title`, `bossbar`, `both`) and styled under `overlay.bossbar`

## Support

- Discord: [SleepSkipUltra Discord](https://discord.gg/FMhuu3meH2)
- Issues: [GitHub Issues](https://github.com/Erotoro/SleepSkip/issues)

## bStats

[![bStats](https://bstats.org/signatures/bukkit/SleepSkip.svg)](https://bstats.org/plugin/bukkit/SleepSkip/29936)
