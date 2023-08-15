package ru.crystals.infinispan.pojo;

import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryActivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryInvalidated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryLoaded;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryVisited;
import org.infinispan.notifications.cachelistener.event.CacheEntryActivatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryInvalidatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryLoadedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryVisitedEvent;
import org.infinispan.notifications.cachemanagerlistener.annotation.CacheStarted;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStartedEvent;

@Listener(observation = Listener.Observation.POST)
public class CacheListener {

    private DefaultCacheManager cacheManager;

    public CacheListener(DefaultCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @CacheEntryLoaded
    public void loaded(CacheEntryLoadedEvent<String, Person> event) {
        Person person = event.getValue();
        System.out.println("@@@EVENT loaded" + event.getKey());
    }

    @CacheEntryInvalidated
    public void inv(CacheEntryInvalidatedEvent<String, Person> event) {
        System.out.println("@@@EVENT Invalidated");
    }

    @CacheEntryCreated
    public void created(CacheEntryCreatedEvent<String, Person> event) {
        System.out.println("@@@EVENT Created");
    }

    @CacheEntryVisited
    private void visit(CacheEntryVisitedEvent<String, Person> event) {
        System.out.println("@@@EVENT visit");
    }

    @CacheEntryActivated
    private void act(CacheEntryActivatedEvent<String, Person> event) {
        System.out.println("@@@EVENT activated");
    }

    @CacheStarted
    public void started(CacheStartedEvent event) {
        System.out.println("cache started " + event.getCacheName());
    }

}
