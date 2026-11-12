package com.dreammaintenance;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class MaintenanceListener {

    private static final UUID EMPTY = new UUID(0L, 0L);

    private final DreamMaintenance plugin;

    MaintenanceListener(DreamMaintenance plugin) {
        this.plugin = plugin;
    }

    /** Server list: maintenance MOTD, red version text, hover lines, custom icon. */
    @Subscribe(order = PostOrder.LATE)
    public void onPing(ProxyPingEvent event) {
        if (!plugin.data().enabled) {
            return;
        }
        YamlConfig cfg = plugin.config();
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        ServerPing.Builder ping = event.getPing().asBuilder();

        Component motd = plugin.mini(cfg.getString("motd.line1", ""))
                .append(Component.newline())
                .append(plugin.mini(cfg.getString("motd.line2", "")));
        ping.description(motd);

        // A protocol number no client uses -> the client shows our text in red instead of the player count
        ping.version(new ServerPing.Version(-1, legacy.serialize(plugin.mini(cfg.getString("version-text", "Maintenance")))));

        List<String> hover = cfg.getStringList("hover-lines");
        ping.clearSamplePlayers();
        ping.samplePlayers(hover.stream()
                .map(line -> new ServerPing.SamplePlayer(legacy.serialize(plugin.mini(line)), EMPTY))
                .toArray(ServerPing.SamplePlayer[]::new));

        if (plugin.icon() != null) {
            ping.favicon(plugin.icon());
        }
        event.setPing(ping.build());
    }

    /** Global maintenance: block joins. Runs late so LuckPerms permissions are loaded. */
    @Subscribe(order = PostOrder.LATE)
    public void onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed() || !plugin.data().enabled) {
            return;
        }
        if (!plugin.canBypass(event.getPlayer())) {
            event.setResult(ResultedEvent.ComponentResult.denied(plugin.text("kick-message")));
        }
    }

    /** Per-server maintenance. */
    @Subscribe(order = PostOrder.LATE)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        RegisteredServer target = event.getResult().getServer().orElse(null);
        if (target == null) {
            return;
        }
        String name = target.getServerInfo().getName();
        Player player = event.getPlayer();
        if (plugin.isServerInMaintenance(name) && !plugin.canBypass(player)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            Component denied = plugin.text("server-denied", "server", name);
            if (event.getPreviousServer() == null) {
                player.disconnect(denied);
            } else {
                player.sendMessage(plugin.prefix().append(denied));
            }
        }
    }
}
