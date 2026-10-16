package io.github.mircodhd.mvplayer.patch

import io.github.mircodhd.mvplayer.game.GameInfo

/**
 * JS patches injected into a game's index.html. Paths are relative to assets/patches/.
 * [beforeEngine] runs before any game script, [beforePlugins] after the rpg_*.js core
 * files but before js/plugins.js and the plugin scripts.
 */
data class PatchSet(val beforeEngine: List<String>, val beforePlugins: List<String>)

/** A group of patches applied when [applies] matches the game. */
private class PatchProfile(
    val id: String,
    val applies: (GameInfo) -> Boolean,
    val beforeEngine: List<String> = emptyList(),
    val beforePlugins: List<String> = emptyList(),
)

object PatchProfiles {
    private val profiles = listOf(
        PatchProfile(
            id = "mv-base",
            applies = { true },
            beforeEngine = listOf("common/player.js"),
            beforePlugins = listOf("mv/engine.js", "mv/storage.js", "mv/input.js", "mv/lifecycle.js"),
        ),
        // Steam builds load greenworks through require(); give them a no-op module.
        PatchProfile(
            id = "greenworks-stub",
            applies = { it.usesGreenworks },
            beforePlugins = listOf("stubs/greenworks.js"),
        ),
    )

    fun select(info: GameInfo): Pair<List<String>, PatchSet> {
        val active = profiles.filter { it.applies(info) }
        return active.map { it.id } to PatchSet(
            beforeEngine = active.flatMap { it.beforeEngine },
            beforePlugins = active.flatMap { it.beforePlugins },
        )
    }
}
