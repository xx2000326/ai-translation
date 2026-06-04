package com.xx.aitranslation.service.export;

import com.xx.aitranslation.entity.TranslationSegment;
import com.xx.aitranslation.enums.ExportFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 纯文本导出器：各段最终文本以换行 + 空行拼接。
 */
@Component
public class TxtDocumentExporter implements DocumentExporter {

    @Override
    public ExportFormat format() {
        return ExportFormat.TXT;
    }

    @Override
    public byte[] export(List<TranslationSegment> segments) {
        StringBuilder sb = new StringBuilder();
        if (!ObjectUtils.isEmpty(segments)) {
            for (TranslationSegment seg : segments) {
                String text = getText(seg);
                sb.append(ObjectUtils.isEmpty(text) ? "" : text).append("\n\n");
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String contentType() {
        return "text/plain;charset=UTF-8";
    }

    @Override
    public String fileExtension() {
        return "txt";
    }
}
