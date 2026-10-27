package com.usat.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "courses", indices = [Index("academicPeriod")])
data class CourseEntity(
    @PrimaryKey val code: String,
    val name: String,
    val academicPeriod: String,
    val weeklyTargetHours: Float
)

@Entity(
    tableName = "activities",
    foreignKeys = [ForeignKey(entity = CourseEntity::class, parentColumns = ["code"], childColumns = ["courseCode"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("courseCode"), Index("dueAtMillis"), Index("category"), Index("completed")]
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val courseCode: String?,
    val courseLabel: String,
    val dueLabel: String,
    val dueAtMillis: Long?,
    val category: String,
    val priority: String,
    val smartReminder: Boolean,
    val plannedHours: Float,
    val completed: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_sessions",
    foreignKeys = [ForeignKey(entity = CourseEntity::class, parentColumns = ["code"], childColumns = ["courseCode"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("courseCode"), Index("startedAtMillis"), Index("endedAtMillis"), Index("state")]
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseCode: String?,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val plannedDurationSeconds: Int = 25 * 60,
    val accumulatedSeconds: Int = 0,
    val state: String = "active"
)

@Entity(
    tableName = "assessments",
    foreignKeys = [ForeignKey(entity = CourseEntity::class, parentColumns = ["code"], childColumns = ["courseCode"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("courseCode"), Index("recordedAtMillis")]
)
data class AssessmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseCode: String,
    val title: String,
    val score: Float,
    val maxScore: Float,
    val recordedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val academicPeriod: String,
    val eventCategoryFilter: String = "All",
    val selectedEventDateEpochDay: Long? = null,
    val activeProfileId: Long? = null
)

@Entity(tableName = "academic_profiles")
data class AcademicProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val academicYear: String,
    val weeklyStudyGoalHours: Float,
    val yearOfStudy: String? = null,
    val semester: String? = null
)

/** Local identity and institution details are shared by all of the student's academic records. */
@Entity(tableName = "student_profile", indices = [Index(value = ["username"], unique = true)])
data class StudentProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val username: String,
    val fullName: String,
    val email: String?,
    val phoneNumber: String?,
    val universityName: String,
    val faculty: String,
    val programme: String,
    val registrationNumber: String?
)

class DuplicateUsernameException : IllegalArgumentException("That username is already in use on this device.")

@Entity(
    tableName = "profile_courses",
    primaryKeys = ["profileId", "courseCode"],
    foreignKeys = [
        ForeignKey(entity = AcademicProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CourseEntity::class, parentColumns = ["code"], childColumns = ["courseCode"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("courseCode")]
)
data class ProfileCourseEntity(val profileId: Long, val courseCode: String)

@Dao
interface UsatDao {
    @Query("SELECT * FROM courses ORDER BY academicPeriod DESC, name COLLATE NOCASE") fun observeCourses(): Flow<List<CourseEntity>>
    @Query("SELECT * FROM activities ORDER BY completed, CASE WHEN dueAtMillis IS NULL THEN 1 ELSE 0 END, dueAtMillis, createdAtMillis DESC") fun observeActivities(): Flow<List<ActivityEntity>>
    @Query("SELECT * FROM study_sessions ORDER BY startedAtMillis DESC") fun observeSessions(): Flow<List<StudySessionEntity>>
    @Query("SELECT * FROM assessments ORDER BY recordedAtMillis DESC") fun observeAssessments(): Flow<List<AssessmentEntity>>
    @Query("SELECT * FROM app_settings WHERE id = 1") fun observeSettings(): Flow<AppSettingsEntity?>
    @Query("SELECT * FROM academic_profiles ORDER BY id") fun observeProfiles(): Flow<List<AcademicProfileEntity>>
    @Query("SELECT * FROM student_profile WHERE id = 1") fun observeStudentProfile(): Flow<StudentProfileEntity?>
    @Query("SELECT * FROM student_profile WHERE id = 1") suspend fun getStudentProfile(): StudentProfileEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM student_profile WHERE username = :username COLLATE NOCASE AND id != :exceptId)") suspend fun usernameInUse(username: String, exceptId: Int): Boolean
    @Query("SELECT * FROM profile_courses ORDER BY profileId, courseCode") fun observeProfileCourses(): Flow<List<ProfileCourseEntity>>
    @Query("SELECT * FROM academic_profiles WHERE id = :id LIMIT 1") suspend fun getProfile(id: Long): AcademicProfileEntity?
    @Query("SELECT courses.* FROM courses INNER JOIN profile_courses ON courses.code = profile_courses.courseCode WHERE profile_courses.profileId = :profileId ORDER BY courses.name COLLATE NOCASE") fun observeCoursesForProfile(profileId: Long): Flow<List<CourseEntity>>
    @Query("SELECT * FROM app_settings WHERE id = 1") suspend fun getSettings(): AppSettingsEntity?
    @Query("SELECT * FROM courses WHERE code = :code LIMIT 1") suspend fun getCourse(code: String): CourseEntity?
    @Query("SELECT * FROM study_sessions WHERE state IN ('active', 'paused') ORDER BY id DESC LIMIT 1") suspend fun getOpenSession(): StudySessionEntity?
    @Query("SELECT * FROM study_sessions WHERE state IN ('active', 'paused')") suspend fun getOpenSessions(): List<StudySessionEntity>
    @Query("SELECT * FROM study_sessions WHERE state IN ('active', 'paused') ORDER BY id DESC LIMIT 1") fun observeOpenSession(): Flow<StudySessionEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCourse(course: CourseEntity)
    @Update suspend fun updateCourse(course: CourseEntity)
    @Query("UPDATE courses SET weeklyTargetHours = :hours WHERE code = :code") suspend fun updateTarget(code: String, hours: Float)
    @Query("DELETE FROM courses WHERE code = :code") suspend fun deleteCourse(code: String)

    @Insert suspend fun insertActivity(activity: ActivityEntity): Long
    @Update suspend fun updateActivity(activity: ActivityEntity)
    @Query("UPDATE activities SET completed = :completed WHERE id = :id") suspend fun setActivityCompleted(id: Long, completed: Boolean)
    @Query("DELETE FROM activities WHERE id = :id") suspend fun deleteActivity(id: Long)

    @Insert suspend fun insertAssessment(assessment: AssessmentEntity): Long
    @Update suspend fun updateAssessment(assessment: AssessmentEntity)
    @Query("DELETE FROM assessments WHERE id = :id") suspend fun deleteAssessment(id: Long)

    @Insert suspend fun insertSession(session: StudySessionEntity): Long
    @Query("UPDATE study_sessions SET endedAtMillis = :endedAt, accumulatedSeconds = :elapsedSeconds, state = 'paused' WHERE id = :id AND state = 'active'") suspend fun pauseSession(id: Long, endedAt: Long, elapsedSeconds: Int)
    @Query("UPDATE study_sessions SET startedAtMillis = :startedAt, endedAtMillis = NULL, state = 'active' WHERE id = :id AND state = 'paused'") suspend fun resumeSession(id: Long, startedAt: Long)
    @Query("UPDATE study_sessions SET endedAtMillis = :endedAt, accumulatedSeconds = :elapsedSeconds, state = 'completed' WHERE id = :id AND state IN ('active', 'paused')") suspend fun completeSession(id: Long, endedAt: Long, elapsedSeconds: Int)
    @Query("UPDATE study_sessions SET endedAtMillis = :endedAt, accumulatedSeconds = :elapsedSeconds, state = 'completed' WHERE id = :id AND state IN ('active', 'paused')") suspend fun closeSession(id: Long, endedAt: Long, elapsedSeconds: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(settings: AppSettingsEntity)
    @Insert suspend fun insertProfile(profile: AcademicProfileEntity): Long
    @Update suspend fun updateProfile(profile: AcademicProfileEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addProfileCourses(courses: List<ProfileCourseEntity>)
    @Query("DELETE FROM profile_courses WHERE profileId = :profileId") suspend fun clearProfileCourses(profileId: Long)
    @Query("DELETE FROM academic_profiles WHERE id = :id") suspend fun deleteProfile(id: Long)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertStudentProfile(profile: StudentProfileEntity)
    @Update suspend fun updateStudentProfile(profile: StudentProfileEntity)

    @Transaction
    suspend fun saveStudentAndAcademicProfile(
        student: StudentProfileEntity,
        profile: AcademicProfileEntity,
        courseCodes: List<String>,
        settings: AppSettingsEntity
    ): Long {
        if (usernameInUse(student.username, student.id)) throw DuplicateUsernameException()
        if (getStudentProfile() == null) insertStudentProfile(student) else updateStudentProfile(student)
        val profileId = if (profile.id == 0L) {
            insertProfile(profile.copy(id = 0L))
        } else {
            updateProfile(profile)
            clearProfileCourses(profile.id)
            profile.id
        }
        val associations = courseCodes.distinct().map { ProfileCourseEntity(profileId, it) }
        if (associations.isNotEmpty()) addProfileCourses(associations)
        saveSettings(settings.copy(activeProfileId = profileId))
        return profileId
    }

    @Transaction
    suspend fun createProfile(profile: AcademicProfileEntity, courseCodes: List<String>): Long {
        val id = insertProfile(profile)
        if (courseCodes.isNotEmpty()) addProfileCourses(courseCodes.distinct().map { ProfileCourseEntity(id, it) })
        return id
    }

    @Transaction
    suspend fun updateProfileAndCourses(profile: AcademicProfileEntity, courseCodes: List<String>) {
        updateProfile(profile)
        clearProfileCourses(profile.id)
        if (courseCodes.isNotEmpty()) addProfileCourses(courseCodes.distinct().map { ProfileCourseEntity(profile.id, it) })
    }

    @Transaction
    suspend fun startSession(session: StudySessionEntity): Long {
        getOpenSessions().forEach { open ->
            val elapsed = open.accumulatedSeconds + if (open.state == "active") ((session.startedAtMillis - open.startedAtMillis).coerceAtLeast(0) / 1000L).toInt() else 0
            closeSession(open.id, session.startedAtMillis, elapsed)
        }
        return insertSession(session)
    }
}

@Database(entities = [CourseEntity::class, ActivityEntity::class, StudySessionEntity::class, AssessmentEntity::class, AppSettingsEntity::class, AcademicProfileEntity::class, ProfileCourseEntity::class, StudentProfileEntity::class], version = 6, exportSchema = true)
abstract class UsatDatabase : RoomDatabase() {
    abstract fun dao(): UsatDao

    companion object {
        /** Version 1 was the local-only schema previously shipped from this project. */
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN selectedEventDateEpochDay INTEGER")
            }
        }

        /** Version 2 was shipped by the earlier account-center build; retain its local records and settings. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activities_completed ON activities(completed)")

                db.execSQL("CREATE TABLE study_sessions_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseCode TEXT, startedAtMillis INTEGER NOT NULL, endedAtMillis INTEGER, plannedDurationSeconds INTEGER NOT NULL, accumulatedSeconds INTEGER NOT NULL, state TEXT NOT NULL, FOREIGN KEY(courseCode) REFERENCES courses(code) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO study_sessions_new (id, courseCode, startedAtMillis, endedAtMillis, plannedDurationSeconds, accumulatedSeconds, state) SELECT id, courseCode, startedAtMillis, endedAtMillis, plannedDurationSeconds, CASE WHEN endedAtMillis IS NULL THEN 0 ELSE MAX(0, CAST((endedAtMillis - startedAtMillis) / 1000 AS INTEGER)) END, state FROM study_sessions")
                db.execSQL("DROP TABLE study_sessions")
                db.execSQL("ALTER TABLE study_sessions_new RENAME TO study_sessions")
                db.execSQL("CREATE INDEX index_study_sessions_courseCode ON study_sessions(courseCode)")
                db.execSQL("CREATE INDEX index_study_sessions_startedAtMillis ON study_sessions(startedAtMillis)")
                db.execSQL("CREATE INDEX index_study_sessions_endedAtMillis ON study_sessions(endedAtMillis)")
                db.execSQL("CREATE INDEX index_study_sessions_state ON study_sessions(state)")

                db.execSQL("CREATE TABLE app_settings_new (id INTEGER NOT NULL, academicPeriod TEXT NOT NULL, eventCategoryFilter TEXT NOT NULL, selectedEventDateEpochDay INTEGER, PRIMARY KEY(id))")
                val year = java.time.LocalDate.now().year
                val currentPeriod = "Semester 1 · $year/${(year + 1) % 100}"
                db.execSQL("INSERT INTO app_settings_new (id, academicPeriod, eventCategoryFilter, selectedEventDateEpochDay) SELECT id, '$currentPeriod', eventCategoryFilter, selectedEventDateEpochDay FROM app_settings")
                db.execSQL("DROP TABLE app_settings")
                db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS academic_profile (id INTEGER NOT NULL, academicYear TEXT NOT NULL, weeklyStudyGoalHours REAL NOT NULL, PRIMARY KEY(id))")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS academic_profiles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, academicYear TEXT NOT NULL, weeklyStudyGoalHours REAL NOT NULL)")
                db.execSQL("INSERT INTO academic_profiles (id, academicYear, weeklyStudyGoalHours) SELECT id, academicYear, weeklyStudyGoalHours FROM academic_profile")
                db.execSQL("CREATE TABLE IF NOT EXISTS profile_courses (profileId INTEGER NOT NULL, courseCode TEXT NOT NULL, PRIMARY KEY(profileId, courseCode), FOREIGN KEY(profileId) REFERENCES academic_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(courseCode) REFERENCES courses(code) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_profile_courses_courseCode ON profile_courses(courseCode)")
                db.execSQL("INSERT OR IGNORE INTO profile_courses (profileId, courseCode) SELECT 1, code FROM courses WHERE EXISTS (SELECT 1 FROM academic_profiles WHERE id = 1)")
                db.execSQL("DROP TABLE academic_profile")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN activeProfileId INTEGER")
                db.execSQL("UPDATE app_settings SET activeProfileId = CASE WHEN EXISTS (SELECT 1 FROM academic_profiles WHERE id = 1) THEN 1 ELSE NULL END")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE academic_profiles ADD COLUMN yearOfStudy TEXT")
                db.execSQL("ALTER TABLE academic_profiles ADD COLUMN semester TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS student_profile (id INTEGER NOT NULL, username TEXT NOT NULL COLLATE NOCASE, fullName TEXT NOT NULL, email TEXT, phoneNumber TEXT, universityName TEXT NOT NULL, faculty TEXT NOT NULL, programme TEXT NOT NULL, registrationNumber TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_student_profile_username ON student_profile(username)")
            }
        }

        @Volatile private var instance: UsatDatabase? = null
        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_3, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        fun get(context: Context): UsatDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, UsatDatabase::class.java, "usat.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build().also { instance = it }
        }
    }
}
