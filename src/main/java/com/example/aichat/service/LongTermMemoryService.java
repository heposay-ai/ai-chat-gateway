package com.example.aichat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LongTermMemoryService {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryService.class);

    @Value("${memory.enabled:false}")
    private boolean enabled;

    @Value("${memory.api-key:}")
    private String apiKey;

    @Value("${memory.base-url:https://dashscope.aliyuncs.com/api/v2/apps/memory}")
    private String baseUrl;

    @Value("${memory.library-id:}")
    private String memoryLibraryId;

    @Value("${memory.top-k:5}")
    private int topK;

    @Value("${memory.min-score:0.3}")
    private double minScore;

    @Value("${memory.enable-rewrite:false}")
    private boolean enableRewrite;

    @Value("${memory.enable-rerank:false}")
    private boolean enableRerank;

    @Value("${memory.profile-schema-ids:}")
    private String profileSchemaIds;

    @Value("${memory.profile-write-schema-id:}")
    private String profileWriteSchemaId;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public String resolveUserId(Long tenantId, String keyId) {
        if (keyId != null && !keyId.isBlank()) {
            return limitUserId("key-" + keyId);
        }
        if (tenantId != null) {
            return "tenant-" + tenantId;
        }
        return "anonymous";
    }

    public String buildMemoryPrompt(String userId, String question) {
        List<String> memories = searchMemories(userId, question);
        List<String> profiles = getUserProfiles(userId);
        if (memories.isEmpty() && profiles.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder("以下是关于当前用户的长期记忆和用户画像，回答时可作为个性化上下文参考，但不要泄露记忆系统实现：\n");
        if (!profiles.isEmpty()) {
            prompt.append("用户画像：\n");
            for (int i = 0; i < profiles.size(); i++) {
                prompt.append(i + 1).append(". ").append(profiles.get(i)).append('\n');
            }
        }
        if (!memories.isEmpty()) {
            prompt.append("长期记忆：\n");
        }
        for (int i = 0; i < memories.size(); i++) {
            prompt.append(i + 1).append(". ").append(memories.get(i)).append('\n');
        }
        return prompt.toString();
    }

    public List<String> searchMemories(String userId, String question) {
        if (!isEnabled() || question == null || question.isBlank()) {
            return List.of();
        }

        try {
            Map<String, Object> body = baseBody(userId);
            body.put("messages", List.of(Map.of("role", "user", "content", question)));
            body.put("top_k", topK);
            body.put("min_score", minScore);
            body.put("enable_rewrite", enableRewrite);
            body.put("enable_rerank", enableRerank);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    apiUrl("/memory_nodes/search"), new HttpEntity<>(body, headers()), Map.class);

            Object nodes = response.getBody() != null ? response.getBody().get("memory_nodes") : null;
            if (!(nodes instanceof List<?> nodeList)) {
                return List.of();
            }

            List<String> memories = new ArrayList<>();
            for (Object node : nodeList) {
                if (node instanceof Map<?, ?> nodeMap) {
                    Object content = nodeMap.get("content");
                    if (content instanceof String text && !text.isBlank()) {
                        memories.add(text);
                    }
                }
            }
            log.debug("[Memory] 搜索完成 userId={}, 命中={}", userId, memories.size());
            return memories;
        } catch (Exception e) {
            log.warn("[Memory] 搜索失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    public void addConversation(String userId, String userMessage, String assistantMessage) {
        if (!isEnabled() || userMessage == null || userMessage.isBlank()) {
            return;
        }

        try {
            Map<String, Object> body = baseBody(userId);
            body.put("messages", List.of(
                    Map.of("role", "user", "content", userMessage),
                    Map.of("role", "assistant", "content", assistantMessage != null ? assistantMessage : "")
            ));
            body.put("meta_data", Map.of("source", "ai-chat-gateway"));
            String schemaId = firstConfiguredProfileSchemaId();
            if (!schemaId.isBlank()) {
                body.put("profile_schema", schemaId);
            }

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    apiUrl("/add"), new HttpEntity<>(body, headers()), Map.class);
            Object nodes = response.getBody() != null ? response.getBody().get("memory_nodes") : null;
            int changed = nodes instanceof List<?> nodeList ? nodeList.size() : 0;
            log.debug("[Memory] 写入完成 userId={}, 变更={}", userId, changed);
        } catch (Exception e) {
            log.warn("[Memory] 写入失败 userId={}: {}", userId, e.getMessage());
        }
    }

    public List<String> getUserProfiles(String userId) {
        if (!isEnabled() || profileSchemaIds == null || profileSchemaIds.isBlank()) {
            return List.of();
        }

        List<String> profiles = new ArrayList<>();
        for (String schemaId : configuredProfileSchemaIds()) {
            try {
                String url = apiUrl("/profile_schemas/" + encode(schemaId) + "/user_profile")
                        + "?user_id=" + encode(userId);
                if (memoryLibraryId != null && !memoryLibraryId.isBlank()) {
                    url += "&memory_library_id=" + encode(memoryLibraryId);
                }

                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
                String profileText = profileToText(response.getBody());
                if (!profileText.isBlank()) {
                    profiles.add(profileText);
                }
            } catch (Exception e) {
                log.warn("[Memory] 用户画像查询失败 userId={}, schemaId={}: {}", userId, schemaId, e.getMessage());
            }
        }
        log.debug("[Memory] 用户画像查询完成 userId={}, 命中={}", userId, profiles.size());
        return profiles;
    }

    public List<Map<String, Object>> listMemories(String userId, int pageNum, int pageSize) {
        if (!isEnabled()) {
            return List.of();
        }

        try {
            String url = apiUrl("/memory_nodes") + "?user_id=" + userId
                    + "&page_num=" + pageNum + "&page_size=" + pageSize;
            if (memoryLibraryId != null && !memoryLibraryId.isBlank()) {
                url += "&memory_library_id=" + memoryLibraryId;
            }
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
            Object nodes = response.getBody() != null ? response.getBody().get("memory_nodes") : null;
            if (!(nodes instanceof List<?> nodeList)) {
                return List.of();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (Object node : nodeList) {
                if (node instanceof Map<?, ?> nodeMap) {
                    Map<String, Object> item = new HashMap<>();
                    nodeMap.forEach((key, value) -> item.put(String.valueOf(key), value));
                    result.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[Memory] 列表查询失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> baseBody(String userId) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        if (memoryLibraryId != null && !memoryLibraryId.isBlank()) {
            body.put("memory_library_id", memoryLibraryId);
        }
        return body;
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String apiUrl(String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + path;
    }

    private String limitUserId(String userId) {
        return userId.length() <= 64 ? userId : userId.substring(0, 64);
    }

    private List<String> configuredProfileSchemaIds() {
        if (profileSchemaIds == null || profileSchemaIds.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String raw : profileSchemaIds.split(",")) {
            String id = raw.trim();
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String firstConfiguredProfileSchemaId() {
        if (profileWriteSchemaId != null && !profileWriteSchemaId.isBlank()) {
            return profileWriteSchemaId.trim();
        }
        List<String> ids = configuredProfileSchemaIds();
        return ids.isEmpty() ? "" : ids.get(0);
    }

    @SuppressWarnings("unchecked")
    private String profileToText(Map body) {
        if (body == null || !(body.get("profile") instanceof Map<?, ?> profile)) {
            return "";
        }

        Object schemaName = profile.get("schema_name");
        Object attributes = profile.get("attributes");
        if (!(attributes instanceof List<?> attrList)) {
            return "";
        }

        List<String> pairs = new ArrayList<>();
        for (Object attr : attrList) {
            if (attr instanceof Map<?, ?> attrMap) {
                Object name = attrMap.get("name");
                Object value = attrMap.get("value");
                if (name instanceof String nameText
                        && value instanceof String valueText
                        && !nameText.isBlank()
                        && !valueText.isBlank()) {
                    pairs.add(nameText + ": " + valueText);
                }
            }
        }

        if (pairs.isEmpty()) {
            return "";
        }

        String prefix = schemaName instanceof String text && !text.isBlank()
                ? text + " - "
                : "";
        return prefix + String.join("; ", pairs);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
