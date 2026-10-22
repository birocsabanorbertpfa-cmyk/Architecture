plugins {
    id("architecture.android.feature")
}

android {
    namespace = "hu.csabi.architecture.feature.labs"
}

dependencies {
    // The paging lab renders a PagingData stream, so it needs the Compose integration.
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.runtime)
}
