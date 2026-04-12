![Platform](https://img.shields.io/badge/platform-Paper%20%7C%20Spigot%20%7C%20Folia-green.svg)
![Java](https://img.shields.io/badge/java-21%2B-orange.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B-red.svg)
[![Support me](https://img.shields.io/badge/Support%20me-Ko--fi-ff5f5f?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/erotoro)

# SleepSkip

SleepSkip is a Minecraft plugin for Paper/Spigot/Folia that skips night when enough players are sleeping, supports thunderstorm daytime sleep, and provides a clean overlay/action bar UX.

## Features

- Night skip with `fixed` or `percent` requirement model
- Unified sleep flow: `IDLE -> ACCELERATING -> FULL_SKIP`
- Smooth transition to morning
- Per-world counters
- AFK-aware counting with Essentials/CMI hooks
- Thunderstorm-only daytime sleep (`weather-sleep-mode: thunderstorm`)
- Overlay + ActionBar status/progress UI
- PlaceholderAPI integration
- Locales: `en`, `ru`, `ua`

## Installation

1. Download the latest release from [Releases](https://github.com/Erotoro/SleepSkip/releases)
2. Put `SleepSkip-1.7.jar` into your server `plugins` folder
3. Restart the server
4. Configure `plugins/SleepSkip/config.yml`

## Default config (`config.yml`)

```yaml
settings:
  language: "en" # en, ru, ua
  required-type: "percent" # fixed or percent
  required-value: 60

  ignore-afk: true
  afk-timeout: 300
  count-afk-sleepers: true
  overlay-enabled: true

  actionbar-duration: 5
  transition-duration-ticks: 60 # 20 ticks = 1 second
  skip-rain: true
  weather-sleep-mode: "thunderstorm" # none, thunderstorm
  per-world: false
  use-actionbar: true

sleep:
  start-threshold-percent: 5
  max-speed-multiplier: 12.0
  update-interval-ticks: 5

overlay:
  enabled: true
  mode: "title"
  update-interval-ticks: 10
  fade-in-ticks: 1
  stay-ticks: 24
  fade-out-ticks: 1
  show-status-before-skip: true
  show-progress-during-transition: true

placeholders:
  offline-mode: "none" # none, global, fallback-world
  fallback-world: ""
```

## Commands

- `/sleep reload`
- `/sleep status`
- `/sleep broadcaststatus`

Permission: `sleepskip.admin`

## Extra permissions

- `sleepskip.bypass` - player is excluded from sleep counters

## PlaceholderAPI

- `%sleepskip_sleeping%`
- `%sleepskip_needed%`
- `%sleepskip_active_players%`
- `%sleepskip_world%`

## Version 1.7 (current)

- Unified night model (`IDLE -> ACCELERATING -> FULL_SKIP`)
- New public config block `sleep.*`
- Legacy migration from `settings.night-behavior` and `gradual-acceleration.*`
- Improved locale fallback handling for corrupted locale files

## Russian (RU)

SleepSkip — плагин для Minecraft (Paper/Spigot/Folia), который пропускает ночь при достаточном количестве спящих игроков, поддерживает сон днём во время грозы и показывает статус через Overlay/ActionBar.

### Основные возможности

- Пропуск ночи по модели `fixed` или `percent`
- Единая логика сна: `IDLE -> ACCELERATING -> FULL_SKIP`
- Плавный переход к утру
- Поддержка per-world подсчёта
- Учёт AFK и интеграции Essentials/CMI
- Сон днём только во время грозы
- PlaceholderAPI
- Локали: `en`, `ru`, `ua`

### Установка

1. Скачайте последнюю версию из [Releases](https://github.com/Erotoro/SleepSkip/releases)
2. Поместите `SleepSkip-1.7.jar` в папку `plugins`
3. Перезапустите сервер
4. Настройте `plugins/SleepSkip/config.yml`

### Команды

- `/sleep reload`
- `/sleep status`
- `/sleep broadcaststatus`

Права: `sleepskip.admin`, `sleepskip.bypass`

## Support

- Discord: [Наш Discord](https://discord.gg/FMhuu3meH2)
- Issues: [GitHub Issues](https://github.com/Erotoro/SleepSkip/issues)

## bStats

[![bStats](https://bstats.org/signatures/bukkit/SleepSkip.svg)](https://bstats.org/plugin/bukkit/SleepSkip/29936)

Author: Erotoro  
Version: 1.7  
Minecraft: 1.21+  
Dependencies: none (optional: Essentials, CMI, PlaceholderAPI)