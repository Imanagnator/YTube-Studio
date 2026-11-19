package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.UserPrefs
import com.example.gecko.StudioProMetrics
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.StudioAmber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioProBottomSheet(
    prefs: UserPrefs,
    metrics: StudioProMetrics,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleAutoCollapse: (Boolean) -> Unit,
    onToggleFocusMode: (Boolean) -> Unit,
    onToggleSeoHud: (Boolean) -> Unit,
    onTogglePrivacyBlur: (Boolean) -> Unit,
    onSaveCustomTemplate: (String) -> Unit,
    onTriggerAction: (action: String, payload: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customTemplateDraft by remember(prefs.studioProCustomTemplate) {
        mutableStateOf(prefs.studioProCustomTemplate)
    }
    var showTemplateEditor by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElectricMint.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ElectricMint.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricMint,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "YTube Studio Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Built-in Custom Studio Extension • Zero Login Needed",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanTelemetry
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Studio Pro")
                }
            }

            // Live Video SEO Readiness Card
            val scoreColor = when {
                metrics.seoScore >= 75 -> ElectricMint
                metrics.seoScore >= 45 -> StudioAmber
                metrics.seoScore >= 0 -> SignalCrimson
                else -> CyanTelemetry
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (metrics.isEditorPage) "Live Video SEO Audit" else "Studio Inspector",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = scoreColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (metrics.seoScore >= 0) "${metrics.seoScore} / 100" else "READY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (metrics.seoScore >= 0) {
                        LinearProgressIndicator(
                            progress = { (metrics.seoScore / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = scoreColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (metrics.titleLen >= 0) {
                                MetricPill("Title ${metrics.titleLen}/100")
                            }
                            if (metrics.descLen >= 0) {
                                MetricPill("Desc ${metrics.descLen}/5000")
                            }
                            MetricPill("Tags ${metrics.tagsCharCount}/500")
                            MetricPill(if (metrics.hasChapters) "Chapters ✓" else "No Chapters")
                            MetricPill("#Tags ${metrics.hashtagCount}")
                        }
                    }

                    Text(
                        text = metrics.topTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // One-Tap Creator Quick Actions
            Text(
                text = "Creator Quick Actions",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioQuickActionTile(
                    icon = Icons.Filled.CloudUpload,
                    label = "Upload Video",
                    accent = SignalCrimson,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_pro_upload_action"),
                    onClick = {
                        onTriggerAction("triggerUpload", "")
                        onDismiss()
                    }
                )
                StudioQuickActionTile(
                    icon = Icons.Filled.Sell,
                    label = "Smart Tags",
                    accent = CyanTelemetry,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_pro_smart_tags"),
                    onClick = { onTriggerAction("generateTags", "") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioQuickActionTile(
                    icon = Icons.Filled.ContentCopy,
                    label = "Copy Tags",
                    accent = ElectricMint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_pro_copy_tags"),
                    onClick = { onTriggerAction("copyTags", "") }
                )
                StudioQuickActionTile(
                    icon = Icons.Filled.Image,
                    label = "HD Thumbnail",
                    accent = StudioAmber,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_pro_hd_thumb"),
                    onClick = { onTriggerAction("downloadThumb", "") }
                )
            }

            // Description Templates
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = ElectricMint, modifier = Modifier.size(18.dp))
                            Text("Description Templates", style = MaterialTheme.typography.titleSmall)
                        }
                        OutlinedButton(
                            onClick = { showTemplateEditor = !showTemplateEditor },
                            modifier = Modifier.testTag("studio_pro_edit_template_btn")
                        ) {
                            Text(if (showTemplateEditor) "Hide Editor" else "Custom")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                onTriggerAction("insertTemplate", "tutorial")
                                onDismiss()
                            },
                            label = { Text("+ Tutorial") }
                        )
                        AssistChip(
                            onClick = {
                                onTriggerAction("insertTemplate", "gaming")
                                onDismiss()
                            },
                            label = { Text("+ Gaming") }
                        )
                        AssistChip(
                            onClick = {
                                onTriggerAction("insertTemplate", "shorts")
                                onDismiss()
                            },
                            label = { Text("+ Shorts") }
                        )
                        AssistChip(
                            onClick = {
                                onTriggerAction("insertTemplate", "vlog")
                                onDismiss()
                            },
                            label = { Text("+ Vlog") }
                        )
                        if (prefs.studioProCustomTemplate.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    onTriggerAction("insertTemplate", "custom")
                                    onDismiss()
                                },
                                label = { Text("+ My Custom") }
                            )
                        }
                    }

                    if (showTemplateEditor) {
                        OutlinedTextField(
                            value = customTemplateDraft,
                            onValueChange = { customTemplateDraft = it },
                            label = { Text("Your Saved Description Template") },
                            placeholder = { Text("Chapters, social links, affiliate links, #hashtags...") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                onSaveCustomTemplate(customTemplateDraft)
                                showTemplateEditor = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricMint, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Custom Template", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Studio Pro Switches
            Text(
                text = "Studio Pro Enhancers",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StudioProToggleRow(
                title = "YTube Studio Pro Active",
                subtitle = "Enable custom built-in YouTube Studio enhancement engine",
                checked = prefs.studioProEnabled,
                onCheckedChange = onToggleEnabled,
                testTag = "studio_pro_master_switch"
            )
            StudioProToggleRow(
                title = "Auto-Collapse Left Sidebar",
                subtitle = "Reclaims ~250px of horizontal screen width for mobile PC Studio",
                checked = prefs.studioProAutoCollapseSidebar,
                enabled = prefs.studioProEnabled,
                onCheckedChange = onToggleAutoCollapse,
                testTag = "studio_pro_sidebar_switch"
            )
            StudioProToggleRow(
                title = "Focus Dashboard (Hide Promo Cards)",
                subtitle = "Hides Creator Insider, News, and Ideas clutter cards",
                checked = prefs.studioProFocusMode,
                enabled = prefs.studioProEnabled,
                onCheckedChange = onToggleFocusMode,
                testTag = "studio_pro_focus_switch"
            )
            StudioProToggleRow(
                title = "Live Floating SEO Score HUD",
                subtitle = "Compact collapsible SEO score & tag bar inside Video Details",
                checked = prefs.studioProSeoHudEnabled,
                enabled = prefs.studioProEnabled,
                onCheckedChange = onToggleSeoHud,
                testTag = "studio_pro_hud_switch"
            )
            StudioProToggleRow(
                title = "Privacy Blur (Screenshots & Recording)",
                subtitle = "Blurs views, subscribers, and revenue numbers until hovered",
                checked = prefs.studioProPrivacyBlur,
                enabled = prefs.studioProEnabled,
                onCheckedChange = onTogglePrivacyBlur,
                icon = Icons.Filled.VisibilityOff,
                testTag = "studio_pro_privacy_switch"
            )
        }
    }
}

@Composable
private fun MetricPill(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun StudioQuickActionTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun StudioProToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(15.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ElectricMint
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
