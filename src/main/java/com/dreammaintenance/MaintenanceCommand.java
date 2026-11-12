package com.dreammaintenance;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * /maintenance                 -> clickable panel (one click = done)
 * /maintenance on [preset|reason...] | off | toggle
 * /maintenance preset <key> | reason <text...>
 * /maintenance timer <time> [preset] | endtimer <time> | abort
 * /maintenance add|remove <player> | list
 * /maintenance server <name> [on|off] | servers
 * /maintenance motd | reload
 */
final class MaintenanceCommand implements SimpleCommand {

    private static final String PANEL_FLAG = "--panel";
    private static final List<String> SUBS = List.of("on", "off", "toggle", "preset", "reason", "timer", "endtimer",
            "abort", "add", "remove", "list", "server", "servers", "motd", "reload", "help");

    private final DreamMaintenance plugin;

    MaintenanceCommand(DreamMaintenance plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(DreamMaintenance.PERM_ADMIN);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        List<String> list = new ArrayList<>(Arrays.asList(invocation.arguments()));
        boolean reopenPanel = list.remove(PANEL_FLAG) && src instanceof Player;
        String[] a = list.toArray(String[]::new);

        if (a.length == 0 || a[0].equalsIgnoreCase("panel")) {
            panel(src);
            return;
        }
        MaintenanceData data = plugin.data();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                if (data.enabled && a.length == 1) {
                    send(src, plugin.msg("already-enabled"));
                } else {
                    boolean wasOn = data.enabled;
                    plugin.enable(a.length > 1 ? join(a, 1) : null);
                    if (wasOn) {
                        send(src, plugin.msg("reason-set"));
                    }
                }
            }
            case "off" -> {
                if (!data.enabled && data.startAt == 0) {
                    send(src, plugin.msg("already-disabled"));
                } else {
                    plugin.disable();
                }
            }
            case "toggle" -> {
                if (data.enabled) {
                    plugin.disable();
                } else {
                    plugin.enable(null);
                }
            }
            case "preset", "reason" -> {
                if (a.length < 2) {
                    send(src, plugin.msg("reason-set"));
                    break;
                }
                boolean preset = plugin.setReason(join(a, 1));
                if (a[0].equalsIgnoreCase("preset") && !preset) {
                    send(src, plugin.msg("unknown-preset", "preset", a[1]));
                    break;
                }
                send(src, plugin.msg("reason-set"));
            }
            case "timer" -> {
                long secs = a.length < 2 ? -1 : TimeUtil.parseSeconds(a[1]);
                if (secs <= 0) {
                    send(src, plugin.msg("invalid-time"));
                    break;
                }
                if (data.enabled) {
                    send(src, plugin.msg("already-enabled"));
                    break;
                }
                if (a.length > 2) {
                    plugin.setReason(join(a, 2));
                }
                data.startAt = System.currentTimeMillis() + secs * 1000;
                plugin.saveAsync();
                send(src, plugin.msg("start-timer-set", "time", TimeUtil.format(secs)));
            }
            case "endtimer" -> {
                long secs = a.length < 2 ? -1 : TimeUtil.parseSeconds(a[1]);
                if (secs <= 0) {
                    send(src, plugin.msg("invalid-time"));
                    break;
                }
                if (!data.enabled) {
                    plugin.enable(a.length > 2 ? join(a, 2) : null);
                }
                data.endAt = System.currentTimeMillis() + secs * 1000;
                plugin.saveAsync();
                send(src, plugin.msg("end-timer-set", "time", TimeUtil.format(secs)));
            }
            case "abort" -> {
                data.startAt = 0;
                data.endAt = 0;
                plugin.saveAsync();
                send(src, plugin.msg("timers-aborted"));
            }
            case "add" -> {
                if (a.length < 2) {
                    send(src, Component.text("/maintenance add <player>", NamedTextColor.RED));
                    break;
                }
                data.whitelist.put(a[1].toLowerCase(Locale.ROOT), a[1]);
                plugin.saveAsync();
                send(src, plugin.msg("whitelist-added", "player", a[1]));
            }
            case "remove" -> {
                if (a.length < 2) {
                    send(src, Component.text("/maintenance remove <player>", NamedTextColor.RED));
                    break;
                }
                if (data.whitelist.remove(a[1].toLowerCase(Locale.ROOT)) == null) {
                    send(src, plugin.msg("whitelist-not-found", "player", a[1]));
                    break;
                }
                plugin.saveAsync();
                send(src, plugin.msg("whitelist-removed", "player", a[1]));
            }
            case "list" -> whitelist(src);
            case "server" -> {
                if (a.length < 2) {
                    servers(src);
                    break;
                }
                Optional<RegisteredServer> server = plugin.proxy().getServer(a[1]);
                if (server.isEmpty()) {
                    send(src, plugin.msg("unknown-server", "server", a[1]));
                    break;
                }
                String name = server.get().getServerInfo().getName();
                boolean on = a.length > 2 ? a[2].equalsIgnoreCase("on") : !plugin.isServerInMaintenance(name);
                plugin.setServerMaintenance(server.get(), on);
                send(src, plugin.msg(on ? "server-enabled" : "server-disabled", "server", name));
            }
            case "servers" -> servers(src);
            case "motd" -> {
                YamlConfig cfg = plugin.config();
                send(src, plugin.prefix().append(Component.text("MOTD preview:", NamedTextColor.GRAY)));
                send(src, plugin.mini(cfg.getString("motd.line1", "")));
                send(src, plugin.mini(cfg.getString("motd.line2", "")));
            }
            case "reload" -> {
                try {
                    plugin.reload();
                    send(src, plugin.msg("reloaded"));
                } catch (IOException e) {
                    send(src, Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            default -> help(src);
        }
        if (reopenPanel) {
            panel(src);
        }
    }

    private void send(CommandSource src, Component message) {
        src.sendMessage(plugin.shadow(message));
    }

    private static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    // ------------------------------------------------------------------ panel

    private static Component button(Component label, NamedTextColor bracket, String command, Component hover) {
        return Component.text("[", bracket)
                .append(label)
                .append(Component.text("]", bracket))
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(hover));
    }

