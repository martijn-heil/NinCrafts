import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.JavaVersion.VERSION_1_8
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.3.31"
    id("com.github.johnrengelman.shadow") version "4.0.2"
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

//kotlin {
//    this.experimental.coroutines = Coroutines.ENABLE
//}

defaultTasks = mutableListOf("shadowJar")

tasks {
    withType<ProcessResources> {
        filter(mapOf(Pair("tokens", mapOf(Pair("version", version)))), ReplaceTokens::class.java)
    }
    withType<ShadowJar> {
        this.classifier = null
        this.configurations = mutableListOf(project.configurations.shadow)
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
    compileOnly("org.bukkit:bukkit:1.14.2-R0.1-SNAPSHOT") { isChanging = true }
    compileOnly(fileTree("lib") { include("*.jar") })
    shadow("com.google.guava:guava:24.1-jre")
    shadow("org.apache.commons:commons-collections4:4.1")
    compile(kotlin("stdlib"))
    compile(kotlin("stdlib-jdk8"))
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}