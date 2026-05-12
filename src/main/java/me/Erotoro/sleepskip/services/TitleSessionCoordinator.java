package me.Erotoro.sleepskip.services;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates ownership of title output per overlay scope.
 * Any delayed show/clear task must verify its token is still current before touching player titles.
 */
public class TitleSessionCoordinator {

    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<String, Long> currentTokens = new ConcurrentHashMap<>();

    public long claim(String scopeKey) {
        long token = sequence.incrementAndGet();
        currentTokens.put(scopeKey, token);
        return token;
    }

    public boolean isCurrent(String scopeKey, long token) {
        if (scopeKey == null || token <= 0L) {
            return false;
        }
        return token == currentTokens.getOrDefault(scopeKey, -1L);
    }

    public long currentToken(String scopeKey) {
        if (scopeKey == null) {
            return -1L;
        }
        return currentTokens.getOrDefault(scopeKey, -1L);
    }
}
