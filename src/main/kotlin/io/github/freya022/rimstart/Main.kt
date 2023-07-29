package io.github.freya022.rimstart

import atlantafx.base.theme.CupertinoDark
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Application
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.OverrunStyle
import javafx.scene.layout.GridPane
import javafx.stage.Stage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.io.path.*

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
    private val logger = KotlinLogging.logger { }

    private val xmlMapper = XmlMapper()

    private val modsConfigPath = Path(System.getenv("appdata"), "..", "LocalLow", "Ludeon Studios", "RimWorld by Ludeon Studios", "Config", "ModsConfig.xml")
    private val rwListFolderPath = Path(System.getenv("appdata"), "..", "LocalLow", "Ludeon Studios", "RimWorld by Ludeon Studios", "ModLists")

    @JvmStatic
    @OptIn(ExperimentalPathApi::class)
    fun main(args: Array<String>): Unit = runBlocking(Dispatchers.Main) {
        Application.setUserAgentStylesheet(CupertinoDark().userAgentStylesheet)

        val modsConfig = xmlMapper.readValue(modsConfigPath.toFile(), ModsConfig::class.java)

        val root = GridPane().apply {
            hgap = 10.0
            vgap = 10.0

            padding = Insets(10.0, 10.0, 10.0, 10.0)

            rwListFolderPath.walk()
                .filter { it.extension == "rml" }
                .sortedByDescending { it.getLastModifiedTime() }
                .map { path ->
                    val rwList = xmlMapper.readValue(path.toFile(), RWList::class.java)

                    logger.trace { "modsConfig = $modsConfig" }
                    logger.trace { "rwList = $rwList" }

                    val isCurrentConfig = modsConfig == rwList.toModsConfig()
                    logger.debug { "modsConfig == builtModsConfig = $isCurrentConfig" }

                    Button(path.nameWithoutExtension).apply {
                        prefWidth = 150.0
                        textOverrun = OverrunStyle.LEADING_WORD_ELLIPSIS
                        isDefaultButton = isCurrentConfig

                        setOnAction {
                            logger.info { "Switching mod list to '${path.nameWithoutExtension}'" }
                        }
                    }
                }
                .chunked(4)
                .forEachIndexed { index, buttons ->
                    addRow(index, *buttons.toTypedArray())
                }
        }

        Stage().apply {
            title = "RimStart"
            isResizable = false
            scene = Scene(root)
        }.show()
    }
}