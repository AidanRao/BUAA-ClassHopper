package top.aidanrao.buaa_classhopper.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import retrofit2.Response
import top.aidanrao.buaa_classhopper.data.api.IclassAccessPolicyApi
import top.aidanrao.buaa_classhopper.data.model.ApiResponse
import top.aidanrao.buaa_classhopper.data.model.IclassAccessPolicy
import top.aidanrao.buaa_classhopper.data.model.dto.IclassAccessPolicyDto

/** In-memory preferences and API behavior belong in tests, not production constructors. */
internal class AccessPolicyFixture(
    var cached: String? = null,
    var remote: suspend () -> IclassAccessPolicy = { error("offline") }
) {
    var failSave = false
    var fetchedAt = 0L
    private val context = mock(Context::class.java)
    private val preferences = mock(SharedPreferences::class.java)

    init {
        `when`(context.getSharedPreferences("iclass_access_policy", Context.MODE_PRIVATE)).thenReturn(preferences)
        `when`(preferences.getString("policy", null)).thenAnswer { cached }
        `when`(preferences.edit()).thenAnswer {
            val editor = mock(SharedPreferences.Editor::class.java)
            var pending: String? = null
            var time = 0L
            `when`(editor.putString(eq("policy"), anyString())).thenAnswer { call -> pending = call.getArgument(1); editor }
            `when`(editor.putLong(eq("fetchedAt"), anyLong())).thenAnswer { call -> time = call.getArgument(1); editor }
            `when`(editor.commit()).thenAnswer {
                if (failSave) false else { cached = pending; fetchedAt = time; true }
            }
            editor
        }
    }

    fun repository(api: IclassAccessPolicyApi = object : IclassAccessPolicyApi {
        override suspend fun getAccessPolicy(): Response<ApiResponse<IclassAccessPolicyDto>> {
            val policy = remote()
            return Response.success(ApiResponse(1, "ok", IclassAccessPolicyDto(
                policy.schemaVersion, policy.revision, policy.studentIds.toList(), policy.names.toList()
            )))
        }
    }): IclassAccessPolicyRepository = IclassAccessPolicyRepository(api, Gson(), context)
}
