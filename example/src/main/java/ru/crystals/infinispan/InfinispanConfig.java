package ru.crystals.infinispan;

import com.zaxxer.hikari.HikariDataSource;
import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.partitionhandling.PartitionHandling;
import org.infinispan.persistence.jdbc.common.DatabaseType;
import org.infinispan.persistence.jdbc.configuration.JdbcStringBasedStoreConfigurationBuilder;
import org.infinispan.spring.starter.embedded.InfinispanGlobalConfigurer;
import org.infinispan.transaction.TransactionMode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.crystals.consul.ConsulComponent;
import ru.crystals.example.Person;
import ru.crystals.shop.Shop;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.Hashtable;

import static ru.crystals.infinispan.Consts.PERSON_CACHE;
import static ru.crystals.infinispan.Consts.SHOP_CACHE;

@Configuration
@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
public class InfinispanConfig {

    public static final String CLUSTER_NAME = System.getenv("INFINISPAN_CLUSTER_NAME");

    private static final int CONSUL_TIMEOUT = 5000;
    private static final String JNDI_DATA_SOURCE = "java:comp/env/jdbc/hikari-infinispan";

    @Bean
    public InfinispanGlobalConfigurer globalConfig() throws IOException {

        ConsulComponent consulComponent = new ConsulComponent(CLUSTER_NAME);
        String infinispan_tcp_port = System.getenv(Consts.INFINISPAN_TCP_PORT);
        int port = Integer.parseInt(infinispan_tcp_port);

        // Регистрируем infinispan кластер с его именем до того, как он запустится и сделает DNS запрос в Consul.
        // Это необходимо, что два одновременно стартуюших сервиса сразу увидели друг-друга.
        consulComponent.registerService(CONSUL_TIMEOUT, port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> consulComponent.unregisterService(CONSUL_TIMEOUT)));

        GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();

        global.cacheContainer()
                .statistics(true)
                .metrics().gauges(true).histograms(true)
                .serialization()
                .marshaller(new JavaSerializationMarshaller())
                .allowList()
                // Добавление классов в whitelist (де)сериализации является обязательным
                .addRegexp("ru.crystals.example.*")
                .addRegexp("ru.crystals.shop.*");

        global.jmx().enable()
                .transport()
                .clusterName(CLUSTER_NAME)

                // https://infinispan.org/docs/stable/titles/embedding/embedding.html#cluster-discovery-protocols_cluster-transport

                // обнаружение узлов будет выполняться DNS запросами
                // см. конфиг tcp-nio-2.xml
                // dns_address - DNS сервер, например Consul
                // dns_query - запрашиваемое имя, например "infinispan-example.service.consul"
                // dns_record_type="SRV"

                // ПРОБЛЕМА: SpringBoot зарегистрирует себя в Consul после старта приложения, а тут будет сразу попытка найти по DNS все узлы.
                // Т.е. если две ноды одновременно стартанут, то каждая решит, что она одна и назначит себя координатором.
                // Поэтому регистрация в Consul выполняется вручную до запуска Infinispan.
                // Регистрируемое имя соответствует тому, которое указано в конфиге для DNS_PNG.
                // Оно должно соответствовать dns_query в конифге для DNS_PING.
                .stack("DNS_PING")

                // обнаружение узлов будет выполняться TCP запросами
                // initial_hosts - список хостов и портов, среди которых будут искаться ноды
                // .stack("TCPPING")
                .defaultTransport()
                .initialClusterSize(1)
                .addProperty("configurationFile", "tcp-nio-2.xml");

