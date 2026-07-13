package com.example.aichat.rag.service;

import com.example.aichat.context.RequestContext;
import com.example.aichat.rag.model.RagResponse;
import com.example.aichat.rag.model.RagSource;
import com.example.aichat.rag.model.RetrievedChunk;
import com.example.aichat.rag.model.RerankResult;
import com.example.aichat.service.ChatModelFactory;
import com.example.aichat.service.LongTermMemoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final RagRetrievalRouter retrievalRouter;
    private final ChatModelFactory modelFactory;
    private final RerankService rerankService;
    private final LongTermMemoryService memoryService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${rag.retrieval.max-results:5}")
    private int maxResults;

    @Value("${rag.rerank.enabled:false}")
    private boolean rerankEnabled;

    @Value("${rag.local-rerank.enabled:true}")
    private boolean localRerankEnabled;

    @Value("${rag.local-rerank.keyword-boost:0.08}")
    private double localKeywordBoost;

    @Value("${rag.local-rerank.normal-boost:0.03}")
    private double localNormalBoost;

    @Value("${rag.local-rerank.max-boost:0.5}")
    private double localMaxBoost;

    @Value("${rag.local-rerank.keywords:pmic,ldo,dcdc,dc-dc,buck,boost,电压,电流,输出,路,通道,引脚,接口,寄存器,电源,规格,最大,最小}")
    private String localRerankKeywords;

    private static final String DEFAULT_MODEL = "deepseek-chat";

    private static final String RAG_SYSTEM_PROMPT = """
            你是一个企业知识库助手，擅长从产品手册、规格书、参数表和售后资料中提取准确事实。

            规则：
            - 只基于参考资料回答，不要编造信息
            - 如果参考资料不足以回答，请如实说明"根据现有资料无法回答该问题"
            - 涉及数量、路数、电压、电流、型号、接口、寄存器、引脚等参数时，优先依据表格、参数行和直接描述
            - 如果用户问"几路/多少路/一共几个"，且参考资料列出了多个输出项，要先逐项识别，再给出统计结果
            - 如果只检索到部分类型（例如只找到 LDO，没有找到 DCDC），必须明确说明缺失项，不要推断
            - 回答时引用关键证据，并尽量标出来源编号；来源中如有页码，也要在回答中体现
            - 使用简洁清晰的语言

            长期记忆：
            %s

            参考资料：
            %s
            """;

    public RagService(RagRetrievalRouter retrievalRouter,
                      ChatModelFactory modelFactory,
                      RerankService rerankService,
                      LongTermMemoryService memoryService) {
        this.retrievalRouter = retrievalRouter;
        this.modelFactory = modelFactory;
        this.rerankService = rerankService;
        this.memoryService = memoryService;
    }

    /**
     * RAG 问答（非流式）
     */
    public RagResponse chat(String question, String modelId, Long tenantId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        log.info("[RAG] 问答: question='{}', model={}, tenantId={}", question, modelId, tenantId);
        String userId = memoryService.resolveUserId(tenantId, RequestContext.get("keyId"));
        String memoryPrompt = memoryOrFallback(memoryService.buildMemoryPrompt(userId, question));

        // 1. 检索（向量 or 混合）
        RetrievalContext ctx = retrieve(question, tenantId);
        if (ctx.contextTexts.isEmpty()) {
            log.info("[RAG] 未找到相关文档");
            return new RagResponse("知识库中没有找到与您问题相关的内容，请先上传相关文档。",
                    List.of(), question);
        }

        // 2. 构建上下文
        StringBuilder contextStr = new StringBuilder();
        for (int i = 0; i < ctx.contextTexts.size(); i++) {
            contextStr.append(String.format("[%d] %s\n\n", i + 1, ctx.contextTexts.get(i)));
        }

        // 3. 调用 LLM
        String systemPrompt = String.format(RAG_SYSTEM_PROMPT, memoryPrompt, contextStr);
        ChatLanguageModel model = modelFactory.getModel(modelId);

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                dev.langchain4j.data.message.UserMessage.from(question)
        );

        String answer = model.generate(messages).content().text();
        memoryService.addConversation(userId, question, answer);
        log.info("[RAG] 回答生成完成, 来源数={}", ctx.sources.size());

        return new RagResponse(answer, ctx.sources, question);
    }

    /**
     * RAG 问答（流式 SSE）
     * 注意：tenantId 显式传入，不依赖 ThreadLocal（因为在线程池执行）
     */
    public SseEmitter streamChat(String question, String modelId, Long tenantId) {
        modelId = modelId != null ? modelId : DEFAULT_MODEL;
        SseEmitter emitter = new SseEmitter(120_000L);

        final String finalModelId = modelId;
        final String keyId = RequestContext.get("keyId");

        executor.submit(() -> {
            try {
                String userId = memoryService.resolveUserId(tenantId, keyId);
                String memoryPrompt = memoryOrFallback(memoryService.buildMemoryPrompt(userId, question));

                // 1. 检索（向量 or 混合）
                RetrievalContext ctx = retrieve(question, tenantId);

                if (ctx.contextTexts.isEmpty()) {
                    emitter.send(SseEmitter.event().data(
                            Map.of("content", "知识库中没有找到与您问题相关的内容，请先上传相关文档。")));
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                    return;
                }

                // 发送来源信息
                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data(ctx.sources));

                // 2. 构建上下文
                StringBuilder contextStr = new StringBuilder();
                for (int i = 0; i < ctx.contextTexts.size(); i++) {
                    contextStr.append(String.format("[%d] %s\n\n", i + 1, ctx.contextTexts.get(i)));
                }

                // 3. 流式调用 LLM
                String systemPrompt = String.format(RAG_SYSTEM_PROMPT, memoryPrompt, contextStr);
                StreamingChatLanguageModel streamModel = modelFactory.getStreamingModel(finalModelId);

                List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
                        dev.langchain4j.data.message.SystemMessage.from(systemPrompt),
                        dev.langchain4j.data.message.UserMessage.from(question)
                );

                streamModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
                    private final StringBuilder fullAnswer = new StringBuilder();

                    @Override
                    public void onNext(String token) {
                        fullAnswer.append(token);
                        try {
                            emitter.send(SseEmitter.event().data(Map.of("content", token)));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        try {
                            memoryService.addConversation(userId, question, fullAnswer.toString());
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("[RAG] 流式生成失败", error);
                        emitter.completeWithError(error);
                    }
                });

            } catch (Exception e) {
                log.error("[RAG] 流式问答失败", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ========== 内部检索逻辑 ==========

    /**
     * 统一检索入口：支持纯向量 / 混合检索 / 混合+Rerank
     */
    private RetrievalContext retrieve(String question, Long tenantId) {
        List<String> contextTexts = new ArrayList<>();
        List<RagSource> sources = new ArrayList<>();
        int candidateCount = Math.max(maxResults * 4, 20);
        List<RetrievedChunk> candidates = retrievalRouter.retrieveByTenant(question, tenantId, candidateCount);

        if (candidates.isEmpty()) {
            return new RetrievalContext(List.of(), List.of());
        }

        List<RetrievedChunk> selected = rerankOrBoost(question, candidates, maxResults);
        for (RetrievedChunk chunk : selected) {
            TextSegment segment = chunk.toSegment();
            contextTexts.add(formatContext(segment));
            sources.add(new RagSource(
                    getFileName(segment.metadata()),
                    getPage(segment.metadata()),
                    Math.round(chunk.score() * 100) / 100.0,
                    segment.text().substring(0, Math.min(100, segment.text().length())) + "..."
            ));
        }

        return new RetrievalContext(contextTexts, sources);
    }

    private List<RetrievedChunk> rerankOrBoost(String question, List<RetrievedChunk> candidates, int limit) {
        if (rerankEnabled && rerankService.isConfigured()) {
            List<String> texts = candidates.stream()
                    .map(RetrievedChunk::text)
                    .toList();
            List<RerankResult> reranked = rerankService.rerank(question, texts, limit);
            List<RetrievedChunk> result = new ArrayList<>();
            for (RerankResult rerank : reranked) {
                if (rerank.originalIndex() >= 0 && rerank.originalIndex() < candidates.size()) {
                    RetrievedChunk original = candidates.get(rerank.originalIndex());
                    result.add(new RetrievedChunk(original.text(), original.metadata(), rerank.score()));
                }
            }
            if (!result.isEmpty()) {
                log.info("[RAG] Rerank完成: candidates={}, selected={}", candidates.size(), result.size());
                return result.stream().limit(limit).toList();
            }
            log.info("[RAG] Rerank无有效结果，使用关键词加权兜底");
        }

        if (!localRerankEnabled) {
            log.info("[RAG] 本地关键词重排未启用，使用检索原顺序: candidates={}, selected={}", candidates.size(), limit);
            return candidates.stream().limit(limit).toList();
        }

        List<String> queryTerms = extractImportantTerms(question);
        if (queryTerms.isEmpty()) {
            log.info("[RAG] 本地关键词重排无有效查询词，使用检索原顺序: candidates={}, selected={}", candidates.size(), limit);
            return candidates.stream().limit(limit).toList();
        }

        List<RetrievedChunk> selected = candidates.stream()
                .map(chunk -> new RetrievedChunk(chunk.text(), chunk.metadata(),
                        chunk.score() + keywordBoost(chunk.text(), queryTerms)))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(limit)
                .toList();
        log.info("[RAG] 使用本地关键词重排: terms={}, candidates={}, selected={}",
                queryTerms, candidates.size(), selected.size());
        return selected;
    }

    private List<String> extractImportantTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        List<String> terms = new ArrayList<>();
        for (String term : configuredSpecKeywords()) {
            if (normalized.contains(term.toLowerCase(Locale.ROOT))) {
                terms.add(term.toLowerCase(Locale.ROOT));
            }
        }
        for (String token : normalized.split("[\\s,，。；;:：?？!！()（）\\[\\]【】]+")) {
            if (token.length() >= 2 && token.length() <= 32) {
                terms.add(token);
            }
        }
        return terms.stream().distinct().toList();
    }

    private double keywordBoost(String text, List<String> queryTerms) {
        if (text == null || queryTerms.isEmpty()) {
            return 0;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        double boost = 0;
        for (String term : queryTerms) {
            if (normalized.contains(term)) {
                boost += isSpecKeyword(term) ? localKeywordBoost : localNormalBoost;
            }
        }
        return Math.min(boost, localMaxBoost);
    }

    private boolean isSpecKeyword(String term) {
        return configuredSpecKeywords().contains(term);
    }

    private List<String> configuredSpecKeywords() {
        if (localRerankKeywords == null || localRerankKeywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(localRerankKeywords.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String formatContext(TextSegment segment) {
        StringBuilder header = new StringBuilder();
        header.append("来源文件: ").append(getFileName(segment.metadata()));
        Integer page = getPage(segment.metadata());
        if (page != null) {
            header.append("，第 ").append(page).append(" 页");
        }
        return header + "\n正文:\n" + segment.text();
    }

    private String memoryOrFallback(String memoryPrompt) {
        return memoryPrompt == null || memoryPrompt.isBlank() ? "(无相关记忆)" : memoryPrompt;
    }

    private String getFileName(dev.langchain4j.data.document.Metadata metadata) {
        String fileName = metadata.getString("file_name");
        return fileName != null ? fileName : "未知";
    }

    private Integer getPage(dev.langchain4j.data.document.Metadata metadata) {
        String page = metadata.getString("page");
        if (page == null || page.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(page);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 检索结果上下文（内部使用）
     */
    private record RetrievalContext(List<String> contextTexts, List<RagSource> sources) {}

}
