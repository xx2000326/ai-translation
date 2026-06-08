export const MAX_REVIEW_ROUND = 3
export const TOTAL_SEGMENTS = MAX_REVIEW_ROUND * 2

export function segmentIndex(round, subPhase) {
  const r = round || 1
  return (r - 1) * 2 + (subPhase === 'RETRANSLATE' ? 1 : 0)
}

export function fullyCompletedSegments(round, subPhase) {
  const r = round || 1
  if (subPhase === 'RETRANSLATE') {
    return (r - 1) * 2 + 1
  }
  return (r - 1) * 2
}

export function isReviewPhaseComplete(status, progressPhase) {
  if (status === 'REVIEW_DONE' || status === 'MANUAL_REVIEW' || status === 'COMPLETED' || status === 'EXPORTED') {
    return true
  }
  // 风格统一阶段说明 AI 审校已结束
  if (progressPhase === 'SUMMARY') {
    return true
  }
  return false
}

export function computeReviewOverallPercent(status, round, subPhase, completed, total, progressPhase) {
  if (isReviewPhaseComplete(status, progressPhase)) {
    return 100
  }
  if (status !== 'REVIEWING') {
    return 0
  }
  const base = fullyCompletedSegments(round, subPhase)
  const currentPct = total > 0 ? completed / total : 0
  return Math.min(100, Math.round(((base + currentPct) / TOTAL_SEGMENTS) * 100))
}

export function buildReviewStepItems(status, round, subPhase, t, progressPhase) {
  const reviewDone = isReviewPhaseComplete(status, progressPhase)
  const current = status === 'REVIEWING' ? segmentIndex(round, subPhase) : TOTAL_SEGMENTS
  const items = []
  for (let i = 0; i < TOTAL_SEGMENTS; i++) {
    const segRound = Math.floor(i / 2) + 1
    const isRetranslate = i % 2 === 1
    let stepStatus = 'wait'
    if (reviewDone || i < current) {
      stepStatus = 'finish'
    } else if (status === 'REVIEWING' && i === current) {
      stepStatus = 'process'
    }
    const title = isRetranslate
      ? t('review.segment.retranslate', { n: segRound })
      : t('review.segment.scoring', { n: segRound })
    const skipped =
      isRetranslate &&
      reviewDone &&
      segRound === (round || 1) &&
      subPhase !== 'RETRANSLATE'
    items.push({ title, status: stepStatus, skipped })
  }
  return items
}

export function reviewProgressLabel(status, round, subPhase, completed, total, t, progressPhase) {
  const r = round || 1
  if (isReviewPhaseComplete(status, progressPhase)) {
    return t('review.progress.done', { rounds: r })
  }
  if (status !== 'REVIEWING') {
    return t('review.progress.idle')
  }
  if (total <= 0) {
    return t('review.progress.preparing', { round: r })
  }
  if (subPhase === 'RETRANSLATE') {
    return t('review.progress.retranslate', { round: r, completed, total })
  }
  return t('review.progress.scoring', { round: r, completed, total })
}
