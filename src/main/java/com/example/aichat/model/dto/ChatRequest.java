package com.example.aichat.model.dto;

import java.util.Map;

public record ChatRequest(
    String message,
    String model,
    Long templateId,
    Map<String, String> variables,
    Long kbId
) {}
