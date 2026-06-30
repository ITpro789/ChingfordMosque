plugins {
    kotlin("jvm")
}

group = "com.chingfordmosque"
version = "0.1.0"

dependencies {
    implementation("org.jsoup:jsoup:1.18.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
