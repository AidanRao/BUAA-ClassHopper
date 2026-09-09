package top.aidanrao.buaa_classhopper.ui

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import top.aidanrao.buaa_classhopper.R

/** Renders one complete result state so a previous date can never leak into a new query. */
class HomeContentRenderer(private val root: View, onSignClick: (Int) -> Unit) {
    private val context = root.context
    private val courseList = root.findViewById<LinearLayout>(R.id.courseList)
    private val courseListRenderer = CourseListRenderer(courseList, onSignClick)
    private val datePickerContainer = root.findViewById<View>(R.id.datePickerContainer)
    private val emptyStateLayout = root.findViewById<View>(R.id.emptyStateLayout)

    fun render(state: HomeCourseState, signingIds: Set<Int> = emptySet()) {
        val loading = state is HomeCourseState.Loading
        val busy = loading || signingIds.isNotEmpty()
        root.findViewById<Button>(R.id.btnGetClass).apply {
            isEnabled = !busy
            alpha = if (busy) 0.65f else 1f
            setText(if (loading) R.string.home_querying else R.string.home_query)
        }
        datePickerContainer.isEnabled = !busy
        datePickerContainer.alpha = if (busy) 0.65f else 1f
        root.findViewById<View>(R.id.loadingState).isVisible = loading
        val courses = (state as? HomeCourseState.Success)?.courses.orEmpty()
        courseListRenderer.render(courses, signingIds)
        courseList.isVisible = courses.isNotEmpty()
        root.findViewById<TextView>(R.id.courseCount).apply {
            isVisible = state is HomeCourseState.Success && courses.isNotEmpty()
            text = context.getString(R.string.home_course_count, courses.size)
        }
        emptyStateLayout.isVisible = !loading && courses.isEmpty()
        val error = state as? HomeCourseState.Error
        root.findViewById<View>(R.id.emptyArtwork).isVisible = error == null
        root.findViewById<TextView>(R.id.resultStateTitle).setText(if (error == null) R.string.home_empty_title else R.string.home_error_title)
        root.findViewById<TextView>(R.id.resultStateDescription).text = if (error == null) {
            context.getString(R.string.home_empty_description)
        } else context.getString(R.string.home_error_description, error.message)
    }
}
