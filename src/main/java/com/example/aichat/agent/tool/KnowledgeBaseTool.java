package com.example.aichat.agent.tool;

import com.example.aichat.rag.model.RetrievedChunk;
import com.example.aichat.rag.service.RagRetrievalRouter;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeBaseTool {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseTool.class);

    private final RagRetrievalRouter retrievalRouter;

    @Value("${rag.retrieval.max-results:5}")
    private int maxResults;

    public KnowledgeBaseTool(RagRetrievalRouter retrievalRouter) {
        this.retrievalRouter = retrievalRouter;
    }

    @Tool("查询企业知识库获取内部文档信息。输入查询问题和知识库ID号。")
    public String queryKnowledgeBase(
            @P("查询问题") String query,
            @P("知识库ID号") Long kbId) {
        log.info("[KBTool] query='{}', kbId={}", query, kbId);
        try {
            List<RetrievedChunk> matches =
                    retrievalRouter.retrieveByKnowledgeBase(query, kbId, maxResults);

            if (matches.isEmpty()) {
                return "知识库中未找到与查询相关的内容";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < matches.size(); i++) {
                RetrievedChunk match = matches.get(i);
                String fileName = match.metadata().getString("file_name");
                sb.append(String.format("[%d] (来源: %s, 相关度: %.2f)\n%s\n\n",
                        i + 1, fileName != null ? fileName : "未知",
                        match.score(), match.text()));
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("[KBTool] 查询失败: {}", e.getMessage());
            return "[知识库查询失败] " + e.getMessage();
        }
    }
}
