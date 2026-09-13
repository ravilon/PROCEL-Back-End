package com.procel.api.service.missions.rules.drools;

import org.kie.api.KieBase;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class DroolsKieBaseCache {
    private final int maxEntries;
    private final long expirationNanos;
    private final LongSupplier ticker;
    private final LinkedHashMap<String, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final ConcurrentMap<String, CompletableFuture<KieBase>> inFlightCompilations = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong compilations = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    DroolsKieBaseCache(int maxEntries, Duration expiration, LongSupplier ticker) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        if (expiration == null || !expiration.isPositive()) throw new IllegalArgumentException("expiration must be positive");
        this.maxEntries = maxEntries;
        this.expirationNanos = expiration.toNanos();
        this.ticker = ticker;
    }

    KieBase getOrCompile(
            String fingerprint,
            Supplier<KieBase> compiler,
            Runnable hitCallback,
            Runnable missCallback,
            java.util.function.LongConsumer evictionCallback
    ) {
        KieBase cached = getIfPresent(fingerprint, evictionCallback);
        if (cached != null) {
            hits.incrementAndGet();
            hitCallback.run();
            return cached;
        }
        misses.incrementAndGet();
        missCallback.run();

        CompletableFuture<KieBase> created = new CompletableFuture<>();
        CompletableFuture<KieBase> existing = inFlightCompilations.putIfAbsent(fingerprint, created);
        if (existing != null) {
            return existing.join();
        }

        try {
            compilations.incrementAndGet();
            KieBase compiled = compiler.get();
            put(fingerprint, compiled, evictionCallback);
            created.complete(compiled);
            return compiled;
        } catch (RuntimeException ex) {
            created.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlightCompilations.remove(fingerprint, created);
        }
    }

    DroolsCacheStats stats() {
        synchronized (entries) {
            evictExpired(ticker.getAsLong());
            return new DroolsCacheStats(
                    hits.get(),
                    misses.get(),
                    compilations.get(),
                    evictions.get(),
                    entries.size()
            );
        }
    }

    private KieBase getIfPresent(String fingerprint, java.util.function.LongConsumer evictionCallback) {
        synchronized (entries) {
            long now = ticker.getAsLong();
            long expired = evictExpired(now);
            if (expired > 0) evictionCallback.accept(expired);
            CacheEntry entry = entries.get(fingerprint);
            if (entry == null) return null;
            entry.lastAccessNanos = now;
            return entry.kieBase;
        }
    }

    private void put(String fingerprint, KieBase kieBase, java.util.function.LongConsumer evictionCallback) {
        synchronized (entries) {
            long now = ticker.getAsLong();
            long expired = evictExpired(now);
            entries.put(fingerprint, new CacheEntry(kieBase, now));
            long overflow = evictOverflow();
            long totalEvicted = expired + overflow;
            if (totalEvicted > 0) evictionCallback.accept(totalEvicted);
        }
    }

    private long evictExpired(long now) {
        long count = 0;
        Iterator<Map.Entry<String, CacheEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            CacheEntry entry = iterator.next().getValue();
            if (now - entry.lastAccessNanos > expirationNanos) {
                iterator.remove();
                count++;
            }
        }
        if (count > 0) evictions.addAndGet(count);
        return count;
    }

    private long evictOverflow() {
        long count = 0;
        Iterator<String> iterator = entries.keySet().iterator();
        while (entries.size() > maxEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
            count++;
        }
        if (count > 0) evictions.addAndGet(count);
        return count;
    }

    private static final class CacheEntry {
        private final KieBase kieBase;
        private long lastAccessNanos;

        private CacheEntry(KieBase kieBase, long lastAccessNanos) {
            this.kieBase = kieBase;
            this.lastAccessNanos = lastAccessNanos;
        }
    }
}
