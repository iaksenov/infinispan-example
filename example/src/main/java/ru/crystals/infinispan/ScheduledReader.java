package ru.crystals.infinispan;

import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.context.Flag;
import org.infinispan.health.HealthStatus;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.crystals.example.Item;
import ru.crystals.example.Person;
import ru.crystals.shop.Shop;

import java.time.LocalTime;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EnableScheduling
@Component
@Transactional
public class ScheduledReader {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledReader.class);

    private final EmbeddedCacheManager cacheManager;

    private final Cache<Long, Person> personCache;
    private final Cache<String, Shop> shopCache;

    private boolean putEnable;

    public ScheduledReader(EmbeddedCacheManager cacheManager,
                           @Qualifier("personCache") Cache<Long, Person> personCache,
                           @Qualifier("shopCache") Cache<String, Shop> shopCache) {
        LOG.info("ScheduledReader start");
        this.cacheManager = cacheManager;
        this.personCache = personCache;
        this.shopCache = shopCache;
        this.putEnable = Boolean.parseBoolean(System.getenv("PUT_ENABLE"));
    }

    @Scheduled(fixedDelay = 250)
    public void putSomeValues() {
        if (putEnable) {
            int size = cacheManager.getMembers().size();
            HealthStatus healthStatus = cacheManager.getHealth().getClusterHealth().getHealthStatus();
            if (healthStatus == HealthStatus.HEALTHY) {
                putPerson();
                putShop();
            } else {
                LOG.warn("PUT SKIPPED!! Cluster size " + size + ", health " + healthStatus);
            }
        }
    }

    private void putPerson() {
        AdvancedCache<Long, Person> pcache = personCache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS);
        TransactionManager transactionManager = pcache.getTransactionManager();
        try {
            transactionManager.begin();

            long keyLong = System.currentTimeMillis();
            String keyStr = String.valueOf(keyLong);

            Person person = new Person(keyStr, "BBB-" + keyStr);
            person.setItems(Stream.of(new Item("1", 1L)).collect(Collectors.toList()));

            pcache.put(keyLong, person);
            transactionManager.commit();

            LOG.info("PUT OK!");
        } catch (Exception e) {
            CacheInfoController.putErrorsCounter.incrementAndGet();
            try {
                if (transactionManager.getTransaction() != null) {
                    transactionManager.rollback();
                }
            } catch (Exception ex) {
                LOG.error("ROLLBACK FAILED !!! ", e);
            }
            LOG.error("PUT FAILED !!! ", e);
        }
    }

    private void putShop() {
        AdvancedCache<String, Shop> pcache = shopCache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS);
        TransactionManager transactionManager = pcache.getTransactionManager();
        try {
            transactionManager.begin();

            long id = System.currentTimeMillis();
            Shop sh = new Shop();
            sh.setId(id);
            sh.setName("Shop-" + id);
            pcache.put(String.valueOf(id), sh);

            transactionManager.commit();

            LOG.info("PUT Shop OK!");
        } catch (Exception e) {
            CacheInfoController.putErrorsCounter.incrementAndGet();
            try {
                if (transactionManager.getTransaction() != null) {
                    transactionManager.rollback();
                }
            } catch (Exception ex) {
                LOG.error("ROLLBACK FAILED !!! ", e);
            }
            LOG.error("PUT FAILED !!! ", e);
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void readAll() {

        //AdvancedCache<Long, Person> aCache = personCache
        //        .getAdvancedCache()
                // флаг SKIP_CACHE_LOAD, чтобы предотвратить чтение из БД
                // иначе каждое обращение к кэшу будет выполнять SELECT(-ы)
        //        .withFlags(Flag.SKIP_CACHE_LOAD);

        LOG.info("Time = {}, cluster size = {}, isCoordinator = {}", LocalTime.now(), cacheManager.getMembers().size(), cacheManager.isCoordinator());
        LOG.info("CacheManager cluster health: {}", cacheManager.getHealth().getClusterHealth().toJson());


        /*
        CacheSet<Map.Entry<Long, Person>> entries = aCache.entrySet();
        long s = 0;
        for (Map.Entry<Long, Person> entry : entries) {
            s += entry.getValue().getItemsSum();
        }
        LOG.info("Cache size = {}, items sum = {}", entries.size(), s);
*/
//        Тест того, что не выполняется десериализация при обращении к одной и той же сущности.
//        Это достижимо только при mediaType="application/x-java-object"

//        Person person1 = aCache.get("1");
//        Person person1_2 = aCache.get("1");
//        LOG.info("Got objects are equals is {}", (person1 == person1_2));
/*
        AdvancedCache<String, Shop> aShopCache = shopCache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD);
        Shop shop = aShopCache.get("1");
        LOG.info("Shop 1 found in cache {}", shop);

 */
    }

    public void setPutEnable(boolean putEnable) {
        this.putEnable = putEnable;
    }

}
