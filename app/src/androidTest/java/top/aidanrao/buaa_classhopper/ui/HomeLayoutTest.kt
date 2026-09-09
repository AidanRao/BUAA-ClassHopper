package top.aidanrao.buaa_classhopper.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.data.model.dto.CourseDto
import java.io.File
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Exercises the production layout/renderer without logging in or sending attendance requests. */
@RunWith(AndroidJUnit4::class)
class HomeLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val courses = (1..4).map { id ->
        CourseDto(id, id, "软件开发方法", "", "", "", "", "(三)304", if (id == 1) 0 else 1,
            LocalDateTime.of(2026, 9, 9, 15, 50), LocalDateTime.of(2026, 9, 9, 18, 15))
    }

    private fun layout(widthDp: Int = 390, fontScale: Float = 1f, night: Boolean = false): View {
        val base = instrumentation.targetContext
        val config = Configuration(base.resources.configuration).apply {
            screenWidthDp = widthDp
            this.fontScale = fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val context = ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_ClassHopper_Home)
        return LayoutInflater.from(context).inflate(R.layout.activity_main, null).apply {
            findViewById<TextView>(R.id.userInfoTextView).apply { text = "饶晨煜 - ZY2623327"; visibility = View.VISIBLE }
            findViewById<TextView>(R.id.academyTextView).apply { text = "国家卓越工程师学院"; visibility = View.VISIBLE }
            findViewById<TextView>(R.id.textViewDate).text = "2026-09-09"
        }
    }

    private fun measure(root: View, widthDp: Int) {
        val density = root.resources.displayMetrics.density
        val width = (widthDp * density).roundToInt()
        val height = (844 * density).roundToInt()
        repeat(3) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
        }
    }

    private fun screenshot(root: View, name: String) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "home-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun stateTransitionsClearPreviousCoursesAndRestoreControls() = instrumentation.runOnMainSync {
        val root = layout()
        var selected: Int? = null
        val renderer = HomeContentRenderer(root) { selected = it }
        renderer.render(HomeCourseState.Success(courses))
        val list = root.findViewById<LinearLayout>(R.id.courseList)
        assertEquals(4, list.childCount)
        assertEquals("共 4 门课程", root.findViewById<TextView>(R.id.courseCount).text.toString())
        list.getChildAt(0).findViewById<Button>(R.id.courseSign).performClick()
        assertEquals(1, selected)
        assertFalse(list.getChildAt(1).findViewById<Button>(R.id.courseSign).isEnabled)
        renderer.render(HomeCourseState.Success(courses), setOf(1))
        assertFalse(root.findViewById<View>(R.id.datePickerContainer).isEnabled)
        assertFalse(list.getChildAt(0).findViewById<Button>(R.id.courseSign).isEnabled)
        renderer.render(HomeCourseState.Loading)
        assertEquals(0, list.childCount)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.loadingState).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.courseCount).visibility)
        assertFalse(root.findViewById<Button>(R.id.btnGetClass).isEnabled)
        renderer.render(HomeCourseState.Error("网络连接失败"))
        assertEquals(0, list.childCount)
        assertEquals(View.GONE, root.findViewById<View>(R.id.emptyArtwork).visibility)
        assertTrue(root.findViewById<TextView>(R.id.resultStateDescription).text.contains("网络连接失败"))
        assertTrue(root.findViewById<Button>(R.id.btnGetClass).isEnabled)
        renderer.render(HomeCourseState.Success(emptyList()))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.emptyArtwork).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.emptyStateLayout).visibility)
        assertEquals(0, list.childCount)
        renderer.render(HomeCourseState.Success(courses))
        assertEquals(4, list.childCount)
        assertEquals(View.GONE, root.findViewById<View>(R.id.emptyStateLayout).visibility)
    }

    @Test fun renderReferenceStatesAndNarrowLargeText() = instrumentation.runOnMainSync {
        val root = layout()
        val renderer = HomeContentRenderer(root) {}
        renderer.render(HomeCourseState.Success(courses.map { it.copy(signStatus = 1) }))
        measure(root, 390)
        screenshot(root, "courses")
        renderer.render(HomeCourseState.Success(emptyList()))
        measure(root, 390)
        screenshot(root, "empty")

        val narrow = layout(320, 1.5f, true)
        HomeContentRenderer(narrow) {}.render(HomeCourseState.Success(listOf(courses.first().copy(
            courseName = "软件开发方法与现代工程实践：很长的课程名称", classroomName = "新主楼第三教学楼304教室"))))
        measure(narrow, 320)
        val row = narrow.findViewById<LinearLayout>(R.id.courseList).getChildAt(0)
        val name = row.findViewById<TextView>(R.id.courseName)
        val metadata = row.findViewById<View>(R.id.courseMetadata)
        val sign = row.findViewById<Button>(R.id.courseSign)
        assertTrue(name.lineCount > 1)
        assertTrue(sign.top >= metadata.bottom)
        assertTrue(name.right <= row.width)
        assertEquals(narrow.context.getColor(R.color.homepage_text), name.currentTextColor)
        screenshot(narrow, "narrow-large-night")
    }
}
