package org.jikvict.testing.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jikvict.testing.junit.JIkvictTestExecutionListener
import org.jikvict.testing.model.TestSuiteResult
import org.junit.platform.engine.discovery.DiscoverySelectors.*
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener

/**
 * Gradle plugin for running tests with JIkvict testing framework.
 * This plugin adds a task for running tests and calculating points.
 */
@Suppress("unused")
class JIkvictTestingPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Register the JIkvictTest task
        project.tasks.register("runJIkvictTests", JIkvictTestTask::class.java) { task ->
            task.description = "Runs tests with JIkvict testing framework and calculates points"
            task.group = "verification"

            // Set default values
            task.testPackage.set("") // Default to empty, which means all packages
            task.outputFile.set(project.layout.buildDirectory.file("jikvict-results.json"))

            // Ensure test classes are compiled before running
            task.dependsOn("testClasses")
        }
    }
}

/**
 * Custom Gradle task for running tests with JIkvict testing framework.
 * This task is configured to always run and not be skipped by Gradle's up-to-date checks.
 */
abstract class JIkvictTestTask : DefaultTask() {

    init {
        // Disable Gradle's up-to-date check to ensure this task always runs
        // This prevents the task from being skipped even if inputs/outputs haven't changed
        outputs.upToDateWhen { false }
    }

    // Task inputs
    @Input
    @Optional
    val testPackage: Property<String?> = project.objects.property(String::class.java)

    // Task outputs
    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun runTests() {
        // Ensure build directory exists
        val buildDir = project.layout.buildDirectory.asFile.get()

        // Check if test classes directory exists
        val testClassesDir = File(buildDir, "classes/kotlin/test")
        val javaTestClassesDir = File(buildDir, "classes/java/test")

        logger.lifecycle("Looking for test classes in:")
        logger.lifecycle("  Kotlin: ${testClassesDir.absolutePath} (exists: ${testClassesDir.exists()})")
        logger.lifecycle("  Java: ${javaTestClassesDir.absolutePath} (exists: ${javaTestClassesDir.exists()})")

        if (!testClassesDir.exists() && !javaTestClassesDir.exists()) {
            logger.lifecycle("No compiled test classes found. Make sure to run 'testClasses' task first.")
            return
        }

        // Set up classpath for test execution
        val testClasspath = mutableListOf<URL>()
        
        // Add test classes directories to classpath
        if (javaTestClassesDir.exists()) {
            testClasspath.add(javaTestClassesDir.toURI().toURL())
        }
        if (testClassesDir.exists()) {
            testClasspath.add(testClassesDir.toURI().toURL())
        }
        
        // Add main classes to classpath
        val mainClassesDir = File(buildDir, "classes/java/main")
        val kotlinMainClassesDir = File(buildDir, "classes/kotlin/main")
        if (mainClassesDir.exists()) {
            testClasspath.add(mainClassesDir.toURI().toURL())
        }
        if (kotlinMainClassesDir.exists()) {
            testClasspath.add(kotlinMainClassesDir.toURI().toURL())
        }

        // Get project dependencies
        val testRuntimeClasspath = project.configurations.getByName("testRuntimeClasspath")
        testRuntimeClasspath.files.forEach { file ->
            if (file.exists()) {
                testClasspath.add(file.toURI().toURL())
            }
        }

        // Create custom class loader with all necessary classes
        val classLoader = URLClassLoader(testClasspath.toTypedArray(), Thread.currentThread().contextClassLoader)
        val originalClassLoader = Thread.currentThread().contextClassLoader
        
        try {
            // Set the custom class loader as context class loader
            Thread.currentThread().contextClassLoader = classLoader

            // Create JUnit launcher
            val launcher = LauncherFactory.create()

            // Create listeners
            val summaryListener = SummaryGeneratingListener()
            val jikvictListener = JIkvictTestExecutionListener()

            // Register listeners
            launcher.registerTestExecutionListeners(summaryListener, jikvictListener)

            // Create discovery request
            val requestBuilder = LauncherDiscoveryRequestBuilder.request()

            // If a specific package is provided, use it; otherwise, discover all tests
            if (testPackage.isPresent && testPackage.get()?.isNotEmpty() == true) {
                logger.lifecycle("Scanning package: ${testPackage.get()}")
                requestBuilder.selectors(selectPackage(testPackage.get()))
            } else {
                // When no package is specified, scan the classpath for all test classes
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
                    
                    // Also try to discover by class selector if available
                    try {
                        // Try to find test classes explicitly
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

            // Add engines
            requestBuilder.configurationParameter("junit.jupiter.testinstance.lifecycle.default", "per_class")

            val request: LauncherDiscoveryRequest = requestBuilder.build()

            // Execute tests
            logger.lifecycle("Executing test discovery and execution...")
            launcher.execute(request)

            // Get test results
            val testSuiteResult = jikvictListener.getTestSuiteResult()

            // Write results to JSON file
            writeResultsToJson(testSuiteResult, outputFile.get().asFile)

            // Print summary
            logger.lifecycle("JIkvict Test Results:")
            logger.lifecycle("Total tests: ${testSuiteResult.testResults.size}")
            logger.lifecycle("Passed tests: ${testSuiteResult.testResults.count { it.passed }}")
            logger.lifecycle("Failed tests: ${testSuiteResult.testResults.count { !it.passed }}")
            logger.lifecycle(
                "Points earned: ${testSuiteResult.totalEarnedPoints}/${testSuiteResult.totalPossiblePoints} (${
                    String.format("%.2f", testSuiteResult.percentageEarned)
                }%)"
            )
            
        } finally {
            // Restore original class loader
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
                
                // Load the class and check if it has test methods
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
                    // Ignore classes that can't be loaded
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