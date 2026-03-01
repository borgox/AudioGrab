package com.example.audiograb

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.audiograb.ui.theme.AudioGrabTheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNewPipeInitialized()
        setContent {
            AudioGrabTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppScreen()
                }
            }
        }
    }
}

private enum class MetaStatus {
    Idle,
    Loading,
    Loaded,
    Error
}

private data class MetaState(
    val status: MetaStatus = MetaStatus.Idle,
    val title: String = "",
    val uploader: String = "",
    val thumbnailUrl: String = "",
    val avatarUrl: String = "",
    val message: String = ""
)

private enum class DownloadStatus {
    Idle,
    Downloading,
    Converting,
    Done,
    Error
}

private data class DownloadState(
    val status: DownloadStatus = DownloadStatus.Idle,
    val progress: Float = 0f,
    val message: String = "",
    val outputFileName: String = ""
)

private enum class QualityOption(val ffmpegArgs: String) {
    Best("-q:a 2"),
    Balanced("-q:a 5"),
    High("-b:a 192k"),
    Standard("-b:a 128k")
}

private data class MetaResult(
    val success: Boolean,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val avatarUrl: String,
    val message: String
)

private data class DownloadResult(
    val success: Boolean,
    val message: String,
    val filePath: String,
    val title: String
)

private data class SelectedStream(
    val url: String,
    val suffix: String,
    val kind: String
)

private val httpClient = OkHttpClient.Builder().build()
@Volatile
private var globalCookieHeader: String = ""

@Composable
private fun AppScreen() {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasStarted by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var singleUrl by remember { mutableStateOf("") }
    var bulkText by remember { mutableStateOf("") }
    var selectedUrl by remember { mutableStateOf("") }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var qualityOption by remember { mutableStateOf(QualityOption.Best) }
    var cookieHeader by remember { mutableStateOf("") }
    var isBulkRunning by remember { mutableStateOf(false) }

    val metadataState = remember { mutableStateMapOf<String, MetaState>() }
    val downloadState = remember { mutableStateMapOf<String, DownloadState>() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistFolderPermission(context, uri)
            selectedFolderUri = uri
        }
    }

    val trimmedSingleUrl = singleUrl.trim()
    val isSingleValid = isValidUrl(trimmedSingleUrl)

    val bulkUrls = remember(bulkText) { parseUrls(bulkText) }

    LaunchedEffect(trimmedSingleUrl) {
        if (isSingleValid) {
            fetchMetadataIfNeeded(trimmedSingleUrl, metadataState)
        }
    }

    LaunchedEffect(bulkUrls) {
        if (bulkUrls.isEmpty()) {
            selectedUrl = ""
        } else if (selectedUrl.isBlank() || selectedUrl !in bulkUrls) {
            selectedUrl = bulkUrls.first()
        }
        bulkUrls.forEach { url ->
            if (isValidUrl(url)) {
                fetchMetadataIfNeeded(url, metadataState)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF7E8), Color(0xFFFBE9D0), Color(0xFFF5D9C3))
                )
            )
    ) {
        if (!hasStarted) {
            SetupScreen(onStart = { hasStarted = true })
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeaderBlock()
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(text = stringResource(id = R.string.tab_single)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(text = stringResource(id = R.string.tab_bulk)) }
                    )
                }

                DownloadSettingsCard(
                    selectedFolderUri = selectedFolderUri,
                    onChooseFolder = { folderPicker.launch(null) },
                    onClearFolder = { selectedFolderUri = null },
                    qualityOption = qualityOption,
                    onQualityChange = { qualityOption = it },
                    cookieHeader = cookieHeader,
                    onCookieChange = {
                        cookieHeader = it
                        globalCookieHeader = it
                    }
                )

                if (selectedTab == 0) {
                    SingleTab(
                        clipboardText = clipboard.getText()?.text.orEmpty(),
                        url = singleUrl,
                        onUrlChange = { singleUrl = it },
                        onPaste = { singleUrl = clipboard.getText()?.text.orEmpty() },
                        isValid = isSingleValid,
                        metaState = metadataState[trimmedSingleUrl] ?: MetaState(),
                        downloadState = downloadState[trimmedSingleUrl] ?: DownloadState(),
                        onDownload = {
                            if (isSingleValid) {
                                scope.launch {
                                    downloadSingle(
                                        context = context,
                                        url = trimmedSingleUrl,
                                        qualityOption = qualityOption,
                                        selectedFolderUri = selectedFolderUri,
                                        cookieHeader = cookieHeader,
                                        downloadState = downloadState
                                    )
                                }
                            }
                        }
                    )
                } else {
                    BulkTab(
                        bulkText = bulkText,
                        onBulkTextChange = { bulkText = it },
                        urls = bulkUrls,
                        selectedUrl = selectedUrl,
                        onSelectUrl = { selectedUrl = it },
                        metadataState = metadataState,
                        downloadState = downloadState,
                        isRunning = isBulkRunning,
                        onDownloadAll = {
                            scope.launch {
                                isBulkRunning = true
                                downloadAll(
                                    context = context,
                                    urls = bulkUrls,
                                    qualityOption = qualityOption,
                                    selectedFolderUri = selectedFolderUri,
                                    cookieHeader = cookieHeader,
                                    metadataState = metadataState,
                                    downloadState = downloadState
                                )
                                isBulkRunning = false
                            }
                        },
                        onDownloadSelected = { url ->
                            scope.launch {
                                downloadSingle(
                                    context = context,
                                    url = url,
                                    qualityOption = qualityOption,
                                    selectedFolderUri = selectedFolderUri,
                                    cookieHeader = cookieHeader,
                                    downloadState = downloadState
                                )
                            }
                        }
                    )
                }

                InfoCard()
            }
        }
    }
}

