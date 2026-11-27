package com.kigtts.next.state

internal val defaultQuickTextPresetIds = setOf("general", "photo")

internal data class QuickPresetPhrase(val title: String, val text: String)

internal data class QuickTextPreset(
    val id: String,
    val name: String,
    val icon: CategoryIcon,
    val phrases: List<QuickPresetPhrase>,
)

private data class PresetDefinition(val id: String, val icon: String, val entries: List<String>)

private val presets = listOf(
    PresetDefinition("general", "chat", listOf("thanks", "cant_talk", "wait", "make_room", "loud", "photos", "contacts", "bye")),
    PresetDefinition("urgent", "warning", listOf("remove_head", "get_out", "stuck", "heatstroke", "medical", "clear_way", "lost_friend", "dont_pull")),
    PresetDefinition("visitors", "emoji_people", listOf("human", "ready", "no_photo", "secret", "kig", "no_touch", "keep_head_on")),
    PresetDefinition("event", "groups", listOf("go_play", "take_break", "go_back", "this_way", "waiting", "starting")),
    PresetDefinition("photo", "photo_camera", listOf("pose", "another", "done", "cute", "accurate")),
    PresetDefinition("body", "favorite", listOf("warm", "cold", "tired", "unwell", "bathroom", "change_head")),
    PresetDefinition("support", "support_agent", listOf("water", "hair", "clothes", "gear", "help")),
    PresetDefinition("intro", "person_add", listOf("me", "character", "account")),
    PresetDefinition("notice", "campaign", listOf("group_photo", "resting", "shy")),
)

internal fun localizedQuickTextPresets(copy: (String) -> String): List<QuickTextPreset> = presets.map { preset ->
    val key = "quick_presets.${preset.id}"
    QuickTextPreset(preset.id, copy("$key.name"), CategoryIcon.valueOf("symbol:${preset.icon}"), preset.entries.map { entry ->
        QuickPresetPhrase(copy("$key.$entry.title"), copy("$key.$entry.text"))
    })
}
