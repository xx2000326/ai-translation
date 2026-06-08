const LOCALES = {
  zh: {
    'translation.progress.title': '初翻进度',
    'review.progress.title': 'AI 审校进度',
    'translation.progress': '已完成 {completed} / {total} 句',
    'translation.progress.preparing': '正在准备翻译任务…',
    'translation.progress.done': '初翻已完成',
    'translation.progress.idle': '尚未开始翻译',
    'review.progress': '审校第 {round} 轮：已完成 {completed} / {total} 句',
    'review.progress.scoring': '审校第 {round} 轮 · 评分中：{completed} / {total} 句',
    'review.progress.retranslate': '审校第 {round} 轮 · 重翻中：{completed} / {total} 句',
    'review.progress.preparing': '审校第 {round} 轮 · 准备中…',
    'review.progress.done': 'AI 审校已完成（共 {rounds} 轮）',
    'review.progress.idle': '审校尚未开始',
    'review.segment.scoring': 'R{n} 评分',
    'review.segment.retranslate': 'R{n} 重翻',
    'review.segment.skipped': '本轮无需重翻',
    'summary.progress.title': '风格统一进度',
    'summary.progress': '已完成 {completed} / {total} 句',
    'summary.progress.preparing': '正在生成风格指南…',
    'summary.progress.done': '风格统一已完成',
    'summary.progress.idle': '风格统一尚未开始',
    'summary.status.running': '风格统一中…'
  },
  en: {
    'translation.progress.title': 'Translation',
    'review.progress.title': 'AI Review',
    'translation.progress': '{completed} / {total} sentences done',
    'translation.progress.preparing': 'Preparing translation…',
    'translation.progress.done': 'Initial translation complete',
    'translation.progress.idle': 'Translation not started yet',
    'review.progress': 'Review round {round}: {completed} / {total} sentences',
    'review.progress.scoring': 'Review round {round} · scoring: {completed} / {total}',
    'review.progress.retranslate': 'Review round {round} · retranslating: {completed} / {total}',
    'review.progress.preparing': 'Review round {round} · preparing…',
    'review.progress.done': 'AI review complete ({rounds} round(s))',
    'review.progress.idle': 'Review not started yet',
    'review.segment.scoring': 'R{n} score',
    'review.segment.retranslate': 'R{n} retranslate',
    'review.segment.skipped': 'No retranslation needed',
    'summary.progress.title': 'Style Unification',
    'summary.progress': '{completed} / {total} sentences done',
    'summary.progress.preparing': 'Generating style guide…',
    'summary.progress.done': 'Style unification complete',
    'summary.progress.idle': 'Style unification not started yet',
    'summary.status.running': 'Unifying style…'
  }
}

export function t(key, params = {}, locale = 'zh') {
  let text = LOCALES[locale]?.[key] ?? LOCALES.zh[key] ?? key
  for (const [name, value] of Object.entries(params)) {
    text = text.replace(`{${name}}`, String(value))
  }
  return text
}
