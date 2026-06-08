# 文档拆分模块（Document Chunk Engine）设计方案 V1

## 一、设计目标

构建一个独立的文档拆分引擎，不依赖具体业务（翻译、RAG、摘要等）。

支持：

* 多格式文档读取
* 文档清洗
* 多种拆分策略
* 父子Chunk
* Overlap
* 元数据管理
* 大文件处理

后续可直接接入：

* AI翻译
* RAG
* AI总结
* Agent流程

---

# 二、整体架构

```text
┌─────────────────────┐
│     DocumentReader  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   DocumentCleaner   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│    Chunk Engine     │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│    Chunk Result     │
└─────────────────────┘
```

流程：

读取文件

↓

清洗内容

↓

拆分Chunk

↓

返回Chunk结果

---

# 三、Reader层设计

统一接口

```java
public interface DocumentReader {

    ParsedDocument read(File file);

}
```

返回对象

```java
@Data
public class ParsedDocument {

    private String fileName;

    private String fileType;

    private String content;

    private Map<String,Object> metadata;

}
```

---

支持实现

```text
PdfDocumentReader
MarkdownDocumentReader
HtmlDocumentReader
WordDocumentReader
TextDocumentReader
```

推荐实现：

Spring AI Reader

PDF

spring-ai-pdf-document-reader

HTML

spring-ai-jsoup-document-reader

Markdown

spring-ai-markdown-document-reader

Office

spring-ai-tika-document-reader

---

# 四、Cleaner层设计

职责：

统一文档内容

接口：

```java
public interface DocumentCleaner {

    String clean(String content);

}
```

默认实现：

```text
TrimCleaner
BlankLineCleaner
PageNumberCleaner
UnicodeCleaner
```

示例：

清理连续空行

```text
原文

A


B



C

↓

A

B

C
```

---

# 五、Chunk层设计

核心模块

接口

```java
public interface ChunkStrategy {

    List<DocumentChunk> chunk(String content);

}
```

---

# 六、Chunk对象设计

```java
@Data
@Builder
public class DocumentChunk {

    private String id;

    private String parentId;

    private Integer level;

    private Integer index;

    private String content;

    private Integer tokenCount;

    private Integer charCount;

    private Map<String,Object> metadata;

}
```

说明：

level

```text
0 = Root

1 = Parent Chunk

2 = Child Chunk
```

---

# 七、支持的拆分策略

## 1. FixedSizeChunkStrategy

固定长度拆分

```text
1000字符

2000字符

5000字符
```

示例：

```text
chunkSize=1000

overlap=100
```

---

## 2. ParagraphChunkStrategy

按段落拆分

```text
段落1

段落2

段落3
```

适合：

小说

文章

博客

---

## 3. SentenceChunkStrategy

按句子拆分

```text
。

！

？

.
!
?
```

适合：

翻译

摘要

---

## 4. MarkdownChunkStrategy

按照标题拆分

```markdown
# 第一章

内容

## 第一节

内容

### 子章节

内容
```

拆分结果

```text
Chunk1

# 第一章

Chunk2

## 第一节

Chunk3

### 子章节
```

适合：

Markdown

技术文档

API文档

---

# 八、Parent Child Chunk设计

## 为什么需要

大模型窗口有限

不能一次处理几十万字文档

需要分层拆分

---

示例

原文

```text
100000字符
```

↓

Parent Chunk

```text
Parent-1
5000字符

Parent-2
5000字符

Parent-3
5000字符
```

↓

Child Chunk

```text
Parent-1

Child-1
Child-2
Child-3

Parent-2

Child-4
Child-5
Child-6
```

结构：

```text
Root

 ├─ Parent-1
 │    ├─ Child-1
 │    ├─ Child-2
 │    └─ Child-3
 │
 ├─ Parent-2
 │    ├─ Child-4
 │    └─ Child-5
 │
 └─ Parent-3
```

---

接口

```java
public interface HierarchicalChunkStrategy {

    ChunkTree chunk(String content);

}
```

---

推荐参数

Parent

```text
3000~5000 token
```

Child

```text
500~1000 token
```

---

# 九、Overlap设计

避免上下文断裂

配置：

```java
private Integer overlap;
```

示例

chunkSize

```text
1000
```

overlap

```text
100
```

结果

```text
Chunk1

1~1000

Chunk2

901~1900

Chunk3

1801~2800
```

这样AI翻译时不会丢上下文。

---

# 十、动态策略选择

工厂模式

```java
public interface ChunkStrategyFactory {

    ChunkStrategy getStrategy(
            FileType fileType,
            ChunkConfig config
    );

}
```

规则

Markdown

```text
MarkdownChunkStrategy
```

PDF

```text
ParagraphChunkStrategy
```

HTML

```text
ParagraphChunkStrategy
```

TXT

```text
SentenceChunkStrategy
```

超大文件

```text
HierarchicalChunkStrategy
```

---

# 十一、配置设计

```yaml
document:

  chunk:

    enabled: true

    strategy: markdown

    chunk-size: 1000

    overlap: 100

    parent-size: 5000

    child-size: 1000

    max-file-size: 50MB
```

---

# 十二、V1阶段范围

仅实现：

√ Reader

√ Cleaner

√ Chunk

√ Parent Child Chunk

√ Overlap

√ Factory

暂不实现：

× AI翻译

× RAG

× Embedding

× Vector数据库

× Agent

先把拆分引擎做成独立模块。

后续翻译系统直接复用该模块。
