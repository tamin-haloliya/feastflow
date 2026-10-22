package com.feastflow.exception;

import lombok.Builder;
import lombok.Data;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
public class ErrorResponse {

    private URI type;
    private String title;
    private int status;
    private String detail;
    private URI instance;
    private Instant timestamp;

    @Builder.Default
    private Map<String, String> errors = new LinkedHashMap<>();
}