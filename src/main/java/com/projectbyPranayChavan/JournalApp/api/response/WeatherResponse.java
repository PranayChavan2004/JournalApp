package com.projectbyPranayChavan.JournalApp.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherResponse {
    private Current current;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Current {
        private int temperature;
        @JsonProperty("weather_descriptions")
        private List<String> weatherDescriptions;
        private int feelslike;
    }
}
