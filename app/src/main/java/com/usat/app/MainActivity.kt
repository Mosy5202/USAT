package com.usat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.usat.app.data.ActivityEntity
import com.usat.app.data.AcademicProfileEntity
import com.usat.app.data.StudentProfileEntity
import com.usat.app.data.AssessmentEntity
import com.usat.app.data.CourseEntity
import com.usat.app.data.StudySessionEntity
import com.usat.app.data.UsatCalculations
import com.usat.app.data.UsatViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val Navy = Color(0xFF14213D)
private val Amber = Color(0xFFFCA311)
private val Canvas = Color(0xFFF5F6F8)
private val Muted = Color(0xFF6B7280)
private val Line = Color(0xFFE8E9ED)
private val Green = Color(0xFF16855B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UsatApp() }
    }
}

private data class StudyTask(
    val id: Long,
    val title: String,
    val courseCode: String?,
    val course: String,
    val due: String,
    val dueAtMillis: Long?,
    val priority: String,
    val type: String = "Academic task",
    val smartReminder: Boolean = false,
    val plannedHours: Float = 1f,
    val done: Boolean = false
)
private data class Course(val code: String, val name: String, val studied: Float, val target: Float, val mark: String, val previousMark: String = "N/A")
private data class AgendaActivity(val title: String, val detail: String, val time: String, val category: String, val hours: Float)
private enum class Page(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Default.Home), Schedule("Events", Icons.Default.DateRange),
    Courses("Courses", Icons.Default.MenuBook), Analytics("Analytics", Icons.Default.BarChart), Advisor("AI Advisor", Icons.Default.AutoAwesome),
    Profiles("Academic profiles", Icons.Default.AccountCircle), Profile("Academic profile", Icons.Default.AccountCircle), ProfileForm("Profile form", Icons.Default.Edit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsatApp() {
    val viewModel: UsatViewModel = viewModel()
    val data by viewModel.uiState.collectAsStateWithLifecycle()
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(data.openSession?.id, data.openSession?.state) {
        while (data.openSession?.state == "active") {
            clockNow = System.currentTimeMillis()
            val active = data.openSession ?: break
            if (UsatViewModel.elapsedSeconds(active, clockNow) >= active.plannedDurationSeconds) {
                viewModel.completeFocusSession(active.id)
                break
            }
            delay(1_000)
        }
    }
    val currentWeekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val allCourses = data.courses.map { entity ->
        val studied = UsatCalculations.completedStudyHours(data.sessions.filter { it.courseCode == entity.code && (it.endedAtMillis ?: 0L) >= currentWeekStart })
        val marks = data.assessments.filter { it.courseCode == entity.code }.sortedByDescending { it.recordedAtMillis }
        val average = UsatCalculations.weightedAssessmentPercent(marks)
        val previous = marks.drop(1).takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.score.toDouble() } / list.sumOf { it.maxScore.toDouble() } * 100.0 }
        Course(entity.code, entity.name, studied, entity.weeklyTargetHours, average?.let { "${it.toInt()}%" } ?: "N/A", previous?.let { "${it.toInt()}%" } ?: "N/A")
    }
    val tasks = data.activities.map { entity ->
        StudyTask(entity.id, entity.title, entity.courseCode, entity.courseLabel, entity.dueLabel, entity.dueAtMillis, entity.priority, entity.category, entity.smartReminder, entity.plannedHours, entity.completed)
    }
    var page by remember { mutableStateOf(Page.Overview) }
    val academicPeriod = data.settings?.academicPeriod?.takeIf { it.isNotBlank() } ?: currentDefaultPeriod()
    var courseQuery by remember { mutableStateOf("") }
    var selectedCourseCode by remember { mutableStateOf("") }
    var showAddCourse by remember { mutableStateOf(false) }
    var courseToEdit by remember { mutableStateOf<CourseEntity?>(null) }
    var profileToEdit by remember { mutableStateOf<AcademicProfileEntity?>(null) }
    var activityToEdit by remember { mutableStateOf<ActivityEntity?>(null) }
    var assessmentToEdit by remember { mutableStateOf<AssessmentEntity?>(null) }
    var analyticsPeriod by remember { mutableStateOf("This week") }
    var analyticsCourse by remember { mutableStateOf("All courses") }
    var trendMode by remember { mutableStateOf("Both") }
    var showAnalyticsEvidence by rememberSaveable { mutableStateOf(false) }
    var showPlanInfo by remember { mutableStateOf(false) }
    var eventFilter by remember { mutableStateOf("All") }
    var showAdd by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showOverviewWhy by rememberSaveable { mutableStateOf(false) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    var showAddAssessment by remember { mutableStateOf(false) }
    LaunchedEffect(data.settings?.eventCategoryFilter) { eventFilter = data.settings?.eventCategoryFilter ?: "All" }
    val selectedEventDate = data.settings?.selectedEventDateEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
    val activeProfile = data.profiles.firstOrNull { it.id == data.settings?.activeProfileId } ?: data.profiles.firstOrNull()
    val activeProfileCourseCodes = data.profileCourses.filter { it.profileId == activeProfile?.id }.map { it.courseCode }.toSet()
    val activeCourseCodes = data.courses.filter { it.academicPeriod == academicPeriod && (activeProfile == null || it.code in activeProfileCourseCodes) }.map { it.code }.toSet()
    val courses = allCourses.filter { it.code in activeCourseCodes }
    val weeklyStudyGoal = activeProfile?.weeklyStudyGoalHours ?: courses.sumOf { it.target.toDouble() }.toFloat()
    val timerRunning = data.openSession?.state == "active"
    val seconds = data.openSession?.let { (it.plannedDurationSeconds - UsatViewModel.elapsedSeconds(it, clockNow)).coerceAtLeast(0) } ?: 25 * 60
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(data.message, data.error) {
        val notice = data.error ?: data.message
        if (notice != null) { snackbarHostState.showSnackbar(notice); viewModel.clearMessage() }
    }
    LaunchedEffect(data.message) {
        if (data.message == "Student and academic profile saved.") {
            profileToEdit = null
            page = Page.Profile
        } else if (data.message == "Academic profile selected.") {
            page = Page.Profile
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Amber, background = Canvas, surface = Color.White)) {
        if (showWelcome) WelcomeScreen(hasProfile = data.profiles.isNotEmpty(), onContinue = {
            showWelcome = false
            page = if (data.profiles.isEmpty()) Page.Profiles else Page.Profile
        }) else {
        Scaffold(containerColor = Canvas, snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Image(painterResource(R.drawable.usat_mark), "USAT app icon", Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                    Text("USAT", color = Navy, fontWeight = FontWeight.Bold)
                } },
                actions = {
                    IconButton(onClick = { showNotifications = true }) { Icon(Icons.Default.NotificationsNone, "Notifications", tint = Navy) }
                    IconButton(onClick = { page = Page.Profiles }) { Icon(Icons.Default.AccountCircle, "Academic profiles", tint = Navy) }
                    if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 380) {
                        FilledTonalButton(onClick = { showAdd = true }, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Amber, contentColor = Navy)) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Activity", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.AddCircle, "Add activity", tint = Amber) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Page.entries.take(5).forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Navy, indicatorColor = Amber.copy(alpha = .25f), selectedTextColor = Navy, unselectedTextColor = Muted))
                }
            }
        }, floatingActionButton = {
            if (page == Page.Schedule || page == Page.Courses || page == Page.Advisor) ExtendedFloatingActionButton(onClick = { if (page == Page.Advisor) page = Page.Overview else showAdd = true }, icon = { Icon(if (page == Page.Advisor) Icons.Default.Home else Icons.Default.Add, null) }, text = { Text(if (page == Page.Advisor) "Dashboard" else "Add activity") }, containerColor = Amber, contentColor = Navy)
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (data.loading) item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) } }
                item { Heading(page, data.studentProfile?.fullName) }
                when (page) {
                    Page.Overview -> {
                        item { OverviewDailyBriefCard(tasks, courses, weeklyStudyGoal, showOverviewWhy, onWhyClick = { showOverviewWhy = !showOverviewWhy }) }
                        item { FocusCard(timerRunning, data.openSession?.state == "paused", seconds, onToggle = { viewModel.toggleFocusSession(selectedCourseCode.takeIf { code -> courses.any { it.code == code } }) }, onReset = { viewModel.resetFocusSession() }, courses = courses, selectedCourse = selectedCourseCode, onCourseSelect = { selectedCourseCode = it }) }
                        val firstOpenTask = tasks.filter { !it.done }.minByOrNull { it.dueAtMillis ?: Long.MAX_VALUE }
                        val studiedHours = courses.sumOf { it.studied.toDouble() }.toFloat()
                        val targetHours = weeklyStudyGoal
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OverviewSummaryCard("Priority tasks", Icons.Default.CheckCircle, "${tasks.count { !it.done }}", "open tasks", firstOpenTask?.let { "Due soon · ${it.title}" } ?: "No tasks need attention", "Review tasks", Modifier.weight(1f), Amber) { page = Page.Schedule }
                                val dueToday = tasks.firstOrNull { it.dueAtMillis?.let { time -> Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() } == true && !it.done }
                                OverviewSummaryCard("Today", Icons.Default.DateRange, dueToday?.due?.substringAfter(", ") ?: if (dueToday == null) "Clear" else dueToday.due, if (dueToday == null) "no due task" else "due today", dueToday?.title ?: "No activities due today", "Open events", Modifier.weight(1f), Navy) { page = Page.Schedule }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val progress = UsatCalculations.targetProgressFraction(studiedHours, targetHours)
                                OverviewSummaryCard("Study progress", Icons.Default.BarChart, "${(progress * 100).toInt()}%", "of target", "${studiedHours.prettyHours()} of ${targetHours.prettyHours()} study hours", "View courses", Modifier.weight(1f), Amber, progress = progress) { page = Page.Courses }
                                OverviewSummaryCard("Upcoming", Icons.Default.Schedule, firstOpenTask?.due?.substringBefore(",") ?: "All clear", if (firstOpenTask == null) "deadlines" else "next deadline", firstOpenTask?.let { "${it.due} · ${it.title}" } ?: "No upcoming deadlines", "See deadlines", Modifier.weight(1f), Navy) { page = Page.Schedule }
                            }
                        }
                    }
                    Page.Schedule -> {
                        val today = selectedEventDate
                        val weekStart = today.with(java.time.DayOfWeek.MONDAY)
                        val weekEnd = weekStart.plusDays(7)
                        val weekTasks = tasks.filter { it.dueAtMillis?.let { millis -> Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() in weekStart..<weekEnd } == true }
                        val academicHours = weekTasks.filter { it.type in listOf("Academic task", "Class or assignment") }.sumOf { it.plannedHours.toDouble() }.toFloat()
                        val studyHours = weekTasks.filter { it.type == "Study session" }.sumOf { it.plannedHours.toDouble() }.toFloat()
                        val extracurricularHours = weekTasks.filter { it.type in listOf("Extracurricular task", "Personal activity") }.sumOf { it.plannedHours.toDouble() }.toFloat()
                        val totalHours = weekTasks.sumOf { it.plannedHours.toDouble() }.toFloat()
                        val targetHours = weeklyStudyGoal
                        val weekStartMillis = weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val weekEndMillis = weekEnd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val studiedHours = UsatCalculations.completedStudyHours(data.sessions.filter { session -> session.endedAtMillis?.let { it >= weekStartMillis && it < weekEndMillis } == true })
                        item { EventsWeekHeader(onAdd = { showAdd = true }, date = today) }
                        item { WeekStrip(date = today, onSelect = viewModel::updateSelectedEventDate) }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                EventMetricCard(totalHours.durationLabel(), "Planned this week", Icons.Default.EventAvailable, Amber, Modifier.weight(1f))
                                EventMetricCard("${studiedHours.prettyHours()} / ${targetHours.prettyHours()}h", "Study target", Icons.Default.AccessTime, Navy, Modifier.weight(1f), if (targetHours > 0f) studiedHours / targetHours else 0f)
                            }
                        }
                        item { BalanceCard(academicHours, studyHours, extracurricularHours) }
                        item { TodayPlanHeader(today) }
                        item { EventCategoryFilters(eventFilter, onSelect = { eventFilter = it; viewModel.updateEventFilter(it) }) }
                        val selectedTasks = tasks.filter { it.dueAtMillis?.let { millis -> Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == today } == true && (eventFilter == "All" || categoryFor(it.type) == eventFilter) }
                        val todaysSessions = data.sessions.filter { session -> (session.state == "completed" || session.state == "active") && Instant.ofEpochMilli(session.startedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate() == today }
                        if (selectedTasks.isEmpty() && todaysSessions.isEmpty()) item { EmptyState("No activities planned for today", "Add an activity with a due date to see it in today’s plan.") }
                        items(selectedTasks, key = { "activity-${it.id}" }) { task -> TaskCard(task, onToggle = { viewModel.setActivityCompleted(task.id, !task.done) }, onEdit = { activityToEdit = data.activities.firstOrNull { it.id == task.id }; showAdd = true }, onDelete = { viewModel.deleteActivity(task.id) }) }
                        items(todaysSessions, key = { "session-${it.id}" }) { session -> StudySessionEventCard(session) }
                        item { EventsAdvisorCard(academicHours + studyHours, extracurricularHours, courses, tasks) }
                        val remindersOn = tasks.any { !it.done && it.smartReminder }
                        item { ReminderStatusCard(remindersOn, tasks.count { !it.done && it.smartReminder }) }
                    }
                    Page.Courses -> {
                        item { PeriodPicker(academicPeriod, onSelect = { viewModel.updateAcademicPeriod(it) }) }
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedTextField(courseQuery, { courseQuery = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Find a course", fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, shape = RoundedCornerShape(13.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
                            IconButton(onClick = { showAddCourse = true }, modifier = Modifier.size(46.dp).background(Amber, RoundedCornerShape(13.dp))) { Icon(Icons.Default.Add, "Add course", tint = Navy) }
                        } }
                        val averageMark = UsatCalculations.weightedAssessmentPercent(data.assessments.filter { assessment -> courses.any { it.code == assessment.courseCode } })
                        val currentGpa = averageMark?.let { "%.2f".format(percentToGpa(it)) } ?: "—"
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CourseOverviewMetric("Active courses", courses.size.toString(), "in this academic period", Icons.Default.MenuBook, Amber, Modifier.weight(1f))
                            CourseOverviewMetric("Current GPA", currentGpa, if (averageMark == null) "Add assessment results" else "Based on current marks", Icons.Default.School, Navy, Modifier.weight(1f))
                        } }
                        item { CourseSectionHeading(academicPeriod) }
                        val filteredCourses = courses.filter { "${it.code} ${it.name}".contains(courseQuery.trim(), ignoreCase = true) }
                        val selectedCourse = filteredCourses.firstOrNull { it.code == selectedCourseCode } ?: filteredCourses.firstOrNull()
                        if (filteredCourses.isEmpty()) item { EmptyState(if (courses.isEmpty()) "No courses in this period" else "No matching courses", if (courses.isEmpty()) "Add a course to start recording targets, sessions, and assessments." else "Try a different search or add this course.") }
                        else item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            filteredCourses.forEach { course ->
                                val dueTasks = tasks.filter { !it.done && it.courseCode == course.code }.sortedBy { it.dueAtMillis ?: Long.MAX_VALUE }
                                CourseCard(course, selected = selectedCourse?.code == course.code, dueSoonCount = dueTasks.size, dueSoonTask = dueTasks.firstOrNull()) { selectedCourseCode = course.code }
                            }
                        } }
                        if (selectedCourse != null) {
                            val selectedEntity = data.courses.first { it.code == selectedCourse.code }
                            val courseAssessments = data.assessments.filter { it.courseCode == selectedCourse.code }
                            item { CoursePerformanceCard(selectedCourse, courseAssessments, onAddAssessment = { assessmentToEdit = null; showAddAssessment = true }, onEditAssessment = { assessmentToEdit = it; showAddAssessment = true }, onDeleteAssessment = { viewModel.deleteAssessment(it.id) }, onEditCourse = { courseToEdit = selectedEntity; showAddCourse = true }, onDeleteCourse = { viewModel.deleteCourse(selectedCourse.code); selectedCourseCode = "" }) }
                            item { TargetEditorCard(selectedCourse, onTargetChange = { target -> viewModel.changeTarget(selectedCourse.code, target) }) }
                            item { CourseInsightCard(selectedCourse) }
                            item { SectionTitle("Upcoming coursework", "Assignments and tasks for ${selectedCourse.code}") }
                            val courseTasks = tasks.filter { !it.done && it.courseCode == selectedCourse.code }
                            if (courseTasks.isEmpty()) item { EmptyState("No upcoming coursework", "Add an activity and assign it to ${selectedCourse.code} to see it here.") }
                            else items(courseTasks) { task -> TaskCard(task, onToggle = { viewModel.setActivityCompleted(task.id, !task.done) }, onEdit = { activityToEdit = data.activities.firstOrNull { it.id == task.id }; showAdd = true }, onDelete = { viewModel.deleteActivity(task.id) }) }
                        }
                    }
                    Page.Analytics -> {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterDropdown("Period", analyticsPeriod, listOf("This week", "This month", "This semester", "This academic year"), Modifier.weight(1f)) { analyticsPeriod = it }
                                FilterDropdown("Course", analyticsCourse, listOf("All courses") + courses.map { it.code }, Modifier.weight(1f)) { analyticsCourse = it }
                            }
                        }
                        val analyticsCourses = if (analyticsCourse == "All courses") courses else courses.filter { it.code == analyticsCourse }
                        val range = analyticsRange(analyticsPeriod, LocalDate.now(), academicPeriod)
                        val selectedCodes = analyticsCourses.map { it.code }.toSet()
                        val analyticsSessions = data.sessions.filter { it.state == "completed" && it.endedAtMillis != null && it.startedAtMillis in range.first..range.second && (analyticsCourse == "All courses" || it.courseCode in selectedCodes) }
                        val analyticsAssessments = data.assessments.filter { it.recordedAtMillis in range.first..range.second && (analyticsCourse == "All courses" || it.courseCode in selectedCodes) }
                        val weeklyStudy = UsatCalculations.completedStudyHours(analyticsSessions)
                        val selectedWeeklyGoal = if (analyticsCourse == "All courses") weeklyStudyGoal else analyticsCourses.sumOf { it.target.toDouble() }.toFloat()
                        val targetStudy = selectedWeeklyGoal * (ChronoUnit.DAYS.between(Instant.ofEpochMilli(range.first).atZone(ZoneId.systemDefault()).toLocalDate(), Instant.ofEpochMilli(range.second).atZone(ZoneId.systemDefault()).toLocalDate()).coerceAtLeast(1).toFloat() / 7f)
                        val averageMark = UsatCalculations.weightedAssessmentPercent(analyticsAssessments)
                        val gpa = averageMark?.let(::percentToGpa)
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnalyticsSummaryCard("Current GPA", gpa?.let { "${"%.2f".format(it)}" } ?: "—", if (gpa == null) "Record assessment results to calculate GPA" else "From ${analyticsAssessments.size} recorded result${if (analyticsAssessments.size == 1) "" else "s"}", Modifier.weight(1f), Navy, Icons.Default.School)
                            AnalyticsSummaryCard("Study time", "${weeklyStudy.prettyHours()}h", if (targetStudy > 0f) "Weekly targets prorated to period: ${targetStudy.prettyHours()}h" else "No course targets in this selection", Modifier.weight(1f), Amber, Icons.Default.AccessTime)
                        } }
                        item { AnalyticsInsightCard(performanceInsight(analyticsCourses, weeklyStudy, targetStudy), showAnalyticsEvidence, onToggleEvidence = { showAnalyticsEvidence = !showAnalyticsEvidence }) }
                        item { PerformanceStudyTrendCard(trendMode, onModeChange = { trendMode = it }, courses = analyticsCourses, period = analyticsPeriod, sessions = analyticsSessions, assessments = analyticsAssessments) }
                        item { TimeAllocationCard(analyticsSessions, analyticsTasks = tasks.filter { it.dueAtMillis?.let { due -> due in range.first..range.second } == true }) }
                        item { SectionTitle("Course performance", "Grades compared with study-target completion") }
                        items(analyticsCourses) { course -> CourseAnalyticsCard(course) }
                        item { SectionTitle("Focus patterns", "Study intensity by time of day") }
                        item { FocusPatternCard(analyticsPeriod, analyticsSessions) }
                        item { PlanBuilderCard(onClick = { showPlanInfo = true }) }
                    }
                    Page.Advisor -> {
                        item { SectionTitle("AI Advisor", "Personalized advice is not connected yet") }
                        item { EmptyState("No generated recommendations", "Your courses, activities, and study records are saved locally. AI advice will appear here after a real recommendation service is connected.") }
                    }
                    Page.Profiles -> {
                        item { SectionTitle("Student profile", if (data.studentProfile == null) "Create your local student and academic profile" else "Student details and academic records") }
                        if (data.profiles.isEmpty()) {
                            item { EmptyState("No student profile yet", "Add your personal and university information with your first academic record.") }
                        } else {
                            items(data.profiles, key = { "profile-${it.id}" }) { profile ->
                                ProfileListItem(profile = profile, courseCount = data.profileCourses.count { it.profileId == profile.id }, selected = activeProfile?.id == profile.id) {
                                    viewModel.selectAcademicProfile(profile.id)
                                }
                            }
                        }
                        item { Button(onClick = { profileToEdit = null; page = Page.ProfileForm }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(if (data.studentProfile == null) "Create Student Profile" else "Add Academic Profile") } }
                    }
                    Page.Profile -> {
                        val profileCourses = data.courses.filter { course -> data.profileCourses.any { it.profileId == activeProfile?.id && it.courseCode == course.code } }
                        if (activeProfile == null) {
                            item { EmptyState("No academic profile selected", "Create an academic profile to get started.") }
                            item { Button(onClick = { profileToEdit = null; page = Page.ProfileForm }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)) { Text(if (data.studentProfile == null) "Create Student Profile" else "Add Academic Profile") } }
                        } else {
                            item { AcademicProfileDetailsCard(student = data.studentProfile, profile = activeProfile, courses = profileCourses, onEdit = { profileToEdit = activeProfile; page = Page.ProfileForm }, onCreateNew = { profileToEdit = null; page = Page.ProfileForm }, onAllProfiles = { page = Page.Profiles }) }
                        }
                    }
                    Page.ProfileForm -> {
                        item { SectionTitle(when { profileToEdit != null -> "Edit student and academic profile"; data.studentProfile == null -> "Create student profile"; else -> "Add academic profile" }, "Personal details are shared across your academic records") }
                        item {
                            AcademicProfileFormCard(
                                studentProfile = data.studentProfile,
                                profile = profileToEdit,
                                courses = data.courses,
                                selectedCourseCodes = data.profileCourses.filter { it.profileId == profileToEdit?.id }.map { it.courseCode }.toSet(),
                                onSave = viewModel::saveStudentAcademicProfile,
                                onAddCourse = { showAddCourse = true },
                                onCancel = { page = if (profileToEdit == null) Page.Profiles else Page.Profile; profileToEdit = null }
                            )
                        }
                    }
                }
            }
        }
        }
    }
    if (!showWelcome && showAdd) AddActivityDialog(courses = courses, initial = activityToEdit, onDismiss = { showAdd = false; activityToEdit = null }, onSave = { activity ->
        if (activity.id == 0L) viewModel.addActivity(activity) else viewModel.updateActivity(activity)
        showAdd = false
        activityToEdit = null
    })
    if (!showWelcome && showAddCourse) AddCourseDialog(initial = courseToEdit, period = academicPeriod, onDismiss = { showAddCourse = false; courseToEdit = null }, onSave = { code, name, target ->
        val existing = courseToEdit
        if (existing == null) viewModel.addCourse(code, name, target, academicPeriod) else viewModel.updateCourse(existing.copy(name = name, weeklyTargetHours = target))
        selectedCourseCode = code
        courseQuery = ""
        showAddCourse = false
        courseToEdit = null
    })
    if (!showWelcome && showAddAssessment) {
        val selectedCourse = courses.firstOrNull { it.code == selectedCourseCode }
        if (selectedCourse != null) AddAssessmentDialog(selectedCourse, assessmentToEdit, onDismiss = { showAddAssessment = false; assessmentToEdit = null }, onSave = { title, score, maxScore ->
            val old = assessmentToEdit
            val record = if (old == null) AssessmentEntity(courseCode = selectedCourse.code, title = title, score = score, maxScore = maxScore) else old.copy(title = title, score = score, maxScore = maxScore)
            if (old == null) viewModel.addAssessment(record) else viewModel.updateAssessment(record)
            showAddAssessment = false
            assessmentToEdit = null
        })
    }
    if (!showWelcome && showNotifications) AlertDialog(
        onDismissRequest = { showNotifications = false },
        icon = { Icon(Icons.Default.NotificationsNone, null, tint = Navy) },
        title = { Text("Notifications", color = Navy, fontWeight = FontWeight.Bold) },
        text = { EmptyState("You’re all caught up", "There are no new notifications right now. Enable a smart reminder when adding an activity to flag it for follow-up.") },
        confirmButton = { TextButton(onClick = { showNotifications = false }) { Text("Done", color = Navy) } },
        containerColor = Color.White
    )
    if (!showWelcome && showPlanInfo) AlertDialog(
        onDismissRequest = { showPlanInfo = false },
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = Amber) },
        title = { Text("Build next week’s plan", color = Navy, fontWeight = FontWeight.Bold) },
        text = { Text("The plan builder interface is ready. Automatic schedule generation will be added later.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) },
        confirmButton = { TextButton(onClick = { showPlanInfo = false }) { Text("Got it", color = Navy) } },
        containerColor = Color.White
    )
}

