# Пример конфигурации и работы с Infinispan в SpringBoot

Переменные окружения:

CONSUL_HOST=192.168.1.201

CONSUL_PORT=8500

CONSUL_DNS_PORT=8600

Имя кластера, которое будет в т.ч. регистрироваться в Consul<br/>
INFINISPAN_CLUSTER_NAME=infinispan-set-omni

INFINISPAN_TCP_PORT=7800

IP адрес, на котором будет слушаться порт INFINISPAN_TCP_PORT. Полезно, когда несколько сетевых интерфейсов. (см. tcp-nio-2.xml)<br/>  
HOST_ADDRESS=192.168.1.201

Записывать ли новые значения в кэш при каждой итерации таймера<br/>
PUT_ENABLE=true/false