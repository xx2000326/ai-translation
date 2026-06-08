任务：实现AI翻译系统V2的文档拆分模块。

目标：
按照文档结构进行拆分，而不是按照句子或换行拆分。

实现要求：

1. 解析Word/PDF文档结构。
2. 识别标题层级：

    * 第X章
    * 第X节
    * 1
    * 1.1
    * 1.1.1
3. 构建DocumentNode树结构：

class DocumentNode {
String title;
int level;
String content;
List<DocumentNode> children;
}

4. 以最小章节节点作为Chunk。

示例：

第1章 公司简介
1.1 公司背景
1.2 发展历程

输出：

Chunk1:
title=1.1 公司背景

Chunk2:
title=1.2 发展历程

5. 如果章节内容超过3000字符：

按段落继续切分：

1.1 公司背景-Part1
1.1 公司背景-Part2

并保留：

parentTitle=1.1 公司背景

6. 输出结果：

class DocumentChunk {
String chunkId;
String title;
String parentTitle;
Integer level;
String content;
}

要求：

* 保留章节层级信息
* 保留父子关系
* 支持后续情感分析、术语抽取、RAG检索和翻译任务调度
* 不允许按照句子级别拆分作为默认策略
