package com.kigtts.next.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kigtts.next.ui.components.ChoiceButtonGroup

@Composable
internal fun <T> SettingChoiceGroup(title:String,options:List<T>,value:T,label:(T)->String,onSelect:(T)->Unit,
    modifier:Modifier=Modifier) = ChoiceButtonGroup(title,options,value,label,onSelect,modifier)