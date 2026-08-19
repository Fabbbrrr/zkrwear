package com.zkrwatch.data.model

import com.zkrwatch.data.net.boolAt
import com.zkrwatch.data.net.numAt
import com.zkrwatch.data.net.path

/**
 * The handful of fields the watch UI needs, parsed from the `data` object of
 * `vehicle/status/latest`. JSON paths are the ones the HA integration reads
 * (sensor.py / lock.py / climate.py).
 */
data class VehicleStatus(
    val socPercent: Int?,
    val rangeKm: Int?,
    val odometerKm: Int?,
    val timeToFullMinutes: Int?,
    val locked: Boolean?,
    val climateActive: Boolean?,
    val interiorTempC: Double?,
    val charging: Boolean?,
    val pluggedIn: Boolean?,
    val chargePowerKw: Double?,
    val sentryActive: Boolean?,
) {
    companion object {
        /** Min charge power (kW) that counts as "actually charging" — filters sensor noise. */
        private const val CHARGE_KW_MIN = 0.1

        fun from(data: Map<String, Any?>): VehicleStatus {
            val avs = "additionalVehicleStatus"
            val ev = "electricVehicleStatus"
            // Lock lives under drivingSafetyStatus; value is "1" (locked) / "0".
            val locking = data.path(avs, "drivingSafetyStatus", "centralLockingStatus")?.toString()?.trim()

            // Charge power (kW) from instantaneous volts × amps: AC (chargeUAct/chargeIAct)
            // or DC pile (dcChargePileUAct/dcChargePileIAct).
            val acKw = (data.numAt(avs, ev, "chargeUAct") ?: 0.0) * (data.numAt(avs, ev, "chargeIAct") ?: 0.0) / 1000.0
            val dcKw = (data.numAt(avs, ev, "dcChargePileUAct") ?: 0.0) * (data.numAt(avs, ev, "dcChargePileIAct") ?: 0.0) / 1000.0
            val chargeKw = maxOf(acKw, dcKw)

            // `isCharging` alone is unreliable: some cars report it false while actively
            // AC-charging (observed: isCharging=false with chargeUAct=230, chargeIAct=9.8,
            // i.e. ~2.3 kW flowing into the pack, and a live timeToFullyCharged). Treat
            // the car as charging when the flag is set OR when measurable current is
            // actually going in, so the charging cue isn't silently missed.
            val charging = data.boolAt(avs, ev, "isCharging") == true || chargeKw > CHARGE_KW_MIN
            val powerKw = chargeKw.takeIf { charging && it > CHARGE_KW_MIN }

            return VehicleStatus(
                socPercent = data.numAt(avs, ev, "chargeLevel")?.toInt(),
                rangeKm = data.numAt(avs, ev, "distanceToEmptyOnBatteryOnly")?.toInt(),
                odometerKm = data.numAt(avs, "maintenanceStatus", "odometer")?.toInt(),
                timeToFullMinutes = data.numAt(avs, ev, "timeToFullyCharged")?.toInt(),
                locked = when (locking) {
                    "1", "1.0" -> true
                    "0", "0.0" -> false
                    else -> null
                },
                climateActive = data.boolAt(avs, "climateStatus", "preClimateActive"),
                interiorTempC = data.numAt(avs, "climateStatus", "interiorTemp"),
                charging = charging,
                pluggedIn = data.boolAt(avs, ev, "isPluggedIn"),
                chargePowerKw = powerKw,
                // Sentry: remoteControlState.vstdModeState (merged in by
                // ZkrRepository.statusWithExtras). Absent -> null/unknown.
                sentryActive = sentryState(data.path(avs, "remoteControlState", "vstdModeState")),
            )
        }

        /** "1" / 1 / true / on → armed; "0" / 0 / false / off → disarmed; else unknown. */
        private fun sentryState(v: Any?): Boolean? = when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> when (v.trim().lowercase()) {
                "1", "1.0", "true", "on" -> true
                "0", "0.0", "false", "off" -> false
                else -> v.toDoubleOrNull()?.let { it.toInt() != 0 }
            }
            else -> null
        }
    }
}
