package com.hotelapp.staff.core.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hotelapp.staff.R

fun copyToClipboard(context: Context, label: String, text: String): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    true
}.getOrDefault(false)

fun sharePlainText(context: Context, subject: String, text: String): Boolean = runCatching {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, subject)
    context.startActivity(chooser)
    true
}.getOrDefault(false)

@Composable
fun HotelLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    Image(
        painter = painterResource(id = R.drawable.ic_hotel_logo),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
fun HotelCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    accentColor: Color? = null,
    containerColor: Color = HotelTheme.colors.surfaceBase,
    borderColor: Color = HotelTheme.colors.borderSubtle,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val cardColors = CardDefaults.outlinedCardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, borderColor)

    val cardContent: @Composable ColumnScope.() -> Unit = {
        if (accentColor != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .fillMaxHeight()
                        .background(accentColor),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(contentPadding),
                    content = content,
                )
            }
        } else {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }

    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = cardColors,
            border = border,
            content = cardContent,
        )
    } else {
        OutlinedCard(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = cardColors,
            border = border,
            content = cardContent,
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val pillShape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(pillShape)
            .background(tone.bg)
            .border(1.dp, tone.border, pillShape)
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = tone.fg,
                modifier = Modifier.size(12.dp),
            )
        } else if (leadingDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tone.fg),
            )
        }
        Text(
            text = label,
            color = tone.fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: StatusTone = HotelTheme.colors.info,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = HotelTheme.colors
    HotelCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(tone.bg)
                        .border(1.dp, tone.border, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tone.fg,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tone.fg),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank() || onClick != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subtitle.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val colors = HotelTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = colors.primaryOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.primaryOnSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

data class FilterOption<T>(
    val value: T,
    val label: String,
    val count: Int? = null,
    val dotColor: Color? = null,
)

@Composable
fun <T> FilterChipRow(
    options: List<FilterOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.label }) { option ->
            val isSelected = option.value == selected
            val displayLabel = if (option.count != null) {
                "${option.label} · ${option.count}"
            } else {
                option.label
            }
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        haptics.tick()
                    }
                    onSelect(option.value)
                },
                leadingIcon = option.dotColor?.let { dot ->
                    {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dot),
                        )
                    }
                },
                label = {
                    Text(
                        text = displayLabel,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                shape = RoundedCornerShape(999.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primarySubtle,
                    selectedLabelColor = colors.primaryOnSurface,
                    containerColor = colors.surfaceBase,
                    labelColor = colors.textSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = colors.borderBase,
                    selectedBorderColor = colors.primarySubtleBorder,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotelTooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = HotelTheme.colors
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            PlainTooltip(
                containerColor = colors.surfaceOverlay,
                contentColor = colors.textPrimary,
            ) {
                Text(tooltip, style = MaterialTheme.typography.bodySmall)
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

@Composable
fun hotelTextFieldColors(): TextFieldColors {
    val colors = HotelTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        disabledTextColor = colors.textDisabled,
        focusedContainerColor = colors.surfaceSunken,
        unfocusedContainerColor = colors.surfaceSunken,
        disabledContainerColor = colors.surfaceSunken.copy(alpha = 0.6f),
        cursorColor = colors.primaryMain,
        focusedBorderColor = colors.primaryMain,
        unfocusedBorderColor = colors.borderBase,
        errorBorderColor = colors.danger.fg,
        focusedLabelColor = colors.primaryOnSurface,
        unfocusedLabelColor = colors.textSecondary,
        focusedLeadingIconColor = colors.primaryOnSurface,
        unfocusedLeadingIconColor = colors.textMuted,
        focusedTrailingIconColor = colors.textSecondary,
        unfocusedTrailingIconColor = colors.textMuted,
        focusedPlaceholderColor = colors.textMuted,
        unfocusedPlaceholderColor = colors.textMuted,
    )
}

@Composable
fun HotelSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearchSubmit: (() -> Unit)? = null,
) {
    val colors = HotelTheme.colors
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = if (query.isNotEmpty()) colors.primaryOnSurface else colors.textMuted,
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear search",
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = hotelTextFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                focusManager.clearFocus()
                keyboard?.hide()
                onSearchSubmit?.invoke()
            },
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun HotelPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    containerColor: Color = HotelTheme.colors.primaryMain,
    contentColor: Color = HotelTheme.colors.primaryContrastText,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.42f),
            disabledContentColor = contentColor.copy(alpha = 0.72f),
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun HotelOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isDestructive: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = HotelTheme.colors
    val fg = if (isDestructive) colors.danger.fg else colors.textPrimary
    val borderColor = if (isDestructive) colors.danger.border else colors.borderBase
    val bgColor = if (isDestructive) colors.danger.bg.copy(alpha = 0.45f) else Color.Transparent

    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) borderColor else colors.borderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = bgColor,
            contentColor = fg,
            disabledContentColor = colors.textDisabled,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = fg,
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun <T> HotelSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = value == selected
            SegmentedButton(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        haptics.tick()
                    }
                    onSelect(value)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primarySubtle,
                    activeContentColor = colors.primaryOnSurface,
                    activeBorderColor = colors.primarySubtleBorder,
                    inactiveContainerColor = colors.surfaceBase,
                    inactiveContentColor = colors.textSecondary,
                    inactiveBorderColor = colors.borderBase,
                ),
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotelPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = HotelTheme.colors
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                containerColor = colors.surfaceOverlay,
                color = colors.primaryMain,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = { content() },
    )
}

