package com.xx.aitranslation.dto;

public record StyleGuideResult(
        String terminology,
        String tone,
        String formatting,
        String notes
) {
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, "术语与用词", terminology);
        appendSection(sb, "语气、时态、人称", tone);
        appendSection(sb, "标点与格式", formatting);
        appendSection(sb, "其他约束", notes);
        return sb.toString().trim();
    }

    private static void appendSection(StringBuilder sb, String title, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        sb.append("## ").append(title).append('\n').append(body.trim()).append("\n\n");
    }
}
