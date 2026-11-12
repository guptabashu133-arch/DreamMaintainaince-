# DreamMaintenance

A one-click maintenance plugin for **Velocity** (3.4.x and 4.x), styled for **TaraashMC**.

## Features
- **Clickable panel**: type `/maintenance` and click buttons in chat.
- **One-click presets** (Update / Bug Fix / Event / Restart / Emergency): one click turns maintenance **ON** and switches the MOTD to `★ TARAASHMC » MAINTENANCE` with that reason.
- **Server list**:
  - Maintenance MOTD
  - Red **"⚠ Maintenance"** in place of the player count
  - Hover lines with the reason and Discord link
  - Optional custom icon
- **Timers**:
  - `timer 5m`: turn ON in 5 minutes, with a countdown broadcast
  - `endtimer 1h`: turn ON now and OFF automatically after 1 hour. The MOTD shows the time remaining.
- **Whitelist**: these players can still join during maintenance.
- **Per-server maintenance**: e.g. only `survival` is under maintenance and the rest of the network stays open.
- State is saved in `data.json`, so it survives a proxy restart.

## Install
1. Put `DreamMaintenance-1.0.1.jar` in `velocity/plugins/`.
2. Restart Velocity.
3. Edit `plugins/dreammaintenance/config.yml` (MOTD, presets, messages).
4. Optional: put a 64x64 `maintenance-icon.png` in the same folder.

## Commands (`/maintenance`, `/mt`, `/maint`)
| Command | Purpose |
|---|---|
| `/maintenance` | Clickable control panel |
| `/maintenance on [preset or reason]` | Maintenance ON |
| `/maintenance off` / `toggle` | Maintenance OFF / toggle |
| `/maintenance preset <name>` / `reason <text>` | Change the MOTD reason |
| `/maintenance timer <time> [preset]` | Turn ON after a delay (`5m`, `1h30m`) |
| `/maintenance endtimer <time>` | Turn ON now, OFF after a delay |
| `/maintenance abort` | Cancel timers |
| `/maintenance add/remove <player>`, `list` | Maintenance whitelist |
| `/maintenance server <name> [on/off]`, `servers` | Per-server maintenance |
| `/maintenance motd` / `reload` | MOTD preview / reload config |

## Permissions
- `dreammaintenance.admin`: all commands. Admins can also join during maintenance.
- `dreammaintenance.bypass`: can join during maintenance.

## Build
```bash
./gradlew build
```
The jar is created at `build/libs/DreamMaintenance-1.0.1.jar`. GitHub Actions also builds it on every push.
