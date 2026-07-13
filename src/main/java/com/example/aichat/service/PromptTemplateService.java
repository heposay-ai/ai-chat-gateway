package com.example.aichat.service;

import com.example.aichat.model.dto.PromptTemplateRequest;
import com.example.aichat.model.entity.PromptTemplate;
import com.example.aichat.model.entity.PromptTemplateVersion;
import com.example.aichat.model.entity.TemplateFavorite;
import com.example.aichat.repository.PromptTemplateRepository;
import com.example.aichat.repository.PromptTemplateVersionRepository;
import com.example.aichat.repository.TemplateFavoriteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final PromptTemplateRepository templateRepository;
    private final PromptTemplateVersionRepository versionRepository;
    private final TemplateFavoriteRepository favoriteRepository;
    private final KbContextProvider kbContextProvider;

    public PromptTemplateService(PromptTemplateRepository templateRepository,
                                  PromptTemplateVersionRepository versionRepository,
                                  TemplateFavoriteRepository favoriteRepository,
                                  KbContextProvider kbContextProvider) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.favoriteRepository = favoriteRepository;
        this.kbContextProvider = kbContextProvider;
    }

    public PromptTemplate create(Long tenantId, PromptTemplateRequest request) {
        PromptTemplate template = new PromptTemplate();
        template.setTenantId(tenantId);
        template.setName(request.name());
        template.setDescription(request.description());
        template.setCategory(request.category());
        template.setContent(request.content());
        template.setVariables(request.variables());
        template.setVersion(1);
        template.setIsPublic(request.isPublic() != null ? request.isPublic() : false);

        template = templateRepository.save(template);
        saveVersion(template, "初始版本");

        log.info("[模板] 创建模板: id={}, name={}, category={}", template.getId(), template.getName(), template.getCategory());
        return template;
    }

    public PromptTemplate update(Long id, PromptTemplateRequest request) {
        PromptTemplate template = templateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + id));

        if (request.name() != null) template.setName(request.name());
        if (request.description() != null) template.setDescription(request.description());
        if (request.category() != null) template.setCategory(request.category());
        if (request.content() != null) template.setContent(request.content());
        if (request.variables() != null) template.setVariables(request.variables());
        if (request.isPublic() != null) template.setIsPublic(request.isPublic());

        template.setVersion(template.getVersion() + 1);
        template = templateRepository.save(template);

        saveVersion(template, request.changeNote() != null ? request.changeNote() : "更新内容");
        log.info("[模板] 更新模板: id={}, version={}", id, template.getVersion());
        return template;
    }

    public PromptTemplate getById(Long id) {
        return templateRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + id));
    }

    public List<PromptTemplate> listByTenant(Long tenantId) {
        return templateRepository.findByTenantIdAndStatusOrderByUpdatedAtDesc(tenantId, 1);
    }

    public void delete(Long id) {
        templateRepository.deleteById(id);
        log.info("[模板] 删除模板: id={}", id);
    }

    /**
     * 渲染模板：替换 {{variable}} 占位符，同时解析 {{kb:知识库名}} 知识库引用
     */
    public String render(Long templateId, Map<String, String> variables, String userQuery, Long tenantId) {
        PromptTemplate template = getById(templateId);
        String content = resolveKbPlaceholders(template.getContent(), userQuery, tenantId);
        return renderTemplate(content, variables);
    }

    /**
     * 渲染模板：替换 {{variable}} 占位符（不解析 KB 引用）
     */
    public String render(Long templateId, Map<String, String> variables) {
        return render(templateId, variables, null, null);
    }

    private String resolveKbPlaceholders(String content, String userQuery, Long tenantId) {
        if (userQuery == null || tenantId == null || content == null) return content;
        return kbContextProvider.resolveKbPlaceholders(content, userQuery, tenantId, 8);
    }

    public String renderTemplate(String templateContent, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(templateContent);
        Set<String> requiredVars = new HashSet<>();
        while (matcher.find()) {
            requiredVars.add(matcher.group(1));
        }

        String result = templateContent;
        for (String varName : requiredVars) {
            String value = variables != null ? variables.get(varName) : null;
            if (value == null) {
                throw new IllegalArgumentException("缺少必填变量: " + varName);
            }
            result = result.replace("{{" + varName + "}}", value);
        }

        log.debug("[模板] 渲染完成, 变量数={}, 结果长度={}", requiredVars.size(), result.length());
        return result;
    }

    public List<PromptTemplateVersion> getVersionHistory(Long templateId) {
        return versionRepository.findByTemplateIdOrderByVersionDesc(templateId);
    }

    // === 模板市场 ===

    public Page<PromptTemplate> listMarket(String category, String keyword, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if (keyword != null && !keyword.isBlank()) {
            return templateRepository.searchPublic(keyword, pageRequest);
        }
        if (category != null && !category.isBlank()) {
            return templateRepository.findByIsPublicTrueAndStatusAndCategory(1, category, pageRequest);
        }
        return templateRepository.findByIsPublicTrueAndStatus(1, pageRequest);
    }

    // === 收藏 ===

    public void addFavorite(Long tenantId, Long templateId) {
        if (favoriteRepository.findByTenantIdAndTemplateId(tenantId, templateId).isEmpty()) {
            TemplateFavorite fav = new TemplateFavorite();
            fav.setTenantId(tenantId);
            fav.setTemplateId(templateId);
            favoriteRepository.save(fav);
        }
    }

    @Transactional
    public void removeFavorite(Long tenantId, Long templateId) {
        favoriteRepository.deleteByTenantIdAndTemplateId(tenantId, templateId);
    }

    public List<PromptTemplate> getFavorites(Long tenantId) {
        List<Long> templateIds = favoriteRepository.findByTenantId(tenantId).stream()
            .map(TemplateFavorite::getTemplateId)
            .toList();
        if (templateIds.isEmpty()) return List.of();
        return templateRepository.findAllById(templateIds);
    }

    private void saveVersion(PromptTemplate template, String changeNote) {
        PromptTemplateVersion version = new PromptTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersion(template.getVersion());
        version.setContent(template.getContent());
        version.setVariables(template.getVariables());
        version.setChangeNote(changeNote);
        versionRepository.save(version);
    }
}
