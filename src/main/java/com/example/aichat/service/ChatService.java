package com.example.aichat.service;

import com.example.aichat.context.RequestContext;
import com.example.aichat.model.dto.ChatRequest;
import com.example.aichat.model.dto.ChatResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatModelFactory modelFactory;
    private final ConversationManager conversationManager;
    private final UsageService usageService;
    private final LongTermMemoryService memoryService;
    private final KbContextProvider kbContextProvider;

    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final int KB_MAX_RESULTS = 8;

    public ChatService(ChatModelFactory modelFactory, ConversationManager conversationManager,
                       UsageService usageService, LongTermMemoryService memoryService,
                       KbContextProvider kbContextProvider) {
        this.modelFactory = modelFactory;
        this.conversationManager = conversationManager;
        this.usageService = usageService;
        this.memoryService = memoryService;
        this.kbContextProvider = kbContextProvider;
    }

    /**
     * 构建知识库上下文 system message（如果 kbId 不为空）
     */
    private String buildKbContext(String userMessage, Long kbId, Long tenantId) {
        if (kbId == null) return "";
        return kbContextProvider.buildContextByKbId(userMessage, kbId, tenantId, KB_MAX_RESULTS);
    }

    private SystemMessage buildKbSystemMessage(String kbContext) {
        if (kbContext.isBlank()) return null;
        return SystemMessage.from("以下是知识库中的参考资料：\n\n" + kbContext
                + "\n---\n请基于以上参考资料回答用户问题。如果资料中没有相关信息，请明确说明。");
    }

    /**
     * Day1 - 单轮聊天（非流式）
     */
    public ChatResponse chat(ChatRequest request) {
        String modelId = request.model() != null ? request.model() : DEFAULT_MODEL;
        log.info("[单轮] 调用模型 modelId={}", modelId);
        long start = System.currentTimeMillis();

        String keyId = RequestContext.get("keyId");
        Long tenantId = RequestContext.get("tenantId");

        try {
            ChatLanguageModel model = modelFactory.getModel(modelId);
            String userId = memoryService.resolveUserId(tenantId, keyId);
            String memoryPrompt = memoryService.buildMemoryPrompt(userId, request.message());
            String kbContext = buildKbContext(request.message(), request.kbId(), tenantId);
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
            if (!memoryPrompt.isBlank()) {
                messages.add(SystemMessage.from(memoryPrompt));
            }
            SystemMessage kbMsg = buildKbSystemMessage(kbContext);
            if (kbMsg != null) {
                messages.add(kbMsg);
            }
            messages.add(UserMessage.from(request.message()));
            Response<AiMessage> response = model.generate(messages);
            String reply = response.content().text();
            int duration = (int) (System.currentTimeMillis() - start);
            log.info("[单轮] 模型返回成功, modelId={}, 耗时={}ms, 回复内容={}", modelId, duration, reply);

            // 记录调用日志
            int estimatedTokens = (request.message().length() + reply.length()) / 2 + 20;
            usageService.logCall(keyId, tenantId, modelId, estimatedTokens / 3, estimatedTokens * 2 / 3,
                duration, "success", null);
            memoryService.addConversation(userId, request.message(), reply);

            return new ChatResponse(reply, modelId, estimatedTokens);
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            log.error("[单轮] 模型调用失败, modelId={}, 耗时={}ms", modelId, duration, e);
            usageService.logCall(keyId, tenantId, modelId, 0, 0, duration, "error", e.getMessage());
            throw e;
        }
    }

    public SseEmitter streamChat(String message, String modelId, Long kbId, String baseUrl, String apiKey, String modelName) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        log.info("[流式] 调用模型 modelId={}, kbId={}, 自定义baseUrl={}, 自定义modelName={}", modelId, kbId, baseUrl != null && !baseUrl.isBlank(), modelName);
        long start = System.currentTimeMillis();

        StreamingChatLanguageModel model;
        if ((baseUrl != null && !baseUrl.isBlank()) || (apiKey != null && !apiKey.isBlank()) || (modelName != null && !modelName.isBlank())) {
            model = modelFactory.createAdHocStreamingModel(modelId, baseUrl, apiKey, modelName);
        } else {
            model = modelFactory.getStreamingModel(modelId);
        }
        SseEmitter emitter = new SseEmitter(300_000L);

        final String finalModelId = modelId;
        final String keyId = RequestContext.get("keyId");
        final Long tenantId = RequestContext.get("tenantId");
        final String userId = memoryService.resolveUserId(tenantId, keyId);
        String memoryPrompt = memoryService.buildMemoryPrompt(userId, message);
        String kbContext = buildKbContext(message, kbId, tenantId);
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        if (!memoryPrompt.isBlank()) {
            messages.add(SystemMessage.from(memoryPrompt));
        }
        SystemMessage kbMsg = buildKbSystemMessage(kbContext);
        if (kbMsg != null) {
            messages.add(kbMsg);
        }
        messages.add(UserMessage.from(message));

        model.generate(messages, new StreamingResponseHandler<AiMessage>() {
            private int tokenCount = 0;
            private final StringBuilder fullAnswer = new StringBuilder();

            @Override
            public void onNext(String token) {
                tokenCount++;
                fullAnswer.append(token);
                try {
                    log.debug("[流式] 调用模型内容: [{}]", token);
                    emitter.send(SseEmitter.event()
                        .data(Map.of("content", token)));
                } catch (IOException e) {
                    log.warn("[流式] SSE发送失败, modelId={}, 已发送token数={}", finalModelId, tokenCount, e);
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                int duration = (int) (System.currentTimeMillis() - start);
                log.info("[流式] 完成, modelId={}, token数={}, 耗时={}ms", finalModelId, tokenCount, duration);
                usageService.logCall(keyId, tenantId, finalModelId, tokenCount / 3, tokenCount * 2 / 3,
                    duration, "success", null);
                memoryService.addConversation(userId, message, fullAnswer.toString());
                try {
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(Throwable error) {
                int duration = (int) (System.currentTimeMillis() - start);
                log.error("[流式] 模型调用失败, modelId={}, token数={}, 耗时={}ms", finalModelId, tokenCount, duration, error);
                usageService.logCall(keyId, tenantId, finalModelId, 0, 0, duration, "error", error.getMessage());
                try {
                    String errMsg = error.getMessage() != null && !error.getMessage().isBlank()
                        ? error.getMessage() : "模型调用失败，请检查 API Key 和 Base URL 配置";
                    emitter.send(SseEmitter.event().data(Map.of("error", errMsg)));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }

    /**
     * Day6 - 带上下文的多轮聊天
     */
    public ChatResponse chatWithContext(String sessionId, ChatRequest request) {
        String modelId = request.model() != null ? request.model() : DEFAULT_MODEL;
        log.info("[多轮] sessionId={}, modelId={}", sessionId, modelId);

        // 保存用户消息
        conversationManager.addMessage(sessionId, "user", request.message());

        // 获取历史消息（自动裁剪）
        List<Map<String, String>> history = conversationManager.getMessages(sessionId, 4000);
        log.debug("[多轮] sessionId={}, 历史消息数={}", sessionId, history.size());

        // 构建 LangChain4j 消息列表
        List<dev.langchain4j.data.message.ChatMessage> messages = history.stream()
            .map(m -> (dev.langchain4j.data.message.ChatMessage) switch (m.get("role")) {
                case "system" -> SystemMessage.from(m.get("content"));
                case "user" -> UserMessage.from(m.get("content"));
                case "assistant" -> AiMessage.from(m.get("content"));
                default -> throw new IllegalArgumentException("Unknown role: " + m.get("role"));
            })
            .toList();

        // 调用模型
        String keyId = RequestContext.get("keyId");
        Long tenantId = RequestContext.get("tenantId");
        long start = System.currentTimeMillis();

        try {
            ChatLanguageModel model = modelFactory.getModel(modelId);
            String userId = memoryService.resolveUserId(tenantId, keyId);
            String memoryPrompt = memoryService.buildMemoryPrompt(userId, request.message());
            String kbContext = buildKbContext(request.message(), request.kbId(), tenantId);
            List<dev.langchain4j.data.message.ChatMessage> finalMessages = new ArrayList<>();
            if (!memoryPrompt.isBlank()) {
                finalMessages.add(SystemMessage.from(memoryPrompt));
            }
            SystemMessage kbMsg = buildKbSystemMessage(kbContext);
            if (kbMsg != null) {
                finalMessages.add(kbMsg);
            }
            finalMessages.addAll(messages);
            Response<AiMessage> response = model.generate(finalMessages);
            String reply = response.content().text();
            int tokens = response.tokenUsage() != null ? response.tokenUsage().totalTokenCount() : 0;
            int promptTokens = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0;
            int completionTokens = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0;
            int duration = (int) (System.currentTimeMillis() - start);
            log.info("[多轮] 模型返回成功, sessionId={}, modelId={}, 耗时={}ms, tokens={}, 回复长度={}",
                sessionId, modelId, duration, tokens, reply.length());

            // 记录调用日志
            usageService.logCall(keyId, tenantId, modelId, promptTokens, completionTokens,
                duration, "success", null);
            memoryService.addConversation(userId, request.message(), reply);

            // 保存 AI 回复
            conversationManager.addMessage(sessionId, "assistant", reply);

            return new ChatResponse(reply, modelId, tokens);
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            log.error("[多轮] 模型调用失败, sessionId={}, modelId={}, 耗时={}ms", sessionId, modelId, duration, e);
            usageService.logCall(keyId, tenantId, modelId, 0, 0, duration, "error", e.getMessage());
            throw e;
        }
    }
}
