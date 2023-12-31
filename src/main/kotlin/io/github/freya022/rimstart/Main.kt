package io.github.freya022.rimstart

import atlantafx.base.theme.CupertinoDark
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.OverrunStyle
import javafx.scene.layout.GridPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import kotlin.io.path.*
import kotlin.system.exitProcess

//Jackson is stupid
data class KnownExpansions @JsonCreator constructor(
    @JsonProperty("li") @JacksonXmlElementWrapper(useWrapping = false) val li: List<String>
)

//Jackson is stupid
data class ActiveMods @JsonCreator constructor(
    @JsonProperty("li") @JacksonXmlElementWrapper(useWrapping = false) val li: List<String>
)

@JacksonXmlRootElement(localName = "ModsConfigData")
data class ModsConfig @JsonCreator constructor(
    @JsonProperty("version") val version: String,
    @JsonProperty("activeMods") val activeMods: ActiveMods,
    @JsonProperty("knownExpansions") val knownExpansions: KnownExpansions
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
        activeMods = ActiveMods(modList.ids),
        knownExpansions = modList.ids.filter { it.startsWith("ludeon.rimworld.") }
            .toHashSet().toList()
            .let(::KnownExpansions)
    )
}

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        Application.launch(App::class.java)
    }
}

class App : Application() {
    private val logger = KotlinLogging.logger { }

    private val xmlMapper = XmlMapper().apply {
        configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
        configure(SerializationFeature.INDENT_OUTPUT, true)
    }

    private val rwFolderPath = when {
        System.getProperty("os.name").startsWith("Windows") ->
            Path(System.getenv("USERPROFILE"), "AppData", "LocalLow", "Ludeon Studios", "RimWorld by Ludeon Studios")
        System.getProperty("os.name").startsWith("Mac OS") ->
            throw IllegalArgumentException("This app does not run on macOS")
        else ->
            Path("~", ".config", "unity3d", "Ludeon Studios", "RimWorld by Ludeon Studios", "Saves")
    }
    private val modsConfigPath = rwFolderPath.resolve("Config").resolve("ModsConfig.xml")
    private val rwListFolderPath = rwFolderPath.resolve("ModLists")

    @OptIn(ExperimentalPathApi::class)
    override fun start(primaryStage: Stage) {
        setUserAgentStylesheet(CupertinoDark().userAgentStylesheet)

        val modsConfigFile = modsConfigPath.toFile()
        val modsConfig = xmlMapper.readValue(modsConfigFile, ModsConfig::class.java)

        val grid = GridPane().apply {
            hgap = 10.0
            vgap = 10.0

            rwListFolderPath.walk()
                .filter { it.extension == "rml" }
                .sortedByDescending { it.getLastModifiedTime() }
                .map { path ->
                    val rwList = xmlMapper.readValue(path.toFile(), RWList::class.java)

                    logger.trace { "modsConfig = $modsConfig" }
                    logger.trace { "rwList = $rwList" }

                    val rwListAsModsConfig = rwList.toModsConfig()
                    val isCurrentConfig = modsConfig == rwListAsModsConfig
                    logger.debug { "modsConfig == builtModsConfig = $isCurrentConfig" }

                    Button(path.nameWithoutExtension).apply {
                        prefWidth = 150.0
                        textOverrun = OverrunStyle.LEADING_WORD_ELLIPSIS
                        isDefaultButton = isCurrentConfig

                        if (isCurrentConfig) {
                            Platform.runLater { requestFocus() }
                        }

                        setOnAction {
                            logger.info { "Switching mod list to '${path.nameWithoutExtension}'" }

                            if (!isCurrentConfig) {
                                modsConfigPath.moveTo(modsConfigPath.resolveSibling("ModsConfig.xml.bak"), overwrite = true)
                                xmlMapper.writeValue(modsConfigFile, rwListAsModsConfig)
                            }
                            hostServices.showDocument("steam://rungameid/294100")
                            exitProcess(0)
                        }
                    }
                }
                .chunked(4)
                .forEachIndexed { index, buttons ->
                    addRow(index, *buttons.toTypedArray())
                }
        }

        val root = VBox(10.0).apply {
            padding = Insets(10.0, 10.0, 10.0, 10.0)
            alignment = Pos.TOP_CENTER

            children += grid
            children += Button("Open RimWorld folder").apply {
                setOnAction {
                    hostServices.showDocument(rwFolderPath.toUri().toString())
                }
            }
        }

        primaryStage.apply {
            title = "RimStart"
            isResizable = false
            scene = Scene(root)
        }.show()
    }
}