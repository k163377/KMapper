plugins {
    id("maven")
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
}

group = "com.mapk"
version = "0.29"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(kotlin("gradle-plugin"))
    }
}

repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(kotlin("reflect"))
    api("com.github.ProjectMapK:Shared:0.15")

    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.6.2") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    // 現状プロパティ名の変換はテストでしか使っていないのでtestImplementation
    // https://mvnrepository.com/artifact/com.google.guava/guava
    testImplementation(group = "com.google.guava", name = "guava", version = "28.2-jre")
}

tasks {
    compileKotlin {
        dependsOn("ktlintFormat")
        kotlinOptions {
            jvmTarget = "1.8"
            allWarningsAsErrors = true
        }
    }

    compileTestKotlin {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "1.8"
        }
    }
    test {
        useJUnitPlatform()
    }
}