@Composable
private fun DownloadSettingsCard(
    selectedFolderUri: Uri?,
    onChooseFolder: () -> Unit,
    onClearFolder: () -> Unit,
    qualityOption: QualityOption,
    onQualityChange: (QualityOption) -> Unit,
    cookieHeader: String,
    onCookieChange: (String) -> Unit
) {
    val context = LocalContext.current
    val folderName = if (selectedFolderUri != null) {
        DocumentFile.fromTreeUri(context, selectedFolderUri)?.name
    } else {
        null
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(id = R.string.settings_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(imageVector = Icons.Outlined.Folder, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.label_save_to), fontWeight = FontWeight.SemiBold)
                    Text(
                        text = folderName ?: stringResource(id = R.string.folder_app_default),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(onClick = onChooseFolder) {
                    Text(text = stringResource(id = R.string.action_choose_folder))
                }
            }
            if (selectedFolderUri != null) {
                Button(
                    onClick = onClearFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(text = stringResource(id = R.string.action_clear_folder))
                }
            }
            QualitySelector(qualityOption = qualityOption, onQualityChange = onQualityChange)
            OutlinedTextField(
                value = cookieHeader,
                onValueChange = onCookieChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(id = R.string.label_cookies)) },
                placeholder = { Text(text = stringResource(id = R.string.cookies_placeholder)) },
                minLines = 2,
                maxLines = 4
            )
            if (cookieHeader.isNotBlank()) {
                Button(
                    onClick = { onCookieChange("") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(text = stringResource(id = R.string.action_clear_cookies))
                }
            }
            Text(
                text = stringResource(id = R.string.label_version, getAppVersion(context)),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun QualitySelector(
    qualityOption: QualityOption,
    onQualityChange: (QualityOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (qualityOption) {
        QualityOption.Best -> stringResource(id = R.string.quality_best)
        QualityOption.Balanced -> stringResource(id = R.string.quality_balanced)
        QualityOption.High -> stringResource(id = R.string.quality_high)
        QualityOption.Standard -> stringResource(id = R.string.quality_standard)
    }

    Column {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            label = { Text(text = stringResource(id = R.string.label_quality)) },
            trailingIcon = {
                Icon(imageVector = Icons.Outlined.KeyboardArrowDown, contentDescription = null)
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.quality_best)) },
                onClick = {
                    onQualityChange(QualityOption.Best)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.quality_balanced)) },
                onClick = {
                    onQualityChange(QualityOption.Balanced)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.quality_high)) },
                onClick = {
                    onQualityChange(QualityOption.High)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(id = R.string.quality_standard)) },
                onClick = {
                    onQualityChange(QualityOption.Standard)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun SingleTab(
    clipboardText: String,
    url: String,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    isValid: Boolean,
    metaState: MetaState,
    downloadState: DownloadState,
    onDownload: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(id = R.string.single_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(text = stringResource(id = R.string.link_placeholder)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Uri)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPaste, enabled = clipboardText.isNotBlank()) {
                    Icon(imageVector = Icons.Outlined.ContentPaste, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.paste))
                }
                Button(
                    onClick = onDownload,
                    enabled = isValid && metaState.status == MetaStatus.Loaded && downloadState.status != DownloadStatus.Downloading
                ) {
                    Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.action_download))
                }
            }
            ValidationRow(isValid = isValid)
        }
    }

    PreviewCard(
        metaState = metaState,
        downloadState = downloadState,
        emptyMessage = stringResource(id = R.string.message_select_link)
    )
}

