package org.jikvict.testing.model

/**
 * Represents the result of a single test execution.
 *
 * @property testName The fully qualified name of the test method.
 * @property displayName The display name of the test.
 * @property possiblePoints The maximum number of points that can be earned for this test.
 * @property earnedPoints The actual points earned for this test (0 if failed).
 * @property passed Whether the test passed or failed.
 * @property logs Collection of log messages captured during test execution.
 */
data class TestResult(
    val testName: String,
    val displayName: String,
    val possiblePoints: Int,
    val earnedPoints: Int,
    val passed: Boolean,
    val logs: List<String> = emptyList()
)

/**
 * Represents the aggregated results of all tests.
 *
 * @property testResults List of individual test results.
 * @property totalPossiblePoints Total possible points across all tests.
 * @property totalEarnedPoints Total points earned across all tests.
 */
data class TestSuiteResult(
    val testResults: List<TestResult>,
    val totalPossiblePoints: Int,
    val totalEarnedPoints: Int,
)