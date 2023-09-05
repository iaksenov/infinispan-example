FROM bellsoft/liberica-runtime-container:jre-17-slim-musl

ENV WORK_DIR "/app"
ENV JAVA_OPTS ""
ENV JAVA_ARGS ""
WORKDIR $WORK_DIR

ENV CONSUL_HOST=192.168.1.201
ENV CONSUL_PORT=8500
ENV HOST_ADDRESS=192.168.1.201
ENV CONSUL_DNS_PORT=8600
ENV INFINISPAN_CLUSTER_NAME=infinispan-set-omni
ENV INFINISPAN_TCP_PORT=7800
ENV PUT_ENABLE=true

ADD ./build/libs/app.jar /app/app.jar

ENTRYPOINT [ "sh", "-c", "java -jar ${JAVA_OPTS}  /app/app.jar ${JAVA_ARGS}"]