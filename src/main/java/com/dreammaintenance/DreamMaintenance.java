package com.dreammaintenance;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.Favicon;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

/** Velocity entry point (declared in velocity-plugin.json). */
public final class DreamMaintenance {

    static final String PERM_ADMIN = "dreammaintenance.admin";
    static final String PERM_BYPASS = "dreammaintenance.bypass";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private volatile YamlConfig config;
    private volatile MaintenanceData data = new MaintenanceData();
    private volatile Favicon icon;
    private volatile long lastCountdownSecond = -1;

    @Inject
    public DreamMaintenance(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            reload();
            data = MaintenanceData.load(dataDirectory.resolve("data.json"));
        } catch (IOException e) {
            logger.error("Could not load DreamMaintenance config/data", e);
            return;
        }
        proxy.getEventManager().register(this, new MaintenanceListener(this));
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("maintenance").aliases("mt", "maint").plugin(this).build(),
                new MaintenanceCommand(this));
        proxy.getScheduler().buildTask(this, this::tick).repeat(1, TimeUnit.SECONDS).schedule();
        logger.info("DreamMaintenance enabled - maintenance is currently {}", data.enabled ? "ON" : "OFF");
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        save();
    }

    void reload() throws IOException {
        config = YamlConfig.load(dataDirectory.resolve("config.yml"), "config.yml");
        icon = null;
        Path iconFile = dataDirectory.resolve("maintenance-icon.png");
        if (config.getBoolean("use-custom-icon", true) && Files.exists(iconFile)) {
            try {
                icon = Favicon.create(iconFile);
            } catch (IOException | IllegalArgumentException e) {
                logger.warn("maintenance-icon.png must be a 64x64 PNG: {}", e.getMessage());
            }
        }
    }

    void save() {
        try {
            data.save(dataDirectory.resolve("data.json"));
        } catch (IOException e) {
            logger.error("Could not save data.json", e);
        }
    }

    void saveAsync() {
        proxy.getScheduler().buildTask(this, this::save).schedule();
    }

    // ------------------------------------------------------------------ state

    ProxyServer proxy() {
        return proxy;
    }

    YamlConfig config() {
        return config;
    }

    MaintenanceData data() {
        return data;
    }

    Favicon icon() {
        return icon;
    }

    boolean canBypass(Player player) {
        return player.hasPermission(PERM_BYPASS)
                || player.hasPermission(PERM_ADMIN)
                || data.whitelist.containsKey(player.getUsername().toLowerCase(Locale.ROOT));
    }

    boolean isServerInMaintenance(String server) {
        return data.servers.contains(server.toLowerCase(Locale.ROOT));
    }

    String reason() {
        if (data.customReason != null && !data.customReason.isBlank()) {
            return data.customReason;
        }
        String preset = data.preset != null ? data.preset : config.getString("default-preset", "update");
        return config.getString("presets." + preset + ".reason", "Maintenance");
    }

    String remainingText() {
        if (data.enabled && data.endAt > 0) {
            long seconds = (data.endAt - System.currentTimeMillis()) / 1000;
            return config.getString("remaining-format", "Back in: <time>").replace("<time>", TimeUtil.format(seconds));
        }
        if (!data.enabled && data.startAt > 0) {
            long seconds = (data.startAt - System.currentTimeMillis()) / 1000;
            return "Starts in " + TimeUtil.format(seconds);
        }
        return config.getString("no-timer-text", "");
    }

    /** Turn maintenance on. presetOrReason may be null (keep current reason). */
    void enable(String presetOrReason) {
        if (presetOrReason != null) {
            setReason(presetOrReason);
        }
        data.startAt = 0;
        boolean wasEnabled = data.enabled;
        data.enabled = true;
        saveAsync();
        if (!wasEnabled) {
            broadcastStaff(msg("enabled"));
            logger.info("Maintenance enabled ({})", reason());
        }
        if (config.getBoolean("kick-online-players", true)) {
            Component kick = text("kick-message");
            for (Player player : proxy.getAllPlayers()) {
                if (!canBypass(player)) {
                    player.disconnect(kick);
                }
            }
        }
    }

    void disable() {
        data.enabled = false;
        data.endAt = 0;
        data.startAt = 0;
        saveAsync();
        broadcastStaff(msg("disabled"));
        logger.info("Maintenance disabled");
    }

    /** @return true if the value was a known preset key. */
    boolean setReason(String presetOrReason) {
        String key = presetOrReason.toLowerCase(Locale.ROOT);
        if (config.getKeys("presets").contains(key)) {
            data.preset = key;
            data.customReason = null;
            saveAsync();
            return true;
        }
        data.customReason = presetOrReason;
        saveAsync();
        return false;
    }

    void setServerMaintenance(RegisteredServer server, boolean on) {
        String name = server.getServerInfo().getName();
        if (on) {
            data.servers.add(name.toLowerCase(Locale.ROOT));
            Component denied = text("server-denied", "server", name);
            Optional<RegisteredServer> fallback = proxy.getServer(config.getString("fallback-server", "lobby"));
            for (Player player : server.getPlayersConnected()) {
                if (canBypass(player)) {
                    continue;
                }
                if (fallback.isPresent() && !fallback.get().equals(server)) {
                    player.createConnectionRequest(fallback.get()).fireAndForget();
                    player.sendMessage(prefix().append(denied));
                } else {
                    player.disconnect(denied);
                }
            }
        } else {
            data.servers.remove(name.toLowerCase(Locale.ROOT));
        }
        saveAsync();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        MaintenanceData d = data;
        if (!d.enabled && d.startAt > 0) {
            long left = (d.startAt - now + 999) / 1000;
            if (left <= 0) {
                enable(null);
                lastCountdownSecond = -1;
            } else if (left != lastCountdownSecond && countdownSeconds().contains(left)) {
                lastCountdownSecond = left;
                Component c = msg("countdown", "time", TimeUtil.format(left));
                proxy.getAllPlayers().forEach(p -> p.sendMessage(c));
            }
        }
        if (d.enabled && d.endAt > 0 && now >= d.endAt) {
            disable();
        }
    }

    private List<Long> countdownSeconds() {
        return config.getStringList("countdown-broadcast-at").stream().map(s -> {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return -1L;
            }
        }).toList();
    }

    private void broadcastStaff(Component message) {
        proxy.getConsoleCommandSource().sendMessage(message);
        for (Player player : proxy.getAllPlayers()) {
            if (player.hasPermission(PERM_ADMIN) || player.hasPermission(PERM_BYPASS)) {
                player.sendMessage(message);
            }
        }
    }

    // ------------------------------------------------------------------ messages

    TagResolver placeholders(String... pairs) {
        TagResolver.Builder builder = TagResolver.builder()
                .resolver(Placeholder.unparsed("reason", reason()))
                .resolver(Placeholder.unparsed("remaining", remainingText()));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            builder.resolver(Placeholder.unparsed(pairs[i], pairs[i + 1]));
        }
        return builder.build();
    }

    Component mini(String miniMessage, String... pairs) {
        return MiniMessage.miniMessage().deserialize(miniMessage, placeholders(pairs));
    }

    Component prefix() {
        return MiniMessage.miniMessage().deserialize(config.getString("messages.prefix", ""));
    }

    Component text(String key, String... pairs) {
        return mini(config.getString("messages." + key, key), pairs);
    }

    Component msg(String key, String... pairs) {
        return prefix().append(text(key, pairs));
    }
}
