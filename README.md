![Platform](https://img.shields.io/badge/platform-Paper%20%7C%20Spigot%20%7C%20Folia-green.svg)
![Java](https://img.shields.io/badge/java-21%2B-orange.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B-red.svg)
![Tested](https://img.shields.io/badge/Tested-Paper%20%26%20Folia%2026.1.2-blue.svg)
[![Support me](https://img.shields.io/badge/Support%20me-Ko--fi-ff5f5f?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/erotoro)

# SleepSkipUltra

SleepSkipUltra is a Minecraft sleep plugin for Paper, Folia, and Spigot servers running 1.21+.
It replaces rough instant night skips with a cleaner sleep flow: players go to bed, the night accelerates, and the world transitions into morning with overlay feedback, sunrise titles, and day tracking.

If you want a Paper sleep plugin or Folia sleep plugin that feels polished instead of abrupt, this is the goal of the project.

> Plugin folder: `plugins/SleepSkip`

## Features

- Smooth sunrise transition instead of a hard instant time jump
- Unified sleep flow: `IDLE -> ACCELERATING -> FULL_SKIP`
- Configurable night acceleration
- Overlay and title progress UI
- Day counter with sunrise title and `%sleepskip_day_count%`
- AFK-aware sleep counting with Essentials and CMI hooks
- Thunderstorm daytime sleep support
- Per-world support
- PlaceholderAPI integration
- Locales: `en`, `ru`, `ua`
- Paper, Folia, and Spigot compatibility

## Installation

1. Download the latest release from [Releases](https://github.com/Erotoro/SleepSkip/releases)
2. Put `SleepSkip-1.8.0.jar` into your server `plugins` folder
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

Permission: `sleepskip.admin`

## Permissions

- `sleepskip.admin` - access to admin commands
- `sleepskip.bypass` - exclude a player from sleep counters

## PlaceholderAPI

| Placeholder | Description |
|---|---|
| `%sleepskip_sleeping%` | Current sleeping players |
| `%sleepskip_needed%` | Required players to skip |
| `%sleepskip_active_players%` | Counted active players |
| `%sleepskip_world%` | Current world name |
| `%sleepskip_day_count%` | Current world day count |

## Compatibility

| Platform | Support |
|---|---|
| Paper 1.21+ | Full, tested on 26.1.2 |
| Folia 1.21+ | Full, tested on 26.1.2 |
| Spigot 1.21+ | Full |

## Notes

- Java 21+
- Main tested targets: Paper 26.1.2 and Folia 26.1.2
- Plugin data folder: `plugins/SleepSkip`

## Support

- Discord: [SleepSkipUltra Discord](https://discord.gg/FMhuu3meH2)
- Issues: [GitHub Issues](https://github.com/Erotoro/SleepSkip/issues)

## bStats

[![bStats](https://bstats.org/signatures/bukkit/SleepSkip.svg)](https://bstats.org/plugin/bukkit/SleepSkip/29936)
