package com.example.aichat.rag.service;

import com.example.aichat.rag.model.KnowledgeDocument;
import com.example.aichat.rag.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub 代码库导入服务
 * 通过 GitHub API 递归获取仓库文件，将代码内容作为知识库文档索引
 */
@Service
public class GitHubImportService {

    private static final Logger log = LoggerFactory.getLogger(GitHubImportService.class);

    private static final Pattern GITHUB_URL_PATTERN =
            Pattern.compile("https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/tree/([^/]+))?/?$");

    /** 支持索引的代码文件扩展名 */
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".tsx", ".jsx", ".go", ".rs", ".rb", ".php",
            ".c", ".cpp", ".h", ".hpp", ".cs", ".swift", ".kt", ".scala", ".clj",
            ".sh", ".bash", ".zsh", ".ps1", ".bat",
            ".sql", ".graphql", ".proto",
            ".html", ".css", ".scss", ".less", ".vue", ".svelte",
            ".xml", ".json", ".yaml", ".yml", ".toml", ".ini", ".conf", ".properties",
            ".md", ".txt", ".rst", ".adoc",
            ".dockerfile", ".gradle", ".cmake",
            "Makefile", "Dockerfile", "Jenkinsfile", "Vagrantfile",
            ".gitignore", ".env.example", ".editorconfig",
            "pom.xml", "build.gradle", "package.json", "Cargo.toml", "go.mod", "requirements.txt"
    );

    /** 忽略的目录 */
    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", "target", "build", "dist", "out", ".idea", ".vscode",
            "__pycache__", ".gradle", "vendor", "bin", ".next", ".nuxt", "coverage",
            ".cache", "tmp", "temp", "logs"
    );

    /** 单文件大小上限 100KB */
    private static final long MAX_FILE_SIZE = 100 * 1024;

    private final ChunkService chunkService;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeDocumentRepository documentRepo;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${github.token:}")
    private String githubToken;

    public GitHubImportService(ChunkService chunkService,
                                VectorStoreService vectorStoreService,
                                KnowledgeDocumentRepository documentRepo,
                                ObjectMapper objectMapper) {
        this.chunkService = chunkService;
        this.vectorStoreService = vectorStoreService;
        this.documentRepo = documentRepo;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 解析 GitHub URL，返回 [owner, repo, branch]
     */
    public String[] parseGitHubUrl(String url) {
        Matcher m = GITHUB_URL_PATTERN.matcher(url.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("无效的 GitHub 链接，请提供格式：https://github.com/owner/repo");
        }
        String owner = m.group(1);
        String repo = m.group(2);
        String branch = m.group(3); // 可能为 null，表示使用默认分支
        return new String[]{owner, repo, branch};
    }

    /**
     * 创建文档记录并异步导入
     */
    public KnowledgeDocument startImport(String githubUrl, Long tenantId, Long kbId) {
        String[] parts = parseGitHubUrl(githubUrl);
        String repoFullName = parts[0] + "/" + parts[1];

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTenantId(tenantId);
        doc.setKbId(kbId);
        doc.setFileName(repoFullName);
        doc.setFilePath(githubUrl);
        doc.setContentType("github-repo");
        doc.setStatus(0);
        doc = documentRepo.save(doc);

        processAsync(doc.getId(), githubUrl);
        return doc;
    }

    /**
     * 异步处理 GitHub 仓库导入
     */
    @Async("ragIndexingExecutor")
    public void processAsync(Long docId, String githubUrl) {
        KnowledgeDocument doc = documentRepo.findById(docId).orElse(null);
        if (doc == null) return;

        List<String> storedEmbeddingIds = new ArrayList<>();
        try {
            String[] parts = parseGitHubUrl(githubUrl);
            String owner = parts[0];
            String repo = parts[1];
            String branch = parts[2];

            // 如果没指定分支，获取默认分支
            if (branch == null) {
                branch = getDefaultBranch(owner, repo);
            }

            log.info("[GitHub导入] 开始: repo={}/{}, branch={}, docId={}", owner, repo, branch, docId);

            // 递归获取仓库文件树
            List<GitHubFile> files = fetchRepoTree(owner, repo, branch);
            log.info("[GitHub导入] 获取到 {} 个代码文件", files.size());

            if (files.isEmpty()) {
                doc.setStatus(-1);
                doc.setErrorMessage("仓库中没有找到可索引的代码文件");
                documentRepo.save(doc);
                return;
            }

            // 拼接所有文件内容
            StringBuilder allContent = new StringBuilder();
            int fileCount = 0;
            for (GitHubFile file : files) {
                try {
                    String content = fetchFileContent(file.downloadUrl);
                    if (content != null && !content.isBlank()) {
                        allContent.append("===== ").append(file.path).append(" =====\n");
                        allContent.append(content).append("\n\n");
                        fileCount++;
                    }
                } catch (Exception e) {
                    log.warn("[GitHub导入] 获取文件内容失败: {}, 跳过", file.path);
                }
            }

            doc.setCharCount(allContent.length());
            doc.setFileSize((long) allContent.toString().getBytes(StandardCharsets.UTF_8).length);
            doc.setStatus(1);
            documentRepo.save(doc);
            log.info("[GitHub导入] 内容提取完成: {} 个文件, {} 字符", fileCount, allContent.length());

            // 切片
            Map<String, String> metadata = new HashMap<>(Map.of(
                    "doc_id", String.valueOf(doc.getId()),
                    "tenant_id", String.valueOf(doc.getTenantId()),
                    "file_name", doc.getFileName(),
                    "source_type", "github"
            ));
            if (doc.getKbId() != null) {
                metadata.put("kb_id", String.valueOf(doc.getKbId()));
            }
            var segments = chunkService.splitText(allContent.toString(), metadata);
            doc.setChunkCount(segments.size());
            documentRepo.save(doc);
            log.info("[GitHub导入] 切片完成: {} 个片段", segments.size());

            // 向量化存储
            storedEmbeddingIds = vectorStoreService.storeAll(segments);

            doc.setStatus(2);
            documentRepo.save(doc);
            log.info("[GitHub导入] 完成: repo={}/{}, docId={}, chunks={}", owner, repo, docId, segments.size());

        } catch (Exception e) {
            log.error("[GitHub导入] 失败: docId={}", docId, e);
            doc.setStatus(-1);
            doc.setErrorMessage(e.getMessage() != null ?
                    e.getMessage().substring(0, Math.min(e.getMessage().length(), 900)) : "未知错误");
            documentRepo.save(doc);

            if (!storedEmbeddingIds.isEmpty()) {
                try {
                    vectorStoreService.removeByIds(storedEmbeddingIds);
                } catch (Exception cleanupErr) {
                    log.warn("[GitHub导入] 清理孤儿向量失败", cleanupErr);
                }
            }
        }
    }

    /**
     * 获取仓库默认分支
     */
    private String getDefaultBranch(String owner, String repo) throws Exception {
        String url = "https://api.github.com/repos/" + owner + "/" + repo;
        JsonNode repoInfo = apiGet(url);
        return repoInfo.path("default_branch").asText("main");
    }

    /**
     * 获取仓库完整文件树（递归）
     */
    private List<GitHubFile> fetchRepoTree(String owner, String repo, String branch) throws Exception {
        String url = "https://api.github.com/repos/" + owner + "/" + repo
                + "/git/trees/" + branch + "?recursive=1";
        JsonNode tree = apiGet(url);
        JsonNode treeNodes = tree.path("tree");

        List<GitHubFile> files = new ArrayList<>();
        for (JsonNode node : treeNodes) {
            if (!"blob".equals(node.path("type").asText())) continue;

            String path = node.path("path").asText();
            long size = node.path("size").asLong(0);

            // 过滤：忽略目录、大文件、非代码文件
            if (size > MAX_FILE_SIZE) continue;
            if (isIgnoredPath(path)) continue;
            if (!isCodeFile(path)) continue;

            String downloadUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo
                    + "/" + branch + "/" + path;
            files.add(new GitHubFile(path, downloadUrl, size));
        }
        return files;
    }

    /**
     * 获取文件内容
     */
    private HttpRequest.Builder authHeader(HttpRequest.Builder builder) {
        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken);
        }
        return builder;
    }

    private String fetchFileContent(String downloadUrl) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "ai-chat-gateway");
        authHeader(builder);
        HttpRequest request = builder.GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return null;
    }

    /**
     * 调用 GitHub API
     */
    private JsonNode apiGet(String url) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "ai-chat-gateway")
                .header("Accept", "application/vnd.github.v3+json");
        authHeader(builder);
        HttpRequest request = builder.GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("GitHub API 请求失败: HTTP " + response.statusCode()
                    + " - " + response.body().substring(0, Math.min(response.body().length(), 200)));
        }
        return objectMapper.readTree(response.body());
    }

    private boolean isIgnoredPath(String path) {
        String[] parts = path.split("/");
        for (String part : parts) {
            if (IGNORED_DIRS.contains(part)) return true;
        }
        return false;
    }

    private boolean isCodeFile(String path) {
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        // 无扩展名的特殊文件
        if (CODE_EXTENSIONS.contains(fileName)) return true;
        // 按扩展名匹配
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx >= 0) {
            return CODE_EXTENSIONS.contains(fileName.substring(dotIdx));
        }
        return false;
    }

    /** GitHub 文件信息 */
    private record GitHubFile(String path, String downloadUrl, long size) {}
}
