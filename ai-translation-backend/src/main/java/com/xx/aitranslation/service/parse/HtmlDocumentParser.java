package com.xx.aitranslation.service.parse;

import com.xx.aitranslation.enums.FileType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * HTML 解析器：按文档顺序提取块级元素（标题 / 段落 / 列表项）文本，每个非空元素为一段。
 */
@Component
public class HtmlDocumentParser implements DocumentParser {

    @Override
    public List<ParsedBlock> parse(InputStream in) throws Exception {
        Document document = Jsoup.parse(in, "UTF-8", "");
        Elements elements = document.select("h1,h2,h3,h4,h5,h6,p,li");
        List<ParsedBlock> blocks = new ArrayList<>();
        int order = 0;
        for (Element element : elements) {
            String text = element.text();
            if (ObjectUtils.isEmpty(text) || ObjectUtils.isEmpty(text.trim())) {
                continue;
            }
            blocks.add(new ParsedBlock(order++, resolveType(element.tagName()), text.trim()));
        }
        return blocks;
    }

    /**
     * 按标签名推断块类型：h 开头为 heading，li 为 list，其余为 paragraph。
     */
    private String resolveType(String tagName) {
        String tag = tagName.toLowerCase();
        if (tag.startsWith("h")) {
            return "heading";
        }
        if ("li".equals(tag)) {
            return "list";
        }
        return "paragraph";
    }

    @Override
    public FileType supportType() {
        return FileType.HTML;
    }
}
