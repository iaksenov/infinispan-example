package ru.crystals.infinispan;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.CacheSet;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.context.Flag;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.crystals.example.Item;
import ru.crystals.example.Person;
import ru.crystals.shop.Shop;

import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import java.time.LocalTime;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.crystals.infinispan.Consts.PERSON_CACHE;
import static ru.crystals.infinispan.Consts.SHOP_CACHE;

@EnableScheduling
@Component
@Transactional
public class ScheduledReader {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledReader.class);

    private final EmbeddedCacheManager cacheManager;

    private final Cache<Long, Person> personCache;
    private final Cache<String, Shop> shopCache;

    private final boolean putEnable;

    public ScheduledReader(EmbeddedCacheManager cacheManager, Configuration configuration) {
        LOG.info("ScheduledReader start");
        this.cacheManager = cacheManager;

        this.personCache = cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache(PERSON_CACHE, configuration);

        this.shopCache = cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache(SHOP_CACHE, configuration);

        this.putEnable = Boolean.parseBoolean(System.getenv("PUT_ENABLE"));
    }

    @Scheduled(fixedDelay = 2000)
    public void putSomeValues() {
        if (putEnable) {
            AdvancedCache<Long, Person> pcache = personCache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS);
            TransactionManager transactionManager = pcache.getTransactionManager();
            try {
                transactionManager.begin();

                long keyLong = System.currentTimeMillis();
                String keyStr = String.valueOf(keyLong);

                Person person = new Person(keyStr, "BBB-" + keyStr);
                person.setItems(Stream.of(new Item("1", 1L)).collect(Collectors.toList()));

                pcache.put(keyLong, person);
                // generate exception
//                pcache.put(null, person);

                transactionManager.commit();

                LOG.info("PUT OK!");
            } catch (Exception e) {
                try {
                    if (transactionManager.getTransaction() != null) {
                        transactionManager.rollback();
                    }
                } catch (SystemException ex) {
                    LOG.error("ROLLBACK FAILED !!! ", e);
                }
                LOG.error("PUT FAILED !!! ", e);
            }
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void readAll() {
        AdvancedCache<Long, Person> aCache = personCache
                .getAdvancedCache()
                // флаг SKIP_CACHE_LOAD, чтобы предотвратить чтение из БД
                // иначе каждое обращение к кэшу будет выполнять SELECT(-ы)
                .withFlags(Flag.SKIP_CACHE_LOAD);

        LOG.info("Time = {}, cluster size = {}, isCoordinator = {}", LocalTime.now(), cacheManager.getMembers().size(), cacheManager.isCoordinator());
        LOG.info("CacheManager cluster health: {}", cacheManager.getHealth().getClusterHealth().toJson());

        CacheSet<Map.Entry<Long, Person>> entries = aCache.entrySet();
        long s = 0;
        for (Map.Entry<Long, Person> entry : entries) {
            s += entry.getValue().getItemsSum();
        }
        LOG.info("Cache size = {}, items sum = {}", entries.size(), s);

//        Тест того, что не выполняется десериализация при обращении к одной и той же сущности.
//        Это достижимо только при mediaType="application/x-java-object"

//        Person person1 = aCache.get("1");
//        Person person1_2 = aCache.get("1");
//        LOG.info("Got objects are equals is {}", (person1 == person1_2));

        AdvancedCache<String, Shop> aShopCache = shopCache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD);
        Shop shop = aShopCache.get("1");
        LOG.info("Shop 1 found in cache {}", shop);
    }

}
