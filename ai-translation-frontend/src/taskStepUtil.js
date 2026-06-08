export const STEP = {
  PARSE: 'PARSE',
  TRANSLATE: 'TRANSLATE',
  REVIEW_SCORE: 'REVIEW_SCORE',
  REVIEW_RETRANSLATE: 'REVIEW_RETRANSLATE',
  SUMMARY_GUIDE: 'SUMMARY_GUIDE',
  SUMMARY_UNIFY: 'SUMMARY_UNIFY'
}

export function findStep(task, stepCode) {
  if (!task?.steps?.length) {
    return null
  }
  return task.steps.find((s) => s.stepCode === stepCode) || null
}

export function stepPercent(step) {
  if (!step || step.status === 'SKIPPED') {
    return null
  }
  if (step.status === 'DONE') {
    return 100
  }
  if (!step.totalCount || step.totalCount <= 0) {
    return step.status === 'RUNNING' ? 0 : null
  }
  const completed = step.completedCount || 0
  return Math.min(100, Math.round((completed / step.totalCount) * 100))
}

export function isStepDone(step) {
  return step && (step.status === 'DONE' || step.status === 'SKIPPED')
}

export function isStepRunning(step) {
  return step && step.status === 'RUNNING'
}

export function isAgentProcessing(status) {
  return status === 'AGENT_PROCESSING'
}

export function isSummaryRunning(task) {
  const unify = findStep(task, STEP.SUMMARY_UNIFY)
  return unify?.status === 'RUNNING'
}

export function resolveTranslateStep(task) {
  const step = findStep(task, STEP.TRANSLATE)
  if (!step) {
    return { completed: 0, total: 0, running: false, done: false }
  }
  return {
    completed: step.completedCount || 0,
    total: step.totalCount || 0,
    running: step.status === 'RUNNING',
    done: isStepDone(step)
  }
}

export function resolveSummaryStep(task) {
  const step = findStep(task, STEP.SUMMARY_UNIFY)
  if (!step || step.status === 'SKIPPED') {
    return null
  }
  return {
    completed: step.completedCount || 0,
    total: step.totalCount || 0,
    running: step.status === 'RUNNING',
    done: step.status === 'DONE'
  }
}

export function resolveReviewContext(task) {
  const scoreStep = findStep(task, STEP.REVIEW_SCORE)
  const retranslateStep = findStep(task, STEP.REVIEW_RETRANSLATE)

  if (scoreStep?.status === 'SKIPPED') {
    return null
  }

  let active = null
  if (retranslateStep?.status === 'RUNNING') {
    active = retranslateStep
  } else if (scoreStep?.status === 'RUNNING') {
    active = scoreStep
  }

  if (active) {
    return {
      round: active.roundNo || task?.reviewRound || 1,
      subPhase: active.subStep || 'SCORING',
      completed: active.completedCount || 0,
      total: active.totalCount || 0,
      reviewing: true
    }
  }

  return {
    round: task?.reviewRound || 1,
    subPhase: 'SCORING',
    completed: 0,
    total: 0,
    reviewing: false
  }
}

export function isReviewPhaseComplete(task) {
  const status = task?.status
  if (['MANUAL_REVIEW', 'COMPLETED', 'EXPORTED'].includes(status)) {
    return true
  }

  const scoreStep = findStep(task, STEP.REVIEW_SCORE)
  if (scoreStep?.status === 'SKIPPED') {
    return true
  }

  const unify = findStep(task, STEP.SUMMARY_UNIFY)
  if (unify && (unify.status === 'RUNNING' || unify.status === 'DONE')) {
    return true
  }

  if (isAgentProcessing(status) && task?.enableSummary) {
    const translateDone = isStepDone(findStep(task, STEP.TRANSLATE))
    const scoreDone = isStepDone(scoreStep)
    const retranslate = findStep(task, STEP.REVIEW_RETRANSLATE)
    const retranslateDone = !retranslate || isStepDone(retranslate)
    if (translateDone && scoreDone && retranslateDone && unify?.status === 'PENDING') {
      return true
    }
  }

  return false
}
