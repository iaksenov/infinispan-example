FROM bellsoft/liberica-runtime-container:jre-17-slim-musl

ENV WORK_DIR "/app"
ENV JAVA_OPTS ""
ENV JAVA_ARGS ""
WORKDIR $WORK_DIR

ADD ./build/libs/app.jar /app/app.jar

ENTRYPOINT [ "sh", "-c", "java -jar ${JAVA_OPTS}  /app/app.jar ${JAVA_ARGS}"]