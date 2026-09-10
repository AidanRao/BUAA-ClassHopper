package top.aidanrao.buaa_classhopper.data.model.dto

data class IclassAccessPolicyDto(
    val schemaVersion: Int? = null,
    val revision: String? = null,
    val studentIds: List<String?>? = null,
    val names: List<String?>? = null
)
