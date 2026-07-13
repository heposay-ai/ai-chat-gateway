package com.example.aichat.service;

import com.example.aichat.config.ModelProperties;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ChatModelFactory.class);

    private final ModelProperties properties;
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatLanguageModel> streamingCache = new ConcurrentHashMap<>();

    public ChatModelFactory(ModelProperties properties) {
        this.properties = properties;
    }

    public ChatLanguageModel getModel(String modelId) {
        return modelCache.computeIfAbsent(modelId, id -> {
            log.info("[模型工厂] 首次创建ChatModel: {}", id);
            return createModel(id);
        });
    }

    public StreamingChatLanguageModel getStreamingModel(String modelId) {
        return streamingCache.computeIfAbsent(modelId, id -> {
            log.info("[模型工厂] 首次创建StreamingModel: {}", id);
            return createStreamingModel(id);
        });
    }

    private ChatLanguageModel createModel(String modelId) {
        var config = getConfig(modelId);
        return switch (config.getProvider()) {
            case OPENAI -> OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();
            case DASHSCOPE -> QwenChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(config.getTemperature().floatValue())
                .maxTokens(config.getMaxTokens())
                .build();
            case CLAUDE -> AnthropicChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModelName())
                .maxTokens(config.getMaxTokens())
                .temperature(config.getTemperature())
                .build();
        };
    }

    private StreamingChatLanguageModel createStreamingModel(String modelId) {
        return createAdHocStreamingModel(modelId, null, null, null);
    }

    public StreamingChatLanguageModel createAdHocStreamingModel(String modelId, String baseUrl, String apiKey, String modelName) {
        var config = getConfig(modelId);
        String effectiveBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : config.getBaseUrl();
        String effectiveApiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : config.getApiKey();
        String effectiveModelName = (modelName != null && !modelName.isBlank()) ? modelName : config.getModelName();
        log.info("[模型工厂] 创建临时StreamingModel: modelId={}, baseUrl={}, modelName={}", modelId, effectiveBaseUrl, effectiveModelName);

        return switch (config.getProvider()) {
            case OPENAI -> OpenAiStreamingChatModel.builder()
                .apiKey(effectiveApiKey)
                .baseUrl(effectiveBaseUrl)
                .modelName(effectiveModelName)
                .build();
            case DASHSCOPE -> QwenStreamingChatModel.builder()
                .apiKey(effectiveApiKey)
                .modelName(effectiveModelName)
                .build();
            case CLAUDE -> AnthropicStreamingChatModel.builder()
                .apiKey(effectiveApiKey)
                .baseUrl(effectiveBaseUrl)
                .modelName(effectiveModelName)
                .maxTokens(config.getMaxTokens())
                .build();
        };
    }

    public List<ModelInfo> listModels() {
        return properties.getConfigs().entrySet().stream()
            .map(e -> new ModelInfo(e.getKey(), e.getValue().getProvider().name(),
                                    e.getValue().getModelName()))
            .toList();
    }

    private ModelProperties.ModelConfig getConfig(String modelId) {
        var config = properties.getConfigs().get(modelId);
        if (config == null) {
            log.error("[模型工厂] 未知模型: {}, 可用模型: {}", modelId, properties.getConfigs().keySet());
            throw new IllegalArgumentException("未知的模型: " + modelId
                + "，可用模型: " + properties.getConfigs().keySet());
        }
        log.debug("[模型工厂] 获取配置: modelId={}, provider={}, modelName={}", modelId, config.getProvider(), config.getModelName());
        return config;
    }

    public record ModelInfo(String id, String provider, String modelName) {}
}
