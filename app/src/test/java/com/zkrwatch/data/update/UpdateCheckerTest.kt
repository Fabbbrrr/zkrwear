package com.zkrwatch.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Version comparison behind "is a newer release available?". */
class UpdateCheckerTest {

    @Test
    fun newer_when_any_component_increases() {
        assertTrue(UpdateChecker.isNewer("1.2.0", "1.1.0")) // minor
        assertTrue(UpdateChecker.isNewer("1.2.1", "1.2.0")) // patch
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9")) // major
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0")) // numeric, not lexical
    }

    @Test
    fun not_newer_when_same_or_older() {
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.2.1"))
    }

    @Test
    fun tolerates_v_prefix_and_missing_components() {
        assertTrue(UpdateChecker.isNewer("v1.3.0", "1.2.9"))
        assertFalse(UpdateChecker.isNewer("v1.2.0", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.2", "1.2.0")) // 1.2 == 1.2.0
        assertTrue(UpdateChecker.isNewer("1.2.0.1", "1.2.0")) // extra component
    }
}
