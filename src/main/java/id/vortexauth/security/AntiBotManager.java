
package id.vortexauth.security;

import id.vortexauth.VortexAuth;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiBotManager {

    private final VortexAuth plugin;
    private final IpAddressManager ipAddressManager;

    private final Map<String, Integer> connections =
            new ConcurrentHashMap<>();

    private final Map<String, Long> lastJoin =
            new ConcurrentHashMap<>();

    private final Map<String, Long> blockedAddresses =
            new ConcurrentHashMap<>();

    private final Map<UUID, String> playerAddresses =
            new ConcurrentHashMap<>();

    public AntiBotManager(
            VortexAuth plugin,
            IpAddressManager ipAddressManager
    ) {
        this.plugin = plugin;
        this.ipAddressManager = ipAddressManager;
    }

    public boolean checkJoin(Player player) {
        if (player == null) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "anti-bot.enabled", true
        )) {
            return true;
        }

        String ipHash = ipAddressManager.getPlayerIpHash(player);

        if (ipHash == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (isTemporarilyBlocked(ipHash, now)) {
            return false;
        }

        if (playerAddresses.containsKey(player.getUniqueId())) {
            return true;
        }

        if (plugin.getConfig().getBoolean(
                "anti-bot.join-protection.enabled", true
        )) {
            long minimumInterval = plugin.getConfig().getLong(
                    "anti-bot.join-protection.minimum-join-interval-ms",
                    1000L
            );

            Long previousJoin = lastJoin.put(ipHash, now);

            if (previousJoin != null
                    && now - previousJoin < minimumInterval) {
                blockAddress(ipHash);
                return false;
            }
        }

        if (plugin.getConfig().getBoolean(
                "anti-bot.connection-limit.enabled", true
        )) {
            int maximumConnections = plugin.getConfig().getInt(
                    "anti-bot.connection-limit.max-connections-per-ip",
                    3
            );

            int currentConnections = connections.getOrDefault(
                    ipHash, 0
            );

            if (currentConnections >= maximumConnections) {
                blockAddress(ipHash);
                return false;
            }
        }

        playerAddresses.put(player.getUniqueId(), ipHash);
        connections.merge(ipHash, 1, Integer::sum);

        return true;
    }

    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }

        String ipHash = playerAddresses.remove(
                player.getUniqueId()
        );

        if (ipHash == null) {
            return;
        }

        connections.computeIfPresent(ipHash, (key, count) -> {
            int updated = count - 1;
            return updated <= 0 ? null : updated;
        });
    }

    public boolean isTemporarilyBlocked(String ipHash) {
        return isTemporarilyBlocked(
                ipHash,
                System.currentTimeMillis()
        );
    }

    private boolean isTemporarilyBlocked(
            String ipHash,
            long now
    ) {
        Long expiry = blockedAddresses.get(ipHash);

        if (expiry == null) {
            return false;
        }

        if (now >= expiry) {
            blockedAddresses.remove(ipHash, expiry);
            return false;
        }

        return true;
    }

    private void blockAddress(String ipHash) {
        if (!plugin.getConfig().getBoolean(
                "anti-bot.temporary-block.enabled", true
        )) {
            return;
        }

        long duration = plugin.getConfig().getLong(
                "anti-bot.temporary-block.duration-seconds",
                300L
        );

        if (duration <= 0) {
            return;
        }

        long expiry = System.currentTimeMillis()
                + duration * 1000L;

        blockedAddresses.put(ipHash, expiry);
    }

    public int getActiveConnections(String ipHash) {
        return connections.getOrDefault(ipHash, 0);
    }

    public int getBlockedAddressCount() {
        cleanupExpiredBlocks();
        return blockedAddresses.size();
    }

    public void cleanupExpiredBlocks() {
        long now = System.currentTimeMillis();

        blockedAddresses.entrySet().removeIf(
                entry -> entry.getValue() <= now
        );
    }

    public void clearPlayer(Player player) {
        handleQuit(player);
    }

    public void shutdown() {
        connections.clear();
        lastJoin.clear();
        blockedAddresses.clear();
        playerAddresses.clear();
    }
}
