plugins {
    `java-library`
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
}

val deps: Map<String, String>
    get() = rootProject.extra["deps"] as Map<String, String>
dependencies {
    implementation(kotlin("stdlib"))
    implementation(deps.getValue("kotlinxCoroutinesCore"))
    implementation(project(":tongs-common"))

    implementation("com.shazam:axmlparser:1.0")
    implementation(deps.getValue("commonsIo"))
    implementation(group = "org.apache.commons", name = "commons-text", version = "1.9")
    implementation(deps.getValue("ddmlib"))
    implementation(deps.getValue("sdklib"))
    implementation("org.smali:dexlib2:2.4.0") {
        exclude(group = "com.google.guava")
    }
    implementation(deps.getValue("gson"))
    implementation(deps.getValue("guava"))
    implementation(deps.getValue("jsr305"))
    implementation(deps.getValue("slf4j"))
    implementation("com.madgag:animated-gif-lib:1.2") // TODO: move GIF creation back to plugin api or runner

    testImplementation(project(":tongs-common-test"))
    testImplementation(kotlin("test"))
    testImplementation(deps.getValue("junit"))
    testImplementation(deps.getValue("hamcrest"))
    testImplementation(deps.getValue("junitParams"))
}