    private static Component button(String label, NamedTextColor color, String command, String hover) {
        return button(Component.text(label, color, TextDecoration.BOLD), NamedTextColor.DARK_GRAY, command,
                Component.text(hover, NamedTextColor.GRAY));
    }

    private static Component line() {
        return Component.text("                                                  ", NamedTextColor.DARK_GRAY,
                TextDecoration.STRIKETHROUGH);
    }

    private void panel(CommandSource src) {
        MaintenanceData data = plugin.data();
        YamlConfig cfg = plugin.config();
        MiniMessage mm = MiniMessage.miniMessage();
        String p = " " + PANEL_FLAG;
        Component space = Component.text(" ");

        send(src, line());
        send(src, Component.text(" ").append(plugin.prefix())
                .append(Component.text("Control Panel", NamedTextColor.WHITE, TextDecoration.BOLD)));
        send(src, Component.text(" Status: ", NamedTextColor.GRAY)
                .append(data.enabled
                        ? Component.text("● ON", NamedTextColor.RED, TextDecoration.BOLD)
                        : Component.text("● OFF", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("   Reason: ", NamedTextColor.GRAY))
                .append(Component.text(plugin.reason(), NamedTextColor.WHITE)));
        send(src, Component.text(" Timer: ", NamedTextColor.GRAY)
                .append(Component.text(data.startAt > 0 || data.endAt > 0 ? plugin.remainingText() : "none",
                        NamedTextColor.YELLOW)));
        send(src, Component.empty());

        send(src, Component.text(" ")
                .append(button("✔ ENABLE", NamedTextColor.RED, "/maintenance on" + p, "Turn maintenance ON (kicks players who aren't whitelisted)"))
                .append(space)
                .append(button("✘ DISABLE", NamedTextColor.GREEN, "/maintenance off" + p, "Turn maintenance OFF (opens the server for everyone)"))
                .append(space)
                .append(button("MOTD", NamedTextColor.AQUA, "/maintenance motd", "Preview the maintenance MOTD")));

        // One click presets: maintenance ON + MOTD reason
        Component presets = Component.empty().append(Component.text(" One-click: ", NamedTextColor.GRAY));
        String current = data.customReason == null ? data.preset : null;
        for (String key : cfg.getKeys("presets")) {
            Component label = mm.deserialize(cfg.getString("presets." + key + ".display", key));
            boolean selected = key.equalsIgnoreCase(current) && data.enabled;
            Component hover = Component.text("Maintenance ON with MOTD: ", NamedTextColor.GRAY)
                    .append(Component.text(cfg.getString("presets." + key + ".reason", ""), NamedTextColor.WHITE));
            presets = presets.append(button(selected ? Component.text("▶ ").append(label) : label,
                    selected ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY,
                    "/maintenance on " + key + p, hover)).append(space);
        }
        send(src, presets);

        Component timers = Component.empty().append(Component.text(" Start in: ", NamedTextColor.GRAY));
        for (String t : cfg.getStringList("panel-start-timers")) {
            timers = timers.append(button(t, NamedTextColor.YELLOW, "/maintenance timer " + t + p, "Start maintenance in " + t)).append(space);
        }
        timers = timers.append(Component.text(" Auto-OFF: ", NamedTextColor.GRAY));
        for (String t : cfg.getStringList("panel-end-timers")) {
            timers = timers.append(button(t, NamedTextColor.GOLD, "/maintenance endtimer " + t + p, "Turn ON now, OFF automatically after " + t)).append(space);
        }
        timers = timers.append(button("✖", NamedTextColor.RED, "/maintenance abort" + p, "Cancel all timers"));
        send(src, timers);

        Component servers = Component.empty().append(Component.text(" Servers: ", NamedTextColor.GRAY));
        for (RegisteredServer server : plugin.proxy().getAllServers()) {
            String name = server.getServerInfo().getName();
            boolean on = plugin.isServerInMaintenance(name);
            servers = servers.append(button(Component.text(name + (on ? " ●" : " ○"), on ? NamedTextColor.RED : NamedTextColor.GREEN),
                    NamedTextColor.DARK_GRAY, "/maintenance server " + name + p,
                    Component.text(on ? "Click: take " + name + " out of maintenance" : "Click: put only " + name + " under maintenance",
                            NamedTextColor.GRAY))).append(space);
        }
        send(src, servers);

        send(src, Component.text(" Whitelist (" + data.whitelist.size() + "): ", NamedTextColor.GRAY)
                .append(button("List", NamedTextColor.WHITE, "/maintenance list", "Show the maintenance whitelist"))
                .append(space)
                .append(Component.text("[+ Add]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.suggestCommand("/maintenance add "))
                        .hoverEvent(HoverEvent.showText(Component.text("Type a player name", NamedTextColor.GRAY))))
                .append(space)
                .append(button("Reload", NamedTextColor.AQUA, "/maintenance reload" + p, "Reload the config")));
        send(src, line());
    }

    private void whitelist(CommandSource src) {
        MaintenanceData data = plugin.data();
        if (data.whitelist.isEmpty()) {
            send(src, plugin.msg("whitelist-empty"));
            return;
        }
        Component b = Component.empty().append(plugin.prefix())
                .append(Component.text("Whitelist: ", NamedTextColor.GRAY));
        for (String name : data.whitelist.values()) {
            b = b.append(Component.text(name + " ", NamedTextColor.WHITE)
                    .append(Component.text("✖", NamedTextColor.RED))
                    .clickEvent(ClickEvent.runCommand("/maintenance remove " + name))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to remove " + name, NamedTextColor.RED))))
                    .append(Component.text("  "));
        }
        send(src, b);
    }

    private void servers(CommandSource src) {
        Component b = Component.empty().append(plugin.prefix());
        for (RegisteredServer server : plugin.proxy().getAllServers()) {
            String name = server.getServerInfo().getName();
            boolean on = plugin.isServerInMaintenance(name);
            b = b.append(Component.text(name + (on ? " ● " : " ○ "), on ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/maintenance server " + name))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to toggle", NamedTextColor.GRAY))));
        }
        send(src, b);
    }

    private void help(CommandSource src) {
        String[] lines = {
            "/maintenance - clickable panel",
            "/maintenance on [preset|reason] | off | toggle",
            "/maintenance preset <name> | reason <text>",
            "/maintenance timer <time> [preset] - start after a delay",
            "/maintenance endtimer <time> - ON now, OFF after a delay",
            "/maintenance abort - cancel timers",
            "/maintenance add|remove <player> | list",
            "/maintenance server <name> [on|off] | servers",
            "/maintenance motd | reload"
        };
        send(src, plugin.prefix().append(Component.text("Commands:", NamedTextColor.WHITE)));
        for (String l : lines) {
            send(src, Component.text(" " + l, NamedTextColor.GRAY));
        }
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        List<String> out;
        if (a.length <= 1) {
            out = SUBS;
        } else if (a.length == 2) {
            out = switch (a[0].toLowerCase(Locale.ROOT)) {
                case "on", "preset" -> plugin.config().getKeys("presets");
                case "timer", "endtimer" -> List.of("30s", "1m", "5m", "10m", "30m", "1h", "2h");
                case "add" -> plugin.proxy().getAllPlayers().stream().map(Player::getUsername).toList();
                case "remove" -> new ArrayList<>(plugin.data().whitelist.values());
                case "server" -> plugin.proxy().getAllServers().stream().map(s -> s.getServerInfo().getName()).toList();
                default -> List.of();
            };
        } else if (a.length == 3) {
            out = switch (a[0].toLowerCase(Locale.ROOT)) {
                case "server" -> List.of("on", "off");
                case "timer" -> plugin.config().getKeys("presets");
                default -> List.of();
            };
        } else {
            out = List.of();
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return CompletableFuture.completedFuture(
                out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).toList());
    }
}
