package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ChoiceButtonGroup
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.NfcShareControl
import kotlinx.coroutines.launch

/** Two swipeable steps keep the connection code separate from the browser collection code. */
@Composable
internal fun FaceWifiShareContent(hotspot: HotspotDetails?, url: String?, personal: Boolean, createHotspot: () -> Unit) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    fun page(index: Int) { scope.launch { pager.animateScrollToPage(index) } }
    ChoiceButtonGroup("扫码步骤", listOf(0, 1), pager.currentPage,
        { if (it == 0) "1 · 连接网络" else "2 · 扫码领取" }, { page(it) })
    HorizontalPager(pager, Modifier.fillMaxWidth().height(344.dp).testTag("face-wifi-pager"),
        beyondViewportPageCount = 1, verticalAlignment = Alignment.Top) { index ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (index == 0) {
                Text("先让对方连上你的网络", style = MaterialTheme.typography.titleMedium)
                if (hotspot != null) {
                    FaceQrCode(hotspot.qr(), "连接热点二维码", 220.dp)
                    Text(hotspot.ssid, style = MaterialTheme.typography.titleSmall)
                    Text("密码：${hotspot.password}", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Box(Modifier.height(220.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.WifiTethering, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("不在同一 Wi-Fi？创建热点让对方扫码连接。", style = MaterialTheme.typography.bodyMedium)
                            FilledTonalButton(feedbackClick(createHotspot)) { Text("创建临时热点") }
                        }
                    }
                    Text("已经连上同一 Wi-Fi，可以直接进行下一步。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(if (personal) "扫码查看名片" else "扫码领取照片", style = MaterialTheme.typography.titleMedium)
                if (url != null) FaceQrCode(url, if (personal) "领取名片二维码" else "领取照片二维码", 220.dp)
                else Box(Modifier.height(236.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text("等待 Wi-Fi 连接", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(if (personal) "对方可在浏览器中查看你的名片和联系方式。" else "对方可在浏览器中预览、选择并保存照片。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("结束分享后，领取码即失效。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val payload = remember(pager.settledPage, hotspot, url) {
        runCatching {
            if (pager.settledPage == 0) hotspot?.let(::faceHotspotNdef) else url?.let(::faceCollectionNdef)
        }.getOrNull()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${pager.currentPage + 1} / 2", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NfcShareControl(payload, compact = true, active = !pager.isScrollInProgress)
      }
        if (pager.currentPage == 0) Button(feedbackClick { page(1) }) { Text("已连接，下一步") }
        else TextButton(feedbackClick { page(0) }) { Text("返回连接网络") }
    }
}
