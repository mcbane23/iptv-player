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
        // Brojanje verzija je isto za test buildove i zvanična izdanja, da se sa jednog na drugo prelazi
        // običnim ažuriranjem: versionCode = 1 000 000 + broj commitova u main-u (CI ga šalje kao
        // APP_BUILD_NUMBER). Raste sa svakim spajanjem i veći je od svih starijih buildova
        // (izdanje 1.0.0 ima 1 000 000). Lokalni build ostaje 1, pa ga CI build uvek može zameniti.
        val buildNumber = providers.environmentVariable("APP_BUILD_NUMBER").orNull?.trim()?.toIntOrNull()
        // Ime verzije: version.txt za zvanično izdanje (release.yml postavlja RELEASE_VERSION),
        // a za test build isto to sa dodatkom „-dev.<broj>”.
        val baseVersion = providers.fileContents(rootProject.layout.projectDirectory.file("version.txt"))
            .asText.orNull?.trim()?.takeIf { Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}""").matches(it) } ?: "0.0.1"
        val release = providers.environmentVariable("RELEASE_VERSION").orNull?.trim()
            ?.takeIf { Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}""").matches(it) }
        versionCode = if (buildNumber != null) 1_000_000 + buildNumber else 1
        versionName = when {
            release != null -> release
            buildNumber != null -> "$baseVersion-dev.$buildNumber"
            else -> "$baseVersion-local"
        }

        // Ključevi Trakt aplikacije stižu iz GitHub Secrets (CI) i nikad nisu u repou.
        // Kad ih nema (lokalni build, testovi), Trakt se u aplikaciji ne prikazuje.
        fun quoted(name: String) = "\"" + (providers.environmentVariable(name).orNull ?: "").trim().replace("\"", "") + "\""
        buildConfigField("String", "TRAKT_CLIENT_ID", quoted("TRAKT_CLIENT_ID"))
        buildConfigField("String", "TRAKT_CLIENT_SECRET", quoted("TRAKT_CLIENT_SECRET"))
        // API ključ za OpenSubtitles (titlovi sa interneta), isto iz GitHub Secrets; bez njega se opcija ne prikazuje.
        buildConfigField("String", "OPENSUBTITLES_API_KEY", quoted("OPENSUBTITLES_API_KEY"))
    }

    signingConfigs {
        // Stalan debug ključ iz repoa, da bi novi debug APK mogao da se instalira preko starog.
        // Koristi se za debug i za test buildove sa grana, nikad za objavljene buildove.
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
        // Test build koji CI objavljuje (dev-latest i ažuriranje u aplikaciji): optimizovan kao izdanje (R8,
        // nije debuggable), jer je debug build sa Compose-om višestruko sporiji na slabijim TV uređajima.
        // Na main-u je potpisan istim ključem kao zvanična izdanja (CI ga dobija iz GitHub Secrets), pa se
        // sa test builda prelazi na izdanje i nazad bez brisanja podataka. Bez tog ključa (grane, lokalno)
        // potpisuje se debug ključem; takav build se ne objavljuje.
        create("dev") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            buildConfigField("String", "UPDATE_CHANNEL", "\"iptv-player-dev\"")
            matchingFallbacks += listOf("release")
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

    // Lint ima svoj posao u CI-ju (lintDebug). Bez ovoga bi svaki optimizovan build (dev, release) uz R8
    // ponovo pokretao lint („lintVital”), što samo produžava čekanje na APK.
    lint {
        checkReleaseBuilds = false
    }
}

/**
 * Android tekstove iz `values-sr` ne koristi kad je uređaj (ili izbor u aplikaciji) na „srpski (latinica)”,
 * jer `sr` bez pisma računa kao ćirilicu. Zato se isti tekstovi pri buildu kopiraju i u `values-b+sr+Latn`;
 * u repou ostaje jedan prevod.
 */
abstract class CopySerbianLatinStrings : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = output.get().dir("values-b+sr+Latn").asFile
        target.deleteRecursively()
        source.get().asFile.copyRecursively(target)
    }
}

val copySerbianLatinStrings = tasks.register<CopySerbianLatinStrings>("copySerbianLatinStrings") {
    source.set(layout.projectDirectory.dir("src/main/res/values-sr"))
    output.set(layout.buildDirectory.dir("generated/serbianLatin/res"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(copySerbianLatinStrings) { it.output }
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
    // Test build: imena Compose funkcija u Perfetto tragu, da se na TV-u vidi šta se iznova slaže.
    // Radi samo kad se praćenje uključi preko adb-a; u zvaničnom izdanju ga nema.
    add("devImplementation", libs.androidx.compose.runtime.tracing)

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
