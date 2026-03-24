# 🌙 SleepSkip

**SleepSkip** - плагин для Minecraft, который позволяет пропускать ночь, когда достаточное количество игроков спит. Поддерживает серверы **Paper**, **Spigot** и **Folia**.

---

## ✨ Особенности

- 🌙 **Пропуск ночи** - когда нужное количество игроков спит, ночь пропускается
- ⏱️ **Плавный переход** - плавный рассвет вместо мгновенной смены времени
- 🌍 **Per-World режим** - настройка пропуска для каждого мира отдельно
- 📊 **Гибкие настройки** - фиксированное число или процент спящих игроков
- 🚶 **AFK обнаружение** - возможность игнорировать AFK игроков при подсчёте
- 🌩️ **Погода ближе к ваниле** - ночью дождь и гроза пропускаются вместе с ночью, днём спать можно только во время грозы
- 💬 **Красивые сообщения** - поддержка RGB-цветов и MiniMessage
- 🌐 **Локализация** - поддержка `ru`, `en`, `ua`
- 🔌 **Интеграции** - Essentials, CMI и PlaceholderAPI
- 📈 **bStats метрики** - статистика использования плагина

---

## 📥 Установка

1. Скачайте последнюю версию плагина из раздела [Releases](https://github.com/Erotoro/SleepSkip/releases)
2. Поместите файл `SleepSkip-1.6.jar` в папку `plugins` вашего сервера
3. Перезапустите сервер
4. Настройте плагин в файле `plugins/SleepSkip/config.yml`

---

## ⚙️ Конфигурация

### `config.yml`

```yaml
# Настройки плагина
settings:
  language: "ru" # ru, en, ua

  # Тип требуемого количества: "fixed" (фиксированное) или "percent" (процент)
  required-type: "percent"

  # Значение: количество игроков (для fixed) или процент (для percent)
  required-value: 50

  # Игнорировать AFK игроков при подсчёте
  ignore-afk: true

  # Таймаут AFK в секундах для встроенной проверки
  afk-timeout: 300

  # Длительность сообщений в ActionBar
  actionbar-duration: 5

  # Длительность плавного перехода в тиках
  transition-duration-ticks: 60

  # Убирать дождь и грозу после skip
  skip-rain: true

  # Режим weather sleep: "none" или "thunderstorm"
  # Днём спать можно только во время грозы
  weather-sleep-mode: "thunderstorm"

  # Считать спящих игроков для каждого мира отдельно
  per-world: false

  # Использовать ActionBar для сообщений
  use-actionbar: true

# Дополнительные overrides для обратной совместимости.
# Если ключ указан здесь, он будет иметь приоритет над lang/<language>.yml
messages:
  # nightSkipped: "<#F6AD72>Доброе утро!"
  # weatherSkipped: "<#F6AD72>Гроза закончилась!"
  # nightSkipping: "<#F6AD72>Ночь пропускается..."
  # weatherSkipping: "<#F6AD72>Игроки спят, чтобы переждать грозу..."
  # sleepingStatus: "<#EC9793>{sleeping}/{needed} игроков спит. Нужно <#EC9793>{needed} для пропуска ночи!"
  # weatherSleepingStatus: "<#EC9793>{sleeping}/{needed} игроков спит. Нужно <#EC9793>{needed} чтобы переждать грозу!"
  # not-enough: "<red>Недостаточно игроков спит!"
  # no-permission: "<red>У вас нет прав для использования этой команды!"
  # reload-success: "<green>Конфиг перезагружен!"
  # status-message: "<#F6AD72>Плагин SleepSkip работает корректно."
  # help-title: "<gold>=== SleepSkip Команды ==="
  # help-reload: "<yellow>/sleep reload - Перезагрузить конфиг"
  # help-status: "<yellow>/sleep status - Проверить статус плагина"
  # help-broadcaststatus: "<yellow>/sleep broadcaststatus - Показать статус всем игрокам"
```

### Локализация

Основные сообщения теперь находятся в:

- `plugins/SleepSkip/lang/ru.yml`
- `plugins/SleepSkip/lang/en.yml`
- `plugins/SleepSkip/lang/ua.yml`

Если вы обновляетесь со старой версии, можно по-прежнему переопределять `messages.*` через `config.yml`.

---

## 🎮 Команды

| Команда | Описание | Разрешение |
|---------|----------|------------|
| `/sleep reload` | Перезагрузить конфиг и локализацию | `sleepskip.admin` |
| `/sleep status` | Показать статус только отправителю | `sleepskip.admin` |
| `/sleep broadcaststatus` | Показать статус всем игрокам | `sleepskip.admin` |

---

## 🔧 Разрешения (Permissions)

| Разрешение | Описание | По умолчанию |
|------------|----------|--------------|
| `sleepskip.admin` | Доступ к админ-командам | `op` |
| `sleepskip.bypass` | Игрок не учитывается в sleep counters | `false` |

---

## 🔌 PlaceholderAPI

Если установлен **PlaceholderAPI**, становятся доступны placeholders:

- `%sleepskip_sleeping%`
- `%sleepskip_needed%`
- `%sleepskip_active_players%`
- `%sleepskip_world%`

---

## 🌩️ Правила сна и погоды

Текущее поведение сделано ближе к ваниле:

- **Ночь + ясная погода** - обычный пропуск ночи
- **Ночь + дождь** - обычный пропуск ночи
- **Ночь + гроза** - обычный пропуск ночи
- **День + гроза** - игроки могут спать, чтобы переждать грозу
- **День + обычный дождь** - спать нельзя

---

## 📊 bStats Метрики

Этот плагин использует **bStats** для сбора анонимной статистики использования:

- Тип сервера (Folia/Paper/Spigot)
- Количество пропущенных ночей
- Настройки конфигурации

Данные помогают улучшать плагин. Если вы хотите отключить метрики, установите `enabled: false` в `plugins/bStats/config.yml`.

---

## 🔄 История версий

### Версия 1.6 (Текущая)
- ✨ Полностью обновлена логика сна и погоды
- ✨ Реальная поддержка Folia-safe scheduling
- ✨ Поддержка daytime sleep во время грозы
- ✨ PlaceholderAPI placeholders
- ✨ Локализация `ru`, `en`, `ua`
- 🔧 Улучшены hooks для Essentials и CMI
- 🔧 Добавлены `sleepskip.bypass` и новые admin-команды
- 🔧 Улучшены ActionBar, статус-кэш и совместимость со старыми конфигами

### Версия 1.5
- ✨ Плавный переход от ночи к утру
- ✨ Новое сообщение `nightSkipping` при начале перехода
- 🔧 Поддержка современных версий сервера
- 🔧 Улучшена синхронизация и обработка событий

### Версия 1.4
- Добавлена поддержка MiniMessage для форматирования сообщений
- Улучшена совместимость с современными версиями Minecraft

### Версия 1.3
- Добавлена возможность игнорировать AFK игроков
- Добавлен режим per-world для независимого подсчёта в каждом мире

---

## 📄 Лицензия

Этот проект распространяется под лицензией **MIT**.

---

## 🤝 Поддержка

- 💬 **Discord**: [Наш Discord](https://discord.gg/FMhuu3meH2)
- 🐛 **Баги и предложения**: [GitHub Issues](https://github.com/Erotoro/SleepSkip/issues)

---

## 📈 Статистика

[![bStats](https://bstats.org/signatures/bukkit/SleepSkip.svg)](https://bstats.org/plugin/bukkit/SleepSkip/29936)

---

**Автор:** Erotoro  
**Версия:** 1.6  
**Версия Minecraft:** 1.21+  
**Зависимости:** None (опционально: Essentials, CMI, PlaceholderAPI)

---

Supported me - https://ko-fi.com/erotoro
Thx <3
