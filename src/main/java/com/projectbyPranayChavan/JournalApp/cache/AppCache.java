package com.projectbyPranayChavan.JournalApp.cache;

import com.projectbyPranayChavan.JournalApp.entities.ConfigJournalAppEntity;
import com.projectbyPranayChavan.JournalApp.repository.ConfigJournalAppRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** rows of the config_journal_app table in memory; refreshed every 10 minutes. */
@Component
public class AppCache {

    public enum keys {
        WEATHER_API
    }

    private final ConfigJournalAppRepository configJournalAppRepository;
    private volatile Map<String, String> appCache = Collections.emptyMap();

    public AppCache(ConfigJournalAppRepository configJournalAppRepository) {
        this.configJournalAppRepository = configJournalAppRepository;
    }

    @PostConstruct
    public void init() {
        Map<String, String> fresh = new HashMap<>();
        List<ConfigJournalAppEntity> all = configJournalAppRepository.findAll();
        for (ConfigJournalAppEntity entity : all) {
            fresh.put(entity.getKey(), entity.getValue());
        }
        appCache = Collections.unmodifiableMap(fresh);
    }

    /** Returns the configured value, or null if there is no such row. */
    public String get(keys key) {
        return appCache.get(key.name());
    }
}
