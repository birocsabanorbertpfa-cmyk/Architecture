plugins {
    id("architecture.android.library")
    id("architecture.android.compose")
}

android {
    namespace = "hu.csabi.architecture.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
