package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.navigation.LocalPageActive

/** Retain the outgoing type's values for the transition, and gate its controls immediately. */
@Composable
internal fun PhraseTypeContent(draft:PhraseDraft,model:AppViewModel,enabled:Boolean,leave:()->Unit) {
    AnimatedContent(draft.type,transitionSpec={
        (fadeIn(tween(180,40))+slideInHorizontally(tween(240)) { if(targetState.ordinal>initialState.ordinal)it/10 else -it/10 })
            .togetherWith(fadeOut(tween(120))).using(SizeTransform(clip=false,sizeAnimationSpec={_,_->tween(240)}))
    },label="phrase-type-content") { shownType ->
        var retained by remember(shownType) { mutableStateOf(draft) }
        if(shownType==draft.type)SideEffect { retained=draft }
        val shown=if(shownType==draft.type)draft else retained
        val interactive=enabled&&shownType==draft.type
        CompositionLocalProvider(LocalPageActive provides interactive) {
            Column(Modifier.testTag("phrase-type-content/"+shownType.name),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                PhraseResourceSettings(shown,model,interactive,leave)
            }
        }
    }
}
