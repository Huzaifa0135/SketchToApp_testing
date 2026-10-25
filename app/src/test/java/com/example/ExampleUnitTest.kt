package com.example

import com.example.core.schema.CanonicalApplicationDocument
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.CanonicalThemeTokens
import com.example.core.schema.ComponentType
import com.example.core.schema.FormValidationRule
import com.example.core.schema.NavActionType
import com.example.core.schema.SchemaValidator
import com.example.features.code_generation.FlutterProjectGenerator
import com.example.features.code_generation.ZipAndArtifactExporter
import com.example.features.figma.FigmaImportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private fun buildSampleMultiScreenDocument(): CanonicalApplicationDocument {
        val homeScreen = CanonicalScreen(
            id = "scr_login",
            name = "Login Screen",
            route = "/login",
            isInitial = true,
            orderIndex = 0,
            components = listOf(
                CanonicalComponent(
                    id = "cmp_header",
                    type = ComponentType.APP_BAR,
                    label = "TaskFlow Login"
                ),
                CanonicalComponent(
                    id = "cmp_email",
                    type = ComponentType.TEXT_FIELD,
                    label = "Email",
                    hint = "you@company.com",
                    formRequired = true,
                    formRule = FormValidationRule.EMAIL
                ),
                CanonicalComponent(
                    id = "cmp_submit",
                    type = ComponentType.BUTTON,
                    label = "Continue to Dashboard",
                    navActionType = NavActionType.NAVIGATE_TO_SCREEN,
                    targetScreenId = "scr_dashboard",
                    navConfirmedByUser = true
                )
            )
        )

        val dashScreen = CanonicalScreen(
            id = "scr_dashboard",
            name = "Main Dashboard",
            route = "/dashboard",
            isInitial = false,
            orderIndex = 1,
            components = listOf(
                CanonicalComponent(
                    id = "cmp_dash_bar",
                    type = ComponentType.APP_BAR,
                    label = "Dashboard"
                ),
                CanonicalComponent(
                    id = "cmp_back_btn",
                    type = ComponentType.BUTTON,
                    label = "Sign Out",
                    navActionType = NavActionType.NAVIGATE_BACK
                )
            )
        )

        return CanonicalApplicationDocument(
            projectId = "proj_unit_1",
            appName = "TaskFlow Pro",
            packageName = "com.generated.taskflow",
            initialScreenId = "scr_login",
            theme = CanonicalThemeTokens(
                primaryColorHex = "#58E6D0",
                accentColorHex = "#8B7CF6"
            ),
            screens = listOf(homeScreen, dashScreen)
        )
    }

    @Test
    fun `schemaValidator passes clean multi-screen document and catches broken target`() {
        val validDoc = buildSampleMultiScreenDocument()
        val okResult = SchemaValidator.validate(validDoc)
        assertTrue("Clean multi-screen doc must pass validation", okResult.isValidForGeneration)
        assertTrue(okResult.errors.isEmpty())

        val brokenScreen = validDoc.screens.first().copy(
            components = listOf(
                CanonicalComponent(
                    id = "cmp_bad_nav",
                    type = ComponentType.BUTTON,
                    label = "Go Missing",
                    navActionType = NavActionType.NAVIGATE_TO_SCREEN,
                    targetScreenId = "scr_does_not_exist"
                )
            )
        )
        val brokenDoc = validDoc.copy(screens = listOf(brokenScreen))
        val badResult = SchemaValidator.validate(brokenDoc)
        assertFalse("Broken navigation target must fail validation", badResult.isValidForGeneration)
    }

    @Test
    fun `flutterProjectGenerator emits deterministic multi-screen Dart files and widget tests`() {
        val doc = buildSampleMultiScreenDocument()
        val project = FlutterProjectGenerator.generate(doc)

        assertTrue(project.isValid)
        assertEquals("taskflow_pro", project.projectSlug)

        val paths = project.files.map { it.relativePath }.toSet()
        assertTrue(paths.contains("pubspec.yaml"))
        assertTrue(paths.contains("lib/main.dart"))
        assertTrue(paths.contains("lib/theme/app_theme.dart"))
        assertTrue(paths.contains("test/widget_test.dart"))
        assertTrue("All generated paths must pass zip-slip check", project.files.all { FlutterProjectGenerator.isSafeRelativePath(it.relativePath) })

        val mainDart = project.files.first { it.relativePath == "lib/main.dart" }.content
        assertTrue(mainDart.contains("AppRoutes.initialRoute"))

        val routerDart = project.files.first { it.relativePath == "lib/navigation/app_router.dart" }.content
        assertTrue(routerDart.contains("'/login'"))
        assertTrue(routerDart.contains("'/dashboard'"))
    }

    @Test
    fun `isSafeRelativePath rejects zip-slip and shell metacharacters`() {
        assertTrue(FlutterProjectGenerator.isSafeRelativePath("lib/screens/home_screen.dart"))
        assertFalse(FlutterProjectGenerator.isSafeRelativePath("../secret.txt"))
        assertFalse(FlutterProjectGenerator.isSafeRelativePath("/etc/passwd"))
        assertFalse(FlutterProjectGenerator.isSafeRelativePath("lib/bad;rm -rf.dart"))
    }

    @Test
    fun `figmaImportService extracts valid file key from design URLs and raw keys`() {
        val fromDesignUrl = FigmaImportService.extractFileKey("https://www.figma.com/design/AbCdEfGhIj123456/Mobile-Wireframes?node-id=0-1")
        assertEquals("AbCdEfGhIj123456", fromDesignUrl)

        val fromRawKey = FigmaImportService.extractFileKey("ZxYwVuTsRq987654")
        assertEquals("ZxYwVuTsRq987654", fromRawKey)
        assertNotNull(ZipAndArtifactExporter.sha256Bytes("hello".toByteArray()))
    }
}
