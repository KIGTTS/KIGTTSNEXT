@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.onboarding

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.SettingsCategory
import com.kigtts.next.theme.ThemeIcon
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.theme.ThemeText
import com.kigtts.next.theme.themeFill
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.MotionTextButton
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.components.expressivePillShapes
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.SafeModeBrand
import com.kigtts.next.ui.settings.*
import kotlinx.coroutines.launch

@Composable
internal fun OnboardingScreen(progress: FirstRunStore, settings: ThemeSettings, reducedMotion: Boolean,
    changeTheme: (ThemeSettings) -> Unit, complete: (Boolean, Boolean) -> Unit, exit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var document by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var accepted by rememberSaveable { mutableStateOf(progress.agreementAccepted()) }
    val pageCount = FirstRunStep.COMPLETE.ordinal
    val pager = rememberPagerState(initialPage = progress.step().ordinal) { pageCount }
    val step = FirstRunStep.entries[pager.settledPage]
    val motion = !reducedMotion && ValueAnimator.areAnimatorsEnabled()
    val (flowerTilt, characterTilt) = rememberOnboardingTilt(motion)

    LaunchedEffect(pager, progress) {
        snapshotFlow { pager.settledPage }.collect { progress.visit(FirstRunStep.entries[it]) }
    }

    fun move(target: Int) {
        scope.launch {
            if (motion) pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
    }

    fun back() {
        val current = pager.settledPage
        if (current == FirstRunStep.WELCOME.ordinal) exit() else move(current - 1)
    }

    fun finish(tryQuickText: Boolean) {
        if (pager.settledPage != FirstRunStep.PERMISSIONS.ordinal || !accepted) return
        progress.visit(FirstRunStep.PERMISSIONS)
        complete(tryQuickText, accepted)
    }

    BackHandler(document == null, onBack = ::back)
    Box(Modifier.fillMaxSize().clipToBounds().themeFill { it.background }) {
        OnboardingFlowers(reducedMotion, flowerTilt, welcome = step == FirstRunStep.WELCOME)
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SafeModeBrand(active = false, modifier = Modifier.testTag("onboarding-brand"))
                LinearProgressIndicator(
                    progress = { (pager.currentPage + pager.currentPageOffsetFraction + 1f) / pageCount },
                    modifier = Modifier.weight(1f),
                )
                if (step != FirstRunStep.WELCOME) {
                    IconButton(onClick = feedbackClick(::back), modifier = Modifier.testTag("onboarding-back")) {
                        ThemeIcon(
                            Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("action.back"),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
            HorizontalPager(
                pager, Modifier.weight(1f).fillMaxWidth().testTag("onboarding-pages"),
                key = { FirstRunStep.entries[it].name },
            ) { index ->
                val page = FirstRunStep.entries[index]
                if (page == FirstRunStep.WELCOME) {
                    OnboardingWelcomePage(step == FirstRunStep.WELCOME, !motion, characterTilt)
                    return@HorizontalPager
                }
                Column(
                    Modifier.fillMaxSize().testTag("onboarding-${page.page}")
                        .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OnboardingPageCopy(page)
                    when (page) {
                        FirstRunStep.APPEARANCE -> {
                            ThemeColorSettings(settings, changeTheme)
                            AppearanceDisplaySettings(settings, changeTheme)
                        }
                        FirstRunStep.FEATURES -> OnboardingRecommendations()
                        FirstRunStep.PERMISSIONS -> {
                            OnboardingPermissions()
                            SettingsGroup(uiText("onboarding.legal_title")) {
                                SettingItem(uiText("settings.category.privacy_policy"), "", Icons.Default.Security,
                                    SettingPosition.FIRST, onClick = { document = SettingsCategory.PRIVACY_POLICY })
                                SettingItem(uiText("settings.category.user_agreement"), "", Icons.Default.Description,
                                    SettingPosition.LAST, onClick = { document = SettingsCategory.USER_AGREEMENT })
                            }
                            Row(
                                Modifier.fillMaxWidth().testTag("onboarding-consent")
                                    .toggleable(value = accepted, role = Role.Checkbox, onValueChange = { accepted = it })
                                    .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Checkbox(checked = accepted, onCheckedChange = null)
                                ThemeText(uiText("onboarding.legal_confirm"), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        FirstRunStep.WELCOME, FirstRunStep.COMPLETE -> Unit
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = feedbackClick {
                        when (val current = FirstRunStep.entries[pager.settledPage]) {
                            FirstRunStep.PERMISSIONS -> finish(false)
                            else -> move(current.ordinal + 1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("onboarding-next"),
                    enabled = !pager.isScrollInProgress && (step != FirstRunStep.PERMISSIONS || accepted),
                    shapes = expressivePillShapes(),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(uiText(when (step) {
                        FirstRunStep.PERMISSIONS -> "onboarding.start"
                        else -> "onboarding.next"
                    }))
                }
                if (step == FirstRunStep.PERMISSIONS) {
                    MotionTextButton(feedbackClick { finish(true) }, enabled = accepted && !pager.isScrollInProgress) {
                        Text(uiText("onboarding.try_quick_text"))
                    }
                }
            }
        }
    }
    document?.let { category ->
        MotionAlertDialog(onDismissRequest = { document = null }, title = {
            Text(uiText(if (category == SettingsCategory.USER_AGREEMENT) "settings.category.user_agreement" else "settings.category.privacy_policy"))
        }, text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                AboutDocumentText(aboutLegalDocuments.getValue(category))
            }
        }, confirmButton = {
            MotionTextButton(dialogFeedbackClick { document = null }) { Text(uiText("action.close")) }
        })
    }
}
private const val WELCOME_ART_ASPECT = 1216f / 1024f

@Composable
private fun OnboardingWelcomePage(active: Boolean, reducedMotion: Boolean, tilt: State<Offset>) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("onboarding-welcome")) {
        val pageHeight = maxHeight
        val contentWidth = (maxWidth - 44.dp).coerceAtLeast(1.dp)
        val artWidth = minOf(390.dp, contentWidth * .92f, pageHeight * .64f * WELCOME_ART_ASPECT)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = pageHeight)
                .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OnboardingPageCopy(FirstRunStep.WELCOME, Modifier.testTag("onboarding-welcome-copy"))
            Spacer(Modifier.weight(1f))
            Box(Modifier.align(Alignment.End).widthIn(max = artWidth).fillMaxWidth()
                .aspectRatio(WELCOME_ART_ASPECT).testTag("onboarding-welcome-art")) {
                OnboardingWelcomeArt(Modifier.matchParentSize(), active = active, reducedMotion = reducedMotion, tilt = tilt)
            }
        }
    }
}

@Composable
private fun OnboardingPageCopy(page: FirstRunStep, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeText(
            uiText("onboarding.${page.page}.title"), Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Start,
        )
        ThemeText(
            uiText("onboarding.${page.page}.body"), Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun OnboardingRecommendations() {
    SettingsGroup(uiText("onboarding.recommendations")) {
        SettingItem(uiText("onboarding.subtitle_feature"), uiText("onboarding.subtitle_detail"), Icons.Default.Subtitles, SettingPosition.FIRST)
        SettingItem(uiText("onboarding.quick_feature"), uiText("onboarding.quick_detail"), Icons.AutoMirrored.Filled.TextSnippet, SettingPosition.MIDDLE)
        SettingItem(uiText("onboarding.listening_feature"), uiText("onboarding.listening_detail"), Icons.Default.Hearing, SettingPosition.MIDDLE)
        SettingItem(uiText("nav.camera"), uiText("onboarding.camera_detail"), Icons.Default.PhotoCamera, SettingPosition.LAST)
    }
}
