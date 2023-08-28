package ru.crystals.consul;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.codec.Charsets;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;

import java.io.IOException;
import java.util.UUID;

public class ConsulComponent {

    private static final String CONSUL_HOST = "CONSUL_HOST";
    private static final String CONSUL_PORT = "CONSUL_PORT";

    private static final Logger log = LoggerFactory.getLogger(ConsulComponent.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String applicationName;
    private final String instanceId;

    public ConsulComponent(String applicationName) {
        this.applicationName = applicationName;
        instanceId = applicationName + "-" + UUID.randomUUID();
    }

    public void registerService(int timeout, int port) throws IOException {

        try (CloseableHttpClient client = makeClient(timeout)) {
            HttpPut request = new HttpPut(makeConsulUrlString() + "/v1/agent/service/register?replace-existing-checks=true");
            request.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"));

            InetUtils inetUtils = new InetUtils(new InetUtilsProperties());
            String ipAddress = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
            RegisterPayload payload = makeRegisterPayload(applicationName, instanceId, ipAddress, port);
            String content = objectMapper.writeValueAsString(payload);
            System.out.println(content);
            request.setEntity(new StringEntity(content, Charsets.UTF_8));
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                log.info("Service registered in consul with instance id: {}", payload.getId());
            } else {
                log.error("Service register failed. Payload: {}, Code: {}, reason: {}",
                        content,
                        response.getStatusLine().getStatusCode(),
                        response.getStatusLine().getReasonPhrase());
                throw new RuntimeException(String.format("Service register failed. Payload: %s. Code: %s, reason: %s", content, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
            }
        }
    }

    public void unregisterService(int timeout) {
        try (CloseableHttpClient client = makeClient(timeout)) {
            HttpPut request = new HttpPut(makeConsulUrlString() + "/v1/agent/service/deregister/" + instanceId);
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == 200) {
                log.info("Service unregistered from consul");
            } else {
                log.error("Service unregister error. Code: {}, reason: {}",
                        response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
                throw new RuntimeException(String.format("Service register failed. Code: %s, reason: %s", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
            }
        } catch (Exception e) {
            log.error("Service unregister failed", e);
        }
    }

    private String makeConsulUrlString() {
        String host = System.getenv(CONSUL_HOST);
        String port = System.getenv(CONSUL_PORT);
        return "http://" + host + (port == null ? "" : ":" + port);
    }

    private CloseableHttpClient makeClient(int timeout) {
        RequestConfig.Builder configBuilder = RequestConfig.custom();
        configBuilder.setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout);

        return HttpClientBuilder.create().setDefaultRequestConfig(configBuilder.build()).build();
    }

    private RegisterPayload makeRegisterPayload(String applicationName, String instanceId, String ipAddress, int port) {
        RegisterPayload payload = new RegisterPayload();
        payload.setName(applicationName);
        payload.setId(instanceId);
        payload.setAddress(ipAddress);
        payload.setPort(port);


        Check check = new Check();

        // это всё надо в конфиг:
        check.setDeregisterCriticalServiceAfter("10s");
        check.setTcp(ipAddress+":"+port);
        check.setTimeout("10s");
        check.setInterval("10s");
        payload.setCheck(check);

        return payload;
    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    private static class RegisterPayload {
        @JsonProperty("ID")
        private String id;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("Address")
        private String address;
        @JsonProperty("Port")
        private int port;
        @JsonProperty("Tags")
        private String[] tags;
        @JsonProperty("Check")
        private Check check;
    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    private static class Check {
        @JsonProperty("DeregisterCriticalServiceAfter")
        private String deregisterCriticalServiceAfter;
        @JsonProperty("TCP")
        private String tcp;
        @JsonProperty("Interval")
        private String interval;
        @JsonProperty("Timeout")
        private String timeout;

        // Регистрируем сразу с Health Status = passing.
        // Иначе health check выполнится консулом только спустя несколько секунд и первый же DNS запрос не вернёт ничего.
        @JsonProperty("Status")
        private String status = "passing";
    }

}
