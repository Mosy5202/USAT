package com.usat.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsatDatabaseTest {
    @get:Rule val migrationHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UsatDatabase::class.java)
    private lateinit var context: Context
    private lateinit var database: UsatDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, UsatDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { database.close() }

    @Test fun databaseStartsEmptyAndStoresUserRecords() = runBlocking {
        val dao = database.dao()
        assertTrue(dao.observeCourses().first().isEmpty())
        assertTrue(dao.observeActivities().first().isEmpty())
        assertTrue(dao.observeSessions().first().isEmpty())
        assertTrue(dao.observeAssessments().first().isEmpty())
        assertTrue(dao.observeProfiles().first().isEmpty())
        assertNull(dao.observeStudentProfile().first())
        assertTrue(dao.observeProfileCourses().first().isEmpty())

        dao.insertCourse(CourseEntity("COM411", "Mobile Development", "Semester 1", 6f))
        val activityId = dao.insertActivity(ActivityEntity(title = "Project report", courseCode = "COM411", courseLabel = "COM411 · Mobile Development", dueLabel = "Due soon", dueAtMillis = 1_800_000_000_000L, category = "Academic task", priority = "High", smartReminder = true, plannedHours = 2f))
        val assessmentId = dao.insertAssessment(AssessmentEntity(courseCode = "COM411", title = "Quiz 1", score = 18f, maxScore = 20f))
        val sessionId = dao.startSession(StudySessionEntity(courseCode = "COM411", startedAtMillis = 1_700_000_000_000L))

        assertEquals("COM411", dao.observeCourses().first().single().code)
        assertEquals(activityId, dao.observeActivities().first().single().id)
        assertEquals(assessmentId, dao.observeAssessments().first().single().id)
        assertEquals(sessionId, dao.observeOpenSession().first()?.id)
    }

    @Test fun fileBackedDatabaseRetainsCourseAfterReopen() = runBlocking {
        val name = "usat-persistence-test-${System.nanoTime()}.db"
        val first = Room.databaseBuilder(context, UsatDatabase::class.java, name).build()
        first.dao().insertCourse(CourseEntity("TEST101", "Persistence check", "Semester 1", 3f))
        first.dao().saveStudentAndAcademicProfile(
            StudentProfileEntity(username = "student_1", fullName = "Test Student", email = "student@example.edu", phoneNumber = null, universityName = "Test University", faculty = "Computing", programme = "Computer Science", registrationNumber = null),
            AcademicProfileEntity(academicYear = "2026/2027", weeklyStudyGoalHours = 12f, yearOfStudy = "Third Year", semester = "Semester 1"),
            listOf("TEST101"), AppSettingsEntity(academicPeriod = "Semester 1")
        )
        first.close()

        val reopened = Room.databaseBuilder(context, UsatDatabase::class.java, name).build()
        try {
            assertEquals("TEST101", reopened.dao().observeCourses().first().single().code)
            val profile = reopened.dao().observeProfiles().first().single()
            assertEquals("2026/2027", profile.academicYear)
            assertEquals(12f, profile.weeklyStudyGoalHours)
            assertEquals("TEST101", reopened.dao().observeCoursesForProfile(profile.id).first().single().code)
            assertEquals("Test Student", reopened.dao().observeStudentProfile().first()?.fullName)
            assertEquals("Test University", reopened.dao().observeStudentProfile().first()?.universityName)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun academicProfileCanBeCreatedEditedAndReopenedWithoutDuplicates() = runBlocking {
        val dao = database.dao()
        dao.insertCourse(CourseEntity("NET301", "Computer Networks", "Semester 1", 5f))
        val student = StudentProfileEntity(username = "net_student", fullName = "Net Student", email = null, phoneNumber = null, universityName = "North University", faculty = "Engineering", programme = "BSc Networks", registrationNumber = null)
        val firstId = dao.saveStudentAndAcademicProfile(student, AcademicProfileEntity(academicYear = "2026/2027", weeklyStudyGoalHours = 12f, yearOfStudy = "Third Year", semester = "Semester 1"), listOf("NET301"), AppSettingsEntity(academicPeriod = "Semester 1"))
        val first = dao.getProfile(firstId)!!
        assertEquals("2026/2027", first.academicYear)
        assertEquals("Third Year", first.yearOfStudy)
        assertEquals(12f, first.weeklyStudyGoalHours)
        assertEquals("Computer Networks", dao.observeCoursesForProfile(firstId).first().single().name)

        dao.saveStudentAndAcademicProfile(student.copy(fullName = "Updated Net Student"), first.copy(weeklyStudyGoalHours = 14f), listOf("NET301", "NET301"), AppSettingsEntity(academicPeriod = "Semester 1"))
        assertEquals(1, scalar(database.openHelper.writableDatabase, "SELECT COUNT(*) FROM academic_profiles"))
        assertEquals(1, scalar(database.openHelper.writableDatabase, "SELECT COUNT(*) FROM student_profile"))
        assertEquals(14f, dao.getProfile(firstId)?.weeklyStudyGoalHours ?: 0f)
        assertEquals("Updated Net Student", dao.getStudentProfile()?.fullName)
        assertEquals(1, dao.observeProfileCourses().first().count { it.profileId == firstId })

        val secondId = dao.saveStudentAndAcademicProfile(student.copy(fullName = "Updated Net Student"), AcademicProfileEntity(academicYear = "2027/2028", weeklyStudyGoalHours = 10f, yearOfStudy = "Fourth Year"), emptyList(), AppSettingsEntity(academicPeriod = "Semester 1"))
        assertTrue(secondId != firstId)
        assertEquals(2, dao.observeProfiles().first().size)
        assertEquals(14f, dao.getProfile(firstId)?.weeklyStudyGoalHours ?: 0f)
        assertEquals("2027/2028", dao.getProfile(secondId)?.academicYear)
        assertEquals("Fourth Year", dao.getProfile(secondId)?.yearOfStudy)
    }

    @Test fun migrationThreeToFourAddsAnEmptyProfileWithoutDroppingUserRecords() {
        val name = "usat-migration-v3.db"
        migrationHelper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO courses(code, name, academicPeriod, weeklyTargetHours) VALUES('NET301', 'Computer Networks', 'Semester 1', 5.0)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(name, 4, true, UsatDatabase.MIGRATION_3_4)
        try {
            assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM courses WHERE code = 'NET301'"))
            assertEquals(0, scalar(migrated, "SELECT COUNT(*) FROM academic_profile"))
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun updatesAndDeletesRemainDatabaseBacked() = runBlocking {
        val dao = database.dao()
        dao.insertCourse(CourseEntity("MAT110", "Mathematics", "Semester 1", 4f))
        dao.updateTarget("MAT110", 6f)
        dao.saveSettings(AppSettingsEntity(academicPeriod = "Semester 1", eventCategoryFilter = "Study"))
        val activityId = dao.insertActivity(ActivityEntity(title = "Practice", courseCode = "MAT110", courseLabel = "MAT110 · Mathematics", dueLabel = "", dueAtMillis = null, category = "Study session", priority = "Medium", smartReminder = false, plannedHours = 1f))
        dao.setActivityCompleted(activityId, true)
        val assessmentId = dao.insertAssessment(AssessmentEntity(courseCode = "MAT110", title = "Quiz", score = 8f, maxScore = 10f))
        dao.updateAssessment(AssessmentEntity(id = assessmentId, courseCode = "MAT110", title = "Quiz revised", score = 9f, maxScore = 10f))

        assertEquals(6f, dao.observeCourses().first().single().weeklyTargetHours)
        assertEquals("Study", dao.observeSettings().first()?.eventCategoryFilter)
        assertTrue(dao.observeActivities().first().single().completed)
        assertEquals("Quiz revised", dao.observeAssessments().first().single().title)

        dao.deleteActivity(activityId)
        dao.deleteAssessment(assessmentId)
        dao.deleteCourse("MAT110")
        assertTrue(dao.observeActivities().first().isEmpty())
        assertTrue(dao.observeAssessments().first().isEmpty())
        assertTrue(dao.observeCourses().first().isEmpty())
    }

    @Test fun summariesAreCalculatedFromRecordedAssessmentsAndSessions() {
        assertNull(UsatCalculations.weightedAssessmentPercent(emptyList()))
        val grade = UsatCalculations.weightedAssessmentPercent(listOf(
            AssessmentEntity(courseCode = "A", title = "Quiz", score = 18f, maxScore = 20f),
            AssessmentEntity(courseCode = "A", title = "Exam", score = 70f, maxScore = 100f)
        ))
        assertEquals(73.333f, grade!!, .01f)

        val hours = UsatCalculations.completedStudyHours(listOf(
            StudySessionEntity(courseCode = "A", startedAtMillis = 1L, endedAtMillis = 3_601_000L, accumulatedSeconds = 3600, state = "completed"),
            StudySessionEntity(courseCode = "A", startedAtMillis = 2L, state = "active")
        ))
        assertEquals(1f, hours, .001f)
        assertEquals(.5f, UsatCalculations.targetProgressFraction(2f, 4f), .001f)
        assertEquals(0f, UsatCalculations.targetProgressFraction(2f, 0f), .001f)
    }

    @Test fun migratesPreviouslyShippedVersionTwoWithoutLosingRecords() {
        val name = "usat-migration-v2.db"
        migrationHelper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO courses(code, name, academicPeriod, weeklyTargetHours) VALUES('COM411', 'Mobile Development', 'Semester 1', 6.0)")
            execSQL("INSERT INTO activities(id, title, courseCode, courseLabel, dueLabel, dueAtMillis, category, priority, smartReminder, plannedHours, completed, createdAtMillis) VALUES(11, 'Project', 'COM411', 'COM411', 'Soon', 1800000000000, 'Academic task', 'High', 1, 2.0, 0, 1700000000000)")
            execSQL("INSERT INTO study_sessions(id, courseCode, startedAtMillis, endedAtMillis, plannedDurationSeconds, state) VALUES(12, 'COM411', 1700000000000, 1700000060000, 1500, 'completed')")
            execSQL("INSERT INTO assessments(id, courseCode, title, score, maxScore, recordedAtMillis) VALUES(13, 'COM411', 'Quiz', 8.0, 10.0, 1700000060000)")
            execSQL("INSERT INTO app_settings(id, eventCategoryFilter, selectedEventDateEpochDay) VALUES(1, 'Academic', 20000)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(name, 3, true, UsatDatabase.MIGRATION_2_3)
        try {
            assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM courses"))
            assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM activities WHERE id = 11 AND smartReminder = 1"))
            assertEquals(60, scalar(migrated, "SELECT accumulatedSeconds FROM study_sessions WHERE id = 12"))
            assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM assessments WHERE id = 13"))
            migrated.query("SELECT academicPeriod, eventCategoryFilter, selectedEventDateEpochDay FROM app_settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).startsWith("Semester 1"))
                assertEquals("Academic", cursor.getString(1))
                assertEquals(20000L, cursor.getLong(2))
            }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationFourToFivePreservesProfileAndAssociatesItsExistingCourses() {
        val name = "usat-migration-v4.db"
        migrationHelper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO courses(code, name, academicPeriod, weeklyTargetHours) VALUES('NET301', 'Computer Networks', 'Semester 1', 5.0)")
            execSQL("INSERT INTO academic_profile(id, academicYear, weeklyStudyGoalHours) VALUES(1, 'Third Year', 12.0)")
            execSQL("INSERT INTO app_settings(id, academicPeriod, eventCategoryFilter, selectedEventDateEpochDay) VALUES(1, 'Semester 1', 'All', NULL)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(name, 5, true, UsatDatabase.MIGRATION_4_5)
        try {
            assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM academic_profiles WHERE id = 1 AND academicYear = 'Third Year' AND weeklyStudyGoalHours = 12"))
            assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM profile_courses WHERE profileId = 1 AND courseCode = 'NET301'"))
            migrated.query("SELECT activeProfileId FROM app_settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
            }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationFiveToSixPreservesAcademicDataAndAddsEmptyStudentIdentity() {
        val name = "usat-migration-v5.db"
        migrationHelper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO courses(code, name, academicPeriod, weeklyTargetHours) VALUES('NET301', 'Computer Networks', 'Semester 1', 5.0)")
            execSQL("INSERT INTO academic_profiles(id, academicYear, weeklyStudyGoalHours) VALUES(1, '2026/2027', 12.0)")
            execSQL("INSERT INTO profile_courses(profileId, courseCode) VALUES(1, 'NET301')")
            execSQL("INSERT INTO app_settings(id, academicPeriod, eventCategoryFilter, activeProfileId) VALUES(1, 'Semester 1', 'All', 1)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(name, 6, true, UsatDatabase.MIGRATION_5_6)
        try {
            assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM courses WHERE code = 'NET301'"))
            assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM academic_profiles WHERE id = 1 AND academicYear = '2026/2027' AND weeklyStudyGoalHours = 12"))
            assertEquals(1, scalar(migrated, "SELECT COUNT(*) FROM profile_courses WHERE profileId = 1 AND courseCode = 'NET301'"))
            assertEquals(0, scalar(migrated, "SELECT COUNT(*) FROM student_profile"))
            migrated.query("SELECT yearOfStudy, semester FROM academic_profiles WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
            }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun studentUsernameIsUniqueIgnoringCaseAtRoomLevel() = runBlocking {
        val dao = database.dao()
        dao.insertStudentProfile(StudentProfileEntity(username = "unique_student", fullName = "First Student", email = null, phoneNumber = null, universityName = "University", faculty = "School", programme = "Degree", registrationNumber = null))
        assertTrue(dao.usernameInUse("UNIQUE_STUDENT", exceptId = 2))
        try {
            dao.insertStudentProfile(StudentProfileEntity(id = 2, username = "UNIQUE_STUDENT", fullName = "Second Student", email = null, phoneNumber = null, universityName = "University", faculty = "School", programme = "Degree", registrationNumber = null))
            throw AssertionError("Expected the unique username index to reject a duplicate.")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            assertEquals(1, scalar(database.openHelper.writableDatabase, "SELECT COUNT(*) FROM student_profile"))
        }
    }

    @Test fun migratesLocalVersionOneSettingsWithoutDroppingSessionTime() {
        val name = "usat-migration-v1.db"
        migrationHelper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO courses(code, name, academicPeriod, weeklyTargetHours) VALUES('BIO100', 'Biology', 'Semester 1', 4.0)")
            execSQL("INSERT INTO study_sessions(id, courseCode, startedAtMillis, endedAtMillis, plannedDurationSeconds, accumulatedSeconds, state) VALUES(1, 'BIO100', 1700000000000, 1700000120000, 1500, 120, 'completed')")
            execSQL("INSERT INTO app_settings(id, academicPeriod, eventCategoryFilter) VALUES(1, 'Semester 1', 'Study')")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(name, 3, true, UsatDatabase.MIGRATION_1_3)
        try {
            assertEquals(120, scalar(migrated, "SELECT accumulatedSeconds FROM study_sessions WHERE id = 1"))
            migrated.query("SELECT academicPeriod, eventCategoryFilter, selectedEventDateEpochDay FROM app_settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Semester 1", cursor.getString(0))
                assertEquals("Study", cursor.getString(1))
                assertTrue(cursor.isNull(2))
            }
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, query: String): Int =
        db.query(query).use { cursor -> check(cursor.moveToFirst()); cursor.getInt(0) }

    @Test fun startingAnotherSessionClosesPreviousAndKeepsElapsedTime() = runBlocking {
        val dao = database.dao()
        val start = 1_700_000_000_000L
        val oldId = dao.startSession(StudySessionEntity(courseCode = null, startedAtMillis = start))
        val newId = dao.startSession(StudySessionEntity(courseCode = null, startedAtMillis = start + 90_000L))

        val rows = dao.observeSessions().first().associateBy { it.id }
        assertEquals("completed", rows.getValue(oldId).state)
        assertEquals(90, rows.getValue(oldId).accumulatedSeconds)
        assertEquals(newId, dao.observeOpenSession().first()?.id)
    }

    @Test fun courseDeleteCascadesAssessmentButKeepsUnlinkedActivityAndSession() = runBlocking {
        val dao = database.dao()
        dao.insertCourse(CourseEntity("BIO100", "Biology", "Semester 1", 4f))
        dao.insertActivity(ActivityEntity(title = "Club meeting", courseCode = "BIO100", courseLabel = "BIO100 · Biology", dueLabel = "", dueAtMillis = null, category = "Extracurricular task", priority = "Low", smartReminder = false, plannedHours = 1f))
        dao.insertAssessment(AssessmentEntity(courseCode = "BIO100", title = "Test", score = 7f, maxScore = 10f))
        dao.insertSession(StudySessionEntity(courseCode = "BIO100", startedAtMillis = 1_700_000_000_000L, state = "completed", endedAtMillis = 1_700_000_060_000L, accumulatedSeconds = 60))

        dao.deleteCourse("BIO100")

        assertTrue(dao.observeAssessments().first().isEmpty())
        assertNull(dao.observeActivities().first().single().courseCode)
        assertNull(dao.observeSessions().first().single().courseCode)
    }
}
