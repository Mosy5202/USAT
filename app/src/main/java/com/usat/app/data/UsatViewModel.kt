package com.usat.app.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UsatUiState(
    val profiles: List<AcademicProfileEntity> = emptyList(),
    val studentProfile: StudentProfileEntity? = null,
    val profileCourses: List<ProfileCourseEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val activities: List<ActivityEntity> = emptyList(),
    val sessions: List<StudySessionEntity> = emptyList(),
    val assessments: List<AssessmentEntity> = emptyList(),
    val settings: AppSettingsEntity? = null,
    val openSession: StudySessionEntity? = null,
    val loading: Boolean = true,
    val message: String? = null,
    val error: String? = null
)

class UsatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UsatRepository(UsatDatabase.get(application).dao())
    private val operationState = kotlinx.coroutines.flow.MutableStateFlow<Pair<String?, String?>>(null to null)
    private val records = combine(repository.courses, repository.activities, repository.sessions, repository.assessments) { courses, activities, sessions, assessments ->
        UsatUiState(courses = courses, activities = activities, sessions = sessions, assessments = assessments, loading = false)
    }
    private val profiles = combine(repository.profiles, repository.profileCourses, repository.studentProfile) { profiles, profileCourses, studentProfile -> Triple(profiles, profileCourses, studentProfile) }
    val uiState: StateFlow<UsatUiState> = combine(records, profiles, repository.settings, repository.openSession, operationState) { data, profileData, settings, openSession, operation ->
        data.copy(profiles = profileData.first, profileCourses = profileData.second, studentProfile = profileData.third, settings = settings, openSession = openSession, message = operation.first, error = operation.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsatUiState())

    init {
        viewModelScope.launch {
            if (repository.currentSettings() == null) {
                val year = LocalDate.now().year
                repository.saveSettings(AppSettingsEntity(academicPeriod = "Semester 1 · $year/${(year + 1) % 100}"))
            }
            val open = repository.currentSession()
            if (open?.state == "active" && elapsedSeconds(open, System.currentTimeMillis()) >= open.plannedDurationSeconds) {
                repository.completeSession(open.id, System.currentTimeMillis(), elapsedSeconds(open, System.currentTimeMillis()))
            }
        }
    }

    fun addCourse(code: String, name: String, targetHours: Float, period: String) = perform("Course added.") {
        require(code.isNotBlank() && name.isNotBlank()) { "Enter a course code and name." }
        require(targetHours in .5f..80f) { "Set a weekly target between 0.5 and 80 hours." }
        require(period.isNotBlank()) { "Choose an academic period first." }
        repository.addCourse(CourseEntity(code.trim().uppercase(), name.trim(), period, targetHours))
    }

    fun saveStudentAcademicProfile(student: StudentProfileEntity, profile: AcademicProfileEntity, courseCodes: List<String>) = perform("Student and academic profile saved.") {
        val username = student.username.trim().lowercase(java.util.Locale.ROOT)
        val fullName = student.fullName.trim()
        val email = student.email?.trim()?.takeIf { it.isNotEmpty() }
        val phone = student.phoneNumber?.trim()?.takeIf { it.isNotEmpty() }
        val university = student.universityName.trim()
        val faculty = student.faculty.trim()
        val programme = student.programme.trim()
        val registration = student.registrationNumber?.trim()?.takeIf { it.isNotEmpty() }
        require(username.matches(Regex("^[a-z0-9._-]{3,24}$"))) { "Username must be 3–24 characters and use letters, numbers, dots, underscores, or hyphens." }
        require(fullName.length in 2..80) { "Enter your full name (2–80 characters)." }
        require(email == null || (email.length <= 254 && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())) { "Enter a valid email address or leave it blank." }
        require(phone == null || phone.matches(Regex("^\\+?[0-9() .-]{7,20}$"))) { "Enter a valid phone number or leave it blank." }
        require(university.length in 2..120) { "Enter your university or institution name (2–120 characters)." }
        require(faculty.length in 2..120) { "Enter your faculty or school (2–120 characters)." }
        require(programme.length in 2..120) { "Enter your programme or degree (2–120 characters)." }
        require(registration == null || registration.length <= 40) { "Student registration number must be 40 characters or fewer." }
        val academicYear = profile.academicYear.trim()
        val yearOfStudy = profile.yearOfStudy?.trim().orEmpty()
        val semester = profile.semester?.trim()?.takeIf { it.isNotEmpty() }
        require(academicYear.length in 4..30) { "Enter an academic year, for example 2026/2027." }
        require(yearOfStudy.length in 2..40) { "Enter your year of study, for example Third Year." }
        require(semester == null || semester.length <= 40) { "Semester or term must be 40 characters or fewer." }
        require(profile.weeklyStudyGoalHours in .5f..80f) { "Set a weekly study goal between 0.5 and 80 hours." }
        require(!repository.usernameInUse(username, student.id)) { "That username is already used by another local profile." }
        val courseSet = courseCodes.distinct()
        require(courseSet.all { repository.courseExists(it) }) { "Select courses that are saved in USAT." }
        if (profile.id != 0L) require(repository.currentProfile(profile.id) != null) { "This academic record no longer exists. Return to profiles and choose another." }
        repository.saveStudentAndAcademicProfile(
            student.copy(username = username, fullName = fullName, email = email, phoneNumber = phone, universityName = university, faculty = faculty, programme = programme, registrationNumber = registration),
            profile.copy(academicYear = academicYear, yearOfStudy = yearOfStudy, semester = semester),
            courseSet
        )
    }

    fun selectAcademicProfile(id: Long) = perform("Academic profile selected.") {
        require(repository.currentProfile(id) != null) { "That academic profile is no longer available." }
        repository.setActiveProfile(id)
    }

    fun changeTarget(code: String, hours: Float) = perform("Study target updated.") {
        require(hours in .5f..80f) { "Set a weekly target between 0.5 and 80 hours." }
        repository.updateTarget(code, hours)
    }

    fun updateCourse(course: CourseEntity) = perform("Course details updated.") {
        require(course.name.isNotBlank()) { "Enter a course name." }
        require(course.weeklyTargetHours in .5f..80f) { "Set a weekly target between 0.5 and 80 hours." }
        repository.updateCourse(course)
    }

    fun deleteCourse(code: String) = perform("Course deleted. Related assessments were removed; activities and sessions were kept without a course link.") {
        repository.deleteCourse(code)
    }

    fun addActivity(activity: ActivityEntity) = perform("Activity saved.") {
        require(activity.title.isNotBlank()) { "Enter an activity name." }
        require(activity.plannedHours in .25f..24f) { "Planned duration must be between 15 minutes and 24 hours." }
        repository.addActivity(activity)
    }

    fun updateActivity(activity: ActivityEntity) = perform("Activity updated.") {
        require(activity.title.isNotBlank()) { "Enter an activity name." }
        require(activity.plannedHours in .25f..24f) { "Planned duration must be between 15 minutes and 24 hours." }
        repository.updateActivity(activity)
    }
    fun setActivityCompleted(id: Long, completed: Boolean) = perform(if (completed) "Activity completed." else "Activity reopened.") { repository.setActivityCompleted(id, completed) }
    fun deleteActivity(id: Long) = perform("Activity deleted.") { repository.deleteActivity(id) }

    fun addAssessment(assessment: AssessmentEntity) = perform("Assessment result saved.") {
        require(assessment.title.isNotBlank()) { "Enter an assessment name." }
        require(assessment.maxScore > 0f && assessment.score in 0f..assessment.maxScore) { "Enter a score between zero and the maximum score." }
        repository.addAssessment(assessment)
    }

    fun updateAssessment(assessment: AssessmentEntity) = perform("Assessment result updated.") {
        require(assessment.title.isNotBlank()) { "Enter an assessment name." }
        require(assessment.maxScore > 0f && assessment.score in 0f..assessment.maxScore) { "Enter a score between zero and the maximum score." }
        repository.updateAssessment(assessment)
    }

    fun deleteAssessment(id: Long) = perform("Assessment removed.") { repository.deleteAssessment(id) }

    fun updateAcademicPeriod(period: String) = perform("Academic period updated.") {
        val existing = repository.currentSettings()
        repository.saveSettings((existing ?: AppSettingsEntity(academicPeriod = period)).copy(academicPeriod = period))
    }

    fun updateEventFilter(filter: String) = perform("Event filter saved.") {
        val existing = repository.currentSettings()
        repository.saveSettings((existing ?: AppSettingsEntity(academicPeriod = defaultPeriod())).copy(eventCategoryFilter = filter))
    }

    fun updateSelectedEventDate(date: LocalDate) = perform(null) {
        val existing = repository.currentSettings()
        repository.saveSettings((existing ?: AppSettingsEntity(academicPeriod = defaultPeriod())).copy(selectedEventDateEpochDay = date.toEpochDay()))
    }

    fun toggleFocusSession(courseCode: String?) = perform(null) {
        val now = System.currentTimeMillis()
        when (val session = repository.currentSession()) {
            null -> repository.startSession(StudySessionEntity(courseCode = courseCode, startedAtMillis = now))
            else -> when (session.state) {
                "active" -> {
                    val elapsed = elapsedSeconds(session, now)
                    if (elapsed >= session.plannedDurationSeconds) repository.completeSession(session.id, now, elapsed)
                    else repository.pauseSession(session.id, now, elapsed)
                }
                "paused" -> repository.resumeSession(session.id, now)
            }
        }
    }

    fun resetFocusSession() = perform("Focus session saved.") {
        val now = System.currentTimeMillis()
        repository.currentSession()?.let { session ->
            repository.completeSession(session.id, now, elapsedSeconds(session, now))
        }
    }

    fun completeFocusSession(id: Long) = viewModelScope.launch {
        runCatching {
            val now = System.currentTimeMillis()
            repository.currentSession()?.takeIf { it.id == id && it.state == "active" }?.let { repository.completeSession(id, now, elapsedSeconds(it, now)) }
        }.onFailure { setError(it.message ?: "Could not save the focus session.") }
    }

    fun clearMessage() { operationState.value = null to null }

    private fun perform(success: String?, action: suspend () -> Unit) = viewModelScope.launch {
        try {
            action()
            success?.let { operationState.value = it to null }
        } catch (error: Exception) {
            setError(error.message?.takeIf { it.isNotBlank() } ?: "Could not save this change. Please try again.")
        }
    }

    private fun setError(error: String) { operationState.value = null to error }

    companion object {
        fun elapsedSeconds(session: StudySessionEntity, now: Long): Int = session.accumulatedSeconds + if (session.state == "active") ((now - session.startedAtMillis).coerceAtLeast(0) / 1000L).toInt() else 0
        private fun defaultPeriod(): String {
            val year = LocalDate.now().year
            return "Semester 1 · $year/${(year + 1) % 100}"
        }
    }
}
