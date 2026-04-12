package top.aidanrao.buaa_classhopper.data.model

data class ApiResponse<T>(
    val code: Int,
    val msg: String,
    val data: T?
) {
    val isSuccess: Boolean get() = code == 1
    val isFailed: Boolean get() = code != 1
    
    fun getDataOrThrow(): T {
        if (isSuccess && data != null) return data
        throw ApiException(code, msg)
    }
}

class ApiException(
    val code: Int,
    override val message: String
) : Exception(message)
