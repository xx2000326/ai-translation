package com.xx.aitranslation.service.python.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PythonPdfParsedText {
    private String taskId;
    private String text;
    private Integer pageCount;
    private List<String> warnings;
    private List<PythonPdfBlock> blocks;
}
