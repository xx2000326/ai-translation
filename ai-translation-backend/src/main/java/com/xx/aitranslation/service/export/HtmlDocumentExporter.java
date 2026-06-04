package com.xx.aitranslation.service.export;

import com.xx.aitranslation.entity.TranslationSegment;
import com.xx.aitranslation.enums.ExportFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * HTML 导出器：输出完整 HTML 文档，按段落 blockType 包裹标签（纯结构化，不还原样式）。
 */
@Component
public class HtmlDocumentExporter implements DocumentExporter {

    @Override
    public ExportFormat format() {
        return ExportFormat.HTML;
    }

    private static final String LIST_TYPE = "list";

    @Override
    public byte[] export(List<TranslationSegment> segments) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>");
        if (!ObjectUtils.isEmpty(segments)) {
            boolean inList = false;
            for (TranslationSegment seg : segments) {
                String type = ObjectUtils.isEmpty(seg.getBlockType()) ? "" : seg.getBlockType().toLowerCase();
                String text = getText(seg);
                String escaped = HtmlUtils.htmlEscape(ObjectUtils.isEmpty(text) ? "" : text);
                boolean isList = LIST_TYPE.equals(type);
                // 连续列表段聚合到同一个 <ul> 中，遇到非列表段则关闭列表
                if (isList && !inList) {
                    sb.append("<ul>");
                    inList = true;
                } else if (!isList && inList) {
                    sb.append("</ul>");
                    inList = false;
                }
                sb.append(wrap(type, escaped));
            }
            if (inList) {
                sb.append("</ul>");
            }
        }
        sb.append("</body></html>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 按块类型包裹文本：heading→h2，list→li，其余→p。
     */
    private String wrap(String type, String escapedText) {
        return switch (type) {
            case "heading" -> "<h2>" + escapedText + "</h2>";
            case LIST_TYPE -> "<li>" + escapedText + "</li>";
            default -> "<p>" + escapedText + "</p>";
        };
    }

    @Override
    public String contentType() {
        return "text/html;charset=UTF-8";
    }

    @Override
    public String fileExtension() {
        return "html";
    }
}
