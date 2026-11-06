
package id.vortexauth.session;

import id.vortexauth.VortexAuth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private final VortexAuth plugin;

    private final Map<UUID, Session> sessions =
            new ConcurrentHashMap<>();

    public SessionManager(VortexAuth plugin) {
        this.plugin = plugin;
    }

    public void createSession(UUID uuid, String ipHash) {
        if (uuid == null) {
            return;
        }

        if (plugin.getConfig().getBoolean(
                "session.ip-check", true
        ) && (ipHash == null || ipHash.isBlank())) {
            invalidateSession(uuid);
            return;
        }

        long now = System.currentTimeMillis();
        long duration = getSessionDuration();

        long expiresAt;

        if (duration == Long.MAX_VALUE
                || Long.MAX_VALUE - now < duration) {
            expiresAt = Long.MAX_VALUE;
        } else {
            expiresAt = now + duration;
        }

        sessions.put(
                uuid,
                new Session(uuid, ipHash, now, expiresAt)
        );
    }

    public boolean isAuthenticated(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        Session session = sessions.get(uuid);

        if (session == null) {
            return false;
        }

        if (isExpired(session)) {
            sessions.remove(uuid, session);
            return false;
        }

        return true;
    }

    public boolean isAuthenticated(
            UUID uuid,
            String currentIpHash
    ) {
        if (!isAuthenticated(uuid)) {
            return false;
        }

        if (!plugin.getConfig().getBoolean(
                "session.ip-check", true
        )) {
            return true;
        }

        if (currentIpHash == null || currentIpHash.isBlank()) {
            return false;
        }

        Session session = sessions.get(uuid);

        if (session == null) {
            return false;
        }

        String savedIpHash = session.ipHash();

        if (savedIpHash == null || savedIpHash.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                savedIpHash.getBytes(StandardCharsets.UTF_8),
                currentIpHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    public void invalidateSession(UUID uuid) {
        if (uuid != null) {
            sessions.remove(uuid);
        }
    }

    public void invalidateAll() {
        sessions.clear();
    }

    public Session getSession(UUID uuid) {
        if (!isAuthenticated(uuid)) {
            return null;
        }

        return sessions.get(uuid);
    }

    public long getRemainingTime(UUID uuid) {
        Session session = getSession(uuid);

        if (session == null) {
            return 0L;
        }

        if (session.expiresAt() == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        return Math.max(
                0L,
                session.expiresAt() - System.currentTimeMillis()
        );
    }

    public int cleanupExpiredSessions() {
        int removed = 0;
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Session> entry :
                sessions.entrySet()) {

            Session session = entry.getValue();

            if (isExpired(session, now)
                    && sessions.remove(entry.getKey(), session)) {
                removed++;
            }
        }

        return removed;
    }

    private boolean isExpired(Session session) {
        return isExpired(session, System.currentTimeMillis());
    }

    private boolean isExpired(Session session, long now) {
        if (!plugin.getConfig().getBoolean(
                "session.timeout.enabled", true
        )) {
            return false;
        }

        return now >= session.expiresAt();
    }

    private long getSessionDuration() {
        if (!plugin.getConfig().getBoolean(
                "session.timeout.enabled", true
        )) {
            return Long.MAX_VALUE;
        }

        long minutes = Math.max(
                1L,
                plugin.getConfig().getLong(
                        "session.timeout.duration-minutes", 30L
                )
        );

        if (minutes > Long.MAX_VALUE / 60_000L) {
            return Long.MAX_VALUE;
        }

        return minutes * 60_000L;
    }

    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return sessions.size();
    }

    public record Session(
            UUID uuid,
            String ipHash,
            long createdAt,
            long expiresAt
    ) {
    }
}
