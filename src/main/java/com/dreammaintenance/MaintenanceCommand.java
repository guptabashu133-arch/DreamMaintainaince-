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
import net.kyori.adventure.text.TextComponent;
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
                    src.sendMessage(plugin.msg("already-enabled"));
                } else {
                    boolean wasOn = data.enabled;
                    plugin.enable(a.length > 1 ? join(a, 1) : null);
                    if (wasOn) {
                        src.sendMessage(plugin.msg("reason-set"));
                    }
                }
            }
            case "off" -> {
                if (!data.enabled && data.startAt == 0) {
                    src.sendMessage(plugin.msg("already-disabled"));
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
                    src.sendMessage(plugin.msg("reason-set"));
                    break;
                }
                boolean preset = plugin.setReason(join(a, 1));
                if (a[0].equalsIgnoreCase("preset") && !preset) {
                    src.sendMessage(plugin.msg("unknown-preset", "preset", a[1]));
                    break;
                }
                src.sendMessage(plugin.msg("reason-set"));
            }
            case "timer" -> {
                long secs = a.length < 2 ? -1 : TimeUtil.parseSeconds(a[1]);
                if (secs <= 0) {
                    src.sendMessage(plugin.msg("invalid-time"));
                    break;
                }
                if (data.enabled) {
                    src.sendMessage(plugin.msg("already-enabled"));
                    break;
                }
                if (a.length > 2) {
                    plugin.setReason(join(a, 2));
                }
                data.startAt = System.currentTimeMillis() + secs * 1000;
                plugin.saveAsync();
                src.sendMessage(plugin.msg("start-timer-set", "time", TimeUtil.format(secs)));
            }
            case "endtimer" -> {
                long secs = a.length < 2 ? -1 : TimeUtil.parseSeconds(a[1]);
                if (secs <= 0) {
                    src.sendMessage(plugin.msg("invalid-time"));
                    break;
                }
                if (!data.enabled) {
                    plugin.enable(a.length > 2 ? join(a, 2) : null);
                }
                data.endAt = System.currentTimeMillis() + secs * 1000;
                plugin.saveAsync();
                src.sendMessage(plugin.msg("end-timer-set", "time", TimeUtil.format(secs)));
            }
            case "abort" -> {
                data.startAt = 0;
                data.endAt = 0;
                plugin.saveAsync();
                src.sendMessage(plugin.msg("timers-aborted"));
            }
            case "add" -> {
                if (a.length < 2) {
                    src.sendMessage(Component.text("/maintenance add <player>", NamedTextColor.RED));
                    break;
                }
                data.whitelist.put(a[1].toLowerCase(Locale.ROOT), a[1]);
                plugin.saveAsync();
                src.sendMessage(plugin.msg("whitelist-added", "player", a[1]));
            }
            case "remove" -> {
                if (a.length < 2) {
                    src.sendMessage(Component.text("/maintenance remove <player>", NamedTextColor.RED));
                    break;
                }
                if (data.whitelist.remove(a[1].toLowerCase(Locale.ROOT)) == null) {
                    src.sendMessage(plugin.msg("whitelist-not-found", "player", a[1]));
                    break;
                }
                plugin.saveAsync();
                src.sendMessage(plugin.msg("whitelist-removed", "player", a[1]));
            }
            case "list" -> whitelist(src);
            case "server" -> {
                if (a.length < 2) {
                    servers(src);
                    break;
                }
                Optional<RegisteredServer> server = plugin.proxy().getServer(a[1]);
                if (server.isEmpty()) {
                    src.sendMessage(plugin.msg("unknown-server", "server", a[1]));
                    break;
                }
                String name = server.get().getServerInfo().getName();
                boolean on = a.length > 2 ? a[2].equalsIgnoreCase("on") : !plugin.isServerInMaintenance(name);
                plugin.setServerMaintenance(server.get(), on);
                src.sendMessage(plugin.msg(on ? "server-enabled" : "server-disabled", "server", name));
            }
            case "servers" -> servers(src);
            case "motd" -> {
                YamlConfig cfg = plugin.config();
                src.sendMessage(plugin.prefix().append(Component.text("MOTD preview:", NamedTextColor.GRAY)));
                src.sendMessage(plugin.mini(cfg.getString("motd.line1", "")));
                src.sendMessage(plugin.mini(cfg.getString("motd.line2", "")));
            }
            case "reload" -> {
                try {
                    plugin.reload();
                    src.sendMessage(plugin.msg("reloaded"));
                } catch (IOException e) {
                    src.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            default -> help(src);
        }
        if (reopenPanel) {
            panel(src);
        }
    }

    private static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    // ------------------------------------------------------------------ panel

    private static Component button(Component label, NamedTextColor bracket, String command, Component hover) {
        return Component.text()
                .append(Component.text("[", bracket))
                .append(label)
                .append(Component.text("]", bracket))
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(hover))
                .build();
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

        src.sendMessage(line());
        src.sendMessage(Component.text(" ").append(plugin.prefix())
                .append(Component.text("Control Panel", NamedTextColor.WHITE, TextDecoration.BOLD)));
        src.sendMessage(Component.text(" Status: ", NamedTextColor.GRAY)
                .append(data.enabled
                        ? Component.text("● ON", NamedTextColor.RED, TextDecoration.BOLD)
                        : Component.text("● OFF", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("   Reason: ", NamedTextColor.GRAY))
                .append(Component.text(plugin.reason(), NamedTextColor.WHITE)));
        src.sendMessage(Component.text(" Timer: ", NamedTextColor.GRAY)
                .append(Component.text(data.startAt > 0 || data.endAt > 0 ? plugin.remainingText() : "none",
                        NamedTextColor.YELLOW)));
        src.sendMessage(Component.empty());

        src.sendMessage(Component.text(" ")
                .append(button("✔ ENABLE", NamedTextColor.RED, "/maintenance on" + p, "Maintenance ON - non-whitelisted players kick"))
                .append(space)
                .append(button("✘ DISABLE", NamedTextColor.GREEN, "/maintenance off" + p, "Maintenance OFF - server sabke liye khulega"))
                .append(space)
                .append(button("MOTD", NamedTextColor.AQUA, "/maintenance motd", "MOTD preview dekho")));

        // One click presets: maintenance ON + MOTD reason
        TextComponent.Builder presets = Component.text().append(Component.text(" One-click: ", NamedTextColor.GRAY));
        String current = data.customReason == null ? data.preset : null;
        for (String key : cfg.getKeys("presets")) {
            Component label = mm.deserialize(cfg.getString("presets." + key + ".display", key));
            boolean selected = key.equalsIgnoreCase(current) && data.enabled;
            Component hover = Component.text("Maintenance ON + MOTD: ", NamedTextColor.GRAY)
                    .append(Component.text(cfg.getString("presets." + key + ".reason", ""), NamedTextColor.WHITE));
            presets.append(button(selected ? Component.text("▶ ").append(label) : label,
                    selected ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY,
                    "/maintenance on " + key + p, hover)).append(space);
        }
        src.sendMessage(presets.build());

        TextComponent.Builder timers = Component.text().append(Component.text(" Start in: ", NamedTextColor.GRAY));
        for (String t : cfg.getStringList("panel-start-timers")) {
            timers.append(button(t, NamedTextColor.YELLOW, "/maintenance timer " + t + p, "Maintenance " + t + " baad ON")).append(space);
        }
        timers.append(Component.text(" Auto-OFF: ", NamedTextColor.GRAY));
        for (String t : cfg.getStringList("panel-end-timers")) {
            timers.append(button(t, NamedTextColor.GOLD, "/maintenance endtimer " + t + p, "Abhi ON, " + t + " baad apne aap OFF")).append(space);
        }
        timers.append(button("✖", NamedTextColor.RED, "/maintenance abort" + p, "Saare timers cancel"));
        src.sendMessage(timers.build());

        TextComponent.Builder servers = Component.text().append(Component.text(" Servers: ", NamedTextColor.GRAY));
        for (RegisteredServer server : plugin.proxy().getAllServers()) {
            String name = server.getServerInfo().getName();
            boolean on = plugin.isServerInMaintenance(name);
            servers.append(button(Component.text(name + (on ? " ●" : " ○"), on ? NamedTextColor.RED : NamedTextColor.GREEN),
                    NamedTextColor.DARK_GRAY, "/maintenance server " + name + p,
                    Component.text(on ? "Click: " + name + " maintenance OFF" : "Click: sirf " + name + " ko maintenance mein daalo",
                            NamedTextColor.GRAY))).append(space);
        }
        src.sendMessage(servers.build());

        src.sendMessage(Component.text(" Whitelist (" + data.whitelist.size() + "): ", NamedTextColor.GRAY)
                .append(button("List", NamedTextColor.WHITE, "/maintenance list", "Whitelist dekho"))
                .append(space)
                .append(Component.text("[+ Add]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.suggestCommand("/maintenance add "))
                        .hoverEvent(HoverEvent.showText(Component.text("Player ka naam likho", NamedTextColor.GRAY))))
                .append(space)
                .append(button("Reload", NamedTextColor.AQUA, "/maintenance reload" + p, "Config reload")));
        src.sendMessage(line());
    }

    private void whitelist(CommandSource src) {
        MaintenanceData data = plugin.data();
        if (data.whitelist.isEmpty()) {
            src.sendMessage(plugin.msg("whitelist-empty"));
            return;
        }
        TextComponent.Builder b = Component.text().append(plugin.prefix())
                .append(Component.text("Whitelist: ", NamedTextColor.GRAY));
        for (String name : data.whitelist.values()) {
            b.append(Component.text(name + " ", NamedTextColor.WHITE)
                    .append(Component.text("✖", NamedTextColor.RED))
                    .clickEvent(ClickEvent.runCommand("/maintenance remove " + name))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to remove " + name, NamedTextColor.RED))))
                    .append(Component.text("  "));
        }
        src.sendMessage(b.build());
    }

    private void servers(CommandSource src) {
        TextComponent.Builder b = Component.text().append(plugin.prefix());
        for (RegisteredServer server : plugin.proxy().getAllServers()) {
            String name = server.getServerInfo().getName();
            boolean on = plugin.isServerInMaintenance(name);
            b.append(Component.text(name + (on ? " ● " : " ○ "), on ? NamedTextColor.RED : NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/maintenance server " + name))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to toggle", NamedTextColor.GRAY))));
        }
        src.sendMessage(b.build());
    }

    private void help(CommandSource src) {
        String[] lines = {
            "/maintenance - clickable panel",
            "/maintenance on [preset|reason] | off | toggle",
            "/maintenance preset <name> | reason <text>",
            "/maintenance timer <time> [preset] - X time baad ON",
            "/maintenance endtimer <time> - abhi ON, X time baad OFF",
            "/maintenance abort - timers cancel",
            "/maintenance add|remove <player> | list",
            "/maintenance server <name> [on|off] | servers",
            "/maintenance motd | reload"
        };
        src.sendMessage(plugin.prefix().append(Component.text("Commands:", NamedTextColor.WHITE)));
        for (String l : lines) {
            src.sendMessage(Component.text(" " + l, NamedTextColor.GRAY));
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
