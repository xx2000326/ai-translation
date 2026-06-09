package com.xx.aitranslation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("translation_paragraph")
public class TranslationParagraph {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private Integer orderNo;

    private String paraPosition;

    private String paraType;

    private String originalText;

    /** 高级拆分：Chunk 唯一标识（文档内）。 */
    private String chunkId;

    /** 高级拆分：章节标题（或超长切分后的 {@code 标题-PartN}）。 */
    private String title;

    /** 高级拆分：父标题（层级父章节标题）。 */
    private String parentTitle;

    /** 高级拆分：章节层级。 */
    @TableField("node_level")
    private Integer level;

    private LocalDateTime createTime;
}
