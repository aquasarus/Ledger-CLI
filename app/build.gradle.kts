plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")

    // Firebase Crashlytics
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")

    // Data Store
    id("com.google.protobuf") version "0.9.4"
}

android {
    namespace = "com.millspills.ledgercli"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.millspills.ledgercli"
        minSdk = 31
        targetSdk = 33
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        dataBinding = true
//        compose = true
    }

//    composeOptions {
//        kotlinCompilerExtensionVersion = "1.4.8"
//    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")

    // Data Store
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.datastore:datastore:1.0.0")
    implementation("com.google.protobuf:protobuf-javalite:3.18.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Firebase Crashlytics
    implementation(platform("com.google.firebase:firebase-bom:32.2.2"))
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Work Manager
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Jetpack Compose
//    implementation("androidx.activity:activity-compose:1.3.1")
//    implementation("androidx.compose.ui:ui:1.0.1")
//    implementation("androidx.compose.material:material:1.0.1")
//    implementation("androidx.compose.ui.tooling:ui-tooling:1.0.1")
//    val composeBom = platform("androidx.compose:compose-bom:2023.08.00")
//    implementation(composeBom)
//    androidTestImplementation(composeBom)
//
//    // Material Design 3
//    implementation("androidx.compose.material3:material3")
//    implementation("androidx.compose.ui:ui")
//
//    // Android Studio Preview support
//    implementation("androidx.compose.ui:ui-tooling-preview")
//    debugImplementation("androidx.compose.ui:ui-tooling")
//
//    // Optional - Add full set of material icons
//    implementation("androidx.compose.material:material-icons-extended")
//    // Optional - Add window size utils
//    implementation("androidx.compose.material3:material3-window-size-class")
//
//    // Optional - Integration with activities
//    implementation("androidx.activity:activity-compose:1.7.3")
//    // Optional - Integration with ViewModels
//    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
//    // Optional - Integration with LiveData
//    implementation("androidx.compose.runtime:runtime-livedata")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.18.1"
    }

    // Generates the java Protobuf-lite code for the Protobufs in this project. See
    // https://github.com/google/protobuf-gradle-plugin#customizing-protobuf-compilation
    // for more information.
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
