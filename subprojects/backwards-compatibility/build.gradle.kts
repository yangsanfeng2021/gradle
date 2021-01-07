plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.asm)
}
