package ru.crystals.infinispan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Просто слушатель события о том, что приложение зарегистрировано, например в Consul
 */
@Component
public class RegistrationListener implements ApplicationListener<InstanceRegisteredEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationListener.class);

    @Override
    public void onApplicationEvent(InstanceRegisteredEvent event) {
        LOG.info("APPLICATION REGISTERED !!!");
    }

}
