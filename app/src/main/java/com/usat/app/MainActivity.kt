package com.usat.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
    val id: Int,
    val title: String,
    val course: String,
    val due: String,
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
    Courses("Courses", Icons.Default.MenuBook), Analytics("Analytics", Icons.Default.BarChart), Advisor("AI Advisor", Icons.Default.AutoAwesome)
}

@RequiresApi(Build.VERSION_CODES.HONEYCOMB_MR2)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsatApp() {
    var tasks by remember { mutableStateOf(listOf(
        StudyTask(1, "Database Systems assignment", "CSC 312", "Today, 11:59 PM", "High"),
        StudyTask(2, "Review calculus problem set", "MTH 204", "Tomorrow, 9:00 AM", "Medium"),
        StudyTask(3, "Prepare project presentation", "CSC 318", "Friday, 2:00 PM", "Medium")
    )) }
    var courses by remember { mutableStateOf(listOf(
        Course("CSC 312", "Database Systems", 6.5f, 8f, "78%", "74%"),
        Course("MTH 204", "Advanced Calculus", 4f, 6f, "72%", "70%"),
        Course("CSC 318", "Software Engineering", 3.5f, 5f, "85%", "80%")
    )) }
    var page by remember { mutableStateOf(Page.Overview) }
    var academicPeriod by remember { mutableStateOf("Semester 2 · 2025/26") }
    var courseQuery by remember { mutableStateOf("") }
    var selectedCourseCode by remember { mutableStateOf("CSC 312") }
    var showAddCourse by remember { mutableStateOf(false) }
    var analyticsPeriod by remember { mutableStateOf("This week") }
    var analyticsCourse by remember { mutableStateOf("All courses") }
    var trendMode by remember { mutableStateOf("Both") }
    var showAnalyticsEvidence by rememberSaveable { mutableStateOf(false) }
    var showPlanInfo by remember { mutableStateOf(false) }
    var eventFilter by remember { mutableStateOf("All") }
    var showAdd by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var timerRunning by remember { mutableStateOf(false) }
    var showOverviewWhy by rememberSaveable { mutableStateOf(false) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    var seconds by remember { mutableIntStateOf(25 * 60) }
    LaunchedEffect(timerRunning) {
        while (timerRunning && seconds > 0) { delay(1000); seconds-- }
        if (seconds == 0) timerRunning = false
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Amber, background = Canvas, surface = Color.White)) {
        if (showWelcome) WelcomeScreen(onContinue = { showWelcome = false }) else {
        Scaffold(containerColor = Canvas, topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Image(painterResource(R.drawable.usat_mark), "USAT app icon", Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
                    Text("USAT", color = Navy, fontWeight = FontWeight.Bold)
                } },
                actions = {
                    IconButton(onClick = { showNotifications = true }) { Icon(Icons.Default.NotificationsNone, "Notifications", tint = Navy) }
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
                Page.entries.take(4).forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Navy, indicatorColor = Amber.copy(alpha = .25f), selectedTextColor = Navy, unselectedTextColor = Muted))
                }
            }
        }, floatingActionButton = {
            if (page != Page.Overview && page != Page.Analytics) ExtendedFloatingActionButton(onClick = { if (page == Page.Advisor) page = Page.Overview else showAdd = true }, icon = { Icon(if (page == Page.Advisor) Icons.Default.Home else Icons.Default.Add, null) }, text = { Text(if (page == Page.Advisor) "Dashboard" else "Add activity") }, containerColor = Amber, contentColor = Navy)
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Heading(page) }
                when (page) {
                    Page.Overview -> {
                        item { OverviewDailyBriefCard(tasks, courses, showOverviewWhy, onWhyClick = { showOverviewWhy = !showOverviewWhy }) }
                        item { FocusCard(timerRunning, seconds, onToggle = { timerRunning = !timerRunning }, onReset = { timerRunning = false; seconds = 25 * 60 }) }
                        val firstOpenTask = tasks.firstOrNull { !it.done }
                        val studiedHours = courses.sumOf { it.studied.toDouble() }.toFloat()
                        val targetHours = courses.sumOf { it.target.toDouble() }.toFloat()
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OverviewSummaryCard("Priority tasks", Icons.Default.CheckCircle, "${tasks.count { !it.done }}", "open tasks", firstOpenTask?.let { "Due soon · ${it.title}" } ?: "No tasks need attention", "Review tasks", Modifier.weight(1f), Amber) { page = Page.Schedule }
                                OverviewSummaryCard("Today", Icons.Default.DateRange, "9:00", "AM · next event", "Advanced Databases · 90 min", "Open schedule", Modifier.weight(1f), Navy) { page = Page.Schedule }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OverviewSummaryCard("Study progress", Icons.Default.BarChart, "${if (targetHours > 0f) (studiedHours / targetHours * 100).toInt() else 0}%", "of target", "${studiedHours.prettyHours()} of ${targetHours.prettyHours()} study hours", "View courses", Modifier.weight(1f), Amber, progress = if (targetHours > 0f) studiedHours / targetHours else 0f) { page = Page.Courses }
                                OverviewSummaryCard("Upcoming", Icons.Default.Schedule, firstOpenTask?.due?.substringBefore(",") ?: "All clear", if (firstOpenTask == null) "deadlines" else "next deadline", firstOpenTask?.let { "${it.due} · ${it.title}" } ?: "No upcoming deadlines", "See deadlines", Modifier.weight(1f), Navy) { page = Page.Schedule }
                            }
                        }
                    }
                    Page.Schedule -> {
                        item { EventsWeekHeader(onAdd = { showAdd = true }) }
                        item { WeekStrip() }
                        val academicHours = 6f + tasks.filter { !it.done && it.id > 3 && it.type in listOf("Academic task", "Class or assignment") }.sumOf { it.plannedHours.toDouble() }.toFloat()
                        val studyHours = 6.5f + tasks.filter { !it.done && it.id > 3 && it.type == "Study session" }.sumOf { it.plannedHours.toDouble() }.toFloat()
                        val extracurricularHours = 6f + tasks.filter { !it.done && it.id > 3 && it.type in listOf("Extracurricular task", "Personal activity") }.sumOf { it.plannedHours.toDouble() }.toFloat()
                        val totalHours = academicHours + studyHours + extracurricularHours
                        val targetHours = courses.sumOf { it.target.toDouble() }.toFloat()
                        val studiedHours = courses.sumOf { it.studied.toDouble() }.toFloat()
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                EventMetricCard(totalHours.durationLabel(), "Planned this week", Icons.Default.EventAvailable, Amber, Modifier.weight(1f))
                                EventMetricCard("${studiedHours.prettyHours()} / ${targetHours.prettyHours()}h", "Study target", Icons.Default.AccessTime, Navy, Modifier.weight(1f), if (targetHours > 0f) studiedHours / targetHours else 0f)
                            }
                        }
                        item { BalanceCard(academicHours, studyHours, extracurricularHours) }
                        item { TodayPlanHeader() }
                        item { EventCategoryFilters(eventFilter, onSelect = { eventFilter = it }) }
                        val agenda = listOf(
                            AgendaActivity("Advanced Databases", "CSC 312 · LT 04 · Class", "09:00 – 10:30", "Academic", 1.5f),
                            AgendaActivity("Calculus study block", "MTH 204 · Library · Focus time", "12:00 – 13:00", "Study", 1f),
                            AgendaActivity("Robotics club", "Engineering Lab · Club activity", "15:30 – 16:30", "Extracurricular", 1f)
                        )
                        val selectedAgenda = agenda.filter { eventFilter == "All" || it.category == eventFilter }
                        val selectedTasks = tasks.filter { !it.done && (eventFilter == "All" || categoryFor(it.type) == eventFilter) }
                        if (selectedAgenda.isEmpty() && selectedTasks.isEmpty()) item { EmptyState("No activities in this category", "Choose another category or add an activity.") }
                        items(selectedAgenda) { activity -> AgendaCard(activity) }
                        items(selectedTasks) { task -> TaskCard(task) { tasks = tasks.map { if (it.id == task.id) it.copy(done = !it.done) else it } } }
                        item { EventsAdvisorCard(academicHours + studyHours, extracurricularHours, courses, tasks) }
                        val remindersOn = tasks.any { !it.done && it.smartReminder }
                        item { ReminderStatusCard(remindersOn, tasks.count { !it.done && it.smartReminder }) }
                    }
                    Page.Courses -> {
                        item { PeriodPicker(academicPeriod, onSelect = { academicPeriod = it }) }
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedTextField(courseQuery, { courseQuery = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Find a course", fontSize = 12.sp) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, shape = RoundedCornerShape(13.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
                            IconButton(onClick = { showAddCourse = true }, modifier = Modifier.size(46.dp).background(Amber, RoundedCornerShape(13.dp))) { Icon(Icons.Default.Add, "Add course", tint = Navy) }
                        } }
                        val averageMark = courses.mapNotNull { it.mark.removeSuffix("%").toFloatOrNull() }.average()
                        val currentGpa = if (averageMark.isNaN()) "—" else "%.2f".format(percentToGpa(averageMark.toFloat()))
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CourseOverviewMetric("Active courses", courses.size.toString(), "in this academic period", Icons.Default.MenuBook, Amber, Modifier.weight(1f))
                            CourseOverviewMetric("Current GPA", currentGpa, if (averageMark.isNaN()) "Add assessment results" else "Based on current marks", Icons.Default.School, Navy, Modifier.weight(1f))
                        } }
                        item { CourseSectionHeading(academicPeriod) }
                        val filteredCourses = courses.filter { "${it.code} ${it.name}".contains(courseQuery.trim(), ignoreCase = true) }
                        val selectedCourse = filteredCourses.firstOrNull { it.code == selectedCourseCode } ?: filteredCourses.firstOrNull()
                        if (filteredCourses.isEmpty()) item { EmptyState("No matching courses", "Try a different search or add this course.") }
                        else item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            filteredCourses.forEach { course ->
                                val dueTasks = tasks.filter { !it.done && it.course.contains(course.code, true) }
                                CourseCard(course, selected = selectedCourse?.code == course.code, dueSoonCount = dueTasks.size, dueSoonTask = dueTasks.firstOrNull()) { selectedCourseCode = course.code }
                            }
                        } }
                        if (selectedCourse != null) {
                            item { CoursePerformanceCard(selectedCourse) }
                            item { TargetEditorCard(selectedCourse, onTargetChange = { target -> courses = courses.map { if (it.code == selectedCourse.code) it.copy(target = target) else it } }) }
                            item { CourseInsightCard(selectedCourse) }
                            item { SectionTitle("Upcoming coursework", "Assignments and tasks for ${selectedCourse.code}") }
                            val courseTasks = tasks.filter { !it.done && it.course.contains(selectedCourse.code, true) }
                            if (courseTasks.isEmpty()) item { EmptyState("No upcoming coursework", "Add an activity and assign it to ${selectedCourse.code} to see it here.") }
                            else items(courseTasks) { task -> TaskCard(task) { tasks = tasks.map { t -> if (t.id == task.id) t.copy(done = !t.done) else t } } }
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
                        val periodMultiplier = when (analyticsPeriod) { "This month" -> 4f; "This semester" -> 16f; "This academic year" -> 32f; else -> 1f }
                        val weeklyStudy = analyticsCourses.sumOf { it.studied.toDouble() }.toFloat() * periodMultiplier
                        val targetStudy = analyticsCourses.sumOf { it.target.toDouble() }.toFloat() * periodMultiplier
                        val averageMark = analyticsCourses.mapNotNull { it.mark.removeSuffix("%").toFloatOrNull() }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
                        val gpa = percentToGpa(averageMark)
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnalyticsSummaryCard("Current GPA", "${"%.2f".format(gpa)}", "Based on ${analyticsCourses.size} selected course${if (analyticsCourses.size == 1) "" else "s"}", Modifier.weight(1f), Navy, Icons.Default.School)
                            AnalyticsSummaryCard(if (analyticsPeriod == "This week") "Weekly study" else "Study hours", "${weeklyStudy.prettyHours()}h", "Target: ${targetStudy.prettyHours()} hours", Modifier.weight(1f), Amber, Icons.Default.AccessTime)
                        } }
                        item { AnalyticsInsightCard(performanceInsight(analyticsCourses, weeklyStudy, targetStudy), showAnalyticsEvidence, onToggleEvidence = { showAnalyticsEvidence = !showAnalyticsEvidence }) }
                        item { PerformanceStudyTrendCard(trendMode, onModeChange = { trendMode = it }, courses = analyticsCourses, period = analyticsPeriod) }
                        item { TimeAllocationCard(periodMultiplier) }
                        item { SectionTitle("Course performance", "Grades compared with study-target completion") }
                        items(analyticsCourses) { course -> CourseAnalyticsCard(course) }
                        item { SectionTitle("Focus patterns", "Study intensity by time of day") }
                        item { FocusPatternCard(analyticsPeriod) }
                        item { PlanBuilderCard(onClick = { showPlanInfo = true }) }
                    }
                    Page.Advisor -> {
                        item { BriefCard() }
                        item { SectionTitle("Recommendations", "Based on your deadlines and study targets") }
                        item { RecommendationCard("Protect a 60-minute study block before 4 PM.", "Your Database Systems assignment is due tonight and is 68% complete. Finishing before Robotics Club leaves a 2.5-hour safety buffer.") }
                        item { RecommendationCard("Add one calculus review session this week.", "You have completed 4 of your 6 target hours. A focused session will help you reach your weekly goal before the next assessment.") }
                        item { Text("Recommendations are illustrative sample content. Connect an AI service to generate advice from real student data.", color = Muted, fontSize = 12.sp) }
                    }
                }
            }
        }
        }
    }
    if (!showWelcome && showAdd) AddActivityDialog(courses = courses, onDismiss = { showAdd = false }, onSave = { title, course, due, type, priority, smartReminder ->
        val plannedHours = when (type) { "Academic task", "Class or assignment", "Study session" -> 1.5f; else -> 1f }
        tasks = tasks + StudyTask((tasks.maxOfOrNull { it.id } ?: 0) + 1, title, course, due.ifBlank { "No due date" }, priority, type, smartReminder, plannedHours)
        showAdd = false
    })
    if (!showWelcome && showAddCourse) AddCourseDialog(onDismiss = { showAddCourse = false }, onSave = { code, name, target ->
        courses = courses + Course(code, name, 0f, target, "N/A", "N/A")
        selectedCourseCode = code
        courseQuery = ""
        showAddCourse = false
    })
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

