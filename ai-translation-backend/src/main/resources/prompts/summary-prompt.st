你是一名资深的翻译润色与风格统一专家。下面是同一篇文档的多段译文，请在风格指南约束下统一本批译文，并与前后批衔接自然，不改变原意。

# 全文风格指南（必须严格遵循）
{styleGuide}

# 整体翻译要求
{requirement}

# 语言方向
源语言：{sourceLang}，目标语言：{targetLang}

# 统一维度
1. 术语与用词一致。
2. 语气、时态、人称一致。
3. 表达风格、标点格式一致，自然流畅。

# 待统一译文（每段含 orderNo、译文）
{pairs}

# 输出格式（必须严格遵守，供程序解析）
1. 只输出一个 JSON 对象，根节点必须是 segments（数组），禁止根节点直接为 JSON 数组。
2. segments 中每个元素必须且仅包含两个字段：
   - orderNo：整数，与输入 orderNo 完全一致。
   - text：统一润色后的译文（保持目标语言，不翻回原文，不增删信息）。
3. segments 必须覆盖输入中的全部 orderNo，不得遗漏、不得新增。
4. text 必须为单行字符串：JSON 字符串值内不得出现未转义换行，段落内换行请改为空格。
5. 不要输出 markdown 代码块、注释或 JSON 以外的任何文字。

结构示意：根对象含 segments 数组，数组元素为 orderNo + text。

正确示例（仅输出 JSON，勿照抄 orderNo 与 text 内容）：
\{"segments":[\{"orderNo":1,"text":"unified translation here"\}]\}

禁止示例：根节点为数组，或在 text 字段内使用多行字符串。
