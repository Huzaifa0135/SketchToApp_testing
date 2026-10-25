package com.example.features.code_generation

import com.example.core.schema.CanonicalApplicationDocument
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.ComponentType
import com.example.core.schema.FormValidationRule
import com.example.core.schema.NavActionType
import com.example.core.schema.SchemaDiagnostic
import com.example.core.schema.SchemaValidator

data class GeneratedSourceFile(
    val relativePath: String,
    val language: String,
    val content: String
)

data class GeneratedFlutterProject(
    val projectSlug: String,
    val files: List<GeneratedSourceFile>,
    val diagnostics: List<SchemaDiagnostic>,
    val isValid: Boolean
)

object FlutterProjectGenerator {

    fun sanitizeSlug(raw: String): String {
        val cleaned = raw.lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        return when {
            cleaned.isEmpty() -> "sketch_generated_app"
            cleaned.first().isDigit() -> "app_$cleaned"
            else -> cleaned.take(40)
        }
    }

    fun sanitizeClassName(raw: String): String {
        val parts = raw.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        val joined = parts.joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
        return when {
            joined.isEmpty() -> "GeneratedScreen"
            joined.first().isDigit() -> "Screen$joined"
            else -> joined
        }
    }

    fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains("..") || path.contains('\u0000')) return false
        return path.matches(Regex("^[a-zA-Z0-9_./\\-]+$"))
    }

    private fun dartColorLiteral(hex: String, fallback: String = "0xFF58E6D0"): String {
        val stripped = hex.trim().removePrefix("#")
        return when (stripped.length) {
            6 -> "Color(0xFF${stripped.uppercase()})"
            8 -> "Color(0x${stripped.uppercase()})"
            else -> "Color($fallback)"
        }
    }

    private fun escapeDartString(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("$", "\\$")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    fun generate(doc: CanonicalApplicationDocument): GeneratedFlutterProject {
        val validation = SchemaValidator.validate(doc)
        val projectSlug = sanitizeSlug(doc.appName)

        if (!validation.isValidForGeneration) {
            return GeneratedFlutterProject(
                projectSlug = projectSlug,
                files = emptyList(),
                diagnostics = validation.diagnostics,
                isValid = false
            )
        }

        val screenRouteConstMap = doc.screens.associate { screen ->
            val slug = sanitizeSlug(screen.name.ifBlank { screen.id })
            screen.id to slug
        }

        val files = mutableListOf<GeneratedSourceFile>()

        // 1. pubspec.yaml
        files.add(
            GeneratedSourceFile(
                relativePath = "pubspec.yaml",
                language = "yaml",
                content = buildPubspec(projectSlug, doc)
            )
        )

        // 2. analysis_options.yaml
        files.add(
            GeneratedSourceFile(
                relativePath = "analysis_options.yaml",
                language = "yaml",
                content = """
                    include: package:flutter_lints/flutter.yaml

                    linter:
                      rules:
                        prefer_const_constructors: true
                        avoid_print: true
                """.trimIndent() + "\n"
            )
        )

        // 3. lib/theme/app_theme.dart
        files.add(
            GeneratedSourceFile(
                relativePath = "lib/theme/app_theme.dart",
                language = "dart",
                content = buildThemeDart(doc)
            )
        )

        // 4. lib/widgets/sketch_placeholder_box.dart
        files.add(
            GeneratedSourceFile(
                relativePath = "lib/widgets/sketch_placeholder_box.dart",
                language = "dart",
                content = buildPlaceholderWidgetDart()
            )
        )

        // 5. Screen files
        doc.screens.forEachIndexed { index, screen ->
            val slug = "${sanitizeSlug(screen.name)}_${index + 1}"
            val className = "${sanitizeClassName(screen.name)}${index + 1}Screen"
            files.add(
                GeneratedSourceFile(
                    relativePath = "lib/screens/${slug}_screen.dart",
                    language = "dart",
                    content = buildScreenDart(
                        doc = doc,
                        screen = screen,
                        className = className,
                        routeConstMap = screenRouteConstMap
                    )
                )
            )
        }

        // 6. lib/navigation/app_router.dart
        files.add(
            GeneratedSourceFile(
                relativePath = "lib/navigation/app_router.dart",
                language = "dart",
                content = buildRouterDart(doc, screenRouteConstMap)
            )
        )

        // 7. lib/main.dart
        files.add(
            GeneratedSourceFile(
                relativePath = "lib/main.dart",
                language = "dart",
                content = buildMainDart(doc)
            )
        )

        // 8. test/widget_test.dart
        files.add(
            GeneratedSourceFile(
                relativePath = "test/widget_test.dart",
                language = "dart",
                content = buildWidgetTestDart(doc)
            )
        )

        // 9. README.md
        files.add(
            GeneratedSourceFile(
                relativePath = "README.md",
                language = "markdown",
                content = buildReadme(projectSlug, doc)
            )
        )

        // Safety verify every generated path stays inside project root
        val unsafePaths = files.filterNot { isSafeRelativePath(it.relativePath) }
        if (unsafePaths.isNotEmpty()) {
            return GeneratedFlutterProject(
                projectSlug = projectSlug,
                files = emptyList(),
                diagnostics = validation.diagnostics,
                isValid = false
            )
        }

        return GeneratedFlutterProject(
            projectSlug = projectSlug,
            files = files,
            diagnostics = validation.diagnostics,
            isValid = true
        )
    }

    private fun buildPubspec(projectSlug: String, doc: CanonicalApplicationDocument): String {
        val assetSection = if (doc.assetIds.isNotEmpty()) {
            "  assets:\n    - assets/images/\n"
        } else {
            ""
        }
        return """
            name: $projectSlug
            description: Generated by SketchToApp AI from validated canonical UI schema v${doc.schemaVersion}.
            publish_to: 'none'
            version: 1.0.0+1

            environment:
              sdk: '>=3.3.0 <4.0.0'

            dependencies:
              flutter:
                sdk: flutter
              cupertino_icons: ^1.0.8

            dev_dependencies:
              flutter_test:
                sdk: flutter
              flutter_lints: ^4.0.0

            flutter:
              uses-material-design: true
            $assetSection
        """.trimIndent() + "\n"
    }

    private fun buildThemeDart(doc: CanonicalApplicationDocument): String {
        val t = doc.theme
        val brightness = if (t.darkMode) "Brightness.dark" else "Brightness.light"
        return """
            import 'package:flutter/material.dart';

            class AppTheme {
              static const Color primary = ${dartColorLiteral(t.primaryColorHex)};
              static const Color secondary = ${dartColorLiteral(t.accentColorHex, "0xFF8B7CF6")};
              static const Color background = ${dartColorLiteral(t.backgroundColorHex, "0xFF0B1118")};
              static const Color surface = ${dartColorLiteral(t.surfaceColorHex, "0xFF16212A")};
              static const double cornerRadius = ${t.borderRadiusDp}.0;

              static ThemeData buildTheme() {
                final colorScheme = ColorScheme.fromSeed(
                  seedColor: primary,
                  brightness: $brightness,
                  primary: primary,
                  secondary: secondary,
                  surface: surface,
                );

                return ThemeData(
                  useMaterial3: true,
                  brightness: $brightness,
                  colorScheme: colorScheme,
                  scaffoldBackgroundColor: background,
                  cardTheme: CardThemeData(
                    color: surface,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(cornerRadius),
                    ),
                  ),
                  inputDecorationTheme: InputDecorationTheme(
                    filled: true,
                    fillColor: surface,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(cornerRadius),
                    ),
                  ),
                );
              }
            }
        """.trimIndent() + "\n"
    }

    private fun buildPlaceholderWidgetDart(): String {
        return """
            import 'package:flutter/material.dart';

            /// Safe fallback widget rendered when an imported design element uses
            /// effects or custom plugins outside the deterministic widget set.
            class SketchPlaceholderBox extends StatelessWidget {
              final String label;
              final String note;

              const SketchPlaceholderBox({
                super.key,
                required this.label,
                this.note = '',
              });

              @override
              Widget build(BuildContext context) {
                return Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: Theme.of(context).colorScheme.outline,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        label,
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      if (note.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(
                          note,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ],
                  ),
                );
              }
            }
        """.trimIndent() + "\n"
    }

    private fun buildRouterDart(
        doc: CanonicalApplicationDocument,
        routeConstMap: Map<String, String>
    ): String {
        val imports = StringBuilder()
        val constants = StringBuilder()
        val mapEntries = StringBuilder()

        doc.screens.forEachIndexed { index, screen ->
            val fileSlug = "${sanitizeSlug(screen.name)}_${index + 1}"
            val className = "${sanitizeClassName(screen.name)}${index + 1}Screen"
            val constKey = routeConstMap[screen.id] ?: "screen_${index + 1}"
            val routeVal = if (screen.route.startsWith("/")) screen.route else "/${screen.route}"

            imports.appendLine("import '../screens/${fileSlug}_screen.dart';")
            constants.appendLine("  static const String $constKey = '${escapeDartString(routeVal)}';")
            mapEntries.appendLine("      $constKey: (context) => const $className(),")
        }

        val initialConst = routeConstMap[doc.initialScreenId]
            ?: routeConstMap.values.firstOrNull()
            ?: "home"

        return """
            import 'package:flutter/material.dart';
            ${imports.toString().trimEnd()}

            class AppRoutes {
            ${constants.toString().trimEnd()}

              static const String initialRoute = $initialConst;

              static Map<String, WidgetBuilder> buildRoutes() {
                return {
            ${mapEntries.toString().trimEnd()}
                };
              }
            }
        """.trimIndent() + "\n"
    }

    private fun buildMainDart(doc: CanonicalApplicationDocument): String {
        return """
            import 'package:flutter/material.dart';
            import 'navigation/app_router.dart';
            import 'theme/app_theme.dart';

            void main() {
              runApp(const GeneratedSketchApp());
            }

            class GeneratedSketchApp extends StatelessWidget {
              const GeneratedSketchApp({super.key});

              @override
              Widget build(BuildContext context) {
                return MaterialApp(
                  title: '${escapeDartString(doc.appName)}',
                  debugShowCheckedModeBanner: false,
                  theme: AppTheme.buildTheme(),
                  initialRoute: AppRoutes.initialRoute,
                  routes: AppRoutes.buildRoutes(),
                );
              }
            }
        """.trimIndent() + "\n"
    }

    private fun buildScreenDart(
        doc: CanonicalApplicationDocument,
        screen: CanonicalScreen,
        className: String,
        routeConstMap: Map<String, String>
    ): String {
        val appBarComp = screen.components.firstOrNull { it.type == ComponentType.APP_BAR }
        val navBarComp = screen.components.firstOrNull { it.type == ComponentType.NAV_BAR }
        val bodyComponents = screen.components.filter {
            it.type != ComponentType.APP_BAR && it.type != ComponentType.NAV_BAR
        }
        val hasFormFields = bodyComponents.any { it.type == ComponentType.TEXT_FIELD }

        val appBarCode = if (appBarComp != null) {
            """
              appBar: AppBar(
                title: const Text('${escapeDartString(appBarComp.label.ifBlank { screen.name })}'),
                automaticallyImplyLeading: true,
              ),
            """.trimIndent()
        } else {
            """
              appBar: AppBar(
                title: const Text('${escapeDartString(screen.name)}'),
              ),
            """.trimIndent()
        }

        val navBarCode = if (navBarComp != null && doc.screens.size >= 2) {
            val destItems = doc.screens.take(4).joinToString("\n") { s ->
                "          const NavigationDestination(icon: Icon(Icons.layers_outlined), label: '${escapeDartString(s.name.take(18))}'),"
            }
            val routeSwitch = doc.screens.take(4).mapIndexed { idx, s ->
                val rc = routeConstMap[s.id] ?: "initialRoute"
                "            if (idx == $idx) Navigator.pushReplacementNamed(context, AppRoutes.$rc);"
            }.joinToString("\n")
            """
              bottomNavigationBar: NavigationBar(
                selectedIndex: ${doc.screens.indexOfFirst { it.id == screen.id }.coerceIn(0, (doc.screens.size - 1).coerceAtMost(3))},
                onDestinationSelected: (idx) {
            $routeSwitch
                },
                destinations: [
            $destItems
                ],
              ),
            """.trimIndent()
        } else {
            ""
        }

        val childrenCode = bodyComponents.joinToString(",\n          const SizedBox(height: 12),\n") { comp ->
            renderComponentWidget(comp, hasFormFields, routeConstMap)
        }

        val bodyTree = if (hasFormFields) {
            """
              Form(
                key: _formKey,
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    $childrenCode
                  ],
                ),
              )
            """.trimIndent()
        } else {
            """
              ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  $childrenCode
                ],
              )
            """.trimIndent()
        }

        return if (hasFormFields) {
            """
                import 'package:flutter/material.dart';
                import '../navigation/app_router.dart';
                import '../widgets/sketch_placeholder_box.dart';

                class $className extends StatefulWidget {
                  const $className({super.key});

                  @override
                  State<$className> createState() => _${className}State();
                }

                class _${className}State extends State<$className> {
                  final _formKey = GlobalKey<FormState>();

                  @override
                  Widget build(BuildContext context) {
                    return Scaffold(
                      $appBarCode
                      body: SafeArea(
                        child: $bodyTree,
                      ),
                      $navBarCode
                    );
                  }
                }
            """.trimIndent() + "\n"
        } else {
            """
                import 'package:flutter/material.dart';
                import '../navigation/app_router.dart';
                import '../widgets/sketch_placeholder_box.dart';

                class $className extends StatelessWidget {
                  const $className({super.key});

                  @override
                  Widget build(BuildContext context) {
                    return Scaffold(
                      $appBarCode
                      body: SafeArea(
                        child: $bodyTree,
                      ),
                      $navBarCode
                    );
                  }
                }
            """.trimIndent() + "\n"
        }
    }

    private fun renderComponentWidget(
        comp: CanonicalComponent,
        inForm: Boolean,
        routeConstMap: Map<String, String>
    ): String {
        val escapedLabel = escapeDartString(comp.label.ifBlank { comp.type.displayLabel })
        val escapedSecondary = escapeDartString(comp.secondaryText)
        val escapedHint = escapeDartString(comp.hint)

        return when (comp.type) {
            ComponentType.TEXT -> {
                val weight = when (comp.fontWeight.lowercase()) {
                    "bold" -> "FontWeight.bold"
                    "medium", "semibold" -> "FontWeight.w600"
                    else -> "FontWeight.normal"
                }
                val align = when (comp.alignment.lowercase()) {
                    "center" -> "TextAlign.center"
                    "end" -> "TextAlign.end"
                    else -> "TextAlign.start"
                }
                """
                  Text(
                    '$escapedLabel',
                    textAlign: $align,
                    style: TextStyle(
                      fontSize: ${comp.fontSizeSp}.0,
                      fontWeight: $weight,
                      color: ${dartColorLiteral(comp.textColorHex, "0xFFE8F5F4")},
                    ),
                  )
                """.trimIndent()
            }

            ComponentType.TEXT_FIELD -> {
                val validatorBlock = when {
                    comp.formRule == FormValidationRule.EMAIL -> """
                        validator: (value) {
                          final v = value?.trim() ?? '';
                          if (v.isEmpty || !v.contains('@') || !v.contains('.')) {
                            return 'Enter a valid email address';
                          }
                          return null;
                        },
                    """.trimIndent()
                    comp.formRule == FormValidationRule.MIN_LENGTH_6 -> """
                        validator: (value) {
                          if ((value ?? '').length < 6) {
                            return 'Minimum 6 characters required';
                          }
                          return null;
                        },
                    """.trimIndent()
                    comp.formRequired || comp.formRule == FormValidationRule.NON_EMPTY -> """
                        validator: (value) {
                          if (value == null || value.trim().isEmpty) {
                            return 'This field is required';
                          }
                          return null;
                        },
                    """.trimIndent()
                    else -> ""
                }
                """
                  TextFormField(
                    decoration: const InputDecoration(
                      labelText: '$escapedLabel',
                      hintText: '$escapedHint',
                    ),
                    $validatorBlock
                  )
                """.trimIndent()
            }

            ComponentType.BUTTON -> {
                val navCall = when (comp.navActionType) {
                    NavActionType.NAVIGATE_TO_SCREEN -> {
                        val routeKey = routeConstMap[comp.targetScreenId]
                        if (routeKey != null) {
                            "Navigator.pushNamed(context, AppRoutes.$routeKey);"
                        } else {
                            ""
                        }
                    }
                    NavActionType.NAVIGATE_BACK -> "Navigator.maybePop(context);"
                    NavActionType.NONE -> {
                        "ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('$escapedLabel tapped')));"
                    }
                }
                val formGuard = if (inForm) {
                    "if (!(_formKey.currentState?.validate() ?? true)) return;\n              "
                } else {
                    ""
                }
                """
                  SizedBox(
                    width: double.infinity,
                    height: ${(comp.heightDp.takeIf { it >= 44 } ?: 50)}.0,
                    child: FilledButton(
                      onPressed: () {
                        $formGuard$navCall
                      },
                      child: const Text('$escapedLabel'),
                    ),
                  )
                """.trimIndent()
            }

            ComponentType.IMAGE -> {
                val h = comp.heightDp.takeIf { it > 0 } ?: 160
                """
                  Container(
                    height: $h.0,
                    width: double.infinity,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(12),
                      color: Theme.of(context).colorScheme.surfaceContainerHighest,
                    ),
                    alignment: Alignment.center,
                    child: const Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.image_outlined, size: 36),
                        SizedBox(height: 6),
                        Text('$escapedLabel'),
                      ],
                    ),
                  )
                """.trimIndent()
            }

            ComponentType.CARD, ComponentType.CONTAINER -> {
                """
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(${comp.paddingDp.coerceIn(8, 32)}.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '$escapedLabel',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          if ('$escapedSecondary'.isNotEmpty) ...[
                            const SizedBox(height: 6),
                            Text('$escapedSecondary'),
                          ],
                        ],
                      ),
                    ),
                  )
                """.trimIndent()
            }

            ComponentType.ROW -> {
                """
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(child: Text('$escapedLabel', style: Theme.of(context).textTheme.titleSmall)),
                      if ('$escapedSecondary'.isNotEmpty) Text('$escapedSecondary'),
                    ],
                  )
                """.trimIndent()
            }

            ComponentType.COLUMN -> {
                """
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('$escapedLabel', style: Theme.of(context).textTheme.titleSmall),
                      if ('$escapedSecondary'.isNotEmpty) Text('$escapedSecondary'),
                    ],
                  )
                """.trimIndent()
            }

            ComponentType.LIST -> {
                """
                  Card(
                    child: Column(
                      children: const [
                        ListTile(
                          leading: Icon(Icons.check_circle_outline),
                          title: Text('$escapedLabel — Item 1'),
                        ),
                        Divider(height: 1),
                        ListTile(
                          leading: Icon(Icons.check_circle_outline),
                          title: Text('$escapedLabel — Item 2'),
                        ),
                      ],
                    ),
                  )
                """.trimIndent()
            }

            ComponentType.DIVIDER -> "const Divider()"

            ComponentType.APP_BAR, ComponentType.NAV_BAR, ComponentType.UNSUPPORTED -> {
                """
                  SketchPlaceholderBox(
                    label: '$escapedLabel',
                    note: '${escapeDartString(comp.uncertaintyNote.ifBlank { "Placeholder component" })}',
                  )
                """.trimIndent()
            }
        }
    }

    private fun buildWidgetTestDart(doc: CanonicalApplicationDocument): String {
        val initialScreen = doc.screens.firstOrNull { it.id == doc.initialScreenId }
            ?: doc.screens.first()
        return """
            import 'package:flutter/material.dart';
            import 'package:flutter_test/flutter_test.dart';
            import '../lib/main.dart';

            void main() {
              testWidgets('Generated app boots and renders initial screen', (WidgetTester tester) async {
                await tester.pumpWidget(const GeneratedSketchApp());
                await tester.pumpAndSettle();

                expect(find.byType(MaterialApp), findsOneWidget);
                expect(find.textContaining('${escapeDartString(initialScreen.name)}'), findsWidgets);
              });
            }
        """.trimIndent() + "\n"
    }

    private fun buildReadme(projectSlug: String, doc: CanonicalApplicationDocument): String {
        return """
            # ${doc.appName} (`$projectSlug`)

            Generated deterministically by **SketchToApp AI** from Canonical UI Schema v${doc.schemaVersion}.

            ## Project Summary
            - **Package ID:** `${doc.packageName}`
            - **Screens:** ${doc.screens.size} (${doc.screens.joinToString { it.name }})
            - **Initial Route:** `${doc.screens.firstOrNull { it.id == doc.initialScreenId }?.route ?: "/home"}`

            ## Local Setup & Execution
            1. Ensure Flutter SDK `>=3.22.0` and Android SDK are installed (`flutter doctor`).
            2. Bootstrap platform runners if needed and fetch packages:
               ```bash
               flutter create --platforms=android .
               flutter pub get
               ```
            3. Run static analysis and widget tests:
               ```bash
               flutter analyze
               flutter test
               ```
            4. Compile Android APK:
               ```bash
               flutter build apk --debug
               ```
        """.trimIndent() + "\n"
    }
}
