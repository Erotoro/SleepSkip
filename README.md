![Platform](https://img.shields.io/badge/platform-Paper%20%7C%20Folia-green.svg)
![Java](https://img.shields.io/badge/java-21%2B-orange.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%2B-red.svg)
![Tested](https://img.shields.io/badge/Tested-Paper%20%26%20Folia%2026.1.2-blue.svg)
[![Support me](https://img.shields.io/badge/Support%20me-Ko--fi-ff5f5f?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/erotoro)

# SleepSkipUltra

SleepSkipUltra is a Minecraft sleep plugin for Paper and Folia servers running 1.20.1+ (Java 21 runtime).
It replaces rough instant night skips with a cleaner sleep flow: players go to bed, the night accelerates, and the world transitions into morning with overlay/bossbar feedback, sunrise titles, and day tracking.

If you want a Paper sleep plugin or Folia sleep plugin that feels polished instead of abrupt, this is the goal of the project.

> Supported versions: **Paper/Folia 1.20.1 – 26.1.2** (every release in this range, Java 21 runtime).

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
- Locales: `en`, `ru`, `ua`, `de`, `es`, `fr`, `pl`, `pt`, `zh`
- Paper and Folia compatibility (1.20.1+)

## Installation

1. Download the latest release from [Releases](https://github.com/Erotoro/SleepSkip/releases)
2. Put `SleepSkip-1.9.0.jar` into your server `plugins` folder
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

## Compatibility

Supported version range: **1.20.1 – 26.1.2** (continuous — every release in between works, not a fixed list).

| Platform | Support |
|---|---|
| Paper 1.20.1 – 26.1.2 | Full, tested on 26.1.2 |
| Folia 1.20.1 – 26.1.2 | Full, tested on 26.1.2 |
| Spigot | Not supported (requires Adventure, use Paper) |

## Notes

- Requires a Java 21 runtime (Paper 1.20.1 servers must run on Java 21)
- Main tested targets: Paper 26.1.2 and Folia 26.1.2
- Plugin data folder: `plugins/SleepSkip`
- Bossbar mode is set via `overlay.mode` (`title`, `bossbar`, `both`) and styled under `overlay.bossbar`

## Support

- Discord: [SleepSkipUltra Discord](https://discord.gg/FMhuu3meH2)
- Issues: [GitHub Issues](https://github.com/Erotoro/SleepSkip/issues)

## bStats

[![bStats](https://bstats.org/signatures/bukkit/SleepSkip.svg)](https://bstats.org/plugin/bukkit/SleepSkip/29936)
