package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kigtts.next.media.WebCookies
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.navigation.LocalPageActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.webkit.CookieManager

@Composable
fun WebSettingsScreen() {
    var confirming by remember { mutableStateOf(false) }; var clearing by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    SettingsLayout {
        SettingsGroup("网页数据") {
            SettingItem("清除 Cookies", if (cleared) "已清除登录信息" else "部分网页需要重新登录", Icons.Default.DeleteSweep,
                SettingPosition.SINGLE, Modifier.testTag("clear-web-cookies"), onClick = if (clearing) null else { { confirming = true } })
        }
    }
    if (confirming && LocalPageActive.current) MotionAlertDialog(onDismissRequest = { confirming = false }, title = { Text(uiText("web.cookies_confirm")) },
        text = { Text(uiText("web.cookies_notice")) }, confirmButton = {
            TextButton(dialogFeedbackClick { confirming = false; clearing = true
                WebCookies.clear { cleared = true; clearing = false; scope.launch(Dispatchers.IO) { CookieManager.getInstance().flush() } }
            }, Modifier.testTag("confirm-clear-web-cookies")) { Text(uiText("web.clear")) }
        }, dismissButton = { TextButton(dialogFeedbackClick { confirming = false }, Modifier.testTag("cancel-clear-web-cookies")) { Text(uiText("action.cancel")) } })
}
