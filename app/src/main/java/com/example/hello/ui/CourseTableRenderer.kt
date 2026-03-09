package com.example.hello.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.example.hello.data.model.dto.CourseDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CourseTableRenderer(
    private val context: Context,
    private val tableLayout: TableLayout,
    private val onSignClick: (courseId: Int) -> Unit,
) {
    fun render(courses: List<CourseDto>) {
        for (i in tableLayout.childCount - 1 downTo 1) {
            tableLayout.removeViewAt(i)
        }

        courses.forEachIndexed { index, course ->
            val row = TableRow(context).apply {
                layoutParams = TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                )
                setPadding(8, 8, 8, 8)
                setBackgroundColor(if (index % 2 == 0) Color.WHITE else "#F5F5F5".toColorInt())
            }

            row.addView(TextView(context).apply {
                text = course.courseName
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
            })

            row.addView(TextView(context).apply {
                text = formatTime(course.classBeginTime, course.classEndTime)
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
            })

            row.addView(TextView(context).apply {
                text = course.classroomName
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.75f)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 4, 4, 4)
            })

            val signButton = Button(context).apply {
                text = if (course.signStatus == 1) "已签到" else "签到"
                isEnabled = course.signStatus != 1
                layoutParams = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 0.75f)
                setOnClickListener { onSignClick(course.id) }
            }
            row.addView(signButton)

            tableLayout.addView(row)
        }
    }

    private fun String.toColorInt(): Int {
        return Color.parseColor(this)
    }

    private fun formatTime(begin: LocalDateTime, end: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return "${begin.format(formatter)} - ${end.format(formatter)}"
    }
}
