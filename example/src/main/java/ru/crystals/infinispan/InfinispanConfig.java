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
import ru.crystals.example.Person;

import java.util.ArrayList;

@Configuration
public class InfinispanConfig {

    public static final String CLUSTER_NAME = "infinispan-example-cluster";

    @Bean
    public InfinispanGlobalConfigurer globalConfig() {
        GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();

        global.cacheContainer()
                .statistics(true)
                .metrics().gauges(true).histograms(true)
                .serialization()
                .marshaller(new JavaSerializationMarshaller())
                .allowList()
                // add classes to whitelist (de)serialization
                .addRegexp("ru.crystals.example.*")
                .addRegexp("ru.crystals.shop.*")
                .addClasses(Person.class)
                .addClasses(ArrayList.class);

        global
                .jmx().enable()
                .transport()
                .clusterName(CLUSTER_NAME)
                //.stack("DNS_PING")
                .stack("TCPPING")
                .defaultTransport()
                .addProperty("configurationFile", "tcp-nio-2.xml");

        return global::build;
    }

    @Bean()
    public InfinispanCacheConfigurer storedReplicatedCacheConfig() {
        ConfigurationBuilder builder = new ConfigurationBuilder();

        builder.clustering().cacheMode(CacheMode.REPL_SYNC)
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

        return manager -> {
            manager.defineConfiguration("person", builder.build());
        };
    }

}
