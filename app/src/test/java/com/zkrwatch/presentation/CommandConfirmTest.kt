package com.zkrwatch.presentation

import com.zkrwatch.data.model.VehicleStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure decision logic behind "poll until the car reflects the command":
 * which commands are observable, and whether a fresh status has landed the change.
 */
class CommandConfirmTest {

    @Test
    fun observable_covers_state_changing_commands_only() {
        listOf(CommandKind.LOCK, CommandKind.UNLOCK, CommandKind.CLIMATE, CommandKind.SENTRY, CommandKind.CHARGING)
            .forEach { assertTrue("$it should be observable", ZkrViewModel.observable(it)) }
        listOf(CommandKind.TRUNK, CommandKind.FLASH)
            .forEach { assertFalse("$it should not be observable", ZkrViewModel.observable(it)) }
    }

    @Test
    fun lock_lands_only_when_car_reports_locked() {
        assertTrue(ZkrViewModel.landed(CommandKind.LOCK, status(locked = false), status(locked = true)))
        assertFalse(ZkrViewModel.landed(CommandKind.LOCK, status(locked = false), status(locked = false)))
        // Unknown lock state is not a confirmation.
        assertFalse(ZkrViewModel.landed(CommandKind.LOCK, status(locked = false), status(locked = null)))
    }

    @Test
    fun unlock_lands_only_when_car_reports_unlocked() {
        assertTrue(ZkrViewModel.landed(CommandKind.UNLOCK, status(locked = true), status(locked = false)))
        assertFalse(ZkrViewModel.landed(CommandKind.UNLOCK, status(locked = true), status(locked = true)))
    }

    @Test
    fun climate_toggle_lands_when_it_flips_from_the_pre_tap_value() {
        // Was off -> expect on.
        assertTrue(ZkrViewModel.landed(CommandKind.CLIMATE, status(climate = false), status(climate = true)))
        assertFalse(ZkrViewModel.landed(CommandKind.CLIMATE, status(climate = false), status(climate = false)))
        // Was on -> expect off.
        assertTrue(ZkrViewModel.landed(CommandKind.CLIMATE, status(climate = true), status(climate = false)))
    }

    @Test
    fun charging_toggle_lands_when_it_flips() {
        assertTrue(ZkrViewModel.landed(CommandKind.CHARGING, status(charging = false), status(charging = true)))
        assertFalse(ZkrViewModel.landed(CommandKind.CHARGING, status(charging = false), status(charging = false)))
    }

    @Test
    fun sentry_toggle_lands_when_it_flips() {
        assertTrue(ZkrViewModel.landed(CommandKind.SENTRY, status(sentry = true), status(sentry = false)))
        assertFalse(ZkrViewModel.landed(CommandKind.SENTRY, status(sentry = true), status(sentry = true)))
    }

    @Test
    fun unknown_pre_tap_state_is_treated_as_off_matching_the_toggle() {
        // before=null -> toggle sends "on" -> lands when the car reads on.
        assertTrue(ZkrViewModel.landed(CommandKind.CLIMATE, null, status(climate = true)))
        assertTrue(ZkrViewModel.landed(CommandKind.CHARGING, null, status(charging = true)))
    }

    private fun status(
        locked: Boolean? = null,
        climate: Boolean? = null,
        sentry: Boolean? = null,
        charging: Boolean? = null,
    ) = VehicleStatus(
        socPercent = null,
        rangeKm = null,
        odometerKm = null,
        timeToFullMinutes = null,
        locked = locked,
        climateActive = climate,
        interiorTempC = null,
        charging = charging,
        pluggedIn = null,
        chargePowerKw = null,
        sentryActive = sentry,
    )
}
