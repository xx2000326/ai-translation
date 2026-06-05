package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;
import net.sf.okapi.common.filters.IFilter;
import net.sf.okapi.filters.html.HtmlFilter;
import org.springframework.stereotype.Component;

@Component
public class OkapiHtmlDocumentParser extends OkapiDocumentParser {

    @Override
    protected IFilter createFilter() {
        return new HtmlFilter();
    }

    @Override
    protected String resolveParaType(net.sf.okapi.common.resource.ITextUnit tu) {
        return "html";
    }

    @Override
    public FileType supportType() {
        return FileType.HTML;
    }
}
