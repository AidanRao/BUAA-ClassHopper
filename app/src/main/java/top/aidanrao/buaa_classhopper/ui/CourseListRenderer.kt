package top.aidanrao.buaa_classhopper.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import top.aidanrao.buaa_classhopper.R
import top.aidanrao.buaa_classhopper.data.model.dto.CourseDto
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.core.view.isVisible

class CourseListRenderer(
    private val container: LinearLayout,
    private val onSignClick: (Int) -> Unit,
) {
    private val context = container.context
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    fun render(courses: List<CourseDto>, signingIds: Set<Int> = emptySet()) {
        container.removeAllViews()
        courses.forEach { course ->
            val row = LayoutInflater.from(context).inflate(R.layout.item_home_course, container, false) as ConstraintLayout
            row.findViewById<TextView>(R.id.courseName).text = course.courseName
            val time = row.findViewById<TextView>(R.id.courseTime)
            val room = row.findViewById<TextView>(R.id.courseRoom)
            time.text = "${course.classBeginTime.format(formatter)} - ${course.classEndTime.format(formatter)}"
            room.text = course.classroomName
            room.visibility = if (course.classroomName.isBlank()) View.GONE else View.VISIBLE
            listOf(time, room).forEach { text ->
                val icon = text.compoundDrawablesRelative[0]
                icon?.setBounds(0, 0, dp(14), dp(14))
                text.setCompoundDrawablesRelative(icon, null, null, null)
            }
            val sign = row.findViewById<Button>(R.id.courseSign)
            val signed = course.signStatus == 1
            val signing = course.id in signingIds
            sign.setText(when { signed -> R.string.home_signed; signing -> R.string.home_signing; else -> R.string.home_sign })
            sign.isEnabled = !signed && signingIds.isEmpty()
            sign.setBackgroundResource(if (signed) R.drawable.home_signed_background else R.drawable.home_sign_background)
            sign.setTextColor(ContextCompat.getColor(context, if (signed) R.color.homepage_green else R.color.homepage_blue))
            val check = if (signed) ContextCompat.getDrawable(context, R.drawable.home_ic_check)?.apply { setBounds(0, 0, dp(14), dp(14)) } else null
            sign.setCompoundDrawablesRelative(check, null, null, null)
            sign.contentDescription = if (signed || signing) sign.text else context.getString(R.string.home_sign_description, course.courseName)
            sign.setOnClickListener { onSignClick(course.id) }

            // Large text and narrow windows move the action below the content instead of squeezing it.
            val config = context.resources.configuration
            if (config.screenWidthDp < 360 || config.fontScale > 1.2f) {
                ConstraintSet().apply {
                    clone(row)
                    connect(R.id.courseName, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(R.id.courseMetadata, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    clear(R.id.courseMetadata, ConstraintSet.BOTTOM)
                    connect(R.id.courseSign, ConstraintSet.TOP, R.id.courseMetadata, ConstraintSet.BOTTOM, dp(8))
                    connect(R.id.courseSign, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    applyTo(row)
                }
            }
            val metadata = row.findViewById<LinearLayout>(R.id.courseMetadata)
            metadata.doOnLayout {
                val required = time.paint.measureText(time.text.toString()) + room.paint.measureText(room.text.toString()) + dp(50)
                if (room.isVisible && required > metadata.width) {
                    metadata.orientation = LinearLayout.VERTICAL
                    room.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) }
                }
            }
            container.addView(row)
        }
    }
}