@Composable private fun WelcomeScreen(hasProfile: Boolean, onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.usat_mark), "USAT", Modifier.size(148.dp).clip(RoundedCornerShape(32.dp)), contentScale = ContentScale.Crop)
            Text("Welcome to USAT", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(if (hasProfile) "Your studies, activities, and goals in one place." else "Create your academic profile to start tracking courses and study goals.", color = Color(0xFFD4DAE5), fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy), modifier = Modifier.fillMaxWidth()) {
                Text(if (hasProfile) "Open my dashboard" else "Create academic profile", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 5.dp))
            }
        }
    }
}

@Composable private fun AcademicProfileFormCard(
    studentProfile: StudentProfileEntity?,
    profile: AcademicProfileEntity?,
    courses: List<CourseEntity>,
    selectedCourseCodes: Set<String>,
    onSave: (StudentProfileEntity, AcademicProfileEntity, List<String>) -> Unit,
    onAddCourse: () -> Unit,
    onCancel: () -> Unit
) {
    var username by remember(studentProfile?.id, profile?.id) { mutableStateOf(studentProfile?.username.orEmpty()) }
    var fullName by remember(studentProfile?.id, profile?.id) { mutableStateOf(studentProfile?.fullName.orEmpty()) }
    var email by remember(studentProfile?.id, profile?.id) { mutableStateOf(studentProfile?.email.orEmpty()) }
    var phone by remember(studentProfile?.id, profile?.id) { mutableStateOf(studentProfile?.phoneNumber.orEmpty()) }
    var university by remember(studentProfile?.id, profile?.id) { mutableStateOf(studentProfile?.universityName.orEmpty()) }
    var faculty by remember(studentProfile?.id, profile?.id) { mutableStateOf(studentProfile?.faculty.orEmpty()) }
    var programme by remember(studentProfile?.id, profile?.id) { mutableStateOf(studentProfile?.programme.orEmpty()) }
    var registrationNumber by remember(studentProfile?.id, profile?.id) { mutableStateOf(studentProfile?.registrationNumber.orEmpty()) }
    var academicYear by remember(profile?.id) { mutableStateOf(profile?.academicYear.orEmpty()) }
    var yearOfStudy by remember(profile?.id) { mutableStateOf(profile?.yearOfStudy.orEmpty()) }
    var semester by remember(profile?.id) { mutableStateOf(profile?.semester.orEmpty()) }
    var weeklyGoal by remember(profile?.weeklyStudyGoalHours) { mutableStateOf(profile?.weeklyStudyGoalHours?.prettyHours().orEmpty()) }
    var selectedCodes by remember(profile?.id) { mutableStateOf(selectedCourseCodes) }
    SurfaceCard {
        Text("Personal information", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username *") }, singleLine = true, supportingText = { Text("3–24 letters, numbers, dots, underscores, or hyphens") })
        OutlinedTextField(fullName, { fullName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Full name *") }, singleLine = true)
        OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email address (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(phone, { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Phone number (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))

        Text("University information", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(university, { university = it }, modifier = Modifier.fillMaxWidth(), label = { Text("University or institution *") }, singleLine = true)
        OutlinedTextField(faculty, { faculty = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Faculty or school *") }, singleLine = true)
        OutlinedTextField(programme, { programme = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Programme or degree *") }, singleLine = true)
        OutlinedTextField(registrationNumber, { registrationNumber = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Student registration number (optional)") }, singleLine = true)

        Text("Academic information", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(yearOfStudy, { yearOfStudy = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Year of study *") }, placeholder = { Text("Third Year") }, singleLine = true)
        OutlinedTextField(
            value = academicYear,
            onValueChange = { academicYear = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Academic year *") },
            placeholder = { Text("2026/2027") },
            singleLine = true
        )
        OutlinedTextField(semester, { semester = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Semester or term (optional)") }, placeholder = { Text("Semester 1") }, singleLine = true)
        OutlinedTextField(
            value = weeklyGoal,
            onValueChange = { weeklyGoal = it.filter { ch -> ch.isDigit() || ch == '.' } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Weekly study-hour target *") },
            placeholder = { Text("12") },
            suffix = { Text("hours") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Text("Your target is saved as hours per week.", color = Muted, fontSize = 10.sp)

        Text("Courses associated with this academic record", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Choose the courses for this year and term.", color = Muted, fontSize = 10.sp)
            }
            TextButton(onClick = onAddCourse) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Text("Add course") }
        }
        if (courses.isEmpty()) {
            Text("No courses added yet", color = Muted, fontSize = 12.sp)
        } else {
            courses.forEach { course ->
                Row(Modifier.fillMaxWidth().background(Canvas, RoundedCornerShape(11.dp)).clickable {
                    selectedCodes = if (course.code in selectedCodes) selectedCodes - course.code else selectedCodes + course.code
                }.padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(course.name, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("${course.code} · ${course.academicPeriod}", color = Muted, fontSize = 9.sp)
                    }
                    Text("${course.weeklyTargetHours.prettyHours()}h/week", color = Navy, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Checkbox(checked = course.code in selectedCodes, onCheckedChange = { checked -> selectedCodes = if (checked) selectedCodes + course.code else selectedCodes - course.code })
                }
            }
        }
        Button(onClick = {
            val student = StudentProfileEntity(
                username = username,
                fullName = fullName,
                email = email,
                phoneNumber = phone,
                universityName = university,
                faculty = faculty,
                programme = programme,
                registrationNumber = registrationNumber
            )
            val academic = AcademicProfileEntity(
                id = profile?.id ?: 0L,
                academicYear = academicYear,
                weeklyStudyGoalHours = weeklyGoal.toFloatOrNull() ?: 0f,
                yearOfStudy = yearOfStudy,
                semester = semester
            )
            onSave(student, academic, selectedCodes.toList())
        }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
        ) {
            Text("Save profile", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 3.dp))
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = Muted) }
    }
}

@Composable private fun AcademicProfileDetailsCard(
    student: StudentProfileEntity?,
    profile: AcademicProfileEntity,
    courses: List<CourseEntity>,
    onEdit: () -> Unit,
    onCreateNew: () -> Unit,
    onAllProfiles: () -> Unit
) {
    SurfaceCard {
        Text("Student profile", color = Amber, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
        Text(student?.fullName ?: "Student details not entered", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (student != null) {
            Text("@${student.username}", color = Muted, fontSize = 12.sp)
            Text("${student.universityName} · ${student.faculty}", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(student.programme, color = Muted, fontSize = 11.sp)
            student.email?.let { Text(it, color = Muted, fontSize = 11.sp) }
            student.phoneNumber?.let { Text(it, color = Muted, fontSize = 11.sp) }
            student.registrationNumber?.let { Text("Registration number · $it", color = Muted, fontSize = 10.sp) }
        }
        HorizontalDivider(color = Line)
        Text("Academic record", color = Amber, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
        Text(profile.yearOfStudy ?: profile.academicYear, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Academic year · ${profile.academicYear}" + (profile.semester?.let { " · $it" } ?: ""), color = Muted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().background(Canvas, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("WEEKLY STUDY GOAL", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                Text("${profile.weeklyStudyGoalHours.prettyHours()} hours/week", color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Default.Flag, null, tint = Amber, modifier = Modifier.size(25.dp))
        }
        Text("Courses · ${courses.size}", color = Navy, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (courses.isEmpty()) Text("No courses associated with this profile yet.", color = Muted, fontSize = 12.sp)
        else courses.forEach { course ->
            Row(Modifier.fillMaxWidth().background(Canvas, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(course.name, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${course.code} · ${course.academicPeriod}", color = Muted, fontSize = 9.sp)
                }
                Text("${course.weeklyTargetHours.prettyHours()}h target", color = Navy, fontSize = 10.sp)
            }
        }
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit Profile") }
        TextButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Add Academic Profile") }
        TextButton(onClick = onAllProfiles, modifier = Modifier.align(Alignment.End)) { Text("All profiles", color = Muted) }
    }
}

@Composable private fun ProfileListItem(profile: AcademicProfileEntity, courseCount: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Amber else Line),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).background(Color(0xFFFFF1D8), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.School, null, tint = Navy) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(profile.yearOfStudy ?: "Academic profile", color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${profile.academicYear}${profile.semester?.let { " · $it" } ?: ""} · $courseCount courses · ${profile.weeklyStudyGoalHours.prettyHours()}h/week", color = Muted, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Heading(page: Page, studentName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(), color = Amber, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
        Text(if (page == Page.Overview) studentName?.let { "Welcome, $it" } ?: "Welcome" else page.label, color = Navy, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
        Text(if (page == Page.Overview) "Here’s what needs your attention today." else "A focused view of your ${page.label.lowercase()}.", color = Muted, fontSize = 13.sp)
    }
}

@Composable private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable private fun OverviewDailyBriefCard(tasks: List<StudyTask>, courses: List<Course>, weeklyGoal: Float, showWhy: Boolean, onWhyClick: () -> Unit) {
    val openTasks = tasks.count { !it.done }
    val studiedHours = courses.sumOf { it.studied.toDouble() }.toFloat()
    val targetHours = weeklyGoal
    val nextTask = tasks.filter { !it.done }.minByOrNull { it.dueAtMillis ?: Long.MAX_VALUE }
    val recommendation = when {
        nextTask != null -> "Review ${nextTask.title}"
        targetHours > studiedHours -> "Plan a focused study block"
        else -> "Your recorded study target is complete"
    }
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Amber))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(34.dp).background(Color(0xFFFFF1D8), RoundedCornerShape(11.dp)), Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Amber, modifier = Modifier.size(18.dp)) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("STUDY PLANNER", color = Amber, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text("A quick look at your saved records", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(recommendation, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                Text(nextTask?.let { "Next open activity: ${it.title}${if (it.due.isNotBlank()) " · ${it.due}" else ""}." } ?: "No open activities are recorded.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFAF9F6))) {
                    BriefMetric("TASKS", "$openTasks left", Modifier.weight(1f))
                    BriefMetric("STUDY", "${studiedHours.prettyHours()} / ${targetHours.prettyHours()}h", Modifier.weight(1f))
                    BriefMetric("NEXT EVENT", nextTask?.due?.substringBefore(",") ?: "None", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().clickable(onClick = onWhyClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (showWhy) "Hide why this was recommended" else "See why this was recommended", color = Color(0xFF9A6000), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Icon(if (showWhy) Icons.Default.ExpandLess else Icons.Default.ArrowForward, null, tint = Color(0xFF9A6000), modifier = Modifier.size(15.dp))
                }
                if (showWhy) {
                    val urgentTask = tasks.filter { !it.done }.maxByOrNull { when (it.priority) { "High" -> 3; "Medium" -> 2; else -> 1 } }
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Why this planner hint is shown", color = Navy, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        RecommendationFactor("01", "Open activity", urgentTask?.let { "${it.title} · ${it.priority} priority" } ?: "No urgent task right now")
                        RecommendationFactor("02", "Study progress", "${studiedHours.prettyHours()} of ${targetHours.prettyHours()} target hours completed.")
                        RecommendationFactor("03", "Recommendation source", "This is a local rule based on saved tasks and sessions; no AI service is connected.")
                    }
                }
            }
        }
    }
}

@Composable private fun BriefMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp)
        Text(value, color = Navy, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable private fun RecommendationFactor(number: String, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFAF9F6)).padding(11.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(23.dp).background(Color(0xFFFFF1D8), RoundedCornerShape(7.dp)), Alignment.Center) { Text(number, color = Color(0xFF9A6000), fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Navy, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable private fun OverviewSummaryCard(
    title: String,
    icon: ImageVector,
    value: String,
    valueLabel: String,
    detail: String,
    actionLabel: String,
    modifier: Modifier = Modifier,
    accent: Color,
    progress: Float? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(172.dp).clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Box(Modifier.size(28.dp).background(if (accent == Amber) Color(0xFFFFF1D8) else Color(0xFFF0F2F7), RoundedCornerShape(9.dp)), Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
                }
            }
            HorizontalDivider(color = Line.copy(alpha = .7f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(value, color = Navy, fontSize = if (value.length > 8) 17.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(valueLabel.uppercase(), color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
            }
            if (progress != null) {
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Amber, trackColor = Canvas)
            }
            Text(detail, Modifier.weight(1f), color = Muted, fontSize = 9.sp, lineHeight = 12.sp, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(actionLabel, color = Navy, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ArrowForward, null, tint = Navy, modifier = Modifier.size(11.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FocusCard(running: Boolean, paused: Boolean, seconds: Int, onToggle: () -> Unit, onReset: () -> Unit, courses: List<Course>, selectedCourse: String, onCourseSelect: (String) -> Unit) {
    var courseMenu by remember { mutableStateOf(false) }
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("FOCUS SESSION", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Navy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(if (running) "Session is being recorded." else "Focus time is recorded from session timestamps.", color = Muted, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = onToggle, modifier = Modifier.height(42.dp).widthIn(min = 88.dp, max = 122.dp), colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                    Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (running) "Pause" else if (paused) "Resume" else "Start", maxLines = 1, softWrap = false, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onReset, modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("Finish session", color = Muted, fontSize = 9.sp, maxLines = 1, softWrap = false) }
            }
        }
        if (courses.isNotEmpty()) {
            ExposedDropdownMenuBox(expanded = courseMenu, onExpandedChange = { courseMenu = !courseMenu }) {
                OutlinedTextField(courses.firstOrNull { it.code == selectedCourse }?.let { "${it.code} · ${it.name}" } ?: "No course", {}, readOnly = true, label = { Text("Session course") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(courseMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true)
                ExposedDropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                    (listOf("No course") + courses.map { "${it.code} · ${it.name}" }).forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onCourseSelect(courses.firstOrNull { "${it.code} · ${it.name}" == option }?.code.orEmpty()); courseMenu = false }) }
                }
            }
        }
    }
}

@Composable private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun TaskCard(task: StudyTask, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Amber, uncheckedColor = Color(0xFFB9C0CA), checkmarkColor = Navy), modifier = Modifier.size(27.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, color = if (task.done) Muted else Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${task.course}  ·  ${task.due}", color = Muted, fontSize = 10.sp)
                Text("${task.type}${if (task.smartReminder) " · Smart reminder" else ""}", color = Muted, fontSize = 9.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(color = if (task.priority == "High") Color(0xFFFFF0EB) else Color(0xFFFFF4DC), shape = CircleShape) {
                    Text(task.priority.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = if (task.priority == "High") Color(0xFFB84A2F) else Color(0xFF9A6000), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Edit, "Edit activity", tint = Muted, modifier = Modifier.size(16.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.DeleteOutline, "Delete activity", tint = Muted, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable private fun EventsWeekHeader(onAdd: () -> Unit, date: LocalDate) {
    val start = date.with(java.time.DayOfWeek.MONDAY)
    val end = start.plusDays(6)
    val dateLabel = "${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(dateLabel, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (date.with(java.time.DayOfWeek.MONDAY) == LocalDate.now().with(java.time.DayOfWeek.MONDAY)) "This week · tap a date to view its plan" else "Selected week · tap a date to view its plan", color = Muted, fontSize = 10.sp)
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(42.dp).background(Amber, RoundedCornerShape(13.dp))) {
            Icon(Icons.Default.Add, "Add event", tint = Navy)
        }
    }
}

@Composable private fun WeekStrip(date: LocalDate, onSelect: (LocalDate) -> Unit) {
    val start = date.with(java.time.DayOfWeek.MONDAY)
    val days = (0..6).map { start.plusDays(it.toLong()) }
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { day ->
                val selected = day == date
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).clickable { onSelect(day) }.background(if (selected) Navy else Color.Transparent).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(day.format(DateTimeFormatter.ofPattern("EEE")), color = if (selected) Amber else Muted, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    Text(day.dayOfMonth.toString(), color = if (selected) Color.White else Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(4.dp).background(if (day == LocalDate.now()) Amber else Color.Transparent, CircleShape))
                }
            }
        }
    }
}

@Composable private fun EventMetricCard(value: String, label: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier, progress: Float? = null) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(34.dp).background(if (accent == Amber) Color(0xFFFFF1D8) else Color(0xFFF0F2F7), RoundedCornerShape(11.dp)), Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(value, color = Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(label, color = Muted, fontSize = 8.sp, maxLines = 1)
                if (progress != null) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Amber, trackColor = Canvas)
            }
        }
    }
}

@Composable private fun TodayPlanHeader(date: LocalDate) {
    val isToday = date == LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(color = Color(0xFFFFF1D8), shape = CircleShape) {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")).uppercase(), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color(0xFF9A6000), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp)
        }
        Text(if (isToday) "Today’s plan" else "Selected day’s plan", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Your activities, grouped by category", color = Muted, fontSize = 10.sp)
    }
}

private fun Float.prettyHours(): String = if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
private fun Float.durationLabel(): String {
    val wholeHours = toInt()
    val minutes = ((this - wholeHours) * 60).toInt()
    return buildString {
        if (wholeHours > 0) append("${wholeHours}h")
        if (minutes > 0) {
            if (isNotEmpty()) append(' ')
            append("${minutes}m")
        }
        if (isEmpty()) append("0m")
    }
}

private fun categoryFor(type: String): String = when (type) {
    "Academic task", "Class or assignment" -> "Academic"
    "Study session" -> "Study"
    "Extracurricular task" -> "Extracurricular"
    else -> "Personal"
}

private fun currentDefaultPeriod(): String {
    val today = LocalDate.now()
    val startYear = if (today.monthValue < 7) today.year - 1 else today.year
    val semester = if (today.monthValue < 7) 2 else 1
    return "Semester $semester · $startYear/${(startYear + 1) % 100}"
}

private fun analyticsRange(period: String, today: LocalDate, academicPeriod: String): Pair<Long, Long> {
    val start = when (period) {
        "This month" -> today.withDayOfMonth(1)
        "This semester" -> {
            val match = Regex("(\\d{4})/(\\d{2,4})").find(academicPeriod)
            val year = match?.groupValues?.get(1)?.toIntOrNull() ?: today.year
            val semester = Regex("Semester (\\d)").find(academicPeriod)?.groupValues?.get(1)?.toIntOrNull() ?: if (today.monthValue < 7) 2 else 1
            LocalDate.of(if (semester == 1) year else year + 1, if (semester == 1) 1 else 7, 1)
        }
        "This academic year" -> LocalDate.of(today.year, 1, 1)
        else -> today.with(java.time.DayOfWeek.MONDAY)
    }
    val endExclusive = when (period) {
        "This month" -> start.plusMonths(1)
        "This semester" -> start.plusMonths(6)
        "This academic year" -> start.plusYears(1)
        else -> start.plusWeeks(1)
    }
    val zone = ZoneId.systemDefault()
    return start.atStartOfDay(zone).toInstant().toEpochMilli() to endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
}

@Composable private fun StudySessionEventCard(session: StudySessionEntity) {
    val elapsed = UsatViewModel.elapsedSeconds(session, System.currentTimeMillis())
    val duration = (elapsed / 3600f)
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(70.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(Instant.ofEpochMilli(session.startedAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")), color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${duration.prettyHours()}h", color = Muted, fontSize = 9.sp)
            }
            Box(Modifier.width(3.dp).height(44.dp).background(Navy, RoundedCornerShape(2.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("STUDY SESSION", color = Navy, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                Text(session.courseCode ?: "Independent study", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(if (session.state == "active") "In progress" else "Recorded", color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable private fun BalanceCard(academic: Float, study: Float, extracurricular: Float) {
    val academicTotal = academic + study
    val ratio = if (extracurricular > 0f) academicTotal / extracurricular else 0f
    val healthy = ratio in 1.5f..3f
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("WEEKLY TIME BALANCE", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text("Academic vs extracurricular", color = Navy, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Compare how your planned commitments use this week’s available time.", color = Muted, fontSize = 11.sp)
            Surface(color = if (healthy) Color(0xFFE8F6F1) else Color(0xFFFFF0EB), shape = CircleShape) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(if (healthy) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (healthy) Green else Color(0xFFB84A2F), modifier = Modifier.size(14.dp))
                    Text(if (healthy) "Healthy balance" else "Needs attention", color = if (healthy) Green else Color(0xFFB84A2F), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("${academicTotal.prettyHours()}h", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text("Academic + study", color = Muted, fontSize = 9.sp) }
                Column(horizontalAlignment = Alignment.End) { Text("${extracurricular.prettyHours()}h", color = Amber, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text("Extracurricular + personal", color = Muted, fontSize = 9.sp) }
            }
            Row(Modifier.fillMaxWidth().height(12.dp).background(Amber, RoundedCornerShape(8.dp))) {
                Box(Modifier.weight(academicTotal.coerceAtLeast(.1f)).fillMaxHeight().background(Navy, RoundedCornerShape(8.dp)))
                Spacer(Modifier.weight(extracurricular.coerceAtLeast(.1f)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("More extracurricular", color = Muted, fontSize = 8.sp); Text("Recommended range", color = Green, fontSize = 8.sp, fontWeight = FontWeight.Bold); Text("More academic", color = Muted, fontSize = 8.sp) }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Classes and academic tasks", color = Muted, fontSize = 10.sp); Text("${academic.prettyHours()}h", color = Navy, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Independent study", color = Muted, fontSize = 10.sp); Text("${study.prettyHours()}h", color = Navy, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Clubs and extracurricular/personal", color = Muted, fontSize = 10.sp); Text("${extracurricular.prettyHours()}h", color = Navy, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
            }
            Row(Modifier.fillMaxWidth().background(Canvas, RoundedCornerShape(12.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(Modifier.size(36.dp).background(Color(0xFFFFF4DC), RoundedCornerShape(10.dp)), Alignment.Center) { Icon(Icons.Default.BarChart, null, tint = Navy, modifier = Modifier.size(20.dp)) }
                Column {
                    Text("YOUR CURRENT RATIO", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp)
                    Text("${"%.1f".format(ratio)} : 1", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("For each extracurricular/personal hour, you have ${"%.1f".format(ratio)} academic and study hours planned.", color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
                }
            }
        }
    }
}

@Composable private fun EventsAdvisorCard(academic: Float, extracurricular: Float, courses: List<Course>, tasks: List<StudyTask>) {
    val ratio = if (extracurricular > 0f) academic / extracurricular else 0f
    val target = courses.sumOf { it.target.toDouble() }.toFloat()
    val studied = courses.sumOf { it.studied.toDouble() }.toFloat()
    val reminders = tasks.count { !it.done && it.smartReminder }
    val advice = when {
        extracurricular <= 0f -> "Add a personal or extracurricular activity to compare how your planned week is balanced."
        ratio > 3f -> "Your academic commitments are more than three times your extracurricular time. Consider protecting a short recovery block this week."
        ratio < 1.5f -> "Extracurricular commitments are taking a larger share of the week. Reserve time for upcoming coursework and study targets."
        studied < target -> "Your weekly balance looks healthy. You have ${(target - studied).prettyHours()} study-target hours left; protect an open block before adding more commitments."
        else -> "Your balance and study target are on track. Keep your planned commitments within the available hours this week."
    }
    Surface(color = Navy, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.TipsAndUpdates, null, tint = Amber); Text("PLANNING INSIGHT", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
            Text(advice, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
            Text("Local summary based on this week’s planned activity hours and recorded study targets${if (reminders > 0) ". $reminders reminder preference${if (reminders == 1) "" else "s"} saved; notifications are not scheduled yet." else "."}", color = Color(0xFFCBD3E1), fontSize = 10.sp)
        }
    }
}

@Composable private fun ReminderStatusCard(enabled: Boolean, count: Int) {
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(if (enabled) Color(0xFFE8F6F1) else Canvas, RoundedCornerShape(12.dp)), Alignment.Center) {
                Icon(Icons.Default.NotificationsActive, null, tint = if (enabled) Green else Muted)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Smart reminders", color = Navy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (enabled) "$count reminder ${if (count == 1) "preference" else "preferences"} saved · delivery not connected" else "Off · no activities have reminders enabled", color = Muted, fontSize = 10.sp)
            }
            Text(if (enabled) "ON" else "OFF", color = if (enabled) Green else Muted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable private fun EventCategoryFilters(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf("All", "Academic", "Study", "Personal", "Extracurricular").forEach { category ->
            FilterChip(selected = selected == category, onClick = { onSelect(category) }, label = { Text(category, fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Amber, selectedLabelColor = Navy), modifier = Modifier.height(34.dp))
        }
    }
}

@Composable private fun AgendaCard(activity: AgendaActivity) {
    val tone = when (activity.category) { "Academic" -> Amber; "Study" -> Navy; "Personal" -> Green; else -> Color(0xFF8A94A6) }
    val tint = when (activity.category) { "Academic" -> Color(0xFFFFF3DC); "Study" -> Color(0xFFEEF1F7); "Personal" -> Color(0xFFEAF6F1); else -> Color(0xFFF4F4F2) }
    Surface(color = tint, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(77.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(activity.time.substringBefore(" – "), color = Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text("${activity.hours.prettyHours()}h", color = Muted, fontSize = 9.sp) }
            Box(Modifier.width(3.dp).height(47.dp).background(tone, RoundedCornerShape(2.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(activity.category.uppercase(), color = tone, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp)
                Text(activity.title, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(activity.detail, color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PeriodPicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val year = LocalDate.now().year
    val periods = (year - 2..year + 1).flatMap { y -> listOf("Semester 1 · $y/${(y + 1) % 100}", "Semester 2 · $y/${(y + 1) % 100}") }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("ACADEMIC PERIOD", color = Navy, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(selected, {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(13.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            periods.forEach { period -> DropdownMenuItem(text = { Text(period) }, onClick = { onSelect(period); expanded = false }) }
        }
    }
    }
}

@Composable private fun AddCourseDialog(initial: CourseEntity?, period: String, onDismiss: () -> Unit, onSave: (String, String, Float) -> Unit) {
    var code by remember(initial?.code) { mutableStateOf(initial?.code.orEmpty()) }
    var name by remember(initial?.code) { mutableStateOf(initial?.name.orEmpty()) }
    var target by remember(initial?.code) { mutableStateOf(initial?.weeklyTargetHours?.prettyHours() ?: "5") }
    val targetHours = target.toFloatOrNull()?.takeIf { it in .5f..80f }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a course" else "Edit course", color = Navy, fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(code, { if (initial == null) code = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Course code") }, placeholder = { Text("e.g. CSC 320") }, singleLine = true, readOnly = initial != null)
            OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Course name") }, singleLine = true)
            OutlinedTextField(target, { target = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Weekly study target") }, suffix = { Text("hours") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Text("Academic period: $period", color = Muted, fontSize = 10.sp)
        } },
        confirmButton = { TextButton(onClick = { if (code.isNotBlank() && name.isNotBlank() && targetHours != null) onSave(code.trim().uppercase(), name.trim(), targetHours) }, enabled = code.isNotBlank() && name.isNotBlank() && targetHours != null) { Text(if (initial == null) "Add course" else "Save changes", color = Navy) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
        containerColor = Color.White
    )
}

@Composable private fun CourseSectionHeading(period: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Your courses", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Select a course to view its performance and study plan.", color = Muted, fontSize = 9.sp)
        }
        Surface(color = Color(0xFFEFF1F4), shape = CircleShape) {
            Text(period.substringBefore(" ·"), Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = Navy, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun CourseOverviewMetric(title: String, value: String, note: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.size(28.dp).background(if (accent == Amber) Color(0xFFFFF1D8) else Color(0xFFF0F2F7), RoundedCornerShape(9.dp)), Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
                }
            }
            Text(value, color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(note, color = Muted, fontSize = 8.sp, maxLines = 1)
        }
    }
}

@Composable private fun CourseCard(course: Course, selected: Boolean, dueSoonCount: Int, dueSoonTask: StudyTask?, onClick: () -> Unit) {
    Surface(modifier = Modifier.width(282.dp).clickable(onClick = onClick), color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Amber else Line), shadowElevation = if (selected) 3.dp else 1.dp) {
        Column(Modifier.fillMaxWidth().heightIn(min = 205.dp).padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(color = Color(0xFFFFF1D8), shape = RoundedCornerShape(7.dp)) { Text(course.code, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF9A6000), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) }
                    Text(course.name, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp, maxLines = 2)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("GRADE", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    Text(course.mark, color = Navy, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            val change = (course.mark.removeSuffix("%").toFloatOrNull() ?: 0f) - (course.previousMark.removeSuffix("%").toFloatOrNull() ?: 0f)
            if (dueSoonTask != null) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8F7F4)).padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Event, null, tint = Amber, modifier = Modifier.size(14.dp))
                    Text("${dueSoonTask.title} · ${dueSoonTask.due}", color = Navy, fontSize = 8.sp, maxLines = 1)
                }
            } else {
                Text("No coursework due soon", color = Muted, fontSize = 9.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Weekly study", color = Muted, fontSize = 8.sp)
                    Text("${course.studied.prettyHours()}h / ${course.target.prettyHours()}h", color = Navy, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(progress = { (course.studied / course.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Amber, trackColor = Line)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${course.mark} average", color = Muted, fontSize = 8.sp)
                if (course.mark != "N/A" && course.previousMark != "N/A") Text("${if (change >= 0) "+" else ""}${change.toInt()} pts", color = if (change >= 0) Green else Color(0xFFB84A2F), fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            if (dueSoonCount > 1) Text("+${dueSoonCount - 1} more coursework item${if (dueSoonCount == 2) "" else "s"} due soon", color = Muted, fontSize = 8.sp)
        }
    }
}

@Composable private fun CoursePerformanceCard(
    course: Course,
    assessments: List<AssessmentEntity>,
    onAddAssessment: () -> Unit,
    onEditAssessment: (AssessmentEntity) -> Unit,
    onDeleteAssessment: (AssessmentEntity) -> Unit,
    onEditCourse: () -> Unit,
    onDeleteCourse: () -> Unit
) {
    val current = course.mark.removeSuffix("%").toFloatOrNull()
    val previous = course.previousMark.removeSuffix("%").toFloatOrNull()
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
      Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(color = Color(0xFFFFF1D8), shape = CircleShape) { Text("${course.code} PERFORMANCE", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color(0xFF9A6000), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp) }
                Text(course.name, color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Assessment results and academic trend", color = Muted, fontSize = 9.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) { Text("CURRENT GRADE", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold); Text(course.mark, color = Navy, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold) }
        }
        if (current != null && previous != null) {
            val change = current - previous
            HorizontalDivider(color = Line)
            listOf("Previous result" to previous, "Current average" to current).forEachIndexed { index, (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Navy, fontSize = 9.sp); Text("${value.toInt()}%", color = Navy, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    LinearProgressIndicator(progress = { (value / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = if (index == 0) Navy.copy(alpha = .7f) else Amber, trackColor = Canvas)
                }
            }
            val improving = current >= previous
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (improving) Color(0xFFEAF6F1) else Color(0xFFFFF0EB)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(if (improving) Icons.Default.TrendingUp else Icons.Default.TrendingDown, null, tint = if (improving) Green else Color(0xFFB84A2F), modifier = Modifier.size(16.dp))
                Text(if (improving) "Improving · up ${change.prettyHours()} points" else "Needs attention · down ${(-change).prettyHours()} points", color = if (improving) Green else Color(0xFFB84A2F), fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
            }
        } else {
            Text("No assessment results recorded yet.", color = Muted, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddAssessment, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Add result", fontSize = 10.sp) }
            TextButton(onClick = onEditCourse) { Text("Edit course", fontSize = 10.sp) }
            TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(15.dp)); Text("Delete", fontSize = 10.sp) }
        }
        if (assessments.isNotEmpty()) {
            HorizontalDivider(color = Line)
            Text("Recorded assessments", color = Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            assessments.sortedByDescending { it.recordedAtMillis }.forEach { result ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(result.title, color = Navy, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("${result.score.prettyHours()} / ${result.maxScore.prettyHours()} · ${Instant.ofEpochMilli(result.recordedAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", color = Muted, fontSize = 9.sp)
                    }
                    IconButton(onClick = { onEditAssessment(result) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Edit, "Edit assessment", tint = Muted, modifier = Modifier.size(15.dp)) }
                    IconButton(onClick = { onDeleteAssessment(result) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.DeleteOutline, "Delete assessment", tint = Muted, modifier = Modifier.size(15.dp)) }
                }
            }
        }
      }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete ${course.code}?") }, text = { Text("Its assessment results will be deleted. Linked activities and sessions will be kept without a course association.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteCourse() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable private fun TargetEditorCard(course: Course, onTargetChange: (Float) -> Unit) {
    var targetText by remember(course.code, course.target) { mutableStateOf(course.target.prettyHours()) }
    val percent = if (course.target > 0f) (course.studied / course.target * 100f).toInt() else 0
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { (course.studied / course.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxSize(), color = Amber, trackColor = Canvas, strokeWidth = 8.dp)
                Text("$percent%", color = Navy, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("WEEKLY TARGET", color = Amber, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .7.sp)
                Text("${course.studied.prettyHours()} of ${course.target.prettyHours()} hours", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Set or adjust your study goal", color = Muted, fontSize = 9.sp)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFF7F7F5)).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { val value = (course.target - .5f).coerceAtLeast(.5f); targetText = value.prettyHours(); onTargetChange(value) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Remove, "Decrease target", tint = Navy, modifier = Modifier.size(16.dp)) }
                    OutlinedTextField(value = targetText, onValueChange = { text ->
                        targetText = text
                        text.toFloatOrNull()?.takeIf { it in .5f..80f }?.let(onTargetChange)
                    }, modifier = Modifier.width(92.dp), singleLine = true, suffix = { Text("h", fontSize = 10.sp) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(9.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
                    IconButton(onClick = { val value = course.target + .5f; targetText = value.prettyHours(); onTargetChange(value) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Add, "Increase target", tint = Navy, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable private fun CourseInsightCard(course: Course) {
    val current = course.mark.removeSuffix("%").toFloatOrNull()
    val previous = course.previousMark.removeSuffix("%").toFloatOrNull()
    val insight = when {
        current == null || previous == null -> "Add assessment results to see performance patterns and tailored course insights."
        current > previous -> "Your mark has improved by ${(current - previous).prettyHours()} points. You have completed ${(course.studied / course.target * 100).toInt()}% of your study target; keep the same study rhythm before the next assessment."
        current < previous -> "Your mark has dropped by ${(previous - current).prettyHours()} points. Consider adding a focused review session; ${(course.target - course.studied).coerceAtLeast(0f).prettyHours()} target hours remain."
        else -> "Your mark is holding steady. You have completed ${(course.studied / course.target * 100).toInt()}% of your target hours for this course."
    }
    Surface(color = Navy, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(34.dp).background(Amber, RoundedCornerShape(10.dp)), Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Navy, modifier = Modifier.size(17.dp)) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("COURSE PERFORMANCE NOTE", color = Amber, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
                    Text(course.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(insight, color = Color(0xFFE1E6EF), fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable private fun ProgressCard(label: String, current: Float, target: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Navy, fontSize = 11.sp, fontWeight = FontWeight.Medium); Text("${(current / target * 100).toInt()}%", color = Muted, fontSize = 10.sp) }
        LinearProgressIndicator(progress = { (current / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = color, trackColor = Canvas)
    }
}

private fun percentToGpa(mark: Float): Float = when {
    mark >= 85f -> 4.0f
    mark >= 80f -> 3.7f
    mark >= 75f -> 3.3f
    mark >= 70f -> 3.0f
    mark >= 65f -> 2.7f
    mark >= 60f -> 2.3f
    mark >= 55f -> 2.0f
    mark >= 50f -> 1.7f
    else -> 0f
}

private fun performanceInsight(courses: List<Course>, studyHours: Float, targetHours: Float): String {
    val marks = courses.mapNotNull { it.mark.removeSuffix("%").toFloatOrNull() }
    if (marks.isEmpty()) return "Add assessment results and complete focus sessions to see insights from your recorded data."
    val average = marks.average()
    val completion = if (targetHours > 0f) (studyHours / targetHours * 100).toInt() else 0
    return when {
        average >= 80 && completion >= 70 -> "Your selected courses average ${average.toInt()}%, and you have completed $completion% of the study target. Keep this pattern through upcoming assessments."
        completion < 60 -> "Your selected courses average ${average.toInt()}%, while target study completion is $completion%. Schedule another focused block to close the gap."
        else -> "Your selected courses average ${average.toInt()}% with $completion% of study targets complete. Review the trend chart to spot where more study time could help."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FilterDropdown(label: String, selected: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), color = Navy, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .7.sp)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(selected, {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
            }
        }
    }
}

@Composable private fun AnalyticsSummaryCard(title: String, value: String, note: String, modifier: Modifier, accent: Color, icon: ImageVector) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), color = Muted, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp)
                Box(Modifier.size(28.dp).background(if (accent == Amber) Color(0xFFFFF1D8) else Color(0xFFF0F2F7), RoundedCornerShape(9.dp)), Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp)) }
            }
            Text(value, color = Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(note, color = Muted, fontSize = 8.sp, lineHeight = 11.sp, maxLines = 2)
        }
    }
}

@Composable private fun AnalyticsInsightCard(detail: String, expanded: Boolean, onToggleEvidence: () -> Unit) {
    Surface(color = Navy, shape = RoundedCornerShape(19.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(36.dp).background(Amber, RoundedCornerShape(11.dp)), Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Navy, modifier = Modifier.size(18.dp)) }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("PERFORMANCE SUMMARY", color = Amber, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
                    Text("Based on recorded study and results", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(detail, color = Color(0xFFD8DFEA), fontSize = 10.sp, lineHeight = 15.sp)
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggleEvidence).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (expanded) "Hide evidence" else "View evidence", color = Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ArrowForward, null, tint = Amber, modifier = Modifier.size(14.dp))
            }
            if (expanded) Text("This summary uses only assessments and focus sessions saved on this device. It is calculated locally and is not generated by an AI service.", color = Color(0xFFCBD3E1), fontSize = 9.sp, lineHeight = 14.sp)
        }
    }
}

@Composable private fun PerformanceStudyTrendCard(mode: String, onModeChange: (String) -> Unit, courses: List<Course>, period: String, sessions: List<StudySessionEntity>, assessments: List<AssessmentEntity>) {
    val pointCount = when (period) { "This week" -> 7; "This month" -> 4; else -> 8 }
    val now = System.currentTimeMillis()
    val rangeStart = when (period) {
        "This week" -> LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        "This month" -> LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        else -> LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val rangeEnd = now.coerceAtLeast(rangeStart + 1)
    val buckets = (0 until pointCount).map { index ->
        val from = rangeStart + ((rangeEnd - rangeStart) * index / pointCount)
        val until = rangeStart + ((rangeEnd - rangeStart) * (index + 1) / pointCount)
        val bucketSessions = sessions.filter { it.startedAtMillis in from until until }
        val bucketAssessments = assessments.filter { it.recordedAtMillis in from until until }
        val grade = bucketAssessments.takeIf { it.isNotEmpty() }?.let { it.sumOf { row -> row.score.toDouble() } / it.sumOf { row -> row.maxScore.toDouble() } * 100.0 }?.toFloat()
        val hours = bucketSessions.sumOf { it.accumulatedSeconds.toDouble() }.toFloat() / 3600f
        grade to hours
    }
    val visiblePerformance = buckets.map { it.first ?: 0f }
    val maxStudy = buckets.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f
    val visibleStudy = buckets.map { it.second / maxStudy * 100f }
    val hasPerformance = buckets.any { it.first != null }
    val hasStudy = buckets.any { it.second > 0f }
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Performance & study trend", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("$period · ${if (courses.size == 1) courses.first().code else "All courses"}", color = Muted, fontSize = 8.sp)
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F4F6)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("Performance only" to "Grades", "Study only" to "Study", "Both" to "Both").forEach { (value, label) ->
                        val selected = mode == value
                        Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(7.dp)).background(if (selected) Color.White else Color.Transparent).clickable { onModeChange(value) }, contentAlignment = Alignment.Center) {
                            Text(label, color = Navy, fontSize = 9.sp, maxLines = 1, softWrap = false, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (mode != "Study only") LegendDot(Amber, "Average grade")
                if (mode != "Performance only") LegendDot(Navy, "Study consistency")
            }
            Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.height(140.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf("100", "75", "50", "25", "0").forEach { Text(it, color = Muted, fontSize = 7.sp) }
                }
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().weight(1f)) {
                        val chartHeight = size.height
                        val chartWidth = size.width
                        (0..4).forEach { index ->
                            val y = chartHeight * index / 4f
                            drawLine(Color(0xFFE9EBEF), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(chartWidth, y), strokeWidth = 1.dp.toPx())
                        }
                        fun plot(values: List<Float>, color: Color) {
                            val path = androidx.compose.ui.graphics.Path()
                            val dx = if (values.size > 1) chartWidth / (values.size - 1) else 0f
                            values.forEachIndexed { index, value ->
                                val x = dx * index
                                val y = chartHeight - (value.coerceIn(0f, 100f) / 100f * chartHeight)
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                            values.forEachIndexed { index, value ->
                                val x = dx * index
                                val y = chartHeight - (value.coerceIn(0f, 100f) / 100f * chartHeight)
                                drawCircle(color, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                            }
                        }
                        if (mode != "Study only" && hasPerformance) plot(visiblePerformance, Amber)
                        if (mode != "Performance only" && hasStudy) plot(visibleStudy, Navy)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        visiblePerformance.indices.forEach { index -> Text(if (period == "This week") "D${index + 1}" else "W${index + 1}", color = Muted, fontSize = 7.sp) }
                    }
                }
            }
            if (!hasPerformance && !hasStudy) EmptyState("No trend data in this period", "Complete a focus session or record an assessment to populate this chart.")
            else Text("Study bars are normalized to the busiest interval. Grade points use assessments recorded in each interval.", color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, color = Muted, fontSize = 9.sp)
    }
}

@Composable private fun TimeAllocationCard(sessions: List<StudySessionEntity>, analyticsTasks: List<StudyTask>) {
    val total = sessions.sumOf { it.accumulatedSeconds.toDouble() }.toFloat() / 3600f
    val grouped = sessions.groupBy { it.courseCode ?: "Independent study" }.map { (course, rows) ->
        course to (rows.sumOf { it.accumulatedSeconds.toDouble() }.toFloat() / 3600f)
    }.filter { it.second > 0f }.sortedByDescending { it.second }.take(4)
    val palette = listOf(Navy, Amber, Green, Color(0xFF8A94A6))
    val entries = grouped.mapIndexed { index, row -> Triple(row.first, row.second, palette[index % palette.size]) }
    val academicPlanned = analyticsTasks.filter { it.type in listOf("Academic task", "Class or assignment", "Study session") }.sumOf { it.plannedHours.toDouble() }.toFloat()
    val extracurricularPlanned = analyticsTasks.filter { it.type in listOf("Extracurricular task", "Personal activity") }.sumOf { it.plannedHours.toDouble() }.toFloat()
    val academic = total + academicPlanned
    val nonAcademic = extracurricularPlanned
    val ratio = if (nonAcademic > 0f) academic / nonAcademic else Float.POSITIVE_INFINITY
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Time allocation", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${total.prettyHours()} hours actually tracked", color = Muted, fontSize = 8.sp)
                }
                Icon(Icons.Default.MoreHoriz, "Time allocation options", tint = Muted)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                        val stroke = 22.dp.toPx()
                        var startAngle = -90f
                        val baseTotal = total
                        entries.forEach { (_, hours, color) ->
                            val sweep = hours / baseTotal * 360f
                            drawArc(color, startAngle, (sweep - 2f).coerceAtLeast(0f), false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("${total.prettyHours()}h", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text("tracked", color = Muted, fontSize = 8.sp)
                    }
                }
            }
            if (entries.isEmpty()) EmptyState("No tracked time yet", "Completed focus sessions will appear here. Planned activity durations are shown separately.")
            entries.forEach { (label, hours, color) ->
                val percent = if (total > 0f) (hours / total * 100).toInt() else 0
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text(label, Modifier.weight(1f), color = Navy, fontSize = 9.sp)
                    Text("${hours.prettyHours()}h", color = Muted, fontSize = 8.sp)
                    Text("$percent%", color = Navy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Planned commitments in this period: ${academicPlanned.prettyHours()} academic/study h · ${extracurricularPlanned.prettyHours()} extracurricular/personal h", color = Muted, fontSize = 8.sp, lineHeight = 12.sp)
            val healthy = ratio in 1.5f..3f
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (healthy) Color(0xFFEAF6F1) else Color(0xFFFFF0EB)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(if (healthy) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (healthy) Green else Color(0xFFB84A2F), modifier = Modifier.size(15.dp))
                Text(if (nonAcademic <= 0f) "Add extracurricular or personal commitments to assess balance." else if (healthy) "Planned balance is within the 1.5–3:1 guide · ${"%.1f".format(ratio)}:1" else "Review the planned academic and personal balance · ${"%.1f".format(ratio)}:1", color = if (healthy) Green else Color(0xFFB84A2F), fontSize = 8.sp, fontWeight = FontWeight.SemiBold, lineHeight = 12.sp)
            }
        }
    }
}

@Composable private fun CourseAnalyticsCard(course: Course) {
    val performance = course.mark.removeSuffix("%").toFloatOrNull()
    val studyPercent = if (course.target > 0f) (course.studied / course.target * 100).toInt() else 0
    val previous = course.previousMark.removeSuffix("%").toFloatOrNull()
    Surface(color = Color.White, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(course.code, color = Amber, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold); Text(course.name, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                if (performance != null && previous != null) {
                    val change = performance - previous
                    Text("${if (change >= 0) "+" else ""}${change.toInt()}%", color = if (change >= 0) Green else Color(0xFFB84A2F), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            AnalyticsComparisonBar("Grade", performance, Amber)
            AnalyticsComparisonBar("Study target", studyPercent.toFloat(), Navy)
        }
    }
}

@Composable private fun AnalyticsComparisonBar(label: String, value: Float?, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Muted, fontSize = 8.sp, modifier = Modifier.width(55.dp))
        LinearProgressIndicator(progress = { ((value ?: 0f) / 100f).coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(5.dp), color = color, trackColor = Line)
        Text(value?.let { "${it.toInt()}%" } ?: "—", color = Navy, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(27.dp))
    }
}

@Composable private fun FocusPatternCard(period: String, sessions: List<StudySessionEntity>) {
    val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val times = listOf("Morning", "Midday", "Afternoon", "Evening")
    val cells = Array(4) { FloatArray(7) }
    sessions.forEach { session ->
        val dateTime = Instant.ofEpochMilli(session.startedAtMillis).atZone(ZoneId.systemDefault())
        val day = dateTime.dayOfWeek.value - 1
        val hour = dateTime.hour
        val bucket = when (hour) { in 5..10 -> 0; in 11..14 -> 1; in 15..18 -> 2; else -> 3 }
        cells[bucket][day] += session.accumulatedSeconds / 3600f
    }
    val peakCell = (0..3).flatMap { t -> (0..6).map { d -> Triple(cells[t][d], t, d) } }.maxByOrNull { it.first }
    val peakLabel = if (peakCell == null || peakCell.first <= 0f) "No peak yet" else "Peak ${times[peakCell.second]}"
    val maxCell = cells.maxOfOrNull { row -> row.maxOrNull() ?: 0f }?.takeIf { it > 0f } ?: 1f
    fun intensity(time: Int, day: Int): Int = (cells[time][day] / maxCell * 4f).toInt().coerceIn(0, 4)
    val levels = listOf(Color(0xFFF1F2F4), Color(0xFFFFE8B2), Color(0xFFFFCA5A), Amber, Navy)
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Focus patterns", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Productive hours by day · $period", color = Muted, fontSize = 8.sp)
                }
                Surface(color = Color(0xFFFFF1D8), shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF9A6000), modifier = Modifier.size(11.dp))
                        Text(peakLabel, color = Color(0xFF9A6000), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Spacer(Modifier.width(59.dp))
                weekdays.forEach { day -> Text(day, Modifier.weight(1f), color = Muted, fontSize = 7.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            times.forEachIndexed { timeIndex, time ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(time, color = Muted, fontSize = 7.sp, modifier = Modifier.width(59.dp))
                    weekdays.indices.forEach { dayIndex ->
                        Box(Modifier.weight(1f).height(21.dp).background(levels[intensity(timeIndex, dayIndex)], RoundedCornerShape(5.dp)))
                    }
                }
            }
            HorizontalDivider(color = Line)
            Text(if (sessions.isEmpty()) "No focus sessions were recorded in this period." else "Intensity reflects completed focus-session time by start day and time of day." , color = Muted, fontSize = 8.sp, lineHeight = 12.sp)
        }
    }
}

@Composable private fun PlanBuilderCard(onClick: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(36.dp).background(Color(0xFFFFF1D8), RoundedCornerShape(11.dp)), Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF9A6000), modifier = Modifier.size(18.dp)) }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("READY FOR NEXT WEEK?", color = Navy, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp)
                    Text("Draft a more balanced plan", color = Muted, fontSize = 9.sp)
                }
            }
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy)) {
                Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Build next week’s plan", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun MetricCard(title: String, value: String, note: String, color: Color) {
    SurfaceCard { Text(title, color = Muted, fontSize = 12.sp); Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) { Text(value, color = Navy, fontSize = 34.sp, fontWeight = FontWeight.Bold); Text("/ 100", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp)) }; Text(note, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable private fun TrendCard() {
    SurfaceCard {
        val values = listOf(.38f, .48f, .43f, .62f, .57f, .73f, .66f, .82f)
        Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(24.dp).height((78 * v).dp).background(if (i == values.lastIndex) Amber else Navy.copy(alpha = .72f), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                    Text("W${i + 1}", color = Muted, fontSize = 8.sp)
                }
            }
        }
        Text("Study hours are trending up alongside course results.", color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun RecommendationCard(title: String, detail: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = Amber, modifier = Modifier.size(18.dp)); Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Text("WHY THIS?  Deadline · Completion · Available time", color = Navy, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .3.sp)
        }
    }
}

@Composable private fun EmptyState(title: String, detail: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Icon(Icons.Default.CheckCircleOutline, null, tint = Green, modifier = Modifier.size(26.dp))
        Text(title, color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AddActivityDialog(courses: List<Course>, initial: ActivityEntity?, onDismiss: () -> Unit, onSave: (ActivityEntity) -> Unit) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var course by remember(initial?.id) { mutableStateOf(initial?.courseCode?.let { code -> courses.firstOrNull { it.code == code }?.let { "${it.code} · ${it.name}" } } ?: initial?.courseLabel ?: "None") }
    var dueAtMillis by remember(initial?.id) { mutableStateOf(initial?.dueAtMillis) }
    var duePickerOpen by remember { mutableStateOf(false) }
    var plannedHoursText by remember(initial?.id) { mutableStateOf(initial?.plannedHours?.toString() ?: "1") }
    var activityType by remember(initial?.id) { mutableStateOf(initial?.category ?: "Academic task") }
    var priority by remember(initial?.id) { mutableStateOf(initial?.priority ?: "Medium") }
    var smartReminder by remember(initial?.id) { mutableStateOf(initial?.smartReminder ?: false) }
    var courseMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    val courseOptions = courses.map { "${it.code} · ${it.name}" } + listOf("Personal", "None")
    val typeOptions = listOf("Academic task", "Class or assignment", "Study session", "Personal activity", "Extracurricular task")
    val priorityOptions = listOf("Low", "Medium", "High")
    val dueLabel = dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")) } ?: "No due date"
    val plannedHours = plannedHoursText.toFloatOrNull()?.takeIf { it in .25f..24f }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Create new activity" else "Edit activity", color = Navy, fontWeight = FontWeight.Bold) }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Task name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(expanded = courseMenu, onExpandedChange = { courseMenu = !courseMenu }) {
                OutlinedTextField(course, {}, readOnly = true, label = { Text("Course") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(courseMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                    courseOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { course = option; courseMenu = false }) }
                }
            }
            OutlinedTextField(dueLabel, { }, readOnly = true, label = { Text("Due date") }, trailingIcon = { TextButton(onClick = { duePickerOpen = true }) { Text(if (dueAtMillis == null) "Choose" else "Change") } }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(plannedHoursText, { plannedHoursText = it }, label = { Text("Planned duration") }, suffix = { Text("hours") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(expanded = typeMenu, onExpandedChange = { typeMenu = !typeMenu }) {
                OutlinedTextField(activityType, {}, readOnly = true, label = { Text("Activity type") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    typeOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { activityType = option; typeMenu = false }) }
                }
            }
            ExposedDropdownMenuBox(expanded = priorityMenu, onExpandedChange = { priorityMenu = !priorityMenu }) {
                OutlinedTextField(priority, {}, readOnly = true, label = { Text("Priority") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(priorityMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                    priorityOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { priority = option; priorityMenu = false }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = smartReminder, onCheckedChange = { smartReminder = it }, colors = CheckboxDefaults.colors(checkedColor = Amber, checkmarkColor = Navy))
                Column(Modifier.padding(start = 4.dp)) {
                    Text("Smart reminder", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Saved with this activity; Android notification delivery is not connected yet.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = {
        val selectedCode = courses.firstOrNull { "${it.code} · ${it.name}" == course }?.code
        val due = dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        if (title.isNotBlank() && plannedHours != null) onSave(ActivityEntity(id = initial?.id ?: 0L, title = title.trim(), courseCode = selectedCode, courseLabel = course, dueLabel = dueLabel, dueAtMillis = due, category = activityType, priority = priority, smartReminder = smartReminder, plannedHours = plannedHours, completed = initial?.completed ?: false, createdAtMillis = initial?.createdAtMillis ?: System.currentTimeMillis()))
    }, enabled = title.isNotBlank() && plannedHours != null) { Text("Save activity", color = Navy) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }, containerColor = Color.White)
    if (duePickerOpen) {
        val pickerInitial = dueAtMillis?.let { millis -> Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() }
        val picker = rememberDatePickerState(initialSelectedDateMillis = pickerInitial)
        DatePickerDialog(onDismissRequest = { duePickerOpen = false }, confirmButton = { TextButton(onClick = {
            dueAtMillis = picker.selectedDateMillis?.let { millis -> Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            duePickerOpen = false
        }) { Text("Select") } }, dismissButton = { TextButton(onClick = { duePickerOpen = false }) { Text("Cancel") } }) { DatePicker(state = picker) }
    }
}

@Composable private fun AddAssessmentDialog(course: Course, initial: AssessmentEntity?, onDismiss: () -> Unit, onSave: (String, Float, Float) -> Unit) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var score by remember(initial?.id) { mutableStateOf(initial?.score?.toString().orEmpty()) }
    var maximum by remember(initial?.id) { mutableStateOf(initial?.maxScore?.toString() ?: "100") }
    val scoreValue = score.toFloatOrNull()
    val maxValue = maximum.toFloatOrNull()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add assessment" else "Edit assessment", color = Navy, fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${course.code} · ${course.name}", color = Muted, fontSize = 12.sp)
            OutlinedTextField(title, { title = it }, label = { Text("Assessment name") }, singleLine = true)
            OutlinedTextField(score, { score = it }, label = { Text("Score") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(maximum, { maximum = it }, label = { Text("Maximum score") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank() && scoreValue != null && maxValue != null) onSave(title.trim(), scoreValue, maxValue) }, enabled = title.isNotBlank() && scoreValue != null && maxValue != null && maxValue > 0f && scoreValue in 0f..maxValue) { Text("Save result", color = Navy) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }, containerColor = Color.White)
}
