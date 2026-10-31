package com.projectbyPranayChavan.JournalApp.service;

import com.projectbyPranayChavan.JournalApp.api.response.WeatherResponse;
import com.projectbyPranayChavan.JournalApp.cache.AppCache;
import com.projectbyPranayChavan.JournalApp.constants.Placeholders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class WeatherService {

    private final String apiKey;
    private final String defaultUrl;
    private final RestTemplate restTemplate;
    private final AppCache appCache;
    private final RedisService redisService;

    public WeatherService(@Value("${weather.api.key:}") String apiKey,
                          @Value("${weather.api.url}") String defaultUrl,
                          RestTemplate restTemplate,
                          AppCache appCache,
                          RedisService redisService) {
        this.apiKey = apiKey;
        this.defaultUrl = defaultUrl;
        this.restTemplate = restTemplate;
        this.appCache = appCache;
        this.redisService = redisService;
    }

    /** Empty when weather is not configured or the provider is unreachable: the UI just hides it. */
    public Optional<WeatherResponse> getWeather(String city) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String cacheKey = "weather_of_" + city.toLowerCase(Locale.ROOT);
        WeatherResponse cached = redisService.get(cacheKey, WeatherResponse.class);
        if (cached != null && cached.getCurrent() != null) {
            return Optional.of(cached);
        }

        String template = appCache.get(AppCache.keys.WEATHER_API);
        if (template == null || template.isBlank()) {
            template = defaultUrl;
        }

        String url = template
                .replace(Placeholders.CITY, URLEncoder.encode(city, StandardCharsets.UTF_8))
                .replace(Placeholders.API_KEY, URLEncoder.encode(apiKey, StandardCharsets.UTF_8));

        try {
            WeatherResponse body = restTemplate.getForObject(URI.create(url), WeatherResponse.class);
            if (body != null && body.getCurrent() != null) {
                redisService.set(cacheKey, body, 300L);
                return Optional.of(body);
            }
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Weather lookup failed: {}", e.getClass().getSimpleName());
        }
        return Optional.empty();
    }
}
