plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
}

android {
    namespace = "com.example.parental_control"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.parental_control"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures{
        viewBinding = true
    }

}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Java-based Android dependencies
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.activity:activity:1.7.2") // Java-based alternative to activity-ktx
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")



    // Firebase (Java-based)
    implementation("com.google.firebase:firebase-auth:22.3.1")

    implementation("com.google.firebase:firebase-database:20.0.0")

    // Google Play Services (Location)
    implementation("com.google.android.gms:play-services-location:18.0.0")

    implementation ("com.google.android.libraries.places:places:2.7.0")


    // Google Maps SDK (Java)
    implementation("com.google.android.gms:play-services-maps:18.1.0")

    implementation ("androidx.recyclerview:recyclerview:1.2.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.espresso:espresso-core:3.5.1")




    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.0")

    implementation ("com.squareup.okhttp3:okhttp:4.9.3")
    implementation ("com.android.volley:volley:1.2.1")

    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation ("com.google.android.material:material:1.10.0")

    implementation ("androidx.viewpager2:viewpager2:1.0.0")


}
