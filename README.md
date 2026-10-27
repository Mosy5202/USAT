# USAT Android app

This is a native Kotlin and Jetpack Compose Android app for USAT, a student planner. It translates the supplied React dashboard into a phone-first Android interface with bottom navigation.

## Open in Android Studio

1. Open Android Studio and choose **Open**.
2. Select this `USATAndroid` folder.
3. Allow Gradle sync to finish, then run the `app` configuration on an Android device or emulator (API 26 or later).

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, and compile SDK 35. Android Studio may prompt you to install SDK 35.

## Included screens and interactions

- **Overview:** a locally computed study-planner summary with an expandable explanation, working 25-minute focus timer, and four summary cards in a 2×2 grid for priority tasks, today's schedule, study progress, and upcoming deadlines.
- **Welcome:** a branded welcome screen appears on each fresh app launch before the dashboard.
- **Events:** weekly date strip, total planned hours, course study-target progress, an academic-to-extracurricular balance comparison with ratio and status, a local planning insight, saved reminder-preference status, and a category-filtered daily plan.
- **Courses:** selectable academic period, course search and add flow, horizontal course cards, selected-course performance trends, editable study targets with progress chart, course insight, and matching upcoming coursework.
- **Analytics:** period/course filters, GPA and study summaries, toggleable performance/study trend based on recorded assessment and focus-session data, tracked-time allocation with percentages and balance feedback, course performance vs study-target completion, a focus grid based on recorded sessions, and a next-week plan interface placeholder.
- **AI Advisor:** a clear not-connected state; recommendations are not fabricated or generated until a real service is implemented.
- Add activities from the responsive top-bar button or floating action button. The form includes task name, course or Personal/None, due date, activity type, priority, and a smart-reminder checkbox. Mark activities complete with their checkbox.
- The notification control opens an empty state when there are no new notifications.
- The launcher icon and header mark use the supplied StudyOS icon artwork, cropped for the USAT app.

## Data and integration boundary

Courses, activities, assessment results, focus sessions, academic-period selection, and event-category filters are stored locally in Room (`usat.db`). The database starts empty; only the initial academic-period preference is created automatically. No demo courses, tasks, grades, or study sessions are inserted. Screens observe Room through DAOs, a repository, and a ViewModel. Deleting a course removes its assessment records and keeps its activities/sessions with the course association cleared. The Room schema is version 3 and includes non-destructive upgrades from the previously shipped version 1 and version 2 schemas; future schema changes must add explicit migrations. The database has no destructive migration fallback. The smart-reminder checkbox persists the user's preference but does not yet schedule Android system notifications. Planner insights and analytics are calculated locally from saved records; no AI service, account, backend, or cloud sync is connected.

The project is a native UI conversion, not a conversion of the React runtime or CSS. Colors and hierarchy follow the supplied design files, with Android-native Material 3 controls and mobile navigation. Room DAO instrumentation tests cover empty startup, saved records, session timing, and course deletion behavior.
