package org.jikvict.testing.annotation

import org.junit.jupiter.api.Test

/**
 * Annotation for marking test methods with point values.
 * Tests marked with this annotation will be processed by the JIkvict testing framework
 * to calculate total points earned.
 *
 * @param points The number of points awarded if the test passes successfully.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
@Test
annotation class JIkvictTest(val points: Int = 0)