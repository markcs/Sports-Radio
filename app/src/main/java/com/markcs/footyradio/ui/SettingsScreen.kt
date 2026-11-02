package com.markcs.footyradio.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import coil3.compose.AsyncImage
import com.markcs.footyradio.Config
import com.markcs.footyradio.PlayerViewModel
import com.markcs.footyradio.R
import com.markcs.footyradio.ui.theme.SubtitleGray
import java.util.Calendar

/** The sub-pages of Settings. [Root] is the grouped list that opens them. */
private enum class SettingsPage { Root, LiveScores, Stations, DataSources, Support, About }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(SettingsPage.Root) }

    // From a sub-page, back and the top-bar arrow return to the Settings list. From the
    // list, they close Settings.
    val goBack: () -> Unit = {
        if (page == SettingsPage.Root) onBack() else page = SettingsPage.Root
    }
    BackHandler { goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleFor(page))) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                SettingsPage.Root -> RootPage(
                    viewModel = viewModel,
                    appVersion = appVersion(context),
                    onNavigate = { page = it }
                )
                SettingsPage.LiveScores -> LiveScoresPage(viewModel)
                SettingsPage.Stations -> StationsPage(viewModel)
                SettingsPage.DataSources -> DataSourcesPage(viewModel)
                SettingsPage.Support -> SupportPage(context)
                SettingsPage.About -> AboutPage(context, appVersion(context))
            }
        }
    }
}

private fun titleFor(page: SettingsPage): Int = when (page) {
    SettingsPage.Root -> R.string.settings_title
    SettingsPage.LiveScores -> R.string.settings_live_scores
    SettingsPage.Stations -> R.string.settings_stations
    SettingsPage.DataSources -> R.string.settings_data_sources
    SettingsPage.Support -> R.string.about_section_support
    SettingsPage.About -> R.string.about_title
}

// --- Pages ---

@Composable
private fun RootPage(
    viewModel: PlayerViewModel,
    appVersion: String,
    onNavigate: (SettingsPage) -> Unit
) {
    val selected = viewModel.selectedSportIds()
    val sportNames = viewModel.availableSports()
        .filter { it.first in selected }
        .map { it.second }
    val liveSummary = if (sportNames.isEmpty()) {
        stringResource(R.string.about_sport_off)
    } else {
        sportNames.joinToString(", ")
    }
    val customCount = viewModel.uiState.customStations.size

    PageColumn {
        SettingsGroup {
            SettingsRow(
                icon = Icons.Filled.SportsSoccer,
                title = stringResource(R.string.settings_live_scores),
                subtitle = liveSummary,
                onClick = { onNavigate(SettingsPage.LiveScores) }
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Radio,
                title = stringResource(R.string.settings_stations),
                subtitle = stringResource(R.string.settings_stations_count, customCount),
                onClick = { onNavigate(SettingsPage.Stations) }
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Link,
                title = stringResource(R.string.settings_data_sources),
                subtitle = stringResource(R.string.settings_data_sources_subtitle),
                onClick = { onNavigate(SettingsPage.DataSources) }
            )
        }
        SettingsGroup {
            SettingsRow(
                icon = Icons.Filled.Help,
                title = stringResource(R.string.about_section_support),
                subtitle = stringResource(R.string.settings_support_subtitle),
                onClick = { onNavigate(SettingsPage.Support) }
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version, appVersion),
                onClick = { onNavigate(SettingsPage.About) }
            )
        }
    }
}

@Composable
private fun LiveScoresPage(viewModel: PlayerViewModel) {
    var selectedSports by remember { mutableStateOf(viewModel.selectedSportIds()) }
    val state = viewModel.uiState

    PageColumn {
        PageHint(stringResource(R.string.settings_live_scores_hint))
        SettingsGroup {
            viewModel.availableSports().forEachIndexed { index, (id, label) ->
                if (index > 0) SettingsDivider()
                val checked = id in selectedSports
                CheckRow(label = label, checked = checked) {
                    viewModel.setSportSelected(id, !checked)
                    selectedSports = if (checked) selectedSports - id else selectedSports + id
                }
            }
        }
        SectionLabel(stringResource(R.string.settings_display))
        SettingsGroup {
            SwitchRow(
                title = stringResource(R.string.about_enable_team_abbreviations),
                subtitle = stringResource(R.string.about_enable_team_abbreviations_desc),
                checked = state.enableTeamAbbreviations,
                onCheckedChange = { viewModel.setTeamAbbreviationsEnabled(it) }
            )
        }
    }
}

