package top.aidanrao.buaa_classhopper.ui

import top.aidanrao.buaa_classhopper.data.model.dto.CourseDto

data class HomeIdentity(val name: String?, val studentId: String?, val academy: String?) {
    val title: String get() = listOfNotNull(name, studentId).filter { it.isNotBlank() }.joinToString(" - ")
}

sealed interface HomeCourseState {
    data object Loading : HomeCourseState
    data class Success(val courses: List<CourseDto>) : HomeCourseState
    data class Error(val message: String) : HomeCourseState
}