@Composable
fun HotelAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tone: StatusTone? = null,
) {
    val colors = HotelTheme.colors
    val bg = tone?.bg ?: colors.primarySubtle
    val border = tone?.border ?: colors.primarySubtleBorder
    val fg = tone?.fg ?: colors.primaryOnSurface

    val initials = remember(name) {
        val parts = name.trim()
            .split(Regex("[\\s._-]+"))
            .filter { it.isNotBlank() }
        when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts.first().take(2).uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = if (size >= 48.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun HotelSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val colors = HotelTheme.colors
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(14.dp),
            containerColor = colors.surfaceOverlay,
            contentColor = colors.textPrimary,
            actionColor = colors.primaryOnSurface,
            dismissActionContentColor = colors.textSecondary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotelModalSheetContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HotelTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceOverlay,
        scrimColor = colors.scrim,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderStrong) },
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            content = content,
        )
    }
}

@Composable
fun LoadingView(
    modifier: Modifier = Modifier,
    message: String = "Loading...",
) {
    HotelLogoLoader(
        message = message,
        modifier = modifier,
    )
}

@Composable
fun OnResumeRefreshEffect(onResume: () -> Unit) {
    val currentOnResume by rememberUpdatedState(onResume)
    var skippedInitial by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (skippedInitial) {
            currentOnResume()
        } else {
            skippedInitial = true
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = HotelTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised)
                    .border(1.dp, colors.borderSubtle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HotelPrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                )
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HotelTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.danger.bg)
                    .border(1.dp, colors.danger.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = colors.danger.fg,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            HotelPrimaryButton(
                text = "Retry",
                onClick = onRetry,
            )
        }
    }
}

@Composable
fun InlineAlertBanner(
    message: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = HotelTheme.colors
    val resolvedIcon = icon ?: when (tone) {
        colors.danger -> Icons.Outlined.ErrorOutline
        colors.success -> Icons.Outlined.CheckCircleOutline
        else -> Icons.Outlined.Info
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tone.bg)
            .border(1.dp, tone.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = resolvedIcon,
            contentDescription = null,
            tint = tone.fg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            color = tone.fg,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = tone.fg,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = colors.surfaceOverlay,
        title = { Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = {
                    if (isDestructive) haptics.reject() else haptics.confirm()
                    onConfirm()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) colors.danger.fg else colors.primaryMain,
                    contentColor = colors.primaryContrastText,
                ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
    )
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = HotelTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
}
