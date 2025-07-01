package ru.crystals.infinispan;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.dataconversion.internal.Json;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.context.Flag;
import org.infinispan.manager.EmbeddedCacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.crystals.example.Person;

import java.util.concurrent.TimeUnit;

import static ru.crystals.infinispan.Consts.PERSON_CACHE;

@RestController
public class CacheInfoController {


    private final EmbeddedCacheManager cacheManager;
    private final Configuration configuration;
    private final Cache<Long, Person> personCache;

    public CacheInfoController(EmbeddedCacheManager cacheManager, Configuration configuration) {
        this.configuration = configuration;
        this.cacheManager = cacheManager;

        this.personCache = cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache(PERSON_CACHE, configuration);
    }

    @GetMapping("/size")
    public String getSize() {
        AdvancedCache<Long, Person> aCache = personCache
                .getAdvancedCache()
                .withFlags(Flag.SKIP_CACHE_LOAD);
        int size = aCache.entrySet().size();
        int clusterSize = cacheManager.getMembers().size();
        boolean coordinator = cacheManager.isCoordinator();
        Json json = cacheManager.getHealth().getClusterHealth().toJson();
        return "" + clusterSize + ":" + coordinator + ":" + json + "\n" + size;
    }

    @GetMapping("/kill")
    public String kill() {
        System.exit(0);
        return "kill";
    }

    @GetMapping("/reload")
    public String reload() {
        AdvancedCache<Long, Person> fromDb = personCache
                .getAdvancedCache()
                .withFlags();
        AdvancedCache<Long, Person> fromMem = personCache
                .getAdvancedCache()
                .withFlags(Flag.SKIP_CACHE_LOAD, Flag.CACHE_MODE_LOCAL);

        if (fromDb.size() > fromMem.size()) {
            for (CacheEntry<Long, Person> entry : fromDb.cacheEntrySet()) {
                fromMem.put(entry.getKey(), entry.getValue(),
                        entry.getLifespan(), TimeUnit.MILLISECONDS);
            }
        }
        return "" + fromDb.size();
    }

}
