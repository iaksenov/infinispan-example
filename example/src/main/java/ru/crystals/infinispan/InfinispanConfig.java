package ru.crystals.infinispan;

import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.persistence.jdbc.common.DatabaseType;
import org.infinispan.persistence.jdbc.configuration.JdbcStringBasedStoreConfigurationBuilder;
import org.infinispan.spring.starter.embedded.InfinispanGlobalConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.crystals.consul.ConsulComponent;

import java.io.IOException;

@Configuration
public class InfinispanConfig {

    public static final String CLUSTER_NAME = System.getenv("INFINISPAN_CLUSTER_NAME");

    private static final int CONSUL_TIMEOUT = 5000;

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
                .initialClusterSize(1)
                .addProperty("configurationFile", "tcp-nio-2.xml");

        return global::build;
    }

    /**
     * Конфиг, который будет использоваться по-умолчанию.
     * В качестве имя конфига надо указать одно из имён кэшей, для остальных он будет использоваться как шаблон.
     * Если указать другое имя, то в БД будут созданы и не будут использоваться лишние таблицы с этим именем.
     *
     * @return конфиг
     */
    @Bean(Consts.PERSON_CACHE)
    public org.infinispan.configuration.cache.Configuration storedReplicatedCacheConfig()  {
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

                // С пулом, который идёт в комплекте (io.agroal) есть проблема восстановления коннектов при обрыве вязи с БД.
                // Вместо него мы будем использовать простой пересоздаваемый коннект, а снаружи будет pg_bouncer.
                //  .connectionPool()
                .simpleConnection()

                .connectionUrl("jdbc:postgresql://192.168.1.201:5432/infinispan?reWriteBatchedInserts=true&ApplicationName=omni")
                .username("postgres")
                .password("postgres")
                .driverClass("org.postgresql.Driver");

        return builder.build();
    }

}
