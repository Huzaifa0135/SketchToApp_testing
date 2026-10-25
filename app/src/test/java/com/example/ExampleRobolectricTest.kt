package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.schema.CanonicalApplicationDocument
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.ComponentType
import com.example.features.auth.OnboardingScreen
import com.example.features.code_generation.FlutterProjectGenerator
import com.example.features.code_generation.ZipAndArtifactExporter
import com.example.features.visual_editor.InteractivePreviewScreen
import com.example.ui.theme.SketchToAppTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun onboardingTour_navigatesStepsAndCompletes() {
        var completed = false
        composeTestRule.setContent {
            SketchToAppTheme {
                OnboardingScreen(onCompleteOnboarding = { completed = true })
            }
        }

        composeTestRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        assertTrue("Onboarding tour should invoke completion callback on final step", completed)
    }

    @Test
    fun interactivePreview_rendersCanonicalWidgetsAndExecutesAction() {
        val doc = CanonicalApplicationDocument(
            projectId = "proj_robo",
            appName = "Preview Demo",
            initialScreenId = "scr_1",
            screens = listOf(
                CanonicalScreen(
                    id = "scr_1",
                    name = "Welcome",
                    route = "/welcome",
                    isInitial = true,
                    components = listOf(
                        CanonicalComponent(id = "c1", type = ComponentType.APP_BAR, label = "Welcome Header"),
                        CanonicalComponent(id = "c2", type = ComponentType.BUTTON, label = "Tap Action")
                    )
                )
            )
        )

        composeTestRule.setContent {
            SketchToAppTheme {
                InteractivePreviewScreen(document = doc, onBack = {})
            }
        }

        composeTestRule.onNodeWithTag("interactive_preview_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome Header").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap Action").performClick()
    }

    @Test
    fun zipExporter_createsValidVerifiedFlutterSourceArchive() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val doc = CanonicalApplicationDocument(
            projectId = "proj_zip",
            appName = "Habit Tracker",
            initialScreenId = "scr_home",
            screens = listOf(
                CanonicalScreen(
                    id = "scr_home",
                    name = "Home",
                    route = "/home",
                    isInitial = true,
                    components = listOf(
                        CanonicalComponent(id = "c1", type = ComponentType.TEXT, label = "Daily Habits")
                    )
                )
            )
        )
        val generated = FlutterProjectGenerator.generate(doc)
        val archiveRes = ZipAndArtifactExporter.createFlutterProjectZip(context, generated)

        assertTrue("ZIP creation must succeed", archiveRes.isSuccess)
        val archive = archiveRes.getOrThrow()
        assertTrue(archive.file.exists() && archive.sizeBytes > 0L)
        assertEquals(64, archive.sha256Hex.length)

        ZipFile(archive.file).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(entryNames.contains("habit_tracker/pubspec.yaml"))
            assertTrue(entryNames.contains("habit_tracker/lib/main.dart"))
        }
    }
}
