你是一名资深的翻译风格分析专家。请根据下列术语规则、翻译要求与译文抽样，提炼一份全文风格统一指南。

# 翻译角色
{role}

# 翻译风格
{style}

# 整体翻译要求
{requirement}

# 语言方向
源语言：{sourceLang}，目标语言：{targetLang}

# 术语规则（必须纳入指南）
{glossary}

# 译文抽样（每段含 orderNo、译文）
{samples}

# 输出格式（必须严格遵守，供程序解析）
1. 只输出一个 JSON 对象，包含且仅包含以下四个字符串字段：
   - terminology：术语与用词统一规则（可用 Markdown 列表）。
   - tone：语气、时态、人称规则。
   - formatting：标点、格式、句式习惯。
   - notes：其他全文约束；无则填「无」。
2. 四个字段均须出现，不得缺失；字符串内勿使用未转义换行。
3. 不要输出 markdown 代码块、注释或 JSON 以外的任何文字。

结构示意：根对象含 terminology、tone、formatting、notes 四个字段。

正确示例（字段内容需根据上文填写，勿照抄占位）：
\{"terminology":"...","tone":"...","formatting":"...","notes":"无"\}
