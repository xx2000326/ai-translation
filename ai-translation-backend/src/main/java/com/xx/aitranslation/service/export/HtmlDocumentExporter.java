package com.xx.aitranslation.service.export;

import com.xx.aitranslation.entity.TranslationSentence;
import com.xx.aitranslation.enums.ExportFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class HtmlDocumentExporter implements DocumentExporter {

    @Override
    public ExportFormat format() {
        return ExportFormat.HTML;
    }

    @Override
    public byte[] export(List<TranslationSentence> sentences) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>");
        if (!ObjectUtils.isEmpty(sentences)) {
            for (TranslationSentence sent : sentences) {
                String text = getText(sent);
                String escaped = HtmlUtils.htmlEscape(ObjectUtils.isEmpty(text) ? "" : text);
                sb.append("<p>").append(escaped).append("</p>");
            }
        }
        sb.append("</body></html>");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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
