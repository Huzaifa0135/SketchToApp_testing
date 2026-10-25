package com.example.core.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SketchToAppRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createProject_andLoadCanonicalDocument_succeedsForOwner() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = SketchToAppRepository(firestore, auth)

        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile("Alice Architect", ALICE_EMAIL, null)
        }
        assertTrue("Profile creation should succeed", profileRes.isSuccess)
        assertEquals(aliceUid, profileRes.getOrThrow().userId)

        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(
                name = "Habit Sketch App",
                description = "From hand-drawn sketch",
                packageName = "com.generated.habitsketch",
                sourceType = "sketch"
            ).getOrThrow()
        }
        assertTrue(projectId.startsWith("proj_"))

        val emittedProjects = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProjects(aliceUid).first { list -> list.any { it.id == projectId } }
        }
        assertTrue(emittedProjects.any { it.id == projectId && it.name == "Habit Sketch App" })

        val canonicalDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.loadCanonicalDocument(projectId).getOrThrow()
        }
        assertEquals("Habit Sketch App", canonicalDoc.appName)
        assertEquals(1, canonicalDoc.screens.size)
        assertTrue(canonicalDoc.screens.first().components.isNotEmpty())
    }

    @Test
    fun crossUserProjectRead_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = SketchToAppRepository(firestore, auth)
        val aliceProjectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProject("Alice Private App", "Secret", "com.alice.secret").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = SketchToAppRepository(firestore, auth)
        val readResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getProjectById(aliceProjectId)
        }
        assertTrue("Cross-user read must fail", readResult.isFailure)
        val fsEx = readResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun unauthenticatedObserveProjects_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = SketchToAppRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeProjects("any_uid").first()
            }
            fail("Unauthenticated query should have thrown PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected wrapped FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
        }
    }

    @Test
    fun buildJobLifecycle_andTerminalStateLocking_enforcedByRules() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = SketchToAppRepository(firestore, auth)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createProject("Build Pipeline Test", "", "com.test.build").getOrThrow()
        }
        val genJobId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createGenerationJob(
                projectId = projectId,
                fileCount = 8,
                diagnosticsSummary = "0 errors",
                zipStorageRef = "exports/test.zip",
                zipChecksumSha256 = "deadbeef",
                succeeded = true
            ).getOrThrow()
        }
        val buildJobId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createBuildJob(
                projectId = projectId,
                generationJobId = genJobId,
                buildProfile = "debug",
                workerMode = "unconfigured",
                initialLogs = "Queued build"
            ).getOrThrow()
        }

        // Transition to terminal 'failed'
        val markFailed = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateBuildJobStatus(
                buildId = buildJobId,
                status = "failed",
                exitCode = 1,
                sanitizedLogs = "Worker URL not configured"
            )
        }
        assertTrue(markFailed.isSuccess)

        // Attempt to mutate terminal build job -> must be rejected by rules!
        val tamperAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateBuildJobStatus(
                buildId = buildJobId,
                status = "succeeded",
                exitCode = 0,
                sanitizedLogs = "Tampered"
            )
        }
        assertTrue("Mutating terminal build job must fail", tamperAttempt.isFailure)
        val fsEx = tamperAttempt.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.rules@sketchtoapp.test"
        const val BOB_EMAIL = "bob.rules@sketchtoapp.test"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
