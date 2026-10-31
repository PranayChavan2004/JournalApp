package com.projectbyPranayChavan.JournalApp.dto;

public record WeatherView(boolean available, String city, Integer temperature, Integer feelsLike, String description) {

    public static WeatherView unavailable(String city) {
        return new WeatherView(false, city, null, null, null);
    }
}
