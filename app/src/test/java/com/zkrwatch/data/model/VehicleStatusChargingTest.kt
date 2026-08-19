package com.zkrwatch.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Charging detection from `status/latest`. The `isCharging` flag is unreliable —
 * observed on a live car reading false while ~2.3 kW of AC current was flowing —
 * so [VehicleStatus.from] also treats measurable charge current as "charging".
 */
class VehicleStatusChargingTest {

    @Test
    fun ac_current_flowing_counts_as_charging_even_when_isCharging_false() {
        // Real payload shape: isCharging=false, but 230 V * 9.8 A ~= 2.25 kW in.
        val status = VehicleStatus.from(ev("isCharging" to false, "chargeUAct" to 230.0, "chargeIAct" to 9.8))
        assertEquals(true, status.charging)
        assertEquals(2.254, status.chargePowerKw!!, 0.01)
    }

    @Test
    fun isCharging_flag_alone_still_counts_as_charging() {
        val status = VehicleStatus.from(ev("isCharging" to true))
        assertEquals(true, status.charging)
        // No current reported -> no power figure, but still charging.
        assertNull(status.chargePowerKw)
    }

    @Test
    fun dc_pile_current_flowing_counts_as_charging() {
        val status = VehicleStatus.from(ev("isCharging" to false, "dcChargePileUAct" to 400.0, "dcChargePileIAct" to 100.0))
        assertEquals(true, status.charging)
        assertTrue(status.chargePowerKw!! > 39.0)
    }

    @Test
    fun idle_car_is_not_charging() {
        val status = VehicleStatus.from(
            ev("isCharging" to false, "chargeUAct" to 0.0, "chargeIAct" to 0.0, "dcChargePileIAct" to 0.0),
        )
        assertEquals(false, status.charging)
        assertNull(status.chargePowerKw)
    }

    @Test
    fun trickle_current_below_threshold_is_not_charging() {
        // 230 V * 0.1 A = 23 W, below the 100 W floor -> sensor noise, not charging.
        val status = VehicleStatus.from(ev("isCharging" to false, "chargeUAct" to 230.0, "chargeIAct" to 0.1))
        assertEquals(false, status.charging)
        assertNull(status.chargePowerKw)
    }

    private fun ev(vararg fields: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "additionalVehicleStatus" to mapOf(
            "electricVehicleStatus" to mapOf("chargeLevel" to 35.6, *fields),
        ),
    )
}
