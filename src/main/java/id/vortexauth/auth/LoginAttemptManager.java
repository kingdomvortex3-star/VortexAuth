
package id.vortexauth.auth;

import id.vortexauth.VortexAuth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginAttemptManager {

    private final VortexAuth plugin;

    private final Map<UUID, AttemptData> attempts =
            new ConcurrentHashMap<>();

    public LoginAttemptManager(VortexAuth plugin) {
        this.plugin = plugin;
    }

    public boolean isLocked(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        AttemptData data = attempts.get(uuid);

        if (data == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (data.lockedUntil() <= now && data.lockedUntil() > 0L) {
            attempts.remove(uuid, data);
            return false;
        }

        return data.lockedUntil() > now;
    }

    public boolean recordFailedAttempt(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        AttemptData updated = attempts.compute(uuid, (key, old) -> {
            if (old != null && old.lockedUntil() > now) {
                return old;
            }

            int count;

            if (old == null || old.lockedUntil() > 0L) {
                count = 1;
            } else {
                count = old.count() + 1;
            }

            int maxAttempts = Math.max(
                    1,
                    plugin.getConfig().getInt(
                            "authentication.login.max-attempts",
                            5
                    )
            );

            if (count >= maxAttempts) {
                return new AttemptData(
                        count,
                        now + getLockDuration()
                );
            }

            return new AttemptData(count, 0L);
        });

        return updated.lockedUntil() > now;
    }

    public void resetAttempts(UUID uuid) {
        if (uuid != null) {
            attempts.remove(uuid);
        }
    }

    public int getAttempts(UUID uuid) {
        if (uuid == null) {
            return 0;
        }

        AttemptData data = attempts.get(uuid);

        if (data == null) {
            return 0;
        }

        if (data.lockedUntil() > 0L
                && data.lockedUntil() <= System.currentTimeMillis()) {
            attempts.remove(uuid, data);
            return 0;
        }

        return data.count();
    }

    public int getRemainingAttempts(UUID uuid) {
        int maximum = Math.max(
                1,
                plugin.getConfig().getInt(
                        "authentication.login.max-attempts",
                        5
                )
        );

        return Math.max(0, maximum - getAttempts(uuid));
    }

    public long getRemainingLockMillis(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }

        AttemptData data = attempts.get(uuid);

        if (data == null || data.lockedUntil() <= 0L) {
            return 0L;
        }

        long remaining = data.lockedUntil()
                - System.currentTimeMillis();

        if (remaining <= 0L) {
            attempts.remove(uuid, data);
            return 0L;
        }

        return remaining;
    }

    public long getRemainingLockTime(UUID uuid) {
        return getRemainingLockMillis(uuid);
    }

    public int cleanupExpiredAttempts() {
        long now = System.currentTimeMillis();
        int removed = 0;

        for (Map.Entry<UUID, AttemptData> entry :
                attempts.entrySet()) {

            AttemptData data = entry.getValue();

            if (data.lockedUntil() > 0L
                    && data.lockedUntil() <= now
                    && attempts.remove(entry.getKey(), data)) {
                removed++;
            }
        }

        return removed;
    }

    public int getTrackedPlayerCount() {
        return attempts.size();
    }

    private long getLockDuration() {
        long seconds = Math.max(
                1L,
                plugin.getConfig().getLong(
                        "authentication.login.lock-duration-seconds",
                        300L
                )
        );

        return Math.min(seconds, 86_400L) * 1000L;
    }

    public record AttemptData(
            int count,
            long lockedUntil
    ) {
    }
}
