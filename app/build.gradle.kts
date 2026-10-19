plugins {
    id("com.android.application")
}

android {
    namespace = "org.hypest.visionrevivetv"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.hypest.visionrevivetv"
        minSdk = 28
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("debugKeystore") {
            val ks = file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugKeystore")
        }
        debug {
            signingConfig = signingConfigs.getByName("debugKeystore")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
