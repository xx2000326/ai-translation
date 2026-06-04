package com.xx.aitranslation.service.export;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.enums.ExportFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 导出器工厂：按 {@link ExportFormat} 路由到对应的 {@link DocumentExporter}。
 */
@Component
public class DocumentExporterFactory {

    private final Map<ExportFormat, DocumentExporter> exporters = new EnumMap<>(ExportFormat.class);

    public DocumentExporterFactory(List<DocumentExporter> exporterList) {
        if (!ObjectUtils.isEmpty(exporterList)) {
            for (DocumentExporter exporter : exporterList) {
                exporters.put(exporter.format(), exporter);
            }
        }
    }

    /**
     * 获取指定格式的导出器，缺失抛业务异常。
     *
     * @param format 导出格式
     * @return 导出器
     */
    public DocumentExporter get(ExportFormat format) {
        DocumentExporter exporter = exporters.get(format);
        if (ObjectUtils.isEmpty(exporter)) {
            throw new BizException("export.format.unsupported");
        }
        return exporter;
    }
}
