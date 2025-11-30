package org.jikvict.testing.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots
import org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.jikvict.testing.junit.JIkvictTestExecutionListener
import org.jikvict.testing.model.TestSuiteResult
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * Gradle plugin for running tests with JIkvict testing framework.
 * This plugin adds a task for running tests and calculating points.
 */
@Suppress("unused")
class JIkvictTestingPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Register the JIkvictTest task
        project.tasks.register("runJIkvictTests", JIkvictTestTask::class.java) {
            description = "Runs tests with JIkvict testing framework and calculates points"
            group = "verification"

            testPackage.set("")
            outputFile.set(project.layout.buildDirectory.file("jikvict-results.json"))
            dependsOn("testClasses")
        }

        project.dependencies.apply {
            add("implementation", "org.junit.platform:junit-platform-launcher:1.10.1")
            add("implementation", "org.junit.jupiter:junit-jupiter-engine:5.10.1")
            add("implementation", "org.junit.jupiter:junit-jupiter-api:5.10.1")
            add("implementation", "org.slf4j:slf4j-api:2.0.13")
            add("implementation", "com.h2database:h2:2.2.224")

            add("testImplementation", "org.junit.jupiter:junit-jupiter-api:5.10.1")
            add("testImplementation", "org.junit.platform:junit-platform-launcher:1.10.1")
            add("testImplementation", "org.assertj:assertj-core:3.27.6")
            add("testImplementation", "org.mockito:mockito-core:5.20.0")
            add("testImplementation", "org.mockito.kotlin:mockito-kotlin:5.3.1")
            add("testImplementation", "io.mockk:mockk:1.14.6")
        }
    }
}

/**
 * Custom Gradle task for running tests with JIkvict testing framework.
 */
abstract class JIkvictTestTask : DefaultTask() {

    init {
        outputs.upToDateWhen { false }
    }

    @Input
    @Optional
    val testPackage: Property<String?> = project.objects.property(String::class.java)

    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun runTests() {
        val buildDir = project.layout.buildDirectory.asFile.get()
        val testClassesDir = File(buildDir, "classes/kotlin/test")
        val javaTestClassesDir = File(buildDir, "classes/java/test")

        logger.lifecycle("Looking for test classes in:")
        logger.lifecycle("  Kotlin: ${testClassesDir.absolutePath} (exists: ${testClassesDir.exists()})")
        logger.lifecycle("  Java: ${javaTestClassesDir.absolutePath} (exists: ${javaTestClassesDir.exists()})")

        if (!testClassesDir.exists() && !javaTestClassesDir.exists()) {
            logger.lifecycle("No compiled test classes found. Make sure to run 'testClasses' task first.")
            return
        }

        val testClasspath = mutableListOf<URL>()

        if (javaTestClassesDir.exists()) {
            testClasspath.add(javaTestClassesDir.toURI().toURL())
        }
        if (testClassesDir.exists()) {
            testClasspath.add(testClassesDir.toURI().toURL())
        }

        val mainClassesDir = File(buildDir, "classes/java/main")
        val kotlinMainClassesDir = File(buildDir, "classes/kotlin/main")
        if (mainClassesDir.exists()) {
            testClasspath.add(mainClassesDir.toURI().toURL())
        }
        if (kotlinMainClassesDir.exists()) {
            testClasspath.add(kotlinMainClassesDir.toURI().toURL())
        }

        val testRuntimeClasspath = project.configurations.getByName("testRuntimeClasspath")
        testRuntimeClasspath.files.forEach { file ->
            if (file.exists()) {
                testClasspath.add(file.toURI().toURL())
            }
        }

        val classLoader = URLClassLoader(testClasspath.toTypedArray(), Thread.currentThread().contextClassLoader)
        val originalClassLoader = Thread.currentThread().contextClassLoader

        try {
            Thread.currentThread().contextClassLoader = classLoader

            val launcher = LauncherFactory.create()
            val summaryListener = SummaryGeneratingListener()
            val jikvictListener = JIkvictTestExecutionListener()

            launcher.registerTestExecutionListeners(summaryListener, jikvictListener)

            val requestBuilder = LauncherDiscoveryRequestBuilder.request()

            if (testPackage.isPresent && testPackage.get()?.isNotEmpty() == true) {
                logger.lifecycle("Scanning package: ${testPackage.get()}")
                requestBuilder.selectors(selectPackage(testPackage.get()))
            } else {
                val classpathRoots = mutableSetOf<Path>()

                if (javaTestClassesDir.exists()) {
                    classpathRoots.add(javaTestClassesDir.toPath())
                    logger.lifecycle("Added Java test classpath: ${javaTestClassesDir.toPath()}")
                }
                if (testClassesDir.exists()) {
                    classpathRoots.add(testClassesDir.toPath())
                    logger.lifecycle("Added Kotlin test classpath: ${testClassesDir.toPath()}")
                }

                if (classpathRoots.isNotEmpty()) {
                    requestBuilder.selectors(selectClasspathRoots(classpathRoots))

                    try {
                        val testClasses = findTestClasses(javaTestClassesDir)
                        if (testClasses.isNotEmpty()) {
                            logger.lifecycle("Found test classes: ${testClasses.joinToString(", ")}")
                            requestBuilder.selectors(*(testClasses.map { selectClass(it) }.toTypedArray()))
                        }
                    } catch (e: Exception) {
                        logger.lifecycle("Could not discover test classes explicitly: ${e.message}")
                    }
                } else {
                    logger.lifecycle("No test directories found to scan")
                    return
                }
            }

            requestBuilder.configurationParameter("junit.jupiter.testinstance.lifecycle.default", "per_class")

            val request: LauncherDiscoveryRequest = requestBuilder.build()

            logger.lifecycle("Executing test discovery and execution...")
            launcher.execute(request)

            val testSuiteResult = jikvictListener.getTestSuiteResult()

            writeResultsToJson(testSuiteResult, outputFile.get().asFile)

            logger.lifecycle("JIkvict Test Results:")
            logger.lifecycle("Total tests: ${testSuiteResult.testResults.size}")
            logger.lifecycle("Passed tests: ${testSuiteResult.testResults.count { it.passed }}")
            logger.lifecycle("Failed tests: ${testSuiteResult.testResults.count { !it.passed }}")

        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    private fun findTestClasses(testDir: File): List<String> {
        val testClasses = mutableListOf<String>()
        if (!testDir.exists()) return testClasses

        testDir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .forEach { file ->
                val relativePath = testDir.toPath().relativize(file.toPath()).toString()
                val className = relativePath.replace("/", ".").replace("\\", ".").removeSuffix(".class")

                try {
                    val clazz = Thread.currentThread().contextClassLoader.loadClass(className)
                    val hasTestMethods = clazz.methods.any { method ->
                        method.annotations.any { it.annotationClass.simpleName == "Test" }
                    }
                    if (hasTestMethods) {
                        testClasses.add(className)
                        logger.lifecycle("Found test class: $className")
                    }
                } catch (e: Exception) {
                    logger.debug("Could not load class $className: ${e.message}")
                }
            }

        return testClasses
    }

    private fun writeResultsToJson(testSuiteResult: TestSuiteResult, outputFile: File) {
        val mapper = ObjectMapper().registerKotlinModule()
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, testSuiteResult)
        logger.lifecycle("Test results written to: ${outputFile.absolutePath}")
    }
}