package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.Language;
import net.sf.okapi.common.LocaleId;
import org.springframework.util.ObjectUtils;

final class ParseLocaleHelper {

    private ParseLocaleHelper() {
    }

    static LocaleId toLocaleId(String sourceLang) {
        if (ObjectUtils.isEmpty(sourceLang)) {
            return LocaleId.fromString("zh-CN");
        }
        if (Language.EN.getCode().equalsIgnoreCase(sourceLang)) {
            return LocaleId.fromString("en-US");
        }
        return LocaleId.fromString("zh-CN");
    }
}