        return global::build;
    }

    /**
     * Run java with :
     * -Djava.naming.factory.initial=org.apache.naming.java.javaURLContextFactory
     *
     * @return
     * @throws NamingException
     */
    private InitialContext getInitialContext() throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.naming.java.javaURLContextFactory");
        env.put(Context.URL_PKG_PREFIXES, "org.apache.naming");
        return new InitialContext(env);
    }

    @Bean
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource(@Qualifier("dataSourceProperties") DataSourceProperties dataSourceProperties) throws NamingException {
        HikariDataSource hds = dataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        InitialContext ic = getInitialContext();
        ic.createSubcontext("java:comp")
                .createSubcontext("env")
                .createSubcontext("jdbc");
        // Регистрируем ds по JNDI имени
        ic.bind(JNDI_DATA_SOURCE, hds);
        ic.close();
        return hds;
    }

    @Bean("personCache")
    public Cache<Long, Person> getPersonCache(EmbeddedCacheManager cacheManager, ConfigurationBuilder builder) {
        // У этого кэша будут резолвятся конфликты после сплит брейна

        builder.clustering()
                .partitionHandling()
                .whenSplit(PartitionHandling.ALLOW_READ_WRITES)
                .mergePolicy(new PersonMergePolicy());
//                .mergePolicy(MergePolicy.PREFERRED_ALWAYS)

        return cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache(PERSON_CACHE, builder.build());
    }

    @Bean("shopCache")
    public Cache<String, Shop> getShopCache(EmbeddedCacheManager cacheManager, ConfigurationBuilder builder) {
        // А у этого кэша не будут резолвятся конфликты

//        builder.clustering()
//                .partitionHandling()
//                .whenSplit(PartitionHandling.ALLOW_READ_WRITES)
//                .mergePolicy();

        return cacheManager.administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache(SHOP_CACHE, builder.build());
    }

    /**
     * Конфиг, который будет использоваться по-умолчанию.
     *
     * @return конфиг
     */
    @Bean
    public ConfigurationBuilder storedReplicatedCacheConfig(DataSource dataSource)  {
        ConfigurationBuilder builder = new ConfigurationBuilder();

        builder.transaction().transactionMode(TransactionMode.TRANSACTIONAL);

        // Режим работы кэша - синхронная репликация, т.е. все ноды имеют полную копию кэша.
        // При изменениях, координатор кластера синхронно отправляет всем участникам сообщения.
        builder.clustering().cacheMode(CacheMode.REPL_SYNC)

                // protobuf - нуждается в proto schema, в котором полям присваиваются их порядковые номера
                // .encoding().mediaType("application/x-protostream")

                // simple text - могут возникнуть проблемы при сериализации в строку
                // .encoding().mediaType("text/plain; charset=UTF-8")

                // json - из коробки не поддерживается, но можно создать свой
                // .encoding().mediaType("application/json")

                // обычная java serialization
                // .encoding().mediaType("application/x-java-serialized-object")

                // хранение java объектов в jvm heap без сериализации
                // Это больше всего подходит под задачу
                .encoding().mediaType(MediaType.APPLICATION_OBJECT_TYPE)
                .statistics().enabled(true)


                // кэш будет персистентным
                .persistence()

                // в режиме passivation данные, которые не влезают в память или редко используются будут сброшены в store,
                // нам такое поведение не нужно
                .passivation(false)

                .addStore(JdbcStringBasedStoreConfigurationBuilder.class)

                .dialect(DatabaseType.POSTGRES)

                // один store на всех, без сегментации, то что надо
                .shared(true)

                // нам нужен прогрев кэша на страте
                .preload(true)

                // далее идёт описание автоматически создаваемой схемы БД
                .table()

                // дропать схему на выходе нам не надо
                // .dropOnExit(true)

                // авто создание схемы на старте
                .createOnStart(true)
                .tableNamePrefix("cache_store")
                .idColumnName("id").idColumnType("TEXT")
                .dataColumnName("payload").dataColumnType("bytea")
                .timestampColumnName("ts").timestampColumnType("BIGINT")
                .segmentColumnName("segment").segmentColumnType("INT")

                // Data source получаемый по JNDI name:
                .dataSource()
                .jndiUrl(JNDI_DATA_SOURCE);

                // С пулом, который идёт в комплекте (io.agroal) есть проблема восстановления коннектов при обрыве вязи с БД.
                // Вместо него мы будем использовать простой пересоздаваемый коннект, а снаружи будет pg_bouncer.
//                .simpleConnection()
//                .connectionUrl("jdbc:postgresql://192.168.1.28:5432/infinispan?reWriteBatchedInserts=true&ApplicationName=omni")
//                .username("postgres")
//                .password("Set324@_p0stgres012!")
//                .driverClass("org.postgresql.Driver");

        // это шаблон
        builder.template(true);

        return builder;
    }

}
