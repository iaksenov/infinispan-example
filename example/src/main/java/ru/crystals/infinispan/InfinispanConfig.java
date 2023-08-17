package ru.crystals.infinispan;

import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.persistence.jdbc.common.DatabaseType;
import org.infinispan.persistence.jdbc.configuration.JdbcStringBasedStoreConfigurationBuilder;
import org.infinispan.spring.starter.embedded.InfinispanCacheConfigurer;
import org.infinispan.spring.starter.embedded.InfinispanGlobalConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfinispanConfig {

    public static final String CLUSTER_NAME = "infinispan-example-cluster";

    public static final String CACHE_CONFIGURATION_NAME = "ReplicatedStored";

    @Bean
    public InfinispanGlobalConfigurer globalConfig() {
        GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();

        global.cacheContainer()
                .statistics(true)
                .metrics().gauges(true).histograms(true)
                .serialization()

                //
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

                // ПРОБЛЕМА: SpringBoot зарегистрирует себя в Consul после старта приложения,
                // а тут будет сразу попытка найти по DNS все узлы.
                // Т.е. если две ноды одновременно стартанут, то каждая решит, что она одна и назначит себя координатором.
                // Спустя несколько секунд, они найдут друг друга, но данные отправленные нодой репликацией не достигнут второй ноды,
                // хотя будут сохранены в общее хранилище.
                .stack("DNS_PING")

                // обнаружение узлов будет выполняться TCP запросами
                // initial_hosts - список хостов и портов, среди которых будут искаться ноды
                // .stack("TCPPING")
                .defaultTransport()
                .initialClusterSize(2)
                .addProperty("configurationFile", "tcp-nio-2.xml");


        return global::build;
    }

    @Bean()
    public InfinispanCacheConfigurer storedReplicatedCacheConfig() {
        ConfigurationBuilder builder = new ConfigurationBuilder();

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
                .encoding().mediaType("application/x-java-object")
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

                // прогрев кэша на страте нам нужен
                .preload(true)
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
                .connectionPool()
                .connectionUrl("jdbc:postgresql://192.168.1.201:5432/infinispan")
                .username("postgres")
                .password("postgres")
                .driverClass("org.postgresql.Driver");

        return manager -> {
            manager.defineConfiguration(CACHE_CONFIGURATION_NAME, builder.build());
        };
    }

}
