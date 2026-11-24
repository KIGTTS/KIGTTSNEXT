@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.onboarding

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kigtts.next.R
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
import com.kigtts.next.ui.settings.*
import kotlinx.coroutines.launch

@Composable
internal fun OnboardingScreen(progress: FirstRunStore, settings: ThemeSettings, reducedMotion: Boolean,
    changeTheme: (ThemeSettings) -> Unit, complete: (Boolean) -> Unit, exit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var document by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var accepted by remember(progress) { mutableStateOf(progress.agreementAccepted()) }
    val pageCount = FirstRunStep.COMPLETE.ordinal
    val pager = rememberPagerState(initialPage = progress.step().ordinal) {
        if (accepted) pageCount else FirstRunStep.AGREEMENT.ordinal + 1
    }
    val step = FirstRunStep.entries[pager.settledPage]
    val motion = !reducedMotion && ValueAnimator.areAnimatorsEnabled()
    val tilt = rememberOnboardingTilt(motion)

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
        if (pager.settledPage != FirstRunStep.PERMISSIONS.ordinal) return
        progress.visit(FirstRunStep.PERMISSIONS)
        complete(tryQuickText)
    }

    BackHandler(document == null, onBack = ::back)
    Box(Modifier.fillMaxSize().clipToBounds().themeFill { it.background }) {
        OnboardingFlowers(reducedMotion, tilt)
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
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "KIGTTS",
                    modifier = Modifier.size(40.dp).background(
                        colorResource(R.color.launcher_background), RoundedCornerShape(13.dp),
                    ).padding(2.dp),
                )
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
                val welcome = page == FirstRunStep.WELCOME
                Column(
                    Modifier.fillMaxSize().testTag("onboarding-${page.page}")
                        .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, if (welcome) Alignment.CenterVertically else Alignment.Top),
                ) {
                    if (welcome) {
                        OnboardingWelcomeArt(
                            Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 12.dp)
                                .widthIn(max = 390.dp).fillMaxWidth().aspectRatio(1216f / 1024f),
                            active = step == FirstRunStep.WELCOME,
                            reducedMotion = !motion,
                            tilt = tilt,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val align = if (welcome) TextAlign.Center else TextAlign.Start
                        ThemeText(
                            uiText("onboarding.${page.page}.title"), Modifier.fillMaxWidth().semantics { heading() },
                            style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground,
                            textAlign = align,
                        )
                        ThemeText(
                            uiText("onboarding.${page.page}.body"), Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = align,
                        )
                    }
                    when (page) {
                        FirstRunStep.AGREEMENT -> SettingItem(
                            uiText("settings.category.user_agreement"), "", Icons.Default.Description, SettingPosition.SINGLE,
                            onClick = { document = SettingsCategory.USER_AGREEMENT },
                        )
                        FirstRunStep.APPEARANCE -> {
                            ThemeColorSettings(settings, changeTheme)
                            AppearanceDisplaySettings(settings, changeTheme)
                        }
                        FirstRunStep.FEATURES -> OnboardingRecommendations()
                        FirstRunStep.PERMISSIONS -> {
                            SettingItem(
                                uiText("settings.category.privacy_policy"), "", Icons.Default.Security, SettingPosition.SINGLE,
                                onClick = { document = SettingsCategory.PRIVACY_POLICY },
                            )
                            OnboardingPermissions()
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
                            FirstRunStep.AGREEMENT -> {
                                val target = current.ordinal + 1
                                if (accepted) {
                                    move(target)
                                } else {
                                    progress.acceptAgreement()
                                    accepted = true
                                    // New pages become measurable on the next layout pass.
                                    pager.requestScrollToPage(target)
                                }
                            }
                            FirstRunStep.PERMISSIONS -> finish(false)
                            else -> move(current.ordinal + 1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("onboarding-next"),
                    enabled = !pager.isScrollInProgress,
                    shapes = expressivePillShapes(),
                ) {
                    Text(uiText(when (step) {
                        FirstRunStep.AGREEMENT -> "onboarding.accept"
                        FirstRunStep.PERMISSIONS -> "onboarding.start"
                        else -> "onboarding.next"
                    }))
                }
                if (step == FirstRunStep.PERMISSIONS) {
                    MotionTextButton(feedbackClick { finish(true) }, enabled = !pager.isScrollInProgress) {
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
@Composable
private fun OnboardingRecommendations() {
    SettingsGroup(uiText("onboarding.recommendations")) {
        SettingItem(uiText("onboarding.subtitle_feature"), uiText("onboarding.subtitle_detail"), Icons.Default.Subtitles, SettingPosition.FIRST)
        SettingItem(uiText("onboarding.quick_feature"), uiText("onboarding.quick_detail"), Icons.AutoMirrored.Filled.TextSnippet, SettingPosition.MIDDLE)
        SettingItem(uiText("onboarding.listening_feature"), uiText("onboarding.listening_detail"), Icons.Default.Hearing, SettingPosition.MIDDLE)
        SettingItem(uiText("nav.camera"), uiText("onboarding.camera_detail"), Icons.Default.PhotoCamera, SettingPosition.LAST)
    }
}