@Composable
private fun ValidationRow(isValid: Boolean) {
    Text(
        text = if (isValid) {
            stringResource(id = R.string.link_valid)
        } else {
            stringResource(id = R.string.link_invalid)
        },
        color = if (isValid) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun BulkTab(
    bulkText: String,
    onBulkTextChange: (String) -> Unit,
    urls: List<String>,
    selectedUrl: String,
    onSelectUrl: (String) -> Unit,
    metadataState: Map<String, MetaState>,
    downloadState: Map<String, DownloadState>,
    isRunning: Boolean,
    onDownloadAll: () -> Unit,
    onDownloadSelected: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(id = R.string.bulk_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = bulkText,
                onValueChange = onBulkTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text(text = stringResource(id = R.string.bulk_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDownloadAll, enabled = urls.isNotEmpty() && !isRunning) {
                    Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.action_download_all))
                }
                Text(
                    text = stringResource(id = R.string.bulk_count, urls.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }

    if (urls.isNotEmpty()) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = stringResource(id = R.string.bulk_list_title), fontWeight = FontWeight.Bold)
                urls.forEach { url ->
                    val meta = metadataState[url] ?: MetaState()
                    val download = downloadState[url] ?: DownloadState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectUrl(url) }
                            .background(
                                if (url == selectedUrl) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    Color.Transparent
                                }
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when (download.status) {
                                DownloadStatus.Done -> Icons.Outlined.CheckCircle
                                DownloadStatus.Error -> Icons.Outlined.ErrorOutline
                                DownloadStatus.Downloading, DownloadStatus.Converting -> Icons.Outlined.CloudDownload
                                else -> Icons.Outlined.Search
                            },
                            contentDescription = null,
                            tint = when (download.status) {
                                DownloadStatus.Done -> MaterialTheme.colorScheme.primary
                                DownloadStatus.Error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = meta.title.ifBlank { url },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2
                            )
                            val subtitle = when (download.status) {
                                DownloadStatus.Downloading -> stringResource(id = R.string.status_downloading)
                                DownloadStatus.Converting -> stringResource(id = R.string.status_converting)
                                DownloadStatus.Done -> stringResource(id = R.string.status_done)
                                DownloadStatus.Error -> stringResource(id = R.string.status_error)
                                else -> when (meta.status) {
                                    MetaStatus.Loading -> stringResource(id = R.string.status_fetching)
                                    MetaStatus.Error -> stringResource(id = R.string.status_error)
                                    else -> stringResource(id = R.string.status_ready)
                                }
                            }
                            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    val selectedMeta = metadataState[selectedUrl] ?: MetaState()
    val selectedDownload = downloadState[selectedUrl] ?: DownloadState()
    PreviewCard(
        metaState = selectedMeta,
        downloadState = selectedDownload,
        emptyMessage = stringResource(id = R.string.message_select_link)
    )

    if (selectedUrl.isNotBlank()) {
        Button(
            onClick = { onDownloadSelected(selectedUrl) },
            enabled = selectedMeta.status == MetaStatus.Loaded && selectedDownload.status != DownloadStatus.Downloading
        ) {
            Icon(imageVector = Icons.Outlined.CloudDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.action_download))
        }
    }
}

@Composable
private fun PreviewCard(
    metaState: MetaState,
    downloadState: DownloadState,
    emptyMessage: String
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val contentTitle = when (metaState.status) {
                MetaStatus.Loading -> stringResource(id = R.string.status_fetching)
                MetaStatus.Error -> stringResource(id = R.string.status_error)
                MetaStatus.Loaded -> stringResource(id = R.string.preview_title)
                MetaStatus.Idle -> stringResource(id = R.string.preview_title)
            }
            Text(text = contentTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            if (metaState.status == MetaStatus.Loaded) {
                if (metaState.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = metaState.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (metaState.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = metaState.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                    }
                    Column {
                        Text(text = metaState.title, style = MaterialTheme.typography.titleMedium)
                        if (metaState.uploader.isNotBlank()) {
                            Text(text = metaState.uploader, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                val message = when (metaState.status) {
                    MetaStatus.Loading -> stringResource(id = R.string.message_fetching)
                    MetaStatus.Error -> metaState.message.ifBlank { stringResource(id = R.string.message_fetch_failed) }
                    else -> emptyMessage
                }
                Text(text = message, style = MaterialTheme.typography.bodyLarge)
            }

            if (downloadState.status != DownloadStatus.Idle) {
                DownloadStatusCard(downloadState = downloadState)
            }
        }
    }
}

@Composable
private fun DownloadStatusCard(downloadState: DownloadState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Crossfade(targetState = downloadState.status, label = "download-status-icon") {
                when (it) {
                    DownloadStatus.Done -> Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    DownloadStatus.Error -> Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    else -> Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            val label = when (downloadState.status) {
                DownloadStatus.Downloading -> stringResource(id = R.string.status_downloading)
                DownloadStatus.Converting -> stringResource(id = R.string.status_converting)
                DownloadStatus.Done -> stringResource(id = R.string.status_done)
                DownloadStatus.Error -> stringResource(id = R.string.status_error)
                else -> stringResource(id = R.string.status_ready)
            }
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
        if (downloadState.message.isNotBlank()) {
            Text(text = downloadState.message, style = MaterialTheme.typography.bodyMedium)
        }
        if (downloadState.status == DownloadStatus.Converting) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            LinearProgressIndicator(
                progress = { downloadState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(id = R.string.headline),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = stringResource(id = R.string.tagline),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun InfoCard() {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(id = R.string.helpful_tips),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = stringResource(id = R.string.tips_body),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SetupScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.setup_title),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = stringResource(id = R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = stringResource(id = R.string.setup_step_one), style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(id = R.string.setup_step_two), style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(id = R.string.setup_step_three), style = MaterialTheme.typography.titleMedium)
            }
        }

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = stringResource(id = R.string.setup_primary_action),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun isValidUrl(url: String): Boolean {
    return url.isNotBlank() && Patterns.WEB_URL.matcher(url).matches()
}

private fun parseUrls(text: String): List<String> {
    val unique = LinkedHashSet<String>()
    text.split("\r\n", "\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { unique.add(it) }
    return unique.toList()
}

private fun getAppVersion(context: Context): String {
    return try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: ""
    } catch (_: Exception) {
        ""
    }
}

private suspend fun fetchMetadataIfNeeded(
    url: String,
    metadataState: MutableMap<String, MetaState>
) {
    val current = metadataState[url]
    if (current?.status == MetaStatus.Loading || current?.status == MetaStatus.Loaded) return

    metadataState[url] = MetaState(status = MetaStatus.Loading)
    val result = withContext(Dispatchers.IO) { runNewPipeInfo(url) }
    metadataState[url] = if (result.success) {
        MetaState(
            status = MetaStatus.Loaded,
            title = result.title,
            uploader = result.uploader,
            thumbnailUrl = result.thumbnailUrl,
            avatarUrl = result.avatarUrl,
            message = ""
        )
    } else {
        MetaState(status = MetaStatus.Error, message = result.message)
    }
}

private suspend fun downloadAll(
    context: Context,
    urls: List<String>,
    qualityOption: QualityOption,
    selectedFolderUri: Uri?,
    cookieHeader: String,
    metadataState: MutableMap<String, MetaState>,
    downloadState: MutableMap<String, DownloadState>
) {
    urls.forEach { url ->
        if (isValidUrl(url)) {
            fetchMetadataIfNeeded(url, metadataState)
            downloadSingle(
                context = context,
                url = url,
                qualityOption = qualityOption,
                selectedFolderUri = selectedFolderUri,
                cookieHeader = cookieHeader,
                downloadState = downloadState
            )
            delay(200)
        } else {
            downloadState[url] = DownloadState(
                status = DownloadStatus.Error,
                message = context.getString(R.string.message_invalid)
            )
        }
    }
}

private suspend fun downloadSingle(
    context: Context,
    url: String,
    qualityOption: QualityOption,
    selectedFolderUri: Uri?,
    cookieHeader: String,
    downloadState: MutableMap<String, DownloadState>
) {
    if (!isValidUrl(url)) {
        downloadState[url] = DownloadState(
            status = DownloadStatus.Error,
            message = context.getString(R.string.message_invalid)
        )
        return
    }

    downloadState[url] = DownloadState(status = DownloadStatus.Downloading, progress = 0.02f)

    val tempDir = resolveTempDir(context)
    val uiScope = CoroutineScope(Dispatchers.Main)
    val result = withContext(Dispatchers.IO) {
        runNewPipeDownload(url, tempDir, cookieHeader) { progress, message ->
            uiScope.launch {
                downloadState[url] = downloadState[url]?.copy(
                    status = DownloadStatus.Downloading,
                    progress = progress.coerceIn(0f, 1f),
                    message = message
                ) ?: DownloadState(status = DownloadStatus.Downloading, progress = progress, message = message)
            }
        }
    }

    if (!result.success) {
        downloadState[url] = DownloadState(
            status = DownloadStatus.Error,
            progress = 0f,
            message = result.message.ifBlank { context.getString(R.string.message_download_failed) }
        )
        return
    }

    val inputFile = File(result.filePath)
    if (!inputFile.exists()) {
        downloadState[url] = DownloadState(
            status = DownloadStatus.Error,
            progress = 0f,
            message = context.getString(R.string.message_download_failed)
        )
        return
    }

    downloadState[url] = downloadState[url]?.copy(
        status = DownloadStatus.Converting,
        progress = 0f,
        message = context.getString(R.string.message_converting)
    ) ?: DownloadState(status = DownloadStatus.Converting, progress = 0f)

    val conversionResult = withContext(Dispatchers.IO) {
        val baseName = sanitizeFileName(result.title.ifBlank { inputFile.nameWithoutExtension })
        val mp3File = File(tempDir, "$baseName.mp3")
        val ffmpegCommand = buildFfmpegCommand(inputFile.absolutePath, mp3File.absolutePath, qualityOption)
        val session = FFmpegKit.execute(ffmpegCommand)

        if (!ReturnCode.isSuccess(session.returnCode)) {
            cleanupTempFiles(inputFile, mp3File)
            return@withContext Pair<DownloadResult?, String?>(null, null)
        }

        val finalFileName = if (mp3File.exists()) mp3File.name else "$baseName.mp3"
        val outputPath = if (selectedFolderUri != null) {
            copyToSaf(context, selectedFolderUri, mp3File, finalFileName)
        } else {
            ""
        }

        val resolvedOutput = if (outputPath.isNotBlank()) {
            outputPath
        } else {
            saveToDefaultMusic(context, mp3File, finalFileName)
        }

        cleanupTempFiles(inputFile, mp3File)
        Pair(null, if (resolvedOutput.isNotBlank()) resolvedOutput else finalFileName)
    }

    val resolvedOutput = conversionResult.second
    if (resolvedOutput.isNullOrBlank()) {
        downloadState[url] = DownloadState(
            status = DownloadStatus.Error,
            message = context.getString(R.string.message_convert_failed)
        )
        return
    }

    val outputName = File(resolvedOutput).name
    downloadState[url] = DownloadState(
        status = DownloadStatus.Done,
        progress = 1f,
        message = context.getString(R.string.message_saved, outputName),
        outputFileName = outputName
    )
}

private fun buildFfmpegCommand(inputPath: String, outputPath: String, qualityOption: QualityOption): String {
    return "-y -i \"$inputPath\" -vn -codec:a libmp3lame ${qualityOption.ffmpegArgs} \"$outputPath\""
}

private fun cleanupTempFiles(vararg files: File) {
    files.forEach { file ->
        if (file.exists()) {
            file.delete()
        }
    }
}

private fun resolveTempDir(context: Context): File {
    val tempDir = File(context.cacheDir, "downloads")
    if (!tempDir.exists()) {
        tempDir.mkdirs()
    }
    return tempDir
}

private fun saveToDefaultMusic(context: Context, source: File, fileName: String): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveToMediaStore(context, source, fileName)
    } else {
        saveToLegacyMusic(source, fileName)
    }
}

private fun saveToMediaStore(context: Context, source: File, fileName: String): String {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/AudioGrab")
    }
    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        ?: return ""

    resolver.openOutputStream(uri)?.use { output ->
        FileInputStream(source).use { input ->
            input.copyTo(output)
        }
    } ?: return ""

    return fileName
}

private fun saveToLegacyMusic(source: File, fileName: String): String {
    val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    val outputDir = File(baseDir, "AudioGrab")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }
    val target = uniqueFile(outputDir, fileName)
    FileInputStream(source).use { input ->
        FileOutputStream(target).use { output ->
            input.copyTo(output)
        }
    }
    return target.absolutePath
}

private fun copyToSaf(context: Context, folderUri: Uri, source: File, fileName: String): String {
    val contentResolver = context.contentResolver
    val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return ""
    val target = createSafFile(folder, fileName)
    val outputStream = contentResolver.openOutputStream(target.uri) ?: return ""
    FileInputStream(source).use { input ->
        outputStream.use { output ->
            input.copyTo(output)
        }
    }
    return target.name ?: fileName
}

private fun createSafFile(folder: DocumentFile, fileName: String): DocumentFile {
    var candidateName = fileName
    var candidate = folder.findFile(candidateName)
    var index = 1
    while (candidate != null) {
        candidateName = appendSuffix(fileName, index)
        candidate = folder.findFile(candidateName)
        index += 1
    }
    return folder.createFile("audio/mpeg", candidateName) ?: folder
}

private fun appendSuffix(fileName: String, index: Int): String {
    val dotIndex = fileName.lastIndexOf('.')
    return if (dotIndex > 0) {
        val name = fileName.substring(0, dotIndex)
        val ext = fileName.substring(dotIndex)
        "$name ($index)$ext"
    } else {
        "$fileName ($index)"
    }
}

private fun uniqueFile(directory: File, fileName: String): File {
    var candidate = File(directory, fileName)
    var index = 1
    while (candidate.exists()) {
        candidate = File(directory, appendSuffix(fileName, index))
        index += 1
    }
    return candidate
}

private fun resolveOutputDir(context: Context): File {
    val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
    val outputDir = File(baseDir, "AudioGrab")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }
    return outputDir
}

private fun sanitizeFileName(raw: String): String {
    val cleaned = raw.replace("/", "-").replace("\\", "-").trim()
    return if (cleaned.isBlank()) {
        "audio_${System.currentTimeMillis()}"
    } else {
        cleaned
    }
}

private fun persistFolderPermission(context: Context, uri: Uri) {
    val resolver = context.contentResolver
    val flags = IntentFlags.readWrite
    try {
        resolver.takePersistableUriPermission(uri, flags)
    } catch (_: SecurityException) {
    }
}

private object IntentFlags {
    const val readWrite: Int =
        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
}

private fun runNewPipeDownload(
    url: String,
    outputDir: File,
    cookieHeader: String,
    onProgress: (Float, String) -> Unit
): DownloadResult {
    return try {
        val info = getStreamInfo(url)
        val selected = pickBestDownloadStream(info)
            ?: return DownloadResult(
                success = false,
                message = "No downloadable stream found",
                filePath = "",
                title = info.name
            )

        val extension = selected.suffix.ifBlank { "m4a" }
        val baseName = sanitizeFileName(info.name)
        val targetFile = File(outputDir, "$baseName.$extension")
        downloadToFile(selected.url, targetFile, onProgress, url, cookieHeader)
        DownloadResult(
            success = true,
            message = if (selected.kind == "video") {
                "Downloaded video stream for audio"
            } else {
                "Download complete"
            },
            filePath = targetFile.absolutePath,
            title = info.name
        )
    } catch (exception: Exception) {
        DownloadResult(
            success = false,
            message = exception.message.orEmpty(),
            filePath = "",
            title = ""
        )
    }
}

private fun runNewPipeInfo(url: String): MetaResult {
    return try {
        val info = getStreamInfo(url)
        val thumbnail = pickBestImageUrl(info.thumbnails)
        val avatar = pickBestImageUrl(info.uploaderAvatars)
        MetaResult(
            success = true,
            title = info.name ?: "",
            uploader = info.uploaderName ?: "",
            thumbnailUrl = thumbnail,
            avatarUrl = if (avatar.isNotBlank()) avatar else thumbnail,
            message = "ok"
        )
    } catch (exception: Exception) {
        MetaResult(
            success = false,
            title = "",
            uploader = "",
            thumbnailUrl = "",
            avatarUrl = "",
            message = exception.message.orEmpty()
        )
    }
}

private fun ensureNewPipeInitialized() {
    try {
        NewPipe.init(OkHttpDownloader())
    } catch (_: Exception) {
    }
}

private fun getStreamInfo(url: String): StreamInfo {
    val service = NewPipe.getServiceByUrl(url)
    return StreamInfo.getInfo(service, url)
}

private fun pickBestAudioStream(info: StreamInfo): AudioStream? {
    val streams = info.audioStreams ?: return null
    return streams.maxByOrNull { it.averageBitrate }
        ?: streams.firstOrNull()
}

private fun pickBestDownloadStream(info: StreamInfo): SelectedStream? {
    val audioStream = pickBestAudioStream(info)
    if (audioStream != null) {
        val url = resolveStreamUrl(audioStream)
        if (!url.isNullOrBlank()) {
            val suffix = audioStream.format?.suffix.orEmpty()
            return SelectedStream(url = url, suffix = suffix, kind = "audio")
        }
    }

    val videoStream = pickBestVideoStream(info)
    if (videoStream != null) {
        val url = resolveStreamUrl(videoStream)
        if (!url.isNullOrBlank()) {
            val suffix = videoStream.format?.suffix.orEmpty()
            return SelectedStream(url = url, suffix = suffix, kind = "video")
        }
    }

    return null
}

private fun pickBestVideoStream(info: StreamInfo): VideoStream? {
    val streams = info.videoStreams?.filter { !it.isVideoOnly } ?: return null
    return streams.maxByOrNull { it.height }
        ?: streams.firstOrNull()
}

private fun resolveStreamUrl(stream: Stream): String? {
    return if (stream.isUrl) {
        stream.content
    } else {
        stream.url
    }
}

private fun pickBestImageUrl(images: List<Image>?): String {
    if (images.isNullOrEmpty()) return ""
    return images.maxByOrNull { image ->
        val width = image.width.takeIf { it > 0 } ?: 0
        val height = image.height.takeIf { it > 0 } ?: 0
        width.coerceAtLeast(height)
    }?.url.orEmpty()
}

private fun downloadToFile(
    url: String,
    target: File,
    onProgress: (Float, String) -> Unit,
    referer: String,
    cookieHeader: String
) {
    val normalizedReferer = buildReferer(referer)
    val origin = buildOrigin(normalizedReferer)
    val headRequest = baseRequestBuilder(
        url = url,
        normalizedReferer = normalizedReferer,
        origin = origin,
        cookieHeader = cookieHeader
    ).head().build()

    val totalLength = httpClient.newCall(headRequest).execute().use { response ->
        if (response.isSuccessful) {
            response.header("Content-Length")?.toLongOrNull() ?: -1L
        } else {
            -1L
        }
    }

    if (totalLength <= 0L) {
        singleDownloadToFile(url, target, onProgress, normalizedReferer, origin, cookieHeader)
        return
    }

    val threadCount = 6
    val chunkSize = (totalLength + threadCount - 1) / threadCount
    val parts = (0 until threadCount).map { index ->
        val start = index * chunkSize
        val end = (start + chunkSize - 1).coerceAtMost(totalLength - 1)
        start to end
    }

    val partFiles = parts.mapIndexed { index, _ ->
        File(target.parentFile ?: target.absoluteFile.parentFile, "${target.nameWithoutExtension}.part$index")
    }
    val downloaded = AtomicLong(0L)
    val errors = AtomicReference<Exception?>(null)
    val rangeUnsupported = AtomicReference<Boolean>(false)
    val latch = CountDownLatch(parts.size)

    parts.forEachIndexed { index, (start, end) ->
        thread(name = "download-part-$index") {
            try {
                var attempt = 0
                while (attempt < 2) {
                    attempt += 1
                    try {
                        val range = "bytes=$start-$end"
                        val request = baseRequestBuilder(
                            url = url,
                            normalizedReferer = normalizedReferer,
                            origin = origin,
                            cookieHeader = cookieHeader
                        ).addHeader("Range", range).build()

                        httpClient.newCall(request).execute().use { response ->
                            if (response.code == 416) {
                                rangeUnsupported.set(true)
                                return@use
                            }
                            if (!response.isSuccessful) {
                                throw IllegalStateException("Part $index failed: ${response.code}")
                            }
                            val body = response.body ?: throw IllegalStateException("Empty response")
                            val buffer = ByteArray(16 * 1024)
                            var partDownloaded = 0L
                            body.byteStream().use { input ->
                                FileOutputStream(partFiles[index]).use { output ->
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read <= 0) break
                                        output.write(buffer, 0, read)
                                        partDownloaded += read
                                        val totalDownloaded = downloaded.addAndGet(read.toLong())
                                        val progress = totalDownloaded.toFloat() / totalLength
                                        onProgress(
                                            progress.coerceIn(0f, 1f),
                                            "Downloading ${formatBytes(totalDownloaded)} / ${formatBytes(totalLength)}"
                                        )
                                    }
                                }
                            }
                        }
                        break
                    } catch (exception: Exception) {
                        if (attempt >= 2) throw exception
                    }
                }
            } catch (exception: Exception) {
                errors.compareAndSet(null, exception)
            } finally {
                latch.countDown()
            }
        }
    }

    latch.await()
    if (rangeUnsupported.get() == true) {
        partFiles.forEach { it.delete() }
        singleDownloadToFile(url, target, onProgress, normalizedReferer, origin, cookieHeader)
        return
    }

    errors.get()?.let { error ->
        partFiles.forEach { part ->
            if (part.exists()) {
                part.delete()
            }
        }
        throw error
    }

    FileOutputStream(target).use { output ->
        partFiles.forEach { part ->
            if (part.exists()) {
                FileInputStream(part).use { input -> input.copyTo(output) }
                part.delete()
            } else {
                throw IllegalStateException("Missing download part")
            }
        }
    }
    onProgress(1f, "Merged & complete!")
}

private fun singleDownloadToFile(
    url: String,
    target: File,
    onProgress: (Float, String) -> Unit,
    normalizedReferer: String,
    origin: String,
    cookieHeader: String
) {
    val rangedRequest = baseRequestBuilder(
        url = url,
        normalizedReferer = normalizedReferer,
        origin = origin,
        cookieHeader = cookieHeader
    ).addHeader("Range", "bytes=0-").build()

    httpClient.newCall(rangedRequest).execute().use { response ->
        if (!response.isSuccessful) {
            if (response.code == 416) {
                return@use
            }
            val errorBody = response.body?.string()?.take(200).orEmpty()
            val details = if (errorBody.isNotBlank()) " - $errorBody" else ""
            throw IllegalStateException("Download failed: ${response.code}$details")
        }
        val body = response.body ?: throw IllegalStateException("Empty response")
        val total = body.contentLength().coerceAtLeast(0L)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var downloaded = 0L
        body.byteStream().use { input ->
            FileOutputStream(target).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                    val message = if (total > 0) {
                        "Downloading ${formatBytes(downloaded)} / ${formatBytes(total)}"
                    } else {
                        "Downloading..."
                    }
                    onProgress(progress.coerceIn(0f, 1f), message)
                }
            }
        }
    }

    if (target.exists() && target.length() > 0L) {
        return
    }

    val fallbackRequest = baseRequestBuilder(
        url = url,
        normalizedReferer = normalizedReferer,
        origin = origin,
        cookieHeader = cookieHeader
    ).build()

    httpClient.newCall(fallbackRequest).execute().use { response ->
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()?.take(200).orEmpty()
            val details = if (errorBody.isNotBlank()) " - $errorBody" else ""
            throw IllegalStateException("Download failed: ${response.code}$details")
        }
        val body = response.body ?: throw IllegalStateException("Empty response")
        val total = body.contentLength().coerceAtLeast(0L)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var downloaded = 0L
        body.byteStream().use { input ->
            FileOutputStream(target).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                    val message = if (total > 0) {
                        "Downloading ${formatBytes(downloaded)} / ${formatBytes(total)}"
                    } else {
                        "Downloading..."
                    }
                    onProgress(progress.coerceIn(0f, 1f), message)
                }
            }
        }
    }
}

private fun baseRequestBuilder(
    url: String,
    normalizedReferer: String,
    origin: String,
    cookieHeader: String
): OkHttpRequest.Builder {
    val builder = OkHttpRequest.Builder()
        .url(url)
        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36")
        .addHeader("X-YouTube-Client-Name", "1")
        .addHeader("X-YouTube-Client-Version", "2.20260228.08.00")
        .addHeader("Accept", "*/*")
        .addHeader("Accept-Language", "en-US,en;q=0.9")
        .addHeader("Accept-Encoding", "gzip, deflate, br, identity")
        .addHeader("Referer", normalizedReferer)
        .addHeader("Origin", origin)
        .addHeader("Connection", "keep-alive")
        .addHeader("Cache-Control", "no-cache")
    if (cookieHeader.isNotBlank()) {
        builder.addHeader("Cookie", cookieHeader.trim())
    }
    return builder
}


private fun formatBytes(bytes: Long): String {
    val unit = 1024.0
    if (bytes < unit) return "$bytes B"
    val exp = (kotlin.math.ln(bytes.toDouble()) / kotlin.math.ln(unit)).toInt()
    val prefix = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp.toDouble()), prefix)
}

private fun buildReferer(rawUrl: String): String {
    return try {
        val uri = URI(rawUrl)
        val host = uri.host.orEmpty()
        if (host.contains("youtu.be") || host.contains("youtube.com")) {
            val videoId = extractYouTubeId(uri)
            if (videoId.isNotBlank()) {
                "https://www.youtube.com/watch?v=$videoId"
            } else {
                "https://www.youtube.com"
            }
        } else {
            rawUrl
        }
    } catch (_: Exception) {
        rawUrl
    }
}