@Composable private fun WelcomeScreen(onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Navy), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(painterResource(R.drawable.usat_mark), "USAT", Modifier.size(148.dp).clip(RoundedCornerShape(32.dp)), contentScale = ContentScale.Crop)
            Text("Welcome to USAT", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Your studies, activities, and goals in one place.", color = Color(0xFFD4DAE5), fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy), modifier = Modifier.fillMaxWidth()) {
                Text("Open my dashboard", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 5.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Heading(page: Page) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("MONDAY, 13 MAY", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
        Text(if (page == Page.Overview) "Welcome, Moses" else page.label, color = Navy, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
        Text(if (page == Page.Overview) "Here’s what needs your attention today." else "A focused view of your ${page.label.lowercase()}.", color = Muted, fontSize = 13.sp)
    }
}

@Composable private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable private fun BriefCard() {
    Surface(color = Navy, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, tint = Amber, modifier = Modifier.size(18.dp)); Text("AI DAILY BRIEF", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            }
            Text("Protect a 60-minute study block before 4 PM.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp)
            Text("Your Database Systems assignment is due tonight. Finishing it before Robotics Club gives you a 2.5-hour safety buffer.", color = Color(0xFFCBD3E1), fontSize = 12.sp, lineHeight = 18.sp)
            Text("See why this was suggested  →", color = Amber, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable private fun OverviewDailyBriefCard(tasks: List<StudyTask>, courses: List<Course>, showWhy: Boolean, onWhyClick: () -> Unit) {
    val openTasks = tasks.count { !it.done }
    val studiedHours = courses.sumOf { it.studied.toDouble() }.toFloat()
    val targetHours = courses.sumOf { it.target.toDouble() }.toFloat()
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Amber))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(34.dp).background(Color(0xFFFFF1D8), RoundedCornerShape(11.dp)), Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Amber, modifier = Modifier.size(18.dp)) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("AI DAILY BRIEF", color = Amber, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text("Your next best move", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text("Protect a 60-minute study block before 4 PM.", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                Text("Your Database Systems assignment is due tonight. Finish before Robotics Club for a safer buffer.", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFAF9F6))) {
                    BriefMetric("TASKS", "$openTasks left", Modifier.weight(1f))
                    BriefMetric("STUDY", "${studiedHours.prettyHours()} / ${targetHours.prettyHours()}h", Modifier.weight(1f))
                    BriefMetric("NEXT EVENT", "9:00 AM", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().clickable(onClick = onWhyClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (showWhy) "Hide why this was recommended" else "See why this was recommended", color = Color(0xFF9A6000), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Icon(if (showWhy) Icons.Default.ExpandLess else Icons.Default.ArrowForward, null, tint = Color(0xFF9A6000), modifier = Modifier.size(15.dp))
                }
                if (showWhy) {
                    val urgentTask = tasks.filter { !it.done }.maxByOrNull { when (it.priority) { "High" -> 3; "Medium" -> 2; else -> 1 } }
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Three factors shaped this suggestion", color = Navy, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        RecommendationFactor("01", "Deadline pressure", urgentTask?.let { "${it.title} · ${it.priority} priority" } ?: "No urgent task right now")
                        RecommendationFactor("02", "Study progress", "${studiedHours.prettyHours()} of ${targetHours.prettyHours()} target hours completed.")
                        RecommendationFactor("03", "Available time", "A focused block fits before your next planned commitment.")
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

@Composable private fun FocusCard(running: Boolean, seconds: Int, onToggle: () -> Unit, onReset: () -> Unit) {
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("FOCUS SESSION", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Navy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(if (running) "Stay focused — you’ve got this." else "Ready for a productive block?", color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Navy), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(if (running) "Pause" else "Start", fontWeight = FontWeight.Bold) }
                TextButton(onClick = onReset, contentPadding = PaddingValues(0.dp)) { Text("Reset timer", color = Muted, fontSize = 11.sp) }
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

@Composable private fun TaskCard(task: StudyTask, onToggle: () -> Unit) {
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Amber, uncheckedColor = Color(0xFFB9C0CA), checkmarkColor = Navy), modifier = Modifier.size(27.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, color = if (task.done) Muted else Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("${task.course}  ·  ${task.due}", color = Muted, fontSize = 10.sp)
                Text("${task.type}${if (task.smartReminder) " · Smart reminder" else ""}", color = Muted, fontSize = 9.sp)
            }
            Surface(color = if (task.priority == "High") Color(0xFFFFF0EB) else Color(0xFFFFF4DC), shape = CircleShape) {
                Text(task.priority.uppercase(), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = if (task.priority == "High") Color(0xFFB84A2F) else Color(0xFF9A6000), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable private fun EventCard(time: String, title: String, detail: String, tone: Color) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(76.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(time.substringBefore(" – "), color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold); if (" – " in time) Text(time.substringAfter(" – "), color = Muted, fontSize = 10.sp) }
            Box(Modifier.width(3.dp).height(46.dp).background(tone, RoundedCornerShape(2.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(detail, color = Muted, fontSize = 10.sp) }
        }
    }
}

@Composable private fun EventsWeekHeader(onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("13 – 19 May 2024", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("This week", color = Muted, fontSize = 10.sp)
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(42.dp).background(Amber, RoundedCornerShape(13.dp))) {
            Icon(Icons.Default.Add, "Add event", tint = Navy)
        }
    }
}

@Composable private fun WeekStrip() {
    val days = listOf("Mon" to "13", "Tue" to "14", "Wed" to "15", "Thu" to "16", "Fri" to "17", "Sat" to "18", "Sun" to "19")
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { index, (label, date) ->
                val selected = index == 0
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(if (selected) Navy else Color.Transparent).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(label, color = if (selected) Amber else Muted, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    Text(date, color = if (selected) Color.White else Navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(4.dp).background(if (index < 6) Amber else Color.Transparent, CircleShape))
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

@Composable private fun TodayPlanHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(color = Color(0xFFFFF1D8), shape = CircleShape) {
            Text("MONDAY, MAY 13", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color(0xFF9A6000), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp)
        }
        Text("Today’s plan", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
        ratio > 3f -> "Your academic commitments are more than three times your extracurricular time. Consider protecting a short recovery block this week."
        ratio < 1.5f -> "Extracurricular commitments are taking a larger share of the week. Reserve time for upcoming coursework and study targets."
        studied < target -> "Your weekly balance looks healthy. You have ${(target - studied).prettyHours()} study-target hours left; protect an open block before adding more commitments."
        else -> "Your balance and study target are on track. Keep your planned commitments within the available hours this week."
    }
    Surface(color = Navy, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.AutoAwesome, null, tint = Amber); Text("AI EVENTS ADVISOR", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
            Text(advice, color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
            Text("Based on this week’s time ratio, study targets${if (reminders > 0) ", and $reminders smart reminder${if (reminders == 1) "" else "s"}" else ""}.", color = Color(0xFFCBD3E1), fontSize = 10.sp)
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
                Text(if (enabled) "$count active ${if (count == 1) "reminder" else "reminders"}" else "Off · no activities have reminders enabled", color = Muted, fontSize = 10.sp)
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
    val periods = listOf("Semester 1 · 2025/26", "Semester 2 · 2025/26", "Semester 1 · 2026/27", "Semester 2 · 2026/27", "Summer · 2025/26")
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

@Composable private fun AddCourseDialog(onDismiss: () -> Unit, onSave: (String, String, Float) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("5") }
    val targetHours = target.toFloatOrNull()?.takeIf { it in .5f..80f }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a course", color = Navy, fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(code, { code = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Course code") }, placeholder = { Text("e.g. CSC 320") }, singleLine = true)
            OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Course name") }, singleLine = true)
            OutlinedTextField(target, { target = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Weekly study target") }, suffix = { Text("hours") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        } },
        confirmButton = { TextButton(onClick = { if (code.isNotBlank() && name.isNotBlank() && targetHours != null) onSave(code.trim().uppercase(), name.trim(), targetHours) }, enabled = code.isNotBlank() && name.isNotBlank() && targetHours != null) { Text("Add course", color = Navy) } },
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

@Composable private fun CoursePerformanceCard(course: Course) {
    val current = course.mark.removeSuffix("%").toFloatOrNull()
    val previous = course.previousMark.removeSuffix("%").toFloatOrNull()
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
            Text("No assessment trend yet. Add results to track your academic progress.", color = Muted, fontSize = 10.sp)
        }
      }
    }
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
                    Text("AI COURSE INSIGHT", color = Amber, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
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
    if (marks.isEmpty()) return "Add course assessment results to receive performance insights. This view uses sample course data."
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
                    Text("AI PERFORMANCE INSIGHT", color = Amber, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
                    Text("Your consistency is producing results", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(detail, color = Color(0xFFD8DFEA), fontSize = 10.sp, lineHeight = 15.sp)
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggleEvidence).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (expanded) "Hide evidence" else "View evidence", color = Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ArrowForward, null, tint = Amber, modifier = Modifier.size(14.dp))
            }
            if (expanded) Text("This insight compares the selected courses’ assessment averages with study-target completion. The current screen uses sample data until real study sessions and marks are connected.", color = Color(0xFFCBD3E1), fontSize = 9.sp, lineHeight = 14.sp)
        }
    }
}

@Composable private fun PerformanceStudyTrendCard(mode: String, onModeChange: (String) -> Unit, courses: List<Course>, period: String) {
    val performance = listOf(61f, 66f, 64f, 70f, 73f, 69f, 77f, 82f)
    val study = listOf(3.5f, 4.2f, 4f, 5.6f, 6.2f, 5.8f, 7.1f, 8f)
    val visiblePoints = if (period == "This week") 7 else if (period == "This month") 4 else 8
    val visiblePerformance = performance.takeLast(visiblePoints)
    val visibleStudy = study.takeLast(visiblePoints)
    val avg = courses.mapNotNull { it.mark.removeSuffix("%").toFloatOrNull() }.average().takeIf { !it.isNaN() }?.toFloat() ?: 72f
    val adjustment = avg - 72f
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Performance & study trend", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("$period · ${if (courses.size == 1) courses.first().code else "All courses"}", color = Muted, fontSize = 8.sp)
                }
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F4F6)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("Performance only" to "Grades", "Study only" to "Study", "Both" to "Both").forEach { (value, label) ->
                        Surface(modifier = Modifier.clip(RoundedCornerShape(7.dp)).clickable { onModeChange(value) }, color = if (mode == value) Color.White else Color.Transparent, shape = RoundedCornerShape(7.dp), shadowElevation = if (mode == value) 1.dp else 0.dp) {
                            Text(label, Modifier.padding(horizontal = 6.dp, vertical = 6.dp), color = Navy, fontSize = 7.sp, fontWeight = if (mode == value) FontWeight.Bold else FontWeight.Medium)
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
                        if (mode != "Study only") plot(visiblePerformance.map { (it + adjustment).coerceIn(0f, 100f) }, Amber)
                        if (mode != "Performance only") plot(visibleStudy.map { (it * 10f).coerceIn(0f, 100f) }, Navy)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        visiblePerformance.indices.forEach { index -> Text(if (period == "This week") "D${index + 1}" else "W${index + 1}", color = Muted, fontSize = 7.sp) }
                    }
                }
            }
            Text("Study hours are normalized for comparison with average grades. Sample data is illustrative.", color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, color = Muted, fontSize = 9.sp)
    }
}

@Composable private fun TimeAllocationCard(multiplier: Float) {
    val entries = listOf(
        Triple("Classes and assignments", 6f, Navy),
        Triple("Independent study", 6.5f, Amber),
        Triple("Extracurricular", 4f, Green),
        Triple("Personal", 2f, Color(0xFF8A94A6))
    )
    val total = entries.sumOf { it.second.toDouble() }.toFloat() * multiplier
    val academic = (entries[0].second + entries[1].second) * multiplier
    val nonAcademic = (entries[2].second + entries[3].second) * multiplier
    val ratio = academic / nonAcademic
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0EAE0)), shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Time allocation", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${total.prettyHours()} hours tracked · average weekly distribution", color = Muted, fontSize = 8.sp)
                }
                Icon(Icons.Default.MoreHoriz, "Time allocation options", tint = Muted)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                        val stroke = 22.dp.toPx()
                        var startAngle = -90f
                        val baseTotal = entries.sumOf { it.second.toDouble() }.toFloat()
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
            entries.forEach { (label, hours, color) ->
                val percent = (hours / entries.sumOf { it.second.toDouble() }.toFloat() * 100).toInt()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(8.dp).background(color, CircleShape))
                    Text(label, Modifier.weight(1f), color = Navy, fontSize = 9.sp)
                    Text("${(hours * multiplier).prettyHours()}h", color = Muted, fontSize = 8.sp)
                    Text("$percent%", color = Navy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (ratio in 1.5f..3f) Color(0xFFEAF6F1) else Color(0xFFFFF0EB)).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(if (ratio in 1.5f..3f) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (ratio in 1.5f..3f) Green else Color(0xFFB84A2F), modifier = Modifier.size(15.dp))
                Text(if (ratio in 1.5f..3f) "Healthy allocation · academic to personal ratio ${"%.1f".format(ratio)}:1" else "Review your academic and personal time balance · ${"%.1f".format(ratio)}:1", color = if (ratio in 1.5f..3f) Green else Color(0xFFB84A2F), fontSize = 8.sp, fontWeight = FontWeight.SemiBold, lineHeight = 12.sp)
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

