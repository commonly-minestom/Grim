import versioning.BuildConfig

plugins {
    `maven-publish`
    grim.`base-conventions`
    id("com.gradleup.shadow")
}

repositories {
    val localOverride = if (BuildConfig.mavenLocalOverride) mavenLocal() else null

    val grimPublicReleases = maven("https://maven.grim.ac/public/releases") {
        mavenContent { releasesOnly() }
    }
    val grimPublicSnapshots = maven("https://maven.grim.ac/public/snapshots") {
        mavenContent { snapshotsOnly() }
    }
    val grimLegacySnapshots = maven("https://repo.grim.ac/snapshots")
    exclusiveContent {
        forRepositories(*listOfNotNull(localOverride, grimPublicReleases, grimPublicSnapshots, grimLegacySnapshots).toTypedArray())
        filter {
            includeGroup("ac.grim.grimac")
            includeGroup("com.github.retrooper")
        }
    }

    // ViaMinestom is not published anywhere yet, install it with its publishToMavenLocal task
    exclusiveContent {
        forRepository { mavenLocal() }
        filter { includeModule("com.viaversion", "viaminestom") }
    }
    exclusive("https://repo.viaversion.com", { mavenContent { releasesOnly() } }) {
        includeModule("com.viaversion", "viaversion")
    }

    exclusive("https://nexus.scarsz.me/content/repositories/releases", { mavenContent { releasesOnly() } }) {
        includeGroup("github.scarsz")
    }

    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

configurations.compileClasspath {
    resolutionStrategy.eachDependency {
        if (requested.group == "net.kyori" && requested.name.startsWith("adventure-")) {
            useVersion(libs.versions.adventure.asProvider().get())
        }
    }
}

dependencies {
    compileOnly(libs.minestom)
    compileOnly(libs.luckperms)
    compileOnly(libs.viaversion)
    compileOnly(libs.viaminestom) {
        isTransitive = false
    }

    implementation(project(":common"))
    implementation(libs.packetevents.api)
    implementation(libs.packetevents.netty.common) {
        exclude(group = "io.netty")
    }
    implementation(libs.adventure.nbt)
    implementation(libs.netty.buffer)
    implementation(libs.guava)
}

publishing.publications.create<MavenPublication>("maven") {
    artifact(tasks["shadowJar"])
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(25)
    }

    shadowJar {
        archiveFileName = "${rootProject.name}-${project.name}-${rootProject.version}.jar"
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude(
            "module-info.class",
            "META-INF/versions/*/module-info.class",
            "META-INF/gradle/**",
            "META-INF/services/javax.annotation.processing.Processor",
            "META-INF/services/reactor.blockhound.integration.BlockHoundIntegration",
        )

        dependencies {
            exclude(dependency("org.slf4j:.*"))
            exclude(dependency("org.jetbrains:annotations"))
            exclude(dependency("org.jspecify:jspecify"))
            exclude(dependency("com.google.errorprone:error_prone_annotations"))
            exclude(dependency("com.google.j2objc:j2objc-annotations"))
        }

        minimize {
            exclude(dependency("ac.grim.grimac:GrimAPI:.*"))
        }

        relocate("io.github.retrooper.packetevents", "ac.grim.grimac.shaded.io.github.retrooper.packetevents")
        relocate("com.github.retrooper.packetevents", "ac.grim.grimac.shaded.com.github.retrooper.packetevents")
        relocate("net.kyori", "ac.grim.grimac.shaded.kyori")
        relocate("io.netty", "ac.grim.grimac.shaded.netty")
        relocate("com.google.common", "ac.grim.grimac.shaded.guava")
        relocate("github.scarsz.configuralize", "ac.grim.grimac.shaded.configuralize")
        relocate("alexh", "ac.grim.grimac.shaded.maps")
        relocate("it.unimi.dsi.fastutil", "ac.grim.grimac.shaded.fastutil")
        relocate("org.yaml.snakeyaml", "ac.grim.grimac.shaded.snakeyaml")
        relocate("org.json", "ac.grim.grimac.shaded.json")
        relocate("org.incendo", "ac.grim.grimac.shaded.incendo")
        relocate("io.leangen.geantyref", "ac.grim.grimac.shaded.geantyref")
        relocate("com.zaxxer", "ac.grim.grimac.shaded.zaxxer")
        relocate("com.lmax", "ac.grim.grimac.shaded.lmax")

        mergeServiceFiles()
    }

    assemble {
        dependsOn(shadowJar)
    }
}
