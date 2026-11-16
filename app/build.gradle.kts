plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.bane23.iptvplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bane23.iptvplayer"
        minSdk = 24
        targetSdk = 37
        // U CI-ju broj builda, da bi svaki novi APK bio „noviji” i ažuriranje u aplikaciji ga prepoznalo.
        // Lokalni build ostaje 1, pa ga CI build uvek može zameniti.
        val ciBuild = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()
        // Zvanično izdanje: release.yml postavlja RELEASE_VERSION iz oznake (v1.2.3 daje „1.2.3”).
        // Broj verzije se računa iz oznake (1.2.3 daje 1002003), pa ne zavisi od broja CI run-a
        // i uvek je veći od dev buildova.
        val release = providers.environmentVariable("RELEASE_VERSION").orNull?.trim()
            ?.let { Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})""").matchEntire(it) }
        if (release != null) {
            val (major, minor, patch) = release.destructured
            versionCode = major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
            versionName = release.value
        } else {
            versionCode = ciBuild ?: 1
            versionName = if (ciBuild != null) "0.0.1-dev.$ciBuild" else "0.0.1-local"
        }

        // Ključevi Trakt aplikacije stižu iz GitHub Secrets (CI) i nikad nisu u repou.
        // Kad ih nema (lokalni build, testovi), Trakt se u aplikaciji ne prikazuje.
        fun quoted(name: String) = "\"" + (providers.environmentVariable(name).orNull ?: "").trim().replace("\"", "") + "\""
        buildConfigField("String", "TRAKT_CLIENT_ID", quoted("TRAKT_CLIENT_ID"))
        buildConfigField("String", "TRAKT_CLIENT_SECRET", quoted("TRAKT_CLIENT_SECRET"))
    }

    signingConfigs {
        // Stalan debug ključ iz repoa, da bi novi debug APK mogao da se instalira preko starog.
        // Koristi se samo za debug, nikad za release.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Ključ za zvanična izdanja postoji samo u GitHub Secrets (release.yml ga privremeno upiše u fajl).
        // Bez njega release build ostaje nepotpisan, što je dovoljno za proveru R8 u CI-ju.
        val releaseStore = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull?.takeIf { it.isNotBlank() }
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // Kanal ažuriranja: oznaka izdanja u javnom repou (vidi data/update/UpdateSource.kt).
            buildConfigField("String", "UPDATE_CHANNEL", "\"iptv-player-dev\"")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "UPDATE_CHANNEL", "\"iptv-player\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    // Samo za crtanje QR koda (dodavanje liste sa telefona).
    implementation(libs.zxing.core)
    // Slanje na Chromecast sa telefona.
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
