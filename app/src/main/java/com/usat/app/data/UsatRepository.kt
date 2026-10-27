package com.usat.app.data

import kotlinx.coroutines.flow.Flow

class UsatRepository(private val dao: UsatDao) {
    val courses: Flow<List<CourseEntity>> = dao.observeCourses()
    val activities: Flow<List<ActivityEntity>> = dao.observeActivities()
    val sessions: Flow<List<StudySessionEntity>> = dao.observeSessions()
    val assessments: Flow<List<AssessmentEntity>> = dao.observeAssessments()
    val settings: Flow<AppSettingsEntity?> = dao.observeSettings()
    val profiles: Flow<List<AcademicProfileEntity>> = dao.observeProfiles()
    val studentProfile: Flow<StudentProfileEntity?> = dao.observeStudentProfile()
    val profileCourses: Flow<List<ProfileCourseEntity>> = dao.observeProfileCourses()
    val openSession: Flow<StudySessionEntity?> = dao.observeOpenSession()

    suspend fun addCourse(course: CourseEntity) = dao.insertCourse(course)
    suspend fun updateCourse(course: CourseEntity) = dao.updateCourse(course)
    suspend fun updateTarget(code: String, hours: Float) = dao.updateTarget(code, hours)
    suspend fun deleteCourse(code: String) = dao.deleteCourse(code)
    suspend fun addActivity(activity: ActivityEntity) = dao.insertActivity(activity)
    suspend fun updateActivity(activity: ActivityEntity) = dao.updateActivity(activity)
    suspend fun setActivityCompleted(id: Long, completed: Boolean) = dao.setActivityCompleted(id, completed)
    suspend fun deleteActivity(id: Long) = dao.deleteActivity(id)
    suspend fun addAssessment(assessment: AssessmentEntity) = dao.insertAssessment(assessment)
    suspend fun updateAssessment(assessment: AssessmentEntity) = dao.updateAssessment(assessment)
    suspend fun deleteAssessment(id: Long) = dao.deleteAssessment(id)
    suspend fun saveSettings(settings: AppSettingsEntity) = dao.saveSettings(settings)
    suspend fun currentSettings() = dao.getSettings()
    suspend fun currentProfile(id: Long) = dao.getProfile(id)
    suspend fun courseExists(code: String) = dao.getCourse(code) != null
    suspend fun createProfile(profile: AcademicProfileEntity, courseCodes: List<String>) = dao.createProfile(profile, courseCodes)
    suspend fun updateProfile(profile: AcademicProfileEntity, courseCodes: List<String>) = dao.updateProfileAndCourses(profile, courseCodes)
    suspend fun saveStudentAndAcademicProfile(student: StudentProfileEntity, profile: AcademicProfileEntity, courseCodes: List<String>) {
        val settings = dao.getSettings() ?: AppSettingsEntity(academicPeriod = defaultPeriod())
        dao.saveStudentAndAcademicProfile(student, profile, courseCodes, settings)
    }
    suspend fun usernameInUse(username: String, exceptId: Int = 1) = dao.usernameInUse(username, exceptId)
    suspend fun setActiveProfile(profileId: Long?) {
        val settings = dao.getSettings() ?: AppSettingsEntity(academicPeriod = defaultPeriod())
        dao.saveSettings(settings.copy(activeProfileId = profileId))
    }
    suspend fun deleteProfile(id: Long) = dao.deleteProfile(id)
    suspend fun currentSession() = dao.getOpenSession()
    suspend fun startSession(session: StudySessionEntity) = dao.startSession(session)
    suspend fun pauseSession(id: Long, at: Long, elapsed: Int) = dao.pauseSession(id, at, elapsed)
    suspend fun resumeSession(id: Long, at: Long) = dao.resumeSession(id, at)
    suspend fun completeSession(id: Long, at: Long, elapsed: Int) = dao.completeSession(id, at, elapsed)

    private fun defaultPeriod(): String {
        val year = java.time.LocalDate.now().year
        return "Semester 1 · $year/${(year + 1) % 100}"
    }
}
