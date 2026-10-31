package com.projectbyPranayChavan.JournalApp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Uniform error body: {"status":400,"message":"...","errors":{"field":"reason"}} */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String message, Map<String, String> errors) {
}
