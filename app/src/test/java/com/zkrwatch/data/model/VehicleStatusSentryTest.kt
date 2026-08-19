package com.zkrwatch.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Stub-only tests for sentry (surveillance) status parsing and the optimistic
 * toggle the UI applies after a successful RSM command. No network: payloads
 * match what [com.zkrwatch.data.repo.ZkrRepository.statusWithExtras] merges
 * from `status/latest` + `getVehicleState`.
 */
class VehicleStatusSentryTest {

    @Test
    fun sentry_on_from_string_one() {
        assertEquals(true, statusFromRemote("1").sentryActive)
    }

    @Test
    fun sentry_on_from_numeric_one() {
        assertEquals(true, statusFromRemote(1).sentryActive)
        assertEquals(true, statusFromRemote(1.0).sentryActive)
    }

    @Test
    fun sentry_on_from_boolean_and_words() {
        assertEquals(true, statusFromRemote(true).sentryActive)
        assertEquals(true, statusFromRemote("true").sentryActive)
        assertEquals(true, statusFromRemote("ON").sentryActive)
        assertEquals(true, statusFromRemote("1.0").sentryActive)
    }

    @Test
    fun sentry_off_from_zero_false_and_words() {
        assertEquals(false, statusFromRemote("0").sentryActive)
        assertEquals(false, statusFromRemote(0).sentryActive)
        assertEquals(false, statusFromRemote(0.0).sentryActive)
        assertEquals(false, statusFromRemote(false).sentryActive)
        assertEquals(false, statusFromRemote("false").sentryActive)
        assertEquals(false, statusFromRemote("off").sentryActive)
        assertEquals(false, statusFromRemote("0.0").sentryActive)
    }

    @Test
    fun sentry_unknown_when_remote_state_missing() {
        val noRemote = VehicleStatus.from(stubLatest(soc = 55))
        assertNull(noRemote.sentryActive)
        assertEquals(55, noRemote.socPercent)
    }

    @Test
    fun sentry_unknown_when_vstd_absent_or_blank() {
        assertNull(statusFromRemote(null).sentryActive)
        assertNull(statusFromRemote("").sentryActive)
        assertNull(statusFromRemote("maybe").sentryActive)
    }

    @Test
    fun merge_does_not_clobber_soc_or_lock() {
        val status = VehicleStatus.from(
            merge(
                stubLatest(soc = 72, locked = "1"),
                stubRemote(vstd = "0"),
            ),
        )
        assertEquals(72, status.socPercent)
        assertEquals(true, status.locked)
        assertEquals(false, status.sentryActive)
    }

    @Test
    fun toggle_off_to_on_matches_viewmodel_optimistic_update() {
        val off = statusFromRemote("0")
        val flipped = off.copy(sentryActive = !(off.sentryActive ?: false))
        assertEquals(true, flipped.sentryActive)
    }

    @Test
    fun toggle_on_to_off_matches_viewmodel_optimistic_update() {
        val on = statusFromRemote("1")
        val flipped = on.copy(sentryActive = !(on.sentryActive ?: false))
        assertEquals(false, flipped.sentryActive)
    }

    @Test
    fun toggle_unknown_treats_as_off_so_first_tap_arms() {
        val unknown = VehicleStatus.from(stubLatest())
        assertNull(unknown.sentryActive)
        val flipped = unknown.copy(sentryActive = !(unknown.sentryActive ?: false))
        assertEquals(true, flipped.sentryActive)
    }

    /** Same merge [com.zkrwatch.data.repo.ZkrRepository.statusWithExtras] performs. */
    private fun statusFromRemote(vstd: Any?): VehicleStatus =
        VehicleStatus.from(merge(stubLatest(), stubRemote(vstd)))

    private fun merge(
        latest: Map<String, Any?>,
        remote: Map<String, Any?>,
    ): Map<String, Any?> {
        val data = latest.toMutableMap()
        @Suppress("UNCHECKED_CAST")
        val avs = ((data["additionalVehicleStatus"] as? Map<String, Any?>) ?: emptyMap())
            .toMutableMap()
        avs["remoteControlState"] = remote
        data["additionalVehicleStatus"] = avs
        return data
    }

    private fun stubLatest(soc: Int = 64, locked: String = "1"): Map<String, Any?> = mapOf(
        "additionalVehicleStatus" to mapOf(
            "electricVehicleStatus" to mapOf("chargeLevel" to soc.toDouble()),
            "drivingSafetyStatus" to mapOf("centralLockingStatus" to locked),
        ),
    )

    private fun stubRemote(vstd: Any?): Map<String, Any?> {
        val body = mutableMapOf<String, Any?>(
            "journalLogState" to "0",
            "gpsstate" to "1",
        )
        if (vstd != null) body["vstdModeState"] = vstd
        return body
    }
}
