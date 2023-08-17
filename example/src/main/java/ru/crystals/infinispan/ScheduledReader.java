package ru.crystals.infinispan;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.CacheSet;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.dataconversion.internal.Json;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.context.Flag;
import org.infinispan.health.Health;
import org.infinispan.manager.EmbeddedCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.crystals.example.Item;
import ru.crystals.example.Person;
import ru.crystals.shop.Shop;

import javax.annotation.PostConstruct;
import java.time.LocalTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EnableScheduling
@Component
public class ScheduledReader {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledReader.class);

    private final EmbeddedCacheManager cacheManager;
    private final Cache<String, Person> personCache;
    private final Cache<String, Shop> shopCache;

    public ScheduledReader(EmbeddedCacheManager cacheManager) {
        LOG.info("ScheduledReader start");
        this.cacheManager = cacheManager;

        Configuration cacheConfiguration = cacheManager.getCacheConfiguration(InfinispanConfig.CACHE_CONFIGURATION_NAME);

        // добываем кэш из менеджера
        this.personCache = cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("person", cacheConfiguration);

        // добываем кэш из менеджера
        this.shopCache = cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("shop", cacheConfiguration);
    }

    @PostConstruct
    public void putEntries() {
        // отправка потребует синхронной репликации
        AdvancedCache<String, Person> acache = personCache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS);

        Person person = new Person("AAA", "BBB");
        person.setItems(Stream.of(new Item("0", 1L)).collect(Collectors.toList()));
        person.setItems(Stream.of(new Item("1", 1L)).collect(Collectors.toList()));
        acache.put(String.valueOf(new Random().nextInt()), person);

        Person person2 = new Person("BBB", "---");
        // Пример указания срока жизни элемента в 60 секунд.
        // Например, для рекламных акций, у которых указан срок окончания можно указать сколько они будут жить в кэше.
        // Также необходимо добавить дельту, если собираемся хранить законченные акции какое-то время.
        // По истечении времени хранения, запись будет удалена из кэша.
        // В БД, при использовании JDBC Store (не SQL Store), есть поле TIMESTAMP для срока хранения.
        // Фоновый процесс Infinispan периодически удаляет устаревшие записи из таблицы самостоятельно.
        acache.put(String.valueOf(new Random().nextInt()), person2, 60, TimeUnit.SECONDS);


        AdvancedCache<String, Shop> aShopCache = shopCache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS);
        Shop shop = Shop.builder().id(1L).address("Address1").name("Shop 1").build();
        aShopCache.put(String.valueOf(shop.getId()), shop);
    }

    @Scheduled(fixedDelay = 15000)
    public void readAll() {
        AdvancedCache<String, Person> aCache = personCache
                .getAdvancedCache()
                // флаг, чтобы предотвратить чтение из БД
                // иначе каждое обращение к кэшу будет выполнять SELECT
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

        // Тест того, что не выполняется десериализация при обращении к одной и той же сущности.
        // Это достижимо только при mediaType="application/x-java-object"
        LOG.info("Got objects are equals is {}", (person1 == person1_2));

        AdvancedCache<String, Shop> aShopCache = shopCache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD);
        Shop shop = aShopCache.get("1");
        LOG.info("Shop 1 found in cache {}", shop);
    }

}
