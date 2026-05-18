plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.monologic"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.monologic"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")          // Apache 2.0

    implementation("androidx.work:work-runtime-ktx:2.9.0")             // Apache 2.0
    implementation("com.squareup.okhttp3:okhttp:4.12.0")               // Apache 2.0
    implementation("org.jsoup:jsoup:1.17.2")                           // MIT
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Apache 2.0
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // Apache 2.0
    implementation("androidx.security:security-crypto:1.0.0")          // Apache 2.0
    implementation("androidx.room:room-runtime:2.6.1")                 // Apache 2.0
    implementation("androidx.room:room-ktx:2.6.1")                     // Apache 2.0
    kapt("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