@Composable
private fun StationsPage(viewModel: PlayerViewModel) {
    var newStationName by remember { mutableStateOf("") }
    var newStationUrl by remember { mutableStateOf("") }
    var newStationImageUrl by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val stations = viewModel.uiState.customStations

    PageColumn {
        SettingsGroup {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.about_add_station),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                FormField(
                    value = newStationName,
                    onValueChange = { newStationName = it },
                    label = stringResource(R.string.about_station_name)
                )
                Spacer(modifier = Modifier.height(8.dp))
                FormField(
                    value = newStationUrl,
                    onValueChange = { newStationUrl = it },
                    label = stringResource(R.string.about_station_stream_url),
                    isUrl = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                FormField(
                    value = newStationImageUrl,
                    onValueChange = { newStationImageUrl = it },
                    label = stringResource(R.string.about_station_image_url),
                    isUrl = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val canAdd = newStationName.isNotBlank() && newStationUrl.isNotBlank()
                    Button(
                        onClick = {
                            if (canAdd) {
                                viewModel.addCustomStation(newStationName, newStationUrl, newStationImageUrl)
                                newStationName = ""
                                newStationUrl = ""
                                newStationImageUrl = ""
                                keyboard?.hide()
                            }
                        },
                        enabled = canAdd,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.about_add))
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.about_section_custom_stations))
        SettingsGroup {
            if (stations.isEmpty()) {
                Text(
                    text = stringResource(R.string.about_no_custom_stations),
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtitleGray,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                stations.forEachIndexed { index, station ->
                    if (index > 0) SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(station.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                station.streamURL,
                                style = MaterialTheme.typography.bodySmall,
                                color = SubtitleGray,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { viewModel.deleteCustomStation(station.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_delete_station),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataSourcesPage(viewModel: PlayerViewModel) {
    var stationsUrl by remember { mutableStateOf(viewModel.getStationsUrl()) }
    var teamsUrl by remember { mutableStateOf(viewModel.getTeamsUrl()) }

    PageColumn {
        PageHint(stringResource(R.string.settings_data_sources_hint))
        SettingsGroup {
            UrlEditor(
                value = stationsUrl,
                onValueChange = { stationsUrl = it },
                label = stringResource(R.string.about_station_url),
                placeholder = stringResource(R.string.about_station_url_hint),
                onReset = {
                    viewModel.resetStationsUrl()
                    stationsUrl = viewModel.getStationsUrl()
                },
                onSave = { viewModel.updateStationsUrl(stationsUrl) }
            )
        }
        SettingsGroup {
            UrlEditor(
                value = teamsUrl,
                onValueChange = { teamsUrl = it },
                label = stringResource(R.string.about_teams_url),
                placeholder = stringResource(R.string.about_teams_url_hint),
                onReset = {
                    viewModel.resetTeamsUrl()
                    teamsUrl = viewModel.getTeamsUrl()
                },
                onSave = { viewModel.updateTeamsUrl(teamsUrl) }
            )
        }
    }
}

@Composable
private fun SupportPage(context: Context) {
    PageColumn {
        SettingsGroup {
            SettingsRow(
                icon = Icons.Filled.Email,
                title = stringResource(R.string.about_email),
                subtitle = Config.email,
                onClick = { openEmail(context) }
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Feedback,
                title = stringResource(R.string.about_feedback),
                subtitle = stringResource(R.string.about_feedback_subtitle),
                onClick = { openUrl(context, Config.feedbackURL) }
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.about_rate),
                onClick = { openPlayStore(context) }
            )
            SettingsDivider()
            SettingsRow(
                icon = Icons.Filled.Share,
                title = stringResource(R.string.about_share),
                onClick = { shareApp(context) }
            )
        }
    }
}

@Composable
private fun AboutPage(context: Context, appVersion: String) {
    PageColumn {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.app_title)) }
                append(stringResource(R.string.about_header_part1))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.about_header_kotlin)) }
                append(stringResource(R.string.about_header_part2))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.about_header_compose)) }
                append(stringResource(R.string.about_header_part3))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.about_header_building)) }
                append(stringResource(R.string.about_header_part4))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(stringResource(R.string.about_header_customizing)) }
                append(stringResource(R.string.about_header_part5))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SubtitleGray,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        SettingsGroup {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = R.mipmap.ic_launcher_round,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.settings_version, appVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtitleGray
                    )
                }
            }
        }

        SectionLabel(stringResource(R.string.about_section_features))
        SettingsGroup {
            features.forEachIndexed { index, feature ->
                if (index > 0) SettingsDivider()
                SettingsRow(
                    icon = feature.icon,
                    title = stringResource(feature.titleRes),
                    subtitle = stringResource(feature.subtitleRes)
                )
            }
        }

        SectionLabel(stringResource(R.string.about_section_credits))
        SettingsGroup {
            Config.libraries.forEachIndexed { index, lib ->
                if (index > 0) SettingsDivider()
                SettingsRow(
                    icon = Icons.Filled.Code,
                    title = "${lib.owner}/${lib.repo}",
                    onClick = { openUrl(context, "https://github.com/${lib.owner}/${lib.repo}") }
                )
            }
        }

        SectionLabel(stringResource(R.string.about_section_legal))
        SettingsGroup {
            SettingsRow(
                icon = Icons.Filled.Gavel,
                title = stringResource(R.string.about_license),
                subtitle = stringResource(R.string.about_license_subtitle),
                onClick = { openUrl(context, Config.licenseURL) }
            )
        }

        Text(
            text = stringResource(R.string.about_copyright, Calendar.getInstance().get(Calendar.YEAR)),
            style = MaterialTheme.typography.bodySmall,
            color = SubtitleGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        )
    }
}

// --- Building blocks ---

/** Scrollable column with the shared page gutter and spacing between items. */
@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

/** A rounded card that groups related rows. */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    // Indented to start after the icon tile, so the divider follows the text.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 66.dp)
            .height(1.dp)
            .background(SubtitleGray.copy(alpha = 0.15f))
    )
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** A row with an icon, title and optional subtitle. It shows a chevron only when tappable. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtitleGray
                )
            }
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = null,
                tint = SubtitleGray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = SubtitleGray)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = SubtitleGray,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun PageHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = SubtitleGray,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isUrl: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = if (isUrl) {
            KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = SubtitleGray.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun UrlEditor(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    onReset: () -> Unit,
    onSave: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = SubtitleGray.copy(alpha = 0.5f)
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    onReset()
                    keyboard?.hide()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.about_station_url_reset))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onSave()
                    keyboard?.hide()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.about_station_url_save))
            }
        }
    }
}

// --- Actions ---

private fun appVersion(context: Context): String = try {
    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    "${pInfo.versionName} (${PackageInfoCompat.getLongVersionCode(pInfo)})"
} catch (_: Exception) {
    "1.0"
}

private fun openEmail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${Config.email}")
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_email_subject))
    }
    try { context.startActivity(intent) } catch (_: Exception) {}
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {}
}

private fun openPlayStore(context: Context) {
    val packageName = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (_: Exception) {
        openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
    }
}

private fun shareApp(context: Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, Config.shareText)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.about_share_chooser)))
}

// --- Features data ---

private data class Feature(val icon: ImageVector, val titleRes: Int, val subtitleRes: Int)

private val features = listOf(
    Feature(Icons.Filled.Code, R.string.feature_kotlin_title, R.string.feature_kotlin_subtitle),
    Feature(Icons.Filled.DirectionsCar, R.string.feature_auto_title, R.string.feature_auto_subtitle),
    Feature(Icons.Filled.Palette, R.string.feature_ui_title, R.string.feature_ui_subtitle),
    Feature(Icons.Filled.MusicNote, R.string.feature_metadata_title, R.string.feature_metadata_subtitle),
    Feature(Icons.Filled.Lock, R.string.feature_lockscreen_title, R.string.feature_lockscreen_subtitle),
    Feature(Icons.Filled.Radio, R.string.feature_stations_title, R.string.feature_stations_subtitle),
    Feature(Icons.Filled.Verified, R.string.feature_setup_title, R.string.feature_setup_subtitle),
)
