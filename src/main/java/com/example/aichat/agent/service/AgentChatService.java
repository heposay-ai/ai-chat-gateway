package com.example.aichat.agent.service;

import com.example.aichat.agent.model.Agent;
import com.example.aichat.agent.model.AgentChatMessage;
import com.example.aichat.agent.model.AgentChatSession;
import com.example.aichat.agent.repository.AgentChatMessageRepository;
import com.example.aichat.agent.repository.AgentChatSessionRepository;
import com.example.aichat.agent.repository.AgentRepository;
import com.example.aichat.service.ChatModelFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);

    private final AgentRepository agentRepo;
    private final AgentChatSessionRepository sessionRepo;
    private final AgentChatMessageRepository messageRepo;
    private final ChatModelFactory modelFactory;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Cache AiService instances per agent to avoid rebuilding
    private final Map<String, AgentAssistant> assistantCache = new ConcurrentHashMap<>();

    interface AgentAssistant {
        TokenStream chat(@MemoryId String sessionId,
                         @dev.langchain4j.service.UserMessage String message);
    }

    public AgentChatService(AgentRepository agentRepo,
                            AgentChatSessionRepository sessionRepo,
                            AgentChatMessageRepository messageRepo,
                            ChatModelFactory modelFactory,
                            ToolRegistry toolRegistry) {
        this.agentRepo = agentRepo;
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.modelFactory = modelFactory;
        this.toolRegistry = toolRegistry;
    }

    private AgentAssistant buildAssistant(Agent agent) {
        String cacheKey = agent.getId() + "_" + agent.getModelId() + "_" + agent.getToolsConfig();
        return assistantCache.computeIfAbsent(cacheKey, k -> {
            StreamingChatLanguageModel model = modelFactory.getStreamingModel(agent.getModelId());
            List<Object> tools = toolRegistry.getToolsForAgent(agent);

            var builder = AiServices.builder(AgentAssistant.class)
                    .streamingChatLanguageModel(model)
                    .chatMemoryProvider(memId -> MessageWindowChatMemory.builder()
                            .maxMessages(20)
                            .build())
                    .systemMessageProvider(memId -> agent.getSystemPrompt());

            if (!tools.isEmpty()) {
                builder.tools(tools);
            }

            log.info("[AgentChat] 构建 AiService: agent={}, model={}, tools={}",
                    agent.getName(), agent.getModelId(), agent.getToolsConfig());
            return builder.build();
        });
    }

    public SseEmitter streamChat(Long agentId, Long sessionId, String question,
                                  String modelOverride, Long tenantId) {
        SseEmitter emitter = new SseEmitter(300_000L);

        executor.submit(() -> {
            try {
                Agent agent = agentRepo.findById(agentId)
                        .orElseThrow(() -> new RuntimeException("Agent不存在"));

                // 如果有模型覆盖，临时修改
                if (modelOverride != null && !modelOverride.isBlank()) {
                    agent.setModelId(modelOverride);
                }

                // 获取或创建会话
                AgentChatSession session = getOrCreateSession(agentId, sessionId, tenantId);

                // 发送 session ID
                emitter.send(SseEmitter.event().name("session")
                        .data(Map.of("sessionId", session.getId())));

                // 构建 assistant
                AgentAssistant assistant = buildAssistant(agent);

                // 开始流式对话
                TokenStream tokenStream = assistant.chat(
                        session.getId().toString(), question);

                StringBuilder fullAnswer = new StringBuilder();

                tokenStream
                        .onPartialResponse(token -> {
                            fullAnswer.append(token);
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(objectMapper.writeValueAsString(
                                                Map.of("type", "token", "content", token))));
                            } catch (IOException ignored) {}
                        })
                        .onToolExecuted(toolExec -> {
                            try {
                                // 发送工具调用完成事件（包含名称、参数和结果）
                                String result = toolExec.result();
                                String displayResult = result;
                                if (displayResult != null && displayResult.length() > 500) {
                                    displayResult = displayResult.substring(0, 500) + "...";
                                }
                                emitter.send(SseEmitter.event().name("tool_call")
                                        .data(objectMapper.writeValueAsString(Map.of(
                                                "toolName", toolExec.request().name(),
                                                "toolArgs", toolExec.request().arguments() != null
                                                        ? toolExec.request().arguments() : "",
                                                "result", displayResult != null ? displayResult : ""
                                        ))));

                                // 保存工具消息
                                saveToolMessage(session.getId(), toolExec.request().name(),
                                        toolExec.request().arguments(), result);
                            } catch (IOException ignored) {}
                        })
                        .onCompleteResponse(response -> {
                            try {
                                // 保存用户消息和助手回复
                                saveMessage(session.getId(), "user", question);
                                saveMessage(session.getId(), "assistant", fullAnswer.toString());
                                updateSessionTitle(session, question);

                                emitter.send(SseEmitter.event()
                                        .data(objectMapper.writeValueAsString(
                                                Map.of("type", "done"))));
                                emitter.complete();
                            } catch (IOException ignored) {
                                emitter.complete();
                            }
                        })
                        .onError(error -> {
                            log.error("[AgentChat] 错误: {}", error.getMessage(), error);
                            try {
                                emitter.send(SseEmitter.event()
                                        .data(objectMapper.writeValueAsString(
                                                Map.of("type", "error", "message",
                                                        error.getMessage() != null ? error.getMessage() : "未知错误"))));
                            } catch (IOException ignored) {}
                            emitter.complete();
                        })
                        .start();

            } catch (Exception e) {
                log.error("[AgentChat] 启动失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .data(objectMapper.writeValueAsString(
                                    Map.of("type", "error", "message", e.getMessage()))));
                } catch (IOException ignored) {}
                emitter.complete();
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        return emitter;
    }

    // ========== W6: 工作流引擎同步调用 ==========

    /**
     * 同步调用 Agent（工作流节点使用）
     * 不走 SSE，直接返回完整文本
     */
    public String chatSync(Agent agent, String prompt) {
        try {
            AgentAssistant assistant = buildAssistant(agent);
            // 用一次性的 memoryId，不复用会话
            String memoryId = "wf_" + System.currentTimeMillis();
            TokenStream tokenStream = assistant.chat(memoryId, prompt);

            StringBuilder result = new StringBuilder();
            var latch = new java.util.concurrent.CountDownLatch(1);
            var errorRef = new java.util.concurrent.atomic.AtomicReference<Throwable>();

            tokenStream
                    .onPartialResponse(result::append)
                    .onCompleteResponse(response -> latch.countDown())
                    .onError(error -> {
                        errorRef.set(error);
                        latch.countDown();
                    })
                    .start();

            boolean completed = latch.await(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                throw new RuntimeException("Agent 调用超时");
            }
            if (errorRef.get() != null) {
                throw new RuntimeException("Agent 调用失败: " + errorRef.get().getMessage(), errorRef.get());
            }
            return result.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent 调用被中断", e);
        }
    }

    // ========== Session / Message 管理 ==========

    public List<AgentChatSession> listSessions(Long agentId, Long tenantId) {
        return sessionRepo.findByAgentIdAndTenantIdOrderByUpdatedAtDesc(agentId, tenantId);
    }

    public List<AgentChatMessage> getMessages(Long sessionId) {
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        messageRepo.deleteBySessionId(sessionId);
        sessionRepo.deleteById(sessionId);
    }

    private AgentChatSession getOrCreateSession(Long agentId, Long sessionId, Long tenantId) {
        if (sessionId != null) {
            return sessionRepo.findById(sessionId).orElseGet(() -> createSession(agentId, tenantId));
        }
        return createSession(agentId, tenantId);
    }

    private AgentChatSession createSession(Long agentId, Long tenantId) {
        AgentChatSession session = new AgentChatSession();
        session.setAgentId(agentId);
        session.setTenantId(tenantId);
        session.setTitle("新会话");
        return sessionRepo.save(session);
    }

    private void saveMessage(Long sessionId, String role, String content) {
        AgentChatMessage msg = new AgentChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        messageRepo.save(msg);
    }

    private void saveToolMessage(Long sessionId, String toolName, String toolInput, String toolOutput) {
        AgentChatMessage msg = new AgentChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole("tool");
        msg.setToolName(toolName);
        msg.setToolInput(toolInput);
        msg.setToolOutput(toolOutput);
        messageRepo.save(msg);
    }

    private void updateSessionTitle(AgentChatSession session, String question) {
        if ("新会话".equals(session.getTitle())) {
            String title = question.length() > 30 ? question.substring(0, 30) + "..." : question;
            session.setTitle(title);
            sessionRepo.save(session);
        }
    }
}
