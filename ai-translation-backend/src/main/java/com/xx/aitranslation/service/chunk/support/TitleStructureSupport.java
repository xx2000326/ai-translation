package com.xx.aitranslation.service.chunk.support;

import com.xx.aitranslation.service.chunk.model.DocumentNode;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标题结构识别与标题树构建（参考 doc/V1/标题拆分.md）。
 * <p>
 * 标题识别规则（返回层级，0 表示正文）：
 * <ul>
 *     <li>Markdown ATX：{@code #} 个数即层级（1~6）</li>
 *     <li>中文章节：{@code 第X章} → 1，{@code 第X节} → 2</li>
 *     <li>数字编号：{@code 1} → 1，{@code 1.1} → 2，{@code 1.1.1} → 3 ……（按点号个数 + 1）</li>
 * </ul>
 * 数字编号要求整行较短，避免误伤以数字开头的正文。采用栈维护标题路径完成父子挂载。
 */
@Component
public class TitleStructureSupport {

    /** 数字编号标题的最大行长度，超出视为正文。 */
    private static final int MAX_HEADING_LENGTH = 60;

    private static final Pattern MARKDOWN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    private static final Pattern CHAPTER =
            Pattern.compile("^第\\s*[0-9一二三四五六七八九十百千零两]+\\s*章([\\s、：:.．]|$).*");

    private static final Pattern SECTION =
            Pattern.compile("^第\\s*[0-9一二三四五六七八九十百千零两]+\\s*节([\\s、：:.．]|$).*");

    private static final Pattern NUMBERED =
            Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.?(?:[\\s、：:．].*)?$");

    /**
     * 构建标题树：返回虚拟根节点（level=0），首个标题之前的正文归属根节点。
     */
    public DocumentNode buildTree(String content) {
        DocumentNode root = new DocumentNode();
        root.setId("ROOT");
        root.setLevel(0);

        DocumentNode currentBody = root;
        StringBuilder buffer = new StringBuilder();

        Deque<DocumentNode> stack = new ArrayDeque<>();
        stack.push(root);
        int seq = 0;

        if (ObjectUtils.isEmpty(content)) {
            return root;
        }

        for (String line : content.split("\n", -1)) {
            HeadingInfo heading = detectHeading(line);
            if (heading == null) {
                buffer.append(line).append('\n');
                continue;
            }
            // 标题行：先把累积正文落到上一个归属节点
            flushBuffer(currentBody, buffer);

            DocumentNode node = new DocumentNode();
            node.setId("T" + (seq++));
            node.setLevel(heading.level());
            node.setTitle(heading.title());

            // 出栈直到栈顶层级 < 当前层级，找到父节点
            while (stack.peek() != null && stack.peek().getLevel() >= heading.level()) {
                stack.pop();
            }
            DocumentNode parent = ObjectUtils.isEmpty(stack.peek()) ? root : stack.peek();
            parent.addChild(node);
            stack.push(node);
            currentBody = node;
        }
        flushBuffer(currentBody, buffer);
        return root;
    }

    private void flushBuffer(DocumentNode target, StringBuilder buffer) {
        if (buffer.length() == 0) {
            return;
        }
        String body = buffer.toString().strip();
        buffer.setLength(0);
        if (!body.isEmpty()) {
            target.setContent(body);
        }
    }

    /**
     * 识别标题，非标题返回 {@code null}。
     */
    public HeadingInfo detectHeading(String line) {
        if (ObjectUtils.isEmpty(line)) {
            return null;
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher md = MARKDOWN.matcher(trimmed);
        if (md.matches()) {
            return new HeadingInfo(md.group(1).length(), md.group(2).strip());
        }
        if (CHAPTER.matcher(trimmed).matches()) {
            return new HeadingInfo(1, trimmed);
        }
        if (SECTION.matcher(trimmed).matches()) {
            return new HeadingInfo(2, trimmed);
        }
        if (trimmed.length() <= MAX_HEADING_LENGTH) {
            Matcher numbered = NUMBERED.matcher(trimmed);
            if (numbered.matches()) {
                String number = numbered.group(1);
                int dots = (int) number.chars().filter(c -> c == '.').count();
                return new HeadingInfo(dots + 1, trimmed);
            }
        }
        return null;
    }

    /**
     * 标题信息：层级 + 标题文本。
     */
    public record HeadingInfo(int level, String title) {
    }
}
