package com.zkrwatch.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Ordered enabled-slot behaviour behind the edit-mode grid: reorder, remove, and the
 * settings toggle. No prefs store (null) so the view model starts from the defaults.
 */
class SlotOrderTest {

    private fun vm() = ZkrViewModel(repo = null, statusCache = null, uiPrefs = null)

    @Test
    fun defaults_are_the_visible_slots_in_enum_order() {
        assertEquals(
            listOf(ActionSlot.LOCK, ActionSlot.TRUNK, ActionSlot.CLIMATE, ActionSlot.SENTRY),
            vm().enabledSlots.value,
        )
    }

    @Test
    fun move_reorders_and_preserves_membership() {
        val vm = vm()
        val before = vm.enabledSlots.value
        vm.moveSlot(0, before.lastIndex)
        val after = vm.enabledSlots.value
        assertEquals(before[0], after.last())
        assertEquals(before.toSet(), after.toSet())
        assertEquals(before.size, after.size)
    }

    @Test
    fun move_out_of_bounds_is_ignored() {
        val vm = vm()
        val before = vm.enabledSlots.value
        vm.moveSlot(0, 99)
        vm.moveSlot(-1, 1)
        vm.moveSlot(1, 1)
        assertEquals(before, vm.enabledSlots.value)
    }

    @Test
    fun remove_drops_the_slot() {
        val vm = vm()
        val slot = vm.enabledSlots.value.first()
        vm.removeSlot(slot)
        assertFalse(slot in vm.enabledSlots.value)
    }

    @Test
    fun toggle_appends_then_removes() {
        val vm = vm()
        assertFalse(ActionSlot.FLASH in vm.enabledSlots.value)
        vm.toggleSlot(ActionSlot.FLASH)
        assertEquals(ActionSlot.FLASH, vm.enabledSlots.value.last())
        vm.toggleSlot(ActionSlot.FLASH)
        assertFalse(ActionSlot.FLASH in vm.enabledSlots.value)
    }
}
