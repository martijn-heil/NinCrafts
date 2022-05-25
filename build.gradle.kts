import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.apache.tools.ant.filters.*
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import java.net.URI

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("io.papermc.paperweight.userdev") version "1.3.3"
    idea
}

group = "com.github.martijn_heil.nincrafts"
version = "1.0-SNAPSHOT"
description = "NinCrafts"

apply {
    plugin("java")
    plugin("kotlin")
    plugin("idea")
}

java {
    //sourceCompatibility = VERSION_1_8
    //targetCompatibility = VERSION_1_8
}

kotlin {
    //this.experimental.coroutines = Coroutines.ENABLE
}

defaultTasks = listOf("shadowJar").toMutableList()

tasks {
    withType<ProcessResources> {
        filter(mapOf(Pair("tokens", mapOf(Pair("version", version)))), ReplaceTokens::class.java)
    }
    withType<ShadowJar> {
        this.classifier = null
        this.configurations = listOf(project.configurations.shadow.get())
    }
}

repositories {
    maven { url = URI("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = URI("https://jitpack.io") }

    mavenCentral()
    mavenLocal()
}

idea {
    project {
        //languageLevel = IdeaLanguageLevel("1.8")
    }
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

dependencies {
    //compileOnly("org.bukkit:bukkit:1.16.2-R0.1-SNAPSHOT") { isChanging = true }
    //compileOnly("org.spigotmc:spigot-api:1.16.2-R0.1-SNAPSHOT") { isChanging = true }
    paperDevBundle("1.18.2-R0.1-SNAPSHOT")
    compileOnly("com.gitlab.martijn-heil:NinCommands:-SNAPSHOT") { isChanging = true }
    compileOnly(fileTree("lib") { include("*.jar") })
    shadow(kotlin("stdlib"))
    //shadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.22.5")
    shadow("com.google.guava:guava:24.1-jre")
    shadow("org.apache.commons:commons-collections4:4.1")
}