private fun extractYouTubeId(uri: URI): String {
    val host = uri.host.orEmpty()
    return if (host.contains("youtu.be")) {
        uri.path?.trim('/') ?: ""
    } else {
        val query = uri.query.orEmpty()
        query.split('&')
            .mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2 && parts[0] == "v") parts[1] else null
            }
            .firstOrNull()
            .orEmpty()
    }
}

private fun buildOrigin(referer: String): String {
    return try {
        val uri = URI(referer)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: ""
        if (host.isBlank()) {
            "https://www.youtube.com"
        } else {
            "$scheme://$host"
        }
    } catch (_: Exception) {
        "https://www.youtube.com"
    }
}

private class OkHttpDownloader : Downloader() {
    override fun execute(request: NewPipeRequest): Response {
        val builder = OkHttpRequest.Builder().url(request.url())
        request.headers().forEach { (key, values) ->
            values.forEach { value -> builder.addHeader(key, value) }
        }
        if (globalCookieHeader.isNotBlank()) {
            builder.addHeader("Cookie", globalCookieHeader.trim())
        }
        val data = request.dataToSend()
        if (data != null) {
            builder.method(request.httpMethod(), data.toRequestBody(null))
        }
        val response = httpClient.newCall(builder.build()).execute()
        val body = response.body?.string().orEmpty()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            body,
            response.request.url.toString()
        )
    }
}
