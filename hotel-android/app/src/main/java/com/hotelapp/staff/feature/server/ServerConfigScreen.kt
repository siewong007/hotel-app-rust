package com.hotelapp.staff.feature.server

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotelapp.staff.BuildConfig
import com.hotelapp.staff.core.config.ServerConfigStore
import com.hotelapp.staff.core.design.HotelCard
import com.hotelapp.staff.core.design.HotelOutlinedButton
import com.hotelapp.staff.core.design.HotelPrimaryButton
import com.hotelapp.staff.core.design.HotelTheme
import com.hotelapp.staff.core.design.InlineAlertBanner
import com.hotelapp.staff.core.design.KeyValueRow
import com.hotelapp.staff.core.design.hotelTextFieldColors
import com.hotelapp.staff.core.design.rememberPlatformHaptics
import com.hotelapp.staff.data.repository.ServerProbeResult
import com.hotelapp.staff.data.repository.ServerRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(
    serverConfigStore: ServerConfigStore,
    serverRepository: ServerRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val colors = HotelTheme.colors
    val haptics = rememberPlatformHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val config by serverConfigStore.config.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var urlInput by remember { mutableStateOf(config.serverUrl) }
    var userEdited by remember { mutableStateOf(false) }
    LaunchedEffect(config.serverUrl) {
        if (!userEdited) {
            urlInput = config.serverUrl
        }
    }
    var isTesting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var probeSuccess by remember { mutableStateOf<ServerProbeResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val presets = buildList {
        if (BuildConfig.ALLOW_CLEARTEXT) {
            add("Emulator Local" to "http://10.0.2.2:3030")
            add("USB / Loopback" to "http://127.0.0.1:3030")
        }
        add("Production" to "https://saliminn.my")
        config.recentUrls.forEach { recent ->
            if (none { it.second == recent }) {
                add(recent.removePrefix("https://").removePrefix("http://") to recent)
            }
        }
    }

    fun runTest() {
        keyboard?.hide()
        scope.launch {
            isTesting = true
            errorMessage = null
            probeSuccess = null
            serverRepository.probeAndOptionallySave(urlInput, saveOnSuccess = false)
                .onSuccess { probe ->
                    haptics.confirm()
                    probeSuccess = probe
                    urlInput = probe.normalizedUrl
                }
                .onFailure { err ->
                    haptics.reject()
                    errorMessage = err.message ?: "Unable to connect to server."
                }
            isTesting = false
        }
    }

    fun saveAndApply() {
        keyboard?.hide()
        scope.launch {
            isSaving = true
            errorMessage = null
            serverRepository.probeAndOptionallySave(urlInput, saveOnSuccess = true)
                .onSuccess {
                    haptics.confirm()
                    onSaved()
                }
                .onFailure { err ->
                    haptics.reject()
                    errorMessage = err.message ?: "Unable to save server URL."
                }
            isSaving = false
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.surfaceApp,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Server Connection", color = colors.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.surfaceBase,
                        scrolledContainerColor = colors.surfaceRaised,
                    ),
                )
                HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HotelCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.primarySubtle)
                            .border(1.dp, colors.primarySubtleBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Dns,
                            contentDescription = null,
                            tint = colors.primaryOnSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Backend Server URL",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (BuildConfig.ALLOW_CLEARTEXT) {
                        "Connect to your local development server, LAN property server, or hosted HTTPS backend."
                    } else {
                        "Connect to your property's HTTPS backend server."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        userEdited = true
                        urlInput = it
                        probeSuccess = null
                        errorMessage = null
                    },
                    label = { Text("Server URL") },
                    placeholder = { Text(BuildConfig.DEFAULT_SERVER_URL) },
                    trailingIcon = {
                        if (urlInput.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    userEdited = true
                                    urlInput = ""
                                    probeSuccess = null
                                    errorMessage = null
                                },
                            ) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear URL")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = hotelTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { runTest() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HotelCard(contentPadding = PaddingValues(vertical = 8.dp)) {
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                presets.forEachIndexed { index, (label, url) ->
                    val isSelected = urlInput.trim().trimEnd('/') == url
                    if (index > 0) {
                        HorizontalDivider(
                            color = colors.borderSubtle,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    ListItem(
                        modifier = Modifier.clickable {
                            haptics.tick()
                            userEdited = true
                            urlInput = url
                            probeSuccess = null
                            errorMessage = null
                        },
                        headlineContent = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) colors.primaryOnSurface else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = if (isSelected) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(colors.primaryMain),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Selected",
                                        tint = colors.primaryContrastText,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        } else null,
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) colors.surfaceSelected else Color.Transparent,
                        ),
                    )
                }
            }

            AnimatedVisibility(visible = errorMessage != null) {
                InlineAlertBanner(message = errorMessage.orEmpty(), tone = colors.danger)
            }

            AnimatedVisibility(visible = probeSuccess != null) {
                probeSuccess?.let { probe ->
                    HotelCard(borderColor = colors.success.border) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = colors.success.fg,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connection Verified",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.success.fg,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        KeyValueRow("Hotel", probe.hotelName ?: "Connected")
                        KeyValueRow("Currency", probe.currency ?: "MYR")
                        KeyValueRow("Timezone", probe.timezone ?: "Default")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HotelOutlinedButton(
                    text = if (isTesting) "Testing..." else "Test",
                    isLoading = isTesting,
                    enabled = !isSaving && urlInput.isNotBlank(),
                    onClick = { runTest() },
                    modifier = Modifier.weight(1f),
                )
                HotelPrimaryButton(
                    text = if (isSaving) "Saving..." else "Save & Apply",
                    isLoading = isSaving,
                    enabled = !isTesting && urlInput.isNotBlank(),
                    onClick = { saveAndApply() },
                    modifier = Modifier.weight(1.3f),
                )
            }
        }
    }
}
