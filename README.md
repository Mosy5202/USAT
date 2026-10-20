# USAT Android app

This is a native Kotlin and Jetpack Compose Android app for USAT, a student planner. It translates the supplied React dashboard into a phone-first Android interface with bottom navigation.

## Open in Android Studio

1. Open Android Studio and choose **Open**.
2. Select this `USATAndroid` folder.
3. Allow Gradle sync to finish, then run the `app` configuration on an Android device or emulator (API 26 or later).

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, and compile SDK 35. Android Studio may prompt you to install SDK 35.

## Included screens and interactions

- **Overview:** compact AI daily brief with an expandable recommendation explanation, working 25-minute focus timer, and four summary cards in a 2×2 grid for priority tasks, today's schedule, study progress, and upcoming deadlines.
- **Welcome:** a branded welcome screen appears on each fresh app launch before the dashboard.
- **Events:** weekly date strip, total planned hours, course study-target progress, an academic-to-extracurricular balance comparison with ratio and status, a rule-based AI Events Advisor sample, smart-reminder status, and a category-filtered daily plan.
- **Courses:** selectable academic period, course search and add flow, horizontal course cards, selected-course performance trends, editable study targets with progress chart, course insight, and matching upcoming coursework.
- **Analytics:** period/course filters, GPA and study summaries, toggleable performance/study trend, tracked-time allocation with percentages and balance feedback, course performance vs study-target completion, GitHub-style focus grid, and a next-week plan interface placeholder.
- **AI Advisor:** sample recommendations with the factors behind each suggestion.
- Add activities from the responsive top-bar button or floating action button. The form includes task name, course or Personal/None, due date, activity type, priority including AI recommendation, and a smart-reminder checkbox. Mark activities complete with their checkbox.
- The notification control opens an empty state when there are no new notifications.
- The launcher icon and header mark use the supplied StudyOS icon artwork, cropped for the USAT app.

## Data and integration boundary

The screens use illustrative sample student data. Weekly time totals and the balance judgement start from sample schedule data and adjust for newly added activities using estimated durations. Activity changes live in memory and reset when the app process restarts. The smart-reminder checkbox records the user's choice on the sample activity; it does not yet schedule Android system notifications. Recommendations, grades, dates, notifications, profile, and charts are mock content; the source files do not provide an API, database, or AI service to connect. For a production build, add persistent local storage (such as Room), authentication and synchronization, real notification scheduling, and a secured recommendation service. Avoid putting private student records or service secrets in the client.

The project is a native UI conversion, not a conversion of the React runtime or CSS. Colors and hierarchy follow the supplied StudyFlow files, with Android-native Material 3 controls and mobile navigation.
