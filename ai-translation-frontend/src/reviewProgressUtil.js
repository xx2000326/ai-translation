import { isReviewPhaseComplete as isReviewCompleteFromSteps, resolveReviewContext } from './taskStepUtil.js'

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

export function isReviewPhaseComplete(task) {
  return isReviewCompleteFromSteps(task)
}

export function computeReviewOverallPercent(task) {
  if (isReviewPhaseComplete(task)) {
    return 100
  }

  const ctx = resolveReviewContext(task)
  if (!ctx.reviewing) {
    return 0
  }

  const base = fullyCompletedSegments(ctx.round, ctx.subPhase)
  const currentPct = ctx.total > 0 ? ctx.completed / ctx.total : 0
  return Math.min(100, Math.round(((base + currentPct) / TOTAL_SEGMENTS) * 100))
}

export function buildReviewStepItems(task, t) {
  const reviewDone = isReviewPhaseComplete(task)
  const ctx = resolveReviewContext(task)
  const current = ctx.reviewing ? segmentIndex(ctx.round, ctx.subPhase) : TOTAL_SEGMENTS
  const items = []
  for (let i = 0; i < TOTAL_SEGMENTS; i++) {
    const segRound = Math.floor(i / 2) + 1
    const isRetranslate = i % 2 === 1
    let stepStatus = 'wait'
    if (reviewDone || i < current) {
      stepStatus = 'finish'
    } else if (ctx.reviewing && i === current) {
      stepStatus = 'process'
    }
    const title = isRetranslate
      ? t('review.segment.retranslate', { n: segRound })
      : t('review.segment.scoring', { n: segRound })
    const skipped =
      isRetranslate &&
      reviewDone &&
      segRound === (ctx.round || 1) &&
      ctx.subPhase !== 'RETRANSLATE'
    items.push({ title, status: stepStatus, skipped })
  }
  return items
}

export function reviewProgressLabel(task, t) {
  const ctx = resolveReviewContext(task)

  if (isReviewPhaseComplete(task)) {
    return t('review.progress.done', { rounds: ctx.round || 1 })
  }
  if (!ctx.reviewing) {
    return t('review.progress.idle')
  }
  if (ctx.total <= 0) {
    return t('review.progress.preparing', { round: ctx.round })
  }
  if (ctx.subPhase === 'RETRANSLATE') {
    return t('review.progress.retranslate', {
      round: ctx.round,
      completed: ctx.completed,
      total: ctx.total
    })
  }
  return t('review.progress.scoring', {
    round: ctx.round,
    completed: ctx.completed,
    total: ctx.total
  })
}
