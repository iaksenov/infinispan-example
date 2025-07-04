package ru.crystals.infinispan;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commons.dataconversion.internal.Json;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.context.Flag;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.crystals.example.Person;
import ru.crystals.shop.Shop;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class CacheInfoController {

    private final EmbeddedCacheManager cacheManager;
    private final Cache<Long, Person> personCache;
    private final Cache<String, Shop> shopCache;
    private final ScheduledReader scheduledReader;

    public static final AtomicInteger personMergeCounter = new AtomicInteger();
    public static final AtomicInteger putErrorsCounter = new AtomicInteger();

    private int maxPersonSize = 0;
    private int maxShopSize = 0;

    public CacheInfoController(EmbeddedCacheManager cacheManager,
                               @Qualifier("personCache") Cache<Long, Person> cache,
                               @Qualifier("shopCache") Cache<String, Shop> shopCache,
                               ScheduledReader scheduledReader) {
        this.cacheManager = cacheManager;
        this.shopCache = shopCache;
        this.scheduledReader = scheduledReader;
        this.personCache = cache;
    }

    @GetMapping("/size")
    public String getSize() {
        AdvancedCache<Long, Person> aCache = personCache
                .getAdvancedCache()
                .withFlags(Flag.SKIP_CACHE_LOAD);
        int size = aCache.entrySet().size();
        if (size > maxPersonSize) {
            maxPersonSize = size;
        }
        AdvancedCache<String, Shop> aShopCache = shopCache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD);
        int shopSize = aShopCache.cacheEntrySet().size();
        if (shopSize > maxShopSize) {
            maxShopSize = shopSize;
        }

        int clusterSize = cacheManager.getMembers().size();
        boolean coordinator = cacheManager.isCoordinator();
        Json json = cacheManager.getHealth().getClusterHealth().toJson();
        return "" + clusterSize + ":" + coordinator + ":" + json + "\n"
                + " Person " + size + "  max: " + maxPersonSize
                + " Shop " + shopSize + "  max: " + maxShopSize +
                "\nPut errors: " + putErrorsCounter.get() + "\n" +
                "Merge count " + personMergeCounter.get();
    }

    @GetMapping("/kill")
    public String kill() {
        try {
            return "kill";
        } finally {
            Executors.newFixedThreadPool(1).submit(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.exit(0);
            });
        }
    }

    @GetMapping("/reload")
    public String reload() {
        AdvancedCache<Long, Person> fromDb = personCache.getAdvancedCache().withFlags();
        AdvancedCache<Long, Person> inMem = personCache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD, Flag.CACHE_MODE_LOCAL);

        if (fromDb.size() > inMem.size()) {
            for (CacheEntry<Long, Person> entry : fromDb.cacheEntrySet()) {
                inMem.put(entry.getKey(), entry.getValue(),
                        entry.getLifespan(), TimeUnit.MILLISECONDS);
            }
        }
        return "" + fromDb.size();
    }

    @GetMapping("/puton")
    public void putOn() {
        scheduledReader.setPutEnable(true);
    }

    @GetMapping("/putoff")
    public void putOff() {
        scheduledReader.setPutEnable(false);
    }

}
