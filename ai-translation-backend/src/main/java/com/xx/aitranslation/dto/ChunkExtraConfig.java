package com.xx.aitranslation.dto;

import lombok.Data;

@Data
public class ChunkExtraConfig {

    /** Markdown：最大拆分标题深度 1～6 */
    private Integer headingLevel;
}
