package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gecko.ExtensionCategory
import com.example.gecko.ManagedExtensionState
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ElectricMint
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.StudioAmber

@Composable
fun ExtensionsHubCard(
    extensions: List<ManagedExtensionState>,
    autoUpdateEnabled: Boolean,
    isUpdatingAll: Boolean,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onUpdateAllNow: () -> Unit,
    onToggleExtension: (extensionId: String, enabled: Boolean) -> Unit,
    onUpdateSingleExtension: (extensionId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCount = extensions.count { it.isInstalled && it.isEnabled }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Extension, contentDescription = null, tint = CyanTelemetry)
                    Column {
                        Text("Mozilla Extensions Hub", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "$activeCount/${extensions.size} Active • Mozilla AMO & GitHub Signed XPIs",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricMint
                        )
                    }
                }
                Button(
                    onClick = onUpdateAllNow,
                    enabled = !isUpdatingAll,
                    colors = ButtonDefaults.buttonColors(containerColor = SignalCrimson),
                    modifier = Modifier.testTag("update_all_extensions_button")
                ) {
                    if (isUpdatingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Update All")
                    }
                }
            }

            // Global Auto-Update Switch for ALL Extensions + yt-dlp Engine
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (autoUpdateEnabled) ElectricMint.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    1.dp,
                    if (autoUpdateEnabled) ElectricMint.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.AutoMode,
                            contentDescription = null,
                            tint = ElectricMint,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Auto-Update All Extensions & yt-dlp",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Automatically installs & updates all 7 extensions + yt-dlp nightly every 24h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = onToggleAutoUpdate,
                        modifier = Modifier.testTag("setting_autoupdate_ext_switch")
                    )
                }
            }

            // Built-in YTube Studio Creator Enhancer status banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioAmber.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, StudioAmber.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "YTube Studio Creator Toolkit",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "BUILT-IN • ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Live Studio SEO title/description counters, 1-tap HD Thumbnail download & Tag Copier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // All 7 Managed Signed Mozilla Extensions
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                extensions.forEach { extState ->
                    val spec = extState.spec
                    val badgeColor = when (spec.category) {
                        ExtensionCategory.BOTH -> SignalCrimson
                        ExtensionCategory.STREAM -> CyanTelemetry
                        ExtensionCategory.STUDIO -> StudioAmber
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (extState.isInstalled && extState.isEnabled) {
                                ElectricMint.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ext_card_${spec.key}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = spec.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = badgeColor.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = spec.category.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = spec.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = extState.statusDetail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (extState.isInstalled && extState.isEnabled) ElectricMint else CyanTelemetry,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onUpdateSingleExtension(spec.id) },
                                    enabled = !extState.isUpdating,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("ext_update_${spec.key}")
                                ) {
                                    if (extState.isUpdating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = CyanTelemetry
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = "Update ${spec.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = extState.isEnabled,
                                    onCheckedChange = { checked -> onToggleExtension(spec.id, checked) },
                                    modifier = Modifier.testTag("ext_switch_${spec.key}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
