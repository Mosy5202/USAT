package com.usat.app.data

/** Calculations shared by database-backed summaries and tests. */
object UsatCalculations {
    fun weightedAssessmentPercent(assessments: List<AssessmentEntity>): Float? {
        val valid = assessments.filter { it.maxScore > 0f && it.score >= 0f }
        val maximum = valid.sumOf { it.maxScore.toDouble() }
        if (valid.isEmpty() || maximum <= 0.0) return null
        return (valid.sumOf { it.score.toDouble() } / maximum * 100.0).toFloat()
    }

    fun completedStudyHours(sessions: List<StudySessionEntity>): Float =
        sessions.asSequence().filter { it.state == "completed" }.sumOf { it.accumulatedSeconds.toDouble() }.toFloat() / 3600f

    fun targetProgressFraction(studiedHours: Float, targetHours: Float): Float =
        if (targetHours > 0f) (studiedHours / targetHours).coerceIn(0f, 1f) else 0f
}
