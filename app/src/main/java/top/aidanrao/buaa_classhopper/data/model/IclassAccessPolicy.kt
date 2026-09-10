package top.aidanrao.buaa_classhopper.data.model

import top.aidanrao.buaa_classhopper.data.model.dto.IclassAccessPolicyDto

/** Immutable, validated snapshot. Identity values are never treated as numeric IDs. */
data class IclassAccessPolicy(
    val schemaVersion: Int,
    val revision: String,
    val studentIds: Set<String>,
    val names: Set<String>
) {
    companion object {
        fun fromDto(data: IclassAccessPolicyDto): IclassAccessPolicy {
            require(data.schemaVersion == 1) { "不支持的白名单版本" }
            val revision = data.revision?.trim()
            require(!revision.isNullOrEmpty()) { "白名单缺少版本标识" }
            fun normalize(entries: List<String?>?): Set<String> {
                requireNotNull(entries) { "白名单缺少名单字段" }
                return entries.map { entry ->
                    val value = entry?.trim()
                    require(!value.isNullOrEmpty()) { "白名单包含空值" }
                    value
                }.toSet()
            }
            return IclassAccessPolicy(1, revision, normalize(data.studentIds), normalize(data.names))
        }
    }

    fun allows(studentId: String?, name: String?): Boolean =
        studentId?.trim()?.takeIf { it.isNotEmpty() }?.let { it in studentIds } == true ||
            name?.trim()?.takeIf { it.isNotEmpty() }?.let { it in names } == true
}

class IclassAccessException(val unavailable: Boolean = false) : Exception(
    if (unavailable) "白名单暂不可用，请稍后重试"
    else "当前账号未获准使用课表和签到功能"
)

