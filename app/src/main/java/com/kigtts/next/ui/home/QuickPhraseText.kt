package com.kigtts.next.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.QuickPhrase
import com.kigtts.next.text.formattedText

/** Normal, editing, drag and container previews share the same title/content hierarchy. */
@Composable
internal fun QuickPhraseText(phrase: QuickPhrase, modifier: Modifier = Modifier) {
    val hasTitle = phrase.title.isNotBlank()
    val rich = phrase.richText
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
        Text(formattedText(if (hasTitle) phrase.title else phrase.text, if (!hasTitle && rich) phrase.formats else emptyList()), Modifier.testTag("phrase-main-" + phrase.id),
            style = MaterialTheme.typography.bodyLarge, maxLines = if (hasTitle) 1 else 2,
            overflow = TextOverflow.Ellipsis)
        if (hasTitle) Text(formattedText(phrase.text, if (rich) phrase.formats else emptyList()), Modifier.testTag("phrase-content-" + phrase.id), maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = .76f))
    }
}
