import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm") version "2.2.20"
}

group = "org.kts"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }

    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.SHORT
        showStackTraces = false
    }

    val htmlReport = reports.html.entryPoint

    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit
        override fun beforeTest(testDescriptor: TestDescriptor) = Unit
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent != null) return
            logger.lifecycle("")
            logger.lifecycle("Mini Order System — результаты")
            logger.lifecycle("  всего:     ${result.testCount}")
            logger.lifecycle("  успешно:   ${result.successfulTestCount}")
            logger.lifecycle("  провалено: ${result.failedTestCount}")
            logger.lifecycle("  пропущено: ${result.skippedTestCount}")
            logger.lifecycle("  отчёт:     ${htmlReport.toURI()}")
            logger.lifecycle("")
        }
    })
}

tasks.register("checkHomework") {
    group = "verification"
    description = "Прогоняет все тесты Mini Order System"
    dependsOn(tasks.test)
}

val checkerJar = layout.projectDirectory.file("checker/checker.jar")

val prepareSubmission = tasks.register<JavaExec>("prepareSubmission") {
    group = "verification"
    description = "Подставляет эталонные тесты перед сдачей"
    classpath = files(checkerJar)
    mainClass = "checker.MainKt"
    args("--prepare")
    outputs.upToDateWhen { false }
}

tasks.test {
    mustRunAfter(prepareSubmission)
}

tasks.register<JavaExec>("submitHomework") {
    group = "verification"
    description = "Прогоняет эталонные тесты и отправляет результат в LMS"
    classpath = files(checkerJar)
    mainClass = "checker.MainKt"
    args(if (project.hasProperty("dryRun")) listOf("--submit", "--dry-run") else listOf("--submit"))
    outputs.upToDateWhen { false }
    dependsOn(prepareSubmission, tasks.test)
}
