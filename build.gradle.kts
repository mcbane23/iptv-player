// Vrh projekta: samo deklaracija pluginova, bez primene.
buildscript {
    dependencies {
        // AGP 9 ima ugrađen Kotlin; ovde zakucavamo verziju Kotlin kompajlera
        // da bude ista kao verzija Compose compiler plugina.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