@Composable private fun FocusPatternCard(period: String) {
    val weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val times = listOf("Morning", "Midday", "Afternoon", "Evening")
    fun intensity(time: Int, day: Int): Int = (time * 3 + day * 2 + time * day) % 5
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
                        Text("Peak 9–12", color = Color(0xFF9A6000), fontSize = 7.sp, fontWeight = FontWeight.Bold)
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
            Text("Illustrative study intensity. Connect tracked focus sessions to show your personal history.", color = Muted, fontSize = 8.sp, lineHeight = 12.sp)
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
@Composable private fun AddActivityDialog(courses: List<Course>, onDismiss: () -> Unit, onSave: (String, String, String, String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("None") }
    var due by remember { mutableStateOf("") }
    var activityType by remember { mutableStateOf("Academic task") }
    var priority by remember { mutableStateOf("AI recommendations") }
    var smartReminder by remember { mutableStateOf(false) }
    var courseMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    val courseOptions = courses.map { "${it.code} · ${it.name}" } + listOf("Personal", "None")
    val typeOptions = listOf("Academic task", "Class or assignment", "Study session", "Personal activity", "Extracurricular task")
    val priorityOptions = listOf("Low", "Medium", "High", "AI recommendations")

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create new activity", color = Navy, fontWeight = FontWeight.Bold) }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Task name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(expanded = courseMenu, onExpandedChange = { courseMenu = !courseMenu }) {
                OutlinedTextField(course, {}, readOnly = true, label = { Text("Course") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(courseMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                    courseOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { course = option; courseMenu = false }) }
                }
            }
            OutlinedTextField(due, { due = it }, label = { Text("Due date and time") }, singleLine = true, placeholder = { Text("e.g. Friday, 2:00 PM") }, modifier = Modifier.fillMaxWidth())
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
                    Text("Suggest a reminder based on urgency and available time", color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onSave(title.trim(), course, due.trim(), activityType, priority, smartReminder) }, enabled = title.isNotBlank()) { Text("Save activity", color = Navy) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }, containerColor = Color.White)
}
