import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.JavaVersion.VERSION_1_8
import java.net.URI
import org.apache.tools.ant.filters.*
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.2.40"
    id("com.github.johnrengelman.shadow") version "2.0.3"
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
    sourceCompatibility = VERSION_1_8
    targetCompatibility = VERSION_1_8
}

kotlin {
    this.experimental.coroutines = Coroutines.ENABLE
}

defaultTasks = listOf("shadowJar")

tasks {
    withType<ProcessResources> {
        filter(mapOf(Pair("tokens", mapOf(Pair("version", version)))), ReplaceTokens::class.java)
    }
    withType<ShadowJar> {
        this.classifier = null
        this.configurations = listOf(project.configurations.shadow)
    }
}

repositories {
    maven { url = URI("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }

    mavenCentral()
    mavenLocal()
}

idea {
    project {
        languageLevel = IdeaLanguageLevel("1.8")
    }
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

dependencies {
    compileOnly("org.bukkit:bukkit:1.12.2-R0.1-SNAPSHOT") { isChanging = true }
    compileOnly(fileTree("lib") { include("*.jar") })
    shadow(kotlin("stdlib"))
    shadow("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.22.5")
    shadow("com.google.guava:guava:24.1-jre")
    shadow("org.apache.commons:commons-collections4:4.1")
}