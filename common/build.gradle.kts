plugins {
    id("com.github.gmazzo.buildconfig") version "3.1.0"
}

val shadePE: Boolean by rootProject.extra

dependencies {
    // True compileOnly deps
    compileOnly("org.geysermc.floodgate:api:2.0-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.1.72.Final")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    // Upstream 2.11.2 API; runtime impl is the externally installed True-OG fork or the shaded bundle.
    if (shadePE) {
        implementation("com.github.retrooper:packetevents-api:2.11.2")
    } else {
        compileOnly("com.github.retrooper:packetevents-api:2.11.2")
    }

    implementation("org.yaml:snakeyaml:2.0")
    implementation("org.kohsuke:github-api:1.326") {
        exclude(group = "commons-io", module = "commons-io")
        exclude(group = "org.apache.commons", module = "commons-lang3")
    }

    implementation("org.incendo:cloud-core:2.0.0")
    implementation("org.incendo:cloud-minecraft-extras:2.0.0-beta.10")
}

buildConfig {
    buildConfigField("String", "GITHUB_REPO", "\"${project.rootProject.ext["githubRepo"]}\"")
    // Exposes shadePE to source code as a compile-time constant.
    buildConfigField("boolean", "SHADE_PE", shadePE.toString())
}