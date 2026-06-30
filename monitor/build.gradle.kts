plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":core"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.chingfordmosque.prayertimes.monitor.MainKt")
}
