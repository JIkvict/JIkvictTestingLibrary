package org.jikvict.testing.junit

import org.jikvict.testing.annotation.JIkvictTest
import org.jikvict.testing.model.TestResult
import org.jikvict.testing.model.TestSuiteResult
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import org.jikvict.testing.model.TestSuiteResult.Companion.calculatePercentageEarned

/**
 * JUnit TestExecutionListener that processes JIkvictTest annotations and collects test results.
 * This listener works with both Java and Kotlin test classes.
 */
class JIkvictTestExecutionListener : TestExecutionListener {
    
    private val testResults = ConcurrentHashMap<String, TestResult>()
    private val testLogs = ConcurrentHashMap<String, MutableList<String>>()
    
    // Fields for capturing System.out and System.err
    private val originalOut = System.out
    private val originalErr = System.err
    private var currentTestId: String? = null
    
    // Streams for standard output
    private val outputStream = ByteArrayOutputStream()
    private val printStream = PrintStream(outputStream)
    
    // Streams for error output
    private val errorStream = ByteArrayOutputStream()
    private val errorPrintStream = PrintStream(errorStream)
    
    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        // Clear previous results when a new test plan starts
        testResults.clear()
        testLogs.clear()
        println("JIkvict: Test plan execution started with ${testPlan.countTestIdentifiers { it.isTest }} tests")
    }
    
    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (testIdentifier.isTest) {
            // Initialize logs collection for this test
            testLogs[testIdentifier.uniqueId] = mutableListOf()
            println("JIkvict: Test started: ${testIdentifier.displayName}")
            
            // Set current test ID and redirect System.out and System.err
            currentTestId = testIdentifier.uniqueId
            
            // Clear any previous output
            outputStream.reset()
            errorStream.reset()
            
            // Redirect standard and error output streams
            System.setOut(printStream)
            System.setErr(errorPrintStream)
        }
    }
    
    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        if (!testIdentifier.isTest) return
        
        // Capture any output and restore original System.out and System.err
        if (currentTestId == testIdentifier.uniqueId) {
            // Restore original streams
            System.setOut(originalOut)
            System.setErr(originalErr)
            
            // Process standard output
            val capturedOutput = outputStream.toString()
            if (capturedOutput.isNotEmpty()) {
                capturedOutput.split("\n").forEach { line ->
                    if (line.isNotEmpty()) {
                        addLog(testIdentifier.uniqueId, "[STDOUT] $line")
                    }
                }
            }
            
            // Process error output
            val capturedErrorOutput = errorStream.toString()
            if (capturedErrorOutput.isNotEmpty()) {
                capturedErrorOutput.split("\n").forEach { line ->
                    if (line.isNotEmpty()) {
                        addLog(testIdentifier.uniqueId, "[STDERR] $line")
                    }
                }
            }
            
            // Reset for next test
            currentTestId = null
            outputStream.reset()
            errorStream.reset()
        }
        
        println("JIkvict: Test finished: ${testIdentifier.displayName} with status ${testExecutionResult.status}")
        
        // Capture exception message if test failed
        if (testExecutionResult.status != TestExecutionResult.Status.SUCCESSFUL) {
            testExecutionResult.throwable.ifPresent { throwable ->
                // Add exception message to logs
                addLog(testIdentifier.uniqueId, "[EXCEPTION] ${throwable.javaClass.simpleName}: ${throwable.message}")
                
                // Add stack trace for more detailed information
                val stackTraceOutput = ByteArrayOutputStream()
                throwable.printStackTrace(PrintStream(stackTraceOutput))
                val stackTrace = stackTraceOutput.toString()
                
                // Add each line of the stack trace to the logs
                stackTrace.split("\n").forEach { line ->
                    if (line.isNotEmpty()) {
                        addLog(testIdentifier.uniqueId, "[STACK_TRACE] $line")
                    }
                }
            }
        }
        
        val testClass = getTestClass(testIdentifier)
        val testMethod = getTestMethod(testIdentifier)
        
        println("JIkvict: Parsed - Class: $testClass, Method: $testMethod")
        
        if (testClass != null && testMethod != null) {
            try {
                // Use Java reflection to find the test method and check for annotations
                // Use the current thread's context classloader to load the class
                val clazz = Thread.currentThread().contextClassLoader.loadClass(testClass)
                
                // Find the method by name (might need to handle overloaded methods better in a real implementation)
                val method = findMethodByName(clazz, testMethod)
                
                // Get points from annotation if present
                val points = getPointsFromAnnotation(method)
                val passed = testExecutionResult.status == TestExecutionResult.Status.SUCCESSFUL
                val earnedPoints = if (passed) points else 0
                
                println("JIkvict: Method found: ${method?.name}, Points: $points, Passed: $passed")
                
                // Create test result
                val result = TestResult(
                    testName = "$testClass#$testMethod",
                    displayName = testIdentifier.displayName,
                    possiblePoints = points,
                    earnedPoints = earnedPoints,
                    passed = passed,
                    logs = testLogs[testIdentifier.uniqueId] ?: emptyList()
                )
                
                testResults[testIdentifier.uniqueId] = result
            } catch (e: Exception) {
                // Log the error but continue processing other tests
                println("Error processing test result for ${testIdentifier.displayName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Finds a method by name in the given class or its superclasses.
     * This implementation searches through all methods regardless of visibility.
     */
    private fun findMethodByName(clazz: Class<*>, methodName: String): Method? {
        // Search through the class hierarchy
        var currentClass: Class<*>? = clazz
        while (currentClass != null) {
            try {
                // Look for the method in declared methods (includes all visibility levels)
                val method = currentClass.declaredMethods.find { it.name == methodName }
                if (method != null) {
                    // Make the method accessible regardless of its visibility
                    if (!method.isAccessible) {
                        method.isAccessible = true
                    }
                    return method
                }
            } catch (_: Exception) {
                // Ignore exceptions and continue searching
            }
            
            // Move up to the superclass
            currentClass = currentClass.superclass
        }
        
        // If not found in the hierarchy, try one last attempt with public methods
        // This is a fallback for cases where the method might be from an interface
        return try {
            clazz.methods.find { it.name == methodName }?.also { 
                if (!it.isAccessible) {
                    it.isAccessible = true
                }
            }
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Gets the points value from the JIkvictTest annotation if present.
     */
    private fun getPointsFromAnnotation(method: Method?): Int {
        if (method == null) return 0
        
        val annotation = method.getAnnotation(JIkvictTest::class.java)
        return annotation?.points ?: 0
    }
    
    /**
     * Adds a log message for the current test.
     * This method is called by the JIkvictTestLogger.
     */
    fun addLog(testId: String, message: String) {
        testLogs.computeIfPresent(testId) { _, logs ->
            logs.add(message)
            logs
        }
    }
    
    /**
     * Gets the aggregated test suite result.
     */
    fun getTestSuiteResult(): TestSuiteResult {
        val results = testResults.values.toList()
        val totalPossiblePoints = results.sumOf { it.possiblePoints }
        val totalEarnedPoints = results.sumOf { it.earnedPoints }
        
        return TestSuiteResult(
            testResults = results,
            totalPossiblePoints = totalPossiblePoints,
            totalEarnedPoints = totalEarnedPoints,
            percentageEarned = calculatePercentageEarned(totalEarnedPoints, totalPossiblePoints)
        )
    }
    
    private fun getTestClass(testIdentifier: TestIdentifier): String? {
        return testIdentifier.uniqueId
            .split("/")
            .firstOrNull { it.contains("class:") }
            ?.substringAfter("class:")
            ?.removeSuffix("]")
    }

    private fun getTestMethod(testIdentifier: TestIdentifier): String? {
        return testIdentifier.uniqueId
            .split("/")
            .firstOrNull { it.contains("method:") || it.contains("test-template-invocation:") }
            ?.substringAfter("method:")
            ?.substringAfter("test-template-invocation:")
            ?.removeSuffix("]")
            ?.substringBefore("(")
    }

}