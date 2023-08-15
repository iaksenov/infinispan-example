package ru.crystals.infinispan.example;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.CacheSet;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.context.Flag;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.persistence.jdbc.common.DatabaseType;
import org.infinispan.persistence.jdbc.configuration.JdbcStringBasedStoreConfigurationBuilder;
import ru.crystals.infinispan.pojo.CacheListener;
import ru.crystals.infinispan.pojo.Item;
import ru.crystals.infinispan.pojo.ItemInterface;
import ru.crystals.infinispan.pojo.Person;
import ru.crystals.shop.Shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class JDBCStore {

    private final DefaultCacheManager cacheManager;
    private Cache<String, Person> personCache;
    private Cache<String, Shop> shopCache;

    public static void main(String[] args) {
        JDBCStore jdbcStore = new JDBCStore(args);
    }

    public JDBCStore(String[] args) {
        cacheManager = createCacheManager();
        Configuration configuration = createConfiguration();
        personCache = createPersonCache(cacheManager, configuration);
//        personCache = createSimpleCache(cacheManager);
        shopCache = createShopCache(cacheManager, configuration);

        shopCache.put("1", Shop.builder().id(1L).cityId(2L).cityName("City").regionId(3L).regionName("Region").formatId(4L).formatName("Format").build());

        if (isNeedPut(args)) {
            putData();
        }

        runRead();
    }

    private boolean isNeedPut(String[] args) {
        return args.length > 0 && "P".equalsIgnoreCase(args[0]);
    }

    private void removeData() {
        AdvancedCache<String, Person> acache = personCache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS);
        acache.remove("key3");
    }

    private void runRead() {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.execute(this::read);
    }

    private void read() {
//        AdvancedCache<String, Person> aCache = personCache.getAdvancedCache().withFlags();
        Cache<String, Person> aCache = personCache.getAdvancedCache().withFlags(Flag.CACHE_MODE_LOCAL, Flag.SKIP_CACHE_LOAD);

        while (!Thread.currentThread().isInterrupted()) {
            aCache = personCache.getAdvancedCache().withFlags(Flag.SKIP_CACHE_LOAD);
            System.out.println("TIME =" + System.currentTimeMillis());
            CacheSet<Map.Entry<String, Person>> entries = aCache.entrySet();
            long s = 0;
            for (Map.Entry<String, Person> entry : entries) {
                s += entry.getValue().getItemsSum();
                System.out.println(entry.getValue());
            }
            System.out.println("CACHE SIZE =" + entries.size() + ", SUM = " + s);
            Person person1 = aCache.get("1");
            Person person1_2 = aCache.get("1");
            System.out.println("Obj equals " + (person1 == person1_2));

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.interrupted();
            }
        }
        cacheManager.stop();
    }

    private void putData() {
        AdvancedCache<String, Person> aCache = personCache.getAdvancedCache().withFlags(Flag.FORCE_SYNCHRONOUS);
        for (int i = 0; i < 10; i++) {
            Person p = new Person("Name_" + i, "Surnameeeeeeeeeeeeeeeeeeeeeee" + i);
            if (i % 2 == 0) {
                p.setValue((long) i);
            }
            List<ItemInterface> items = new ArrayList<>();
            for (int j = 0; j < 100; j++) {
                Item item = new Item("Item_" + j, (long) j);
                items.add(item);
            }
            p.setItems(items);
            aCache.put("" + i, p);
        }
    }

    private Cache<String, Person> createSimpleCache(DefaultCacheManager cacheManager) {
        GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
        ConfigurationBuilder builder = new ConfigurationBuilder();

        builder.clustering().cacheMode(CacheMode.LOCAL)
                .statistics().enabled(true);

        Cache<String, Person> result = cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("person", builder.build());

        result.addListener(new CacheListener(cacheManager));
        return result;
    }

    private DefaultCacheManager createCacheManager() {
        GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();

        global.cacheContainer().statistics(true)
                .serialization()
                .marshaller(new JavaSerializationMarshaller())
                .allowList()
                // add classes to whitelist (de)serialization
                .addRegexp("ru.crystals.infinispan.pojo.*")
                .addRegexp("ru.crystals.shop.*")
                .addClasses(ArrayList.class);

        global.jmx().enable()
                .transport()
                //.stack("DNS_PING")
                .stack("TCPPING")
                .defaultTransport()
                .clusterName("test-cluster")
                .addProperty("configurationFile", "tcp-nio-2.xml");

        GlobalConfiguration glCfg = global.build();

        return new DefaultCacheManager(glCfg);
    }

    private Cache<String, Person> createPersonCache(DefaultCacheManager cacheManager, Configuration configuration) {
        Cache<String, Person> result = cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("person", configuration);
        result.addListener(new CacheListener(cacheManager));
        return result;
    }

    private Cache<String, Shop> createShopCache(DefaultCacheManager cacheManager, Configuration configuration) {
        Cache<String, Shop> result = cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("shop", configuration);

        return result;
    }

    private Configuration createConfiguration() {
        ConfigurationBuilder builder = new ConfigurationBuilder();

        builder.clustering().cacheMode(CacheMode.REPL_SYNC).l1().lifespan(100000L, TimeUnit.DAYS).cleanupTaskFrequency(60, TimeUnit.SECONDS)
                // protobuf, need proto schema
//                .encoding().mediaType("application/x-protostream")
                // simple text, bad way
//                .encoding().mediaType("text/plain; charset=UTF-8")
                // json - need custom marshaller
//                .encoding().mediaType("application/json")
                // pure java serialization
//                .encoding().mediaType("application/x-java-serialized-object")
                .encoding().mediaType("application/x-java-object")
                .statistics().enabled(true)

                // persistence settings:
                .persistence()
                .passivation(false)
                .addStore(JdbcStringBasedStoreConfigurationBuilder.class)
                .dialect(DatabaseType.POSTGRES)
                // one store for all nodes
                .shared(true)
                // warming up on startup
                .preload(true)
                .table()
//                .dropOnExit(true)
                // auto create DB schema
                .createOnStart(true)
                .tableNamePrefix("cache_store")
                .idColumnName("id").idColumnType("TEXT")
                .dataColumnName("payload").dataColumnType("bytea")
                .timestampColumnName("ts").timestampColumnType("BIGINT")
                .segmentColumnName("segment").segmentColumnType("INT")
                .connectionPool()
                .connectionUrl("jdbc:postgresql://192.168.1.201:5432/infinispan")
                .username("postgres")
                .password("postgres")
                .driverClass("org.postgresql.Driver");
        return builder.build();
    }
}
