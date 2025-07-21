# JIkvict Testing Library

JIkvict Testing Library is a Kotlin/Java library and Gradle plugin for enhancing JUnit tests with point-based scoring. It allows you to assign point values to tests and calculate the total points earned based on test results.

## Features

- Assign point values to tests using the `@JIkvictTest` annotation
- Automatically calculate points earned based on test results
- Generate detailed JSON reports with test information
- Works with both Java and Kotlin test classes
- Captures test logs for detailed reporting
- Provides summary statistics (total points, percentage earned, etc.)

## Installation

### Gradle (Kotlin DSL)

Add the following to your `build.gradle.kts` file:

```kotlin
// Add the local Maven repository
repositories {
    mavenLocal()
    mavenCentral()
}

// Apply the plugin
plugins {
    id("org.jikvict.testing") version "1.0-SNAPSHOT"
}

// Add the library dependency
dependencies {
    testImplementation("org.jikvict:JIkvictTestingLibrary:1.0-SNAPSHOT")
}
```

### Gradle (Groovy DSL)

Add the following to your `build.gradle` file:

```groovy
// Add the local Maven repository
repositories {
    mavenLocal()
    mavenCentral()
}

// Apply the plugin
plugins {
    id 'org.jikvict.testing' version '1.0-SNAPSHOT'
}

// Add the library dependency
dependencies {
    testImplementation 'org.jikvict:JIkvictTestingLibrary:1.0-SNAPSHOT'
}
```

## Usage

### Annotating Tests

Add the `@JIkvictTest` annotation to your test methods to assign point values:

#### Java

```java
import org.jikvict.testing.annotation.JIkvictTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MyJavaTest {
    
    @Test
    @JIkvictTest(points = 10)
    public void testAddition() {
        assertEquals(4, 2 + 2);
    }
    
    @Test
    @JIkvictTest(points = 20)
    public void testMultiplication() {
        assertEquals(6, 2 * 3);
    }
}
```

#### Kotlin

```kotlin
// Import statements
// import org.jikvict.testing.annotation.JIkvictTest
// import org.junit.jupiter.api.Test
// import org.junit.jupiter.api.Assertions.assertEquals

class MyKotlinTest {
    
    @Test
    @JIkvictTest(points = 15)
    fun testSubtraction() {
        assertEquals(1, 3 - 2)
    }
    
    @Test
    @JIkvictTest(points = 25)
    fun testDivision() {
        assertEquals(2, 6 / 3)
    }
}
```

### Running Tests and Calculating Points

Run the `runJIkvictTests` Gradle task to execute the tests and calculate points:

```bash
./gradlew runJIkvictTests
```

This will:
1. Run all tests in your project
2. Calculate points based on test results
3. Generate a JSON report with detailed test information
4. Print a summary of the results

### Viewing Results

The test results are written to `build/jikvict-results.json` by default. The JSON file contains detailed information about each test, including:

- Test name
- Display name
- Possible points
- Earned points
- Pass/fail status
- Test logs

Example JSON output:

```json
{
  "testResults": [
    {
      "testName": "com.example.MyTest#testAddition",
      "displayName": "testAddition()",
      "possiblePoints": 10,
      "earnedPoints": 10,
      "passed": true,
      "logs": [
        "[DEBUG_LOG] Test executed successfully"
      ]
    },
    {
      "testName": "com.example.MyTest#testMultiplication",
      "displayName": "testMultiplication()",
      "possiblePoints": 20,
      "earnedPoints": 0,
      "passed": false,
      "logs": [
        "[DEBUG_LOG] Test failed"
      ]
    }
  ],
  "totalPossiblePoints": 30,
  "totalEarnedPoints": 10,
  "percentageEarned": 33.33333333333333
}
```

## Configuration

The `runJIkvictTests` task can be configured in your `build.gradle.kts` file:

```kotlin
tasks.named<org.jikvict.testing.gradle.JIkvictTestTask>("runJIkvictTests") {
    // Specify a package to run tests from (optional)
    testPackage.set("com.example.tests")
    
    // Specify a custom output file (optional)
    outputFile.set(layout.buildDirectory.file("custom-results.json"))
}
```

## Logging

You can add debug logs to your tests that will be captured in the test results:

#### Java

```java
@Test
@JIkvictTest(points = 10)
public void testWithLogs() {
    System.out.println("[DEBUG_LOG] Starting test");
    // Test code
    System.out.println("[DEBUG_LOG] Test completed");
}
```

#### Kotlin

```kotlin
// Import statements are omitted for brevity
// @Test annotation from JUnit
// @JIkvictTest annotation from our library

@Test
@JIkvictTest(points = 15)
fun testWithLogs() {
    println("[DEBUG_LOG] Starting test")
    // Test code
    println("[DEBUG_LOG] Test completed")
}
```

## API Reference

### JIkvictTest Annotation

```kotlin
// Definition of the JIkvictTest annotation
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class JIkvictTest(val points: Int = 0)
```

- `points`: The number of points awarded if the test passes successfully (default: 0)

### TestResult Class

```kotlin
// Data class for storing individual test results
data class TestResult(
    val testName: String,        // Fully qualified name of the test method
    val displayName: String,     // Display name of the test
    val possiblePoints: Int,     // Maximum points that can be earned
    val earnedPoints: Int,       // Actual points earned (0 if failed)
    val passed: Boolean,         // Whether the test passed or failed
    val logs: List<String> = emptyList() // Log messages captured during execution
)
```

### TestSuiteResult Class

```kotlin
// Data class for storing aggregated test results
data class TestSuiteResult(
    val testResults: List<TestResult>,    // List of individual test results
    val totalPossiblePoints: Int,         // Total possible points across all tests
    val totalEarnedPoints: Int            // Total points earned across all tests
) {
    // Calculated property for percentage of points earned
    val percentageEarned: Double
        get() = if (totalPossiblePoints > 0) {
            (totalEarnedPoints.toDouble() / totalPossiblePoints) * 100
        } else {
            0.0
        }
}
```

## Examples

See the sample tests in the repository for examples of how to use the library:

- [JavaSampleTest.java](src/test/java/org/jikvict/testing/sample/JavaSampleTest.java)
- [KotlinSampleTest.kt](src/test/kotlin/org/jikvict/testing/sample/KotlinSampleTest.kt)

## License

This project is licensed under the MIT License - see the LICENSE file for details.