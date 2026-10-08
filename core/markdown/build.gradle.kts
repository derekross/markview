plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)
    implementation(libs.commonmark.gfm.strikethrough)
    implementation(libs.commonmark.gfm.alerts)
    implementation(libs.commonmark.task.list.items)
    implementation(libs.commonmark.autolink)
    implementation(libs.commonmark.footnotes)
    implementation(libs.commonmark.yaml.front.matter)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
