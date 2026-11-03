// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.google.services) apply false
}

// Prevent OneDrive file-locking, cloud reparse points, and sync corruption
if (rootProject.projectDir.absolutePath.contains("OneDrive")) {
    val buildRoot = file("${System.getProperty("user.home")}/.gradle-builds/LofiQ")
    allprojects {
        layout.buildDirectory.set(file("$buildRoot/${project.name}"))
    }
}
