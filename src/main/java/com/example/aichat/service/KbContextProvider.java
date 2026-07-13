package com.example.aichat.service;

import com.example.aichat.rag.model.RetrievedChunk;
import com.example.aichat.rag.service.KnowledgeBaseService;
import com.example.aichat.rag.service.RagRetrievalRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class KbContextProvider {

    private static final Logger log = LoggerFactory.getLogger(KbContextProvider.class);

    private static final Pattern KB_PLACEHOLDER = Pattern.compile("\\{\\{kb:([^}]+)\\}\\}");

    private final RagRetrievalRouter retrievalRouter;
    private final KnowledgeBaseService kbService;

    public KbContextProvider(RagRetrievalRouter retrievalRouter,
                             KnowledgeBaseService kbService) {
        this.retrievalRouter = retrievalRouter;
        this.kbService = kbService;
    }

    /**
     * 根据知识库 ID 检索并构建上下文字符串
     */
    public String buildContextByKbId(String query, Long kbId, Long tenantId, int maxResults) {
        if (kbId == null) return "";
        try {
            kbService.getByIdAndTenant(kbId, tenantId);
            List<RetrievedChunk> chunks = retrievalRouter.retrieveByKnowledgeBase(query, kbId, maxResults);
            if (chunks.isEmpty()) {
                log.info("[KB Context] 知识库 kbId={} 未检索到相关文档", kbId);
                return "";
            }
            String context = formatChunks(chunks);
            log.info("[KB Context] 知识库 kbId={} 注入 {} 条语料, {} 字符",
                    kbId, chunks.size(), context.length());
            return context;
        } catch (Exception e) {
            log.warn("[KB Context] 知识库 kbId={} 检索失败: {}", kbId, e.getMessage());
            return "";
        }
    }

    /**
     * 解析模板中的 {{kb:知识库名}} 占位符，替换为检索结果
     */
    public String resolveKbPlaceholders(String template, String userQuery, Long tenantId, int maxResults) {
        Matcher matcher = KB_PLACEHOLDER.matcher(template);
        if (!matcher.find()) return template;

        matcher.reset();
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String kbName = matcher.group(1).trim();
            String context = resolveByKbName(kbName, userQuery, tenantId, maxResults);
            matcher.appendReplacement(result, Matcher.quoteReplacement(context));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveByKbName(String kbName, String userQuery, Long tenantId, int maxResults) {
        try {
            var kbs = kbService.listByTenant(tenantId);
            var kb = kbs.stream()
                    .filter(k -> kbName.equals(k.getName()))
                    .findFirst()
                    .orElse(null);
            if (kb == null) {
                log.warn("[KB Context] 未找到知识库: name={}", kbName);
                return "[未找到知识库: " + kbName + "]";
            }
            return buildContextByKbId(userQuery, kb.getId(), tenantId, maxResults);
        } catch (Exception e) {
            log.warn("[KB Context] 解析知识库占位符失败: kb={}, error={}", kbName, e.getMessage());
            return "[知识库检索失败: " + kbName + "]";
        }
    }

    private String formatChunks(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            sb.append("[").append(i + 1).append("] ");
            String fileName = chunk.metadata() != null ?
                    chunk.metadata().getString("file_name") : null;
            if (fileName != null && !fileName.isBlank()) {
                sb.append("来源: ").append(fileName).append("\n");
            }
            sb.append(chunk.text()).append("\n\n");
        }
        return sb.toString();
    }
}
