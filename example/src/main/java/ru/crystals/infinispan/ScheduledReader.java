package ru.crystals.infinispan;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.CacheSet;
import org.infinispan.commons.dataconversion.internal.Json;
import org.infinispan.context.Flag;
import org.infinispan.health.Health;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.crystals.example.Person;

import java.time.LocalTime;
import java.util.Map;

@EnableScheduling
@Component
public class ScheduledReader {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledReader.class);

    private final EmbeddedCacheManager cacheManager;
    private final Cache<String, Person> personCache;

    public ScheduledReader(EmbeddedCacheManager cacheManager) {
        LOG.info("ScheduledReader start");
        this.cacheManager = cacheManager;
        personCache = cacheManager.getCache("person");
    }

    @Scheduled(fixedDelay = 15000)
    public void readAll() {
        AdvancedCache<String, Person> aCache = personCache
                .getAdvancedCache()
                // чтобы предотвратить чтение из БД
                .withFlags(Flag.SKIP_CACHE_LOAD);

        int clusterSize = cacheManager.getMembers().size();
        Health health = cacheManager.getHealth();
        Json healthJson = health.getClusterHealth().toJson();
        LOG.info("Time = {}, cluster size = {}", LocalTime.now(), clusterSize);
        LOG.info("CacheManager cluster health: {}", healthJson);

        CacheSet<Map.Entry<String, Person>> entries = aCache.entrySet();
        long s = 0;
        for (Map.Entry<String, Person> entry : entries) {
            s += entry.getValue().getItemsSum();
        }

        LOG.info("Cache size = {}, items sum = {}", entries.size(), s);
        Person person1 = aCache.get("1");
        Person person1_2 = aCache.get("1");
        LOG.info("Got objects are equals is {}", (person1 == person1_2));
    }

}
