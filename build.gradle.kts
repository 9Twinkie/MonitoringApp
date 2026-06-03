plugins {
    alias(libs.plugins.android.application) apply false
    id("com.android.library") version "8.7.3" apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
