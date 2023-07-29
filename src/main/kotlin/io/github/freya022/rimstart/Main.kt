package io.github.freya022.rimstart

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlin.io.path.Path

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModsConfig @JsonCreator constructor(
    @JsonProperty("version") val version: String,
    @JsonProperty("activeMods") val activeMods: List<String>,
    @JsonProperty("knownExpansions") val knownExpansions: List<String>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RWList @JsonCreator constructor(
    @JsonProperty("meta") val meta: Meta,
    @JsonProperty("modList") val modList: ModList
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Meta @JsonCreator constructor(
        @JsonProperty("gameVersion") val gameVersion: String
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ModList @JsonCreator constructor(
        @JsonProperty("ids") val ids: List<String>
    )
}

fun RWList.toModsConfig(): ModsConfig {
    return ModsConfig(
        version = meta.gameVersion,
        activeMods = modList.ids,
        knownExpansions = modList.ids.filter { it.startsWith("ludeon.rimworld.") }.toHashSet().toList()
    )
}

object Main {
    private val modsConfigPath = Path(System.getenv("appdata"), "..", "LocalLow", "Ludeon Studios", "RimWorld by Ludeon Studios", "Config", "ModsConfig.xml")
    private val rwListPath = Path(System.getenv("appdata"), "..", "LocalLow", "Ludeon Studios", "RimWorld by Ludeon Studios", "ModLists", "Mods Multi 1.4 2.rml")

    @JvmStatic
    fun main(args: Array<String>) {
        val xmlMapper = XmlMapper()
        val rwList = xmlMapper.readValue(rwListPath.toFile(), RWList::class.java)
        val modsConfig = xmlMapper.readValue(modsConfigPath.toFile(), ModsConfig::class.java)
        val builtModsConfig = rwList.toModsConfig()

        println("modsConfig = $modsConfig")
        println("rwList = $rwList")

        println("modsConfig == builtModsConfig = ${modsConfig == builtModsConfig}")
    }
}