package top.aidanrao.buaa_classhopper.data.repository

import com.google.gson.Gson
import top.aidanrao.buaa_classhopper.data.model.IclassAccessPolicy
import top.aidanrao.buaa_classhopper.data.model.IclassAccessException
import top.aidanrao.buaa_classhopper.data.model.dto.IclassAccessPolicyDto

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IclassAccessPolicyTest {
    private val json = """{"schemaVersion":1,"revision":"v1","studentIds":[" 00123 ","00123"],"names":[" 张三 "]}"""
    private fun parsePolicy(json: String): IclassAccessPolicy =
        IclassAccessPolicy.fromDto(Gson().fromJson(json, IclassAccessPolicyDto::class.java))

    @Test fun exactOrMatchingTrimsWithoutNumericConversion() {
        val policy = parsePolicy(json)
        assertTrue(policy.allows(" 00123 ", null))
        assertTrue(policy.allows(null, " 张三 "))
        assertFalse(policy.allows("123", "张"))
        assertFalse(policy.allows(null, null))
        assertFalse(policy.allows("", " "))
        assertEquals(1, policy.studentIds.size)
    }

    @Test fun invalidPolicyFieldsAreRejectedBeforeUse() {
        listOf(
            "{}",
            json.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            json.replace("\"revision\":\"v1\"", "\"revision\":\" \""),
            json.replace("\"names\":[\" 张三 \"]", "\"names\":null"),
            json.replace("\"names\":[\" 张三 \"]", "\"names\":[null]"),
            json.replace("\"names\":[\" 张三 \"]", "\"names\":[\" \"]")
        ).forEach { invalid ->
            assertThrows(Exception::class.java) { parsePolicy(invalid) }
        }
    }

    @Test fun cachePrecedenceAndEmptyRemoteRevocationSurviveRestart() = runBlocking<Unit> {
        val fixture = AccessPolicyFixture(remote = { IclassAccessPolicy(1, "empty", emptySet(), emptySet()) })
        val repo = fixture.repository()
        repo.loadLocal(json)
        repo.requireAllowed("00123", null)
        assertTrue(repo.refresh())
        assertTrue(fixture.fetchedAt > 0)
        assertThrows(IclassAccessException::class.java) { repo.requireAllowed("00123", "张三") }
        fixture.remote = { error("offline") }
        val restarted = fixture.repository()
        restarted.loadLocal(json)
        assertFalse(restarted.refresh())
        assertThrows(IclassAccessException::class.java) { restarted.requireAllowed("00123", null) }
    }

    @Test fun corruptCacheUsesBuiltInAndNetworkFailurePreservesIt() = runBlocking<Unit> {
        val repo = AccessPolicyFixture("corrupt").repository()
        repo.loadLocal(json)
        repo.requireAllowed("00123", null)
        assertFalse(repo.refresh())
        repo.requireAllowed(null, "张三")
    }

    @Test fun validCacheOverridesBuiltIn() {
        val repo = AccessPolicyFixture("""{"schemaVersion":1,"revision":"empty","studentIds":[],"names":[]}""")
            .repository()
        repo.loadLocal(json)
        assertThrows(IclassAccessException::class.java) { repo.requireAllowed("00123", null) }
    }

    @Test fun startupUsesLocalImmediatelyAndRefreshesOnlyOnce() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val repo = AccessPolicyFixture(json) {
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            parsePolicy(json)
        }.repository()
        try {
            repo.start()
            repo.requireAllowed("00123", null)
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            repo.start()
            assertEquals(1, calls.get())
        } finally { release.countDown() }
    }

    @Test fun failedSaveKeepsCurrentSnapshotAndNoConfigurationDenies() = runBlocking<Unit> {
        val fixture = AccessPolicyFixture(remote = { IclassAccessPolicy(1, "empty", emptySet(), emptySet()) })
        fixture.failSave = true
        val repo = fixture.repository()
        repo.loadLocal(json)
        assertFalse(repo.refresh())
        repo.requireAllowed("00123", null)
        val absent = AccessPolicyFixture().repository()
        absent.loadLocal("")
        assertTrue(assertThrows(IclassAccessException::class.java) { absent.requireAllowed("00123", null) }.unavailable)
    }
}
