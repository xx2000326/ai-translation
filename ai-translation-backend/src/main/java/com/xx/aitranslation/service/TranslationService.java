package com.xx.aitranslation.service;

import com.xx.aitranslation.agent.TranslationAgent;
import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.TranslateRequest;
import com.xx.aitranslation.dto.TranslateResponse;
import com.xx.aitranslation.entity.Customer;
import com.xx.aitranslation.enums.TranslationRole;
import com.xx.aitranslation.enums.TranslationStyle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

/**
 * 翻译核心服务，编排完整翻译流程：
 * <pre>
 * 解析 Role/Style -> 查询术语库(MySQL) -> 检索相似翻译(PGVector RAG)
 * -> 构建 Prompt -> 调用 LLM -> 写入历史(MySQL) -> 写入记忆(PGVector)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private final GlossaryService glossaryService;
    private final RagService ragService;
    private final HistoryService historyService;
    private final CustomerService customerService;
    private final TranslationAgent translationAgent;

    public TranslateResponse translate(TranslateRequest request) {
        if (ObjectUtils.isEmpty(request.getText())) {
            throw new BizException("translate.text.empty");
        }

        Customer customer = customerService.getById(request.getCustomerId());
        TranslationRole role = resolveRole(request.getRole(), customer);
        TranslationStyle style = resolveStyle(request.getStyle(), customer);

        // 1. 查询 MySQL 术语库（当前客户），构建术语规则
        String glossary = glossaryService.buildGlossaryRules(request.getCustomerId(), request.getText());
        // 2. 查询 PGVector（当前客户），检索相似历史翻译
        String ragContext = ragService.buildRagContext(request.getText(), request.getCustomerId(),
                role.getCode(), style.getCode());

        // 3. 构建 Prompt 并调用 LLM
        String translatedText;
        try {
            translatedText = translationAgent.translate(request.getText(), role, style, glossary, ragContext);
        } catch (Exception e) {
            log.error("调用大模型翻译失败", e);
            throw new BizException("translate.failed");
        }

        // 4. 写入 MySQL 翻译历史
        historyService.save(request.getCustomerId(), request.getText(), translatedText, role.getCode(), style.getCode());
        // 5. 写入 PGVector 翻译记忆（绑定当前客户）
        ragService.saveMemory(request.getText(), request.getCustomerId(), role.getCode(), style.getCode());

        return new TranslateResponse(translatedText, role.getCode(), style.getCode());
    }

    private TranslationRole resolveRole(String requestRole, Customer customer) {
        TranslationRole role = TranslationRole.fromCode(requestRole);
        if (!ObjectUtils.isEmpty(requestRole) && role == null) {
            throw new BizException("translate.role.invalid");
        }
        if (role == null && customer != null) {
            role = TranslationRole.fromCode(customer.getDefaultRole());
        }
        return role == null ? TranslationRole.PROFESSIONAL : role;
    }

    private TranslationStyle resolveStyle(String requestStyle, Customer customer) {
        TranslationStyle style = TranslationStyle.fromCode(requestStyle);
        if (!ObjectUtils.isEmpty(requestStyle) && style == null) {
            throw new BizException("translate.style.invalid");
        }
        if (style == null && customer != null) {
            style = TranslationStyle.fromCode(customer.getDefaultStyle());
        }
        return style == null ? TranslationStyle.FORMAL : style;
    }
}
