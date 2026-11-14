plugins {
    kotlin("jvm") version "2.4.21"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "zone.vao"

version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.vao.zone/snapshots") {
        content { includeModule("zone.vao", "craftcanvas-api") }
    }
}

val apiVersion = providers.gradleProperty("craftcanvasApiVersion").orElse("1.0.0-SNAPSHOT")
val paperVersion = "1.21.11-R0.1-20260511.115010-91"

dependencies {
    compileOnly("zone.vao:craftcanvas-api:${apiVersion.get()}")
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    implementation(kotlin("stdlib"))
}

kotlin { jvmToolchain(21) }

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.shadowJar {
    archiveFileName.set("CraftCanvasLudo-${version}.jar")
    from("LICENSE")
    filesMatching("META-INF/*.kotlin_module") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    exclude("META-INF/versions/**/module-info.class", "module-info.class")
    manifest { attributes("Implementation-Version" to project.version.toString()) }
}

tasks.jar { enabled = false }

tasks.build { dependsOn(tasks.shadowJar) }
