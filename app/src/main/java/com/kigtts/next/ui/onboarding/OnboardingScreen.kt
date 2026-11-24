@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kigtts.next.R
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.SettingsCategory
import com.kigtts.next.theme.AppearanceCardSurface
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.theme.ThemeText
import com.kigtts.next.theme.themeFill
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.MotionTextButton
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.components.expressivePillShapes
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.settings.*

@Composable
internal fun OnboardingScreen(step: FirstRunStep, settings: ThemeSettings, reducedMotion: Boolean,
    changeTheme: (ThemeSettings) -> Unit, next: (Boolean) -> Unit, back: () -> Unit, exit: () -> Unit) {
    var document by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    val key = when (step) {
        FirstRunStep.AGREEMENT -> "agreement"
        FirstRunStep.PERMISSIONS -> "permissions"
        FirstRunStep.APPEARANCE -> "appearance"
        FirstRunStep.FEATURES -> "features"
        FirstRunStep.COMPLETE -> return
    }
    val goBack = if (step == FirstRunStep.AGREEMENT || step == FirstRunStep.PERMISSIONS) exit else back
    BackHandler(document == null, onBack = goBack)

    Box(Modifier.fillMaxSize().clipToBounds().themeFill { it.background }.testTag("onboarding-$key")) {
        OnboardingFlowers(reducedMotion)
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(R.drawable.logo_horizontal), "KIGTTS", Modifier.width(102.dp).height(28.dp),
                    tint = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(progress = { (step.ordinal + 1) / 4f }, modifier = Modifier.weight(1f))
            }
            AnimatedContent(step, Modifier.weight(1f).fillMaxWidth(), label = "onboarding-step",
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }) { current ->
                val page = when (current) {
                    FirstRunStep.AGREEMENT -> "agreement"
                    FirstRunStep.PERMISSIONS -> "permissions"
                    FirstRunStep.APPEARANCE -> "appearance"
                    FirstRunStep.FEATURES, FirstRunStep.COMPLETE -> "features"
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 18.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeText(uiText("onboarding.$page.title"), Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                        ThemeText(uiText("onboarding.$page.body"), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (current) {
                        FirstRunStep.AGREEMENT -> SettingsGroup(uiText("onboarding.documents")) {
                            SettingItem(uiText("settings.category.user_agreement"), "", Icons.Default.Description, SettingPosition.FIRST,
                                onClick = { document = SettingsCategory.USER_AGREEMENT })
                            SettingItem(uiText("settings.category.privacy_policy"), "", Icons.Default.Security, SettingPosition.LAST,
                                onClick = { document = SettingsCategory.PRIVACY_POLICY })
                        }
                        FirstRunStep.PERMISSIONS -> OnboardingPermissions()
                        FirstRunStep.APPEARANCE -> {
                            ThemeColorSettings(settings, changeTheme)
                            AppearanceDisplaySettings(settings, changeTheme)
                        }
                        FirstRunStep.FEATURES -> OnboardingRecommendations()
                        FirstRunStep.COMPLETE -> Unit
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                when (step) {
                    FirstRunStep.AGREEMENT -> MotionTextButton(feedbackClick(exit)) { Text(uiText("onboarding.exit")) }
                    FirstRunStep.PERMISSIONS -> Unit
                    FirstRunStep.FEATURES -> MotionTextButton(feedbackClick { next(true) }) { Text(uiText("onboarding.try_quick_text")) }
                    else -> MotionTextButton(feedbackClick(back)) { Text(uiText("action.back")) }
                }
                Button(feedbackClick { next(false) }, modifier = Modifier.weight(1f).testTag("onboarding-next"),
                    shapes = expressivePillShapes()) {
                    Text(uiText(when (step) {
                        FirstRunStep.AGREEMENT -> "onboarding.accept"
                        FirstRunStep.FEATURES -> "onboarding.start"
                        else -> "onboarding.next"
                    }))
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
