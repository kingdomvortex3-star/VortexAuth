
package id.vortexauth.session;

import id.vortexauth.VortexAuth;

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

        long duration = getSessionDuration();

        long expiresAt = duration == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + duration;

        Session session = new Session(
                uuid,
                ipHash,
                System.currentTimeMillis(),
                expiresAt
        );

        sessions.put(uuid, session);
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
                "session.ip-check",
                true
        )) {
            return true;
        }

        Session session = sessions.get(uuid);

        if (session == null) {
            return false;
        }

        String savedIpHash = session.ipHash();

        if (savedIpHash == null || currentIpHash == null) {
            return false;
        }

        return savedIpHash.equals(currentIpHash);
    }

    public void invalidateSession(UUID uuid) {
        if (uuid == null) {
            return;
        }

        sessions.remove(uuid);
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

        for (Map.Entry<UUID, Session> entry :
                sessions.entrySet()) {

            Session session = entry.getValue();

            if (isExpired(session)) {
                if (sessions.remove(
                        entry.getKey(),
                        session
                )) {
                    removed++;
                }
            }
        }

        return removed;
    }

    private boolean isExpired(Session session) {
        if (!plugin.getConfig().getBoolean(
                "session.timeout.enabled",
                true
        )) {
            return false;
        }

        return System.currentTimeMillis()
                >= session.expiresAt();
    }

    private long getSessionDuration() {
        boolean enabled = plugin.getConfig().getBoolean(
                "session.timeout.enabled",
                true
        );

        if (!enabled) {
            return Long.MAX_VALUE;
        }

        long minutes = plugin.getConfig().getLong(
                "session.timeout.duration-minutes",
                30
        );

        minutes = Math.max(1, minutes);

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
