package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.schema.CanonicalComponent
import com.example.core.schema.CanonicalScreen
import com.example.core.schema.CanonicalThemeTokens
import com.example.core.schema.ComponentType
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkstationTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("topbar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back"
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun StatusFeedbackBanners(
    statusMessage: String,
    errorMessage: String,
    onDismiss: () -> Unit
) {
    if (errorMessage.isNotBlank()) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { onDismiss() }
                .testTag("error_banner")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Error notice",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else if (statusMessage.isNotBlank()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { onDismiss() }
                .testTag("status_banner")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "Success notice",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun PipelineStageBadge(stage: String) {
    val containerColor = when (stage.lowercase()) {
        "ready", "generated", "succeeded" -> MaterialTheme.colorScheme.primaryContainer
        "edited", "analyzed", "building" -> MaterialTheme.colorScheme.secondaryContainer
        "failed" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when (stage.lowercase()) {
        "ready", "generated", "succeeded" -> MaterialTheme.colorScheme.primary
        "edited", "analyzed", "building" -> MaterialTheme.colorScheme.onSecondaryContainer
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        shape = CircleShape
    ) {
        Text(
            text = stage.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

fun parseSafeThemeColor(hex: String, fallback: Color): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(("FF$clean").toLong(16))
            8 -> Color(clean.toLong(16))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Shared live canonical UI renderer used in both Visual Editor (selection mode)
 * and Interactive Application Preview (interactive form + navigation mode).
 */
@Composable
fun CanonicalPhoneFrameRenderer(
    screen: CanonicalScreen,
    themeTokens: CanonicalThemeTokens,
    selectedComponentId: String = "",
    interactiveMode: Boolean = false,
    fieldValues: Map<String, String> = emptyMap(),
    fieldErrors: Map<String, String> = emptyMap(),
    onFieldValueChange: (String, String) -> Unit = { _, _ -> },
    onSelectComponent: (CanonicalComponent) -> Unit = {},
    onTriggerButton: (CanonicalComponent) -> Unit = {}
) {
    val bgColor = parseSafeThemeColor(themeTokens.backgroundColorHex, MaterialTheme.colorScheme.background)
    val surfaceColor = parseSafeThemeColor(themeTokens.surfaceColorHex, MaterialTheme.colorScheme.surfaceVariant)
    val primaryColor = parseSafeThemeColor(themeTokens.primaryColorHex, MaterialTheme.colorScheme.primary)
    val radius = themeTokens.borderRadiusDp.coerceIn(4, 28).dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp)),
        color = bgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Simulated status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceColor.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("9:41", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(screen.route, style = MaterialTheme.typography.labelSmall, color = primaryColor)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                screen.components.forEach { comp ->
                    val isSelected = !interactiveMode && comp.id == selectedComponentId
                    val selectionModifier = if (!interactiveMode) {
                        Modifier
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(radius)
                            )
                            .clickable { onSelectComponent(comp) }
                            .padding(4.dp)
                    } else {
                        Modifier
                    }

                    Box(modifier = Modifier.fillMaxWidth().then(selectionModifier)) {
                        when (comp.type) {
                            ComponentType.APP_BAR -> {
                                Surface(
                                    color = surfaceColor,
                                    shape = RoundedCornerShape(radius),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = comp.label.ifBlank { screen.name },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                    )
                                }
                            }

                            ComponentType.TEXT -> {
                                val textAlign = when (comp.alignment) {
                                    "center" -> TextAlign.Center
                                    "end" -> TextAlign.End
                                    else -> TextAlign.Start
                                }
                                val weight = when (comp.fontWeight) {
                                    "bold" -> FontWeight.Bold
                                    "medium" -> FontWeight.SemiBold
                                    else -> FontWeight.Normal
                                }
                                Text(
                                    text = comp.label,
                                    fontSize = comp.fontSizeSp.sp,
                                    fontWeight = weight,
                                    textAlign = textAlign,
                                    color = parseSafeThemeColor(comp.textColorHex, MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            ComponentType.TEXT_FIELD -> {
                                val currentVal = fieldValues[comp.id].orEmpty()
                                val errText = fieldErrors[comp.id]
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = currentVal,
                                        onValueChange = { if (interactiveMode) onFieldValueChange(comp.id, it) },
                                        readOnly = !interactiveMode,
                                        enabled = interactiveMode,
                                        label = { Text(comp.label) },
                                        placeholder = { if (comp.hint.isNotBlank()) Text(comp.hint) },
                                        isError = !errText.isNullOrBlank(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(radius),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (!errText.isNullOrBlank()) {
                                        Text(
                                            text = errText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 2.dp, start = 6.dp)
                                        )
                                    }
                                }
                            }

                            ComponentType.BUTTON -> {
                                Button(
                                    onClick = {
                                        if (interactiveMode) onTriggerButton(comp)
                                        else onSelectComponent(comp)
                                    },
                                    shape = RoundedCornerShape(radius),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((comp.heightDp.takeIf { it >= 48 } ?: 48).dp)
                                ) {
                                    Text(comp.label.ifBlank { "Button" })
                                }
                            }

                            ComponentType.IMAGE -> {
                                Surface(
                                    color = surfaceColor,
                                    shape = RoundedCornerShape(radius),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((comp.heightDp.takeIf { it >= 80 } ?: 132).dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Image,
                                            contentDescription = comp.label.ifBlank { "Image placeholder" },
                                            tint = primaryColor,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(comp.label, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            ComponentType.CARD, ComponentType.CONTAINER, ComponentType.LIST -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                                    shape = RoundedCornerShape(radius),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(comp.paddingDp.coerceIn(8, 28).dp)) {
                                        Text(comp.label, style = MaterialTheme.typography.titleSmall)
                                        if (comp.secondaryText.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                comp.secondaryText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            ComponentType.ROW -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(comp.label, style = MaterialTheme.typography.titleSmall)
                                    if (comp.secondaryText.isNotBlank()) {
                                        Text(
                                            comp.secondaryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = primaryColor
                                        )
                                    }
                                }
                            }

                            ComponentType.COLUMN -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(comp.label, style = MaterialTheme.typography.titleSmall)
                                    if (comp.secondaryText.isNotBlank()) {
                                        Text(
                                            comp.secondaryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            ComponentType.DIVIDER -> HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            ComponentType.NAV_BAR -> {
                                Surface(
                                    color = surfaceColor,
                                    shape = RoundedCornerShape(radius),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Text("Home", style = MaterialTheme.typography.labelMedium, color = primaryColor)
                                        Text("Explore", style = MaterialTheme.typography.labelMedium)
                                        Text("Profile", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            ComponentType.UNSUPPORTED -> {
                                Surface(
                                    color = surfaceColor,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(radius),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Placeholder: ${comp.label}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        if (comp.uncertaintyNote.isNotBlank()) {
                                            Text(
                                                text = comp.uncertaintyNote,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DART_KEYWORDS = setOf(
    "import", "class", "extends", "const", "final", "static", "void",
    "return", "if", "else", "true", "false", "null", "super", "required", "override"
)

@Composable
fun SyntaxHighlightedCodeBlock(source: String) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val highlighted: AnnotatedString = buildAnnotatedString {
        source.lines().forEachIndexed { idx, line ->
            withStyle(SpanStyle(color = muted)) {
                append("%3d  ".format(idx + 1))
            }
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
                withStyle(SpanStyle(color = muted)) {
                    appendLine(line)
                }
            } else {
                val tokens = line.split(Regex("(?<=\\s)|(?=\\s)|(?=[(){}:,;])|(?<=[(){}:,;])"))
                for (tok in tokens) {
                    val clean = tok.trim().removePrefix("@")
                    when {
                        clean in DART_KEYWORDS -> withStyle(SpanStyle(color = secondary, fontWeight = FontWeight.SemiBold)) { append(tok) }
                        tok.startsWith("'") || tok.endsWith("'") -> withStyle(SpanStyle(color = primary)) { append(tok) }
                        else -> withStyle(SpanStyle(color = onSurface)) { append(tok) }
                    }
                }
                append("\n")
            }
        }
    }

    Text(
        text = highlighted,
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(12.dp)
    )
}
