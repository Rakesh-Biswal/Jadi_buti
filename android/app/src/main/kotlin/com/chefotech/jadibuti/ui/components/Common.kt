package com.chefotech.jadibuti.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chefotech.jadibuti.R
import com.chefotech.jadibuti.ui.theme.Amber
import com.chefotech.jadibuti.ui.theme.AmberSoft
import com.chefotech.jadibuti.ui.theme.Blue
import com.chefotech.jadibuti.ui.theme.BlueSoft
import com.chefotech.jadibuti.ui.theme.Green
import com.chefotech.jadibuti.ui.theme.GreenSoft
import com.chefotech.jadibuti.ui.theme.Grey
import com.chefotech.jadibuti.ui.theme.HeaderGreen
import com.chefotech.jadibuti.ui.theme.Red
import com.chefotech.jadibuti.ui.theme.RedSoft

/*
 * Jadi-Buti design system: one flat header style, one outlined card style, one chip style,
 * big buttons. Everything is sized for elderly users: 56–60dp touch targets, 16–18sp body text.
 */

/** Full-width, tall primary button (min 60dp touch target). */
@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp)); Spacer(Modifier.size(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BigOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp)) {
        if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.size(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Flat header used on secondary screens. Optional subtitle and trailing actions. */
@Composable
fun AppTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth().background(HeaderGreen)) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(28.dp)) }
            } else Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

/** "A ChefoTech product" mark. Light variant sits on the green header; the other on surfaces. */
@Composable
fun BrandBadge(onHeader: Boolean = true) {
    val bg = if (onHeader) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primaryContainer
    val fg = if (onHeader) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text("A ChefoTech product", style = MaterialTheme.typography.labelSmall, color = fg, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

/** Brand header for the main screens: logo, product name and the ChefoTech mark on the left. */
@Composable
fun BrandTopBar(subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth().background(HeaderGreen)) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().heightIn(min = 68.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.White, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(44.dp)) {
                Image(painterResource(R.drawable.jadi_buti_logo), contentDescription = "Jadi-Buti", contentScale = ContentScale.Fit, modifier = Modifier.padding(3.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Jadi-Buti", style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    BrandBadge()
                }
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Outlined, flat card — the only card style in the app. */
@Composable
fun AppCard(modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surface, padding: Int = 16, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.padding(padding.dp)) { content() }
    }
}

enum class Tone { GREEN, AMBER, RED, BLUE, GREY }

fun toneColors(tone: Tone): Pair<Color, Color> = when (tone) {
    Tone.GREEN -> GreenSoft to Green
    Tone.AMBER -> AmberSoft to Color(0xFF7A4E00)
    Tone.RED -> RedSoft to Red
    Tone.BLUE -> BlueSoft to Blue
    Tone.GREY -> Color(0xFFECF0EE) to Grey
}

/** Kept for callers that want the strong tone colour alone. */
fun toneAccent(tone: Tone): Color = when (tone) { Tone.AMBER -> Amber; else -> toneColors(tone).second }

@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val (bg, fg) = toneColors(tone)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun statusTone(status: String): Tone = when (status) {
    "TAKEN" -> Tone.GREEN; "DUE" -> Tone.AMBER; "MISSED" -> Tone.RED; "SKIPPED" -> Tone.GREY; "SNOOZED" -> Tone.BLUE; else -> Tone.BLUE
}

/** Small labelled icon, e.g. "After food" with a plate icon. */
@Composable
fun IconLabel(icon: ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
fun MemberAvatar(name: String, size: Int = 44) {
    Box(Modifier.size(size.dp).background(HeaderGreen, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(), color = Color.White, style = if (size >= 56) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
    }
}

/** Circular icon badge used on list rows. */
@Composable
fun IconBadge(icon: ImageVector, tone: Tone = Tone.GREEN, size: Int = 44) {
    val (bg, fg) = toneColors(tone)
    Box(Modifier.size(size.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size((size * 0.55).dp))
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) { IconBadge(icon, Tone.GREEN, 72); Spacer(Modifier.height(16.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoBanner(text: String, tone: Tone = Tone.AMBER, modifier: Modifier = Modifier, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null) {
    val (bg, fg) = toneColors(tone)
    Surface(color = bg, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, fg.copy(alpha = 0.25f)), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)) }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = Color(0xFF16231C))
            if (action != null) { Spacer(Modifier.width(8.dp)); action() }
        }
    }
}

@Composable
fun ErrorText(message: String?) {
    if (!message.isNullOrBlank()) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
}

/** Thick progress bar with label, e.g. stock level or doses done today. */
@Composable
fun LabelledProgress(label: String, progress: Float, tone: Tone = Tone.GREEN, modifier: Modifier = Modifier) {
    val (_, fg) = toneColors(tone)
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp), color = fg, trackColor = fg.copy(alpha = 0.18f))
    }
}

/** Big stat tile used on dashboards. */
@Composable
fun StatTile(value: String, label: String, tone: Tone, modifier: Modifier = Modifier) {
    val (bg, fg) = toneColors(tone)
    Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = fg)
            Text(label, style = MaterialTheme.typography.bodySmall, color = fg, textAlign = TextAlign.Center)
        }
    }
}
