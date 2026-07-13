package com.example.aichat.controller;

import com.example.aichat.model.dto.ChatRequest;
import com.example.aichat.model.dto.ChatResponse;
import com.example.aichat.model.entity.ChatSession;
import com.example.aichat.repository.ChatSessionRepository;
import com.example.aichat.service.ChatModelFactory;
import com.example.aichat.service.ChatService;
import com.example.aichat.service.ConversationManager;
import com.example.aichat.service.PromptTemplateService;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ChatModelFactory modelFactory;
    private final ConversationManager conversationManager;
    private final ChatSessionRepository sessionRepository;
    private final PromptTemplateService promptTemplateService;

    public ChatController(ChatService chatService,
                         ChatModelFactory modelFactory,
                         ConversationManager conversationManager,
                         ChatSessionRepository sessionRepository,
                         PromptTemplateService promptTemplateService) {
        this.chatService = chatService;
        this.modelFactory = modelFactory;
        this.conversationManager = conversationManager;
        this.sessionRepository = sessionRepository;
        this.promptTemplateService = promptTemplateService;
    }

    // === Day1: 单轮聊天 ===

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        ChatRequest finalRequest = applyTemplate(request);
        log.info("[单轮聊天] model={}, message长度={}, templateId={}", finalRequest.model(),
            finalRequest.message() != null ? finalRequest.message().length() : 0, request.templateId());
        long start = System.currentTimeMillis();
        ChatResponse resp = chatService.chat(finalRequest);
        log.info("[单轮聊天] 完成, 耗时={}ms, model={}", System.currentTimeMillis() - start, resp.model());
        return resp;
    }

    // === Day2: 流式聊天 ===

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "deepseek-chat") String model,
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) String baseUrl,
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String modelName) {
        log.info("[流式聊天] model={}, message长度={}, kbId={}, 自定义baseUrl={}, 自定义modelName={}", model, message != null ? message.length() : 0, kbId, baseUrl != null && !baseUrl.isBlank(), modelName);
        return chatService.streamChat(message, model, kbId, baseUrl, apiKey, modelName);
    }

    // === Day3-4: 模型列表 ===

    @GetMapping("/models")
    public List<ChatModelFactory.ModelInfo> listModels() {
        log.debug("[模型列表] 查询可用模型");
        return modelFactory.listModels();
    }

    // === Day6: 多轮对话 ===

    @PostMapping("/sessions")
    public Map<String, String> createSession(
            @RequestParam(required = false, defaultValue = "deepseek-chat") String model,
            @RequestParam(required = false) String systemPrompt) {
        String sessionId = UUID.randomUUID().toString();
        log.info("[创建会话] sessionId={}, model={}, 有systemPrompt={}", sessionId, model, systemPrompt != null && !systemPrompt.isBlank());

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setTenantId(1L); // TODO: 从认证上下文获取
        session.setModel(model);
        session.setSystemPrompt(systemPrompt);
        sessionRepository.save(session);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            conversationManager.addMessage(sessionId, "system", systemPrompt);
        }

        return Map.of("sessionId", sessionId);
    }

    @PostMapping("/sessions/{sessionId}/chat")
    public ChatResponse chatWithContext(
            @PathVariable String sessionId,
            @RequestBody ChatRequest request) {
        ChatRequest finalRequest = applyTemplate(request);
        log.info("[多轮聊天] sessionId={}, model={}, message长度={}", sessionId, finalRequest.model(),
            finalRequest.message() != null ? finalRequest.message().length() : 0);
        long start = System.currentTimeMillis();
        ChatResponse resp = chatService.chatWithContext(sessionId, finalRequest);
        log.info("[多轮聊天] 完成, sessionId={}, 耗时={}ms, tokens={}", sessionId, System.currentTimeMillis() - start, resp.tokenUsed());
        return resp;
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<Map<String, String>> getMessages(@PathVariable String sessionId) {
        log.debug("[查询历史] sessionId={}", sessionId);
        return conversationManager.getMessages(sessionId, Integer.MAX_VALUE);
    }

    /**
     * 如果请求中指定了模板，渲染模板替换 message
     */
    private ChatRequest applyTemplate(ChatRequest request) {
        if (request.templateId() == null) return request;
        Long tenantId = com.example.aichat.context.RequestContext.get("tenantId");
        String rendered = promptTemplateService.render(request.templateId(), request.variables(),
                request.message(), tenantId);
        return new ChatRequest(rendered, request.model(), null, null, request.kbId());
    }
}
