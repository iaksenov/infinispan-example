package ru.crystals.infinispan;

import org.infinispan.conflict.EntryMergePolicy;
import org.infinispan.container.entries.CacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.crystals.example.Person;

import java.util.List;

public class PersonMergePolicy implements EntryMergePolicy<Long, Person> {

    private static final Logger LOG = LoggerFactory.getLogger(PersonMergePolicy.class);

    @Override
    public CacheEntry<Long, Person> merge(CacheEntry<Long, Person> preferredEntry, List<CacheEntry<Long, Person>> otherEntries) {
        CacheInfoController.personMergeCounter.incrementAndGet();
        LOG.info("######### Person merge entry: {}", preferredEntry);
        if (preferredEntry == null || preferredEntry.getValue() == null) {
            return getLatest(otherEntries);
        } else {
            CacheEntry<Long, Person> latest = getLatest(otherEntries);
            if (latest == null) {
                return preferredEntry;
            } else {
                return preferredEntry.getValue().getLastUpdate() > latest.getValue().getLastUpdate() ? preferredEntry : latest;
            }
        }
    }

    private CacheEntry<Long, Person> getLatest(List<CacheEntry<Long, Person>> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        CacheEntry<Long, Person> result = null;
        for (CacheEntry<Long, Person> entry : entries) {
            if (result == null) {
                result = entry;
            } else {
                if (entry.getValue() != null && entry.getValue().getLastUpdate() > result.getValue().getLastUpdate()) {
                    result = entry;
                }
            }
        }
        return result;
    }

}
