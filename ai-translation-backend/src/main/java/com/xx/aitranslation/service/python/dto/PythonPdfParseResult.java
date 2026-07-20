package com.xx.aitranslation.service.python.dto;

import lombok.Data;

import java.util.List;

@Data
public class PythonPdfParseResult {
    private String taskId;
    private String fileName;
    private Integer pageCount;
    private String parserStrategy;
    private String status;
    private List<String> warnings;
    private List<PythonPdfBlock> blocks;
    private String markdown;
    private String plainText;
}
