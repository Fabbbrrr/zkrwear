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
) {
    companion object {
        fun from(data: Map<String, Any?>): VehicleStatus {
            val avs = "additionalVehicleStatus"
            val ev = "electricVehicleStatus"
            // Lock lives under drivingSafetyStatus; value is "1" (locked) / "0".
            val locking = data.path(avs, "drivingSafetyStatus", "centralLockingStatus")?.toString()?.trim()
            val charging = data.boolAt(avs, ev, "isCharging")

            // Charge power (kW) from instantaneous volts × amps: AC (chargeUAct/chargeIAct)
            // or DC pile (dcChargePileUAct/dcChargePileIAct). Only meaningful while charging.
            val acKw = (data.numAt(avs, ev, "chargeUAct") ?: 0.0) * (data.numAt(avs, ev, "chargeIAct") ?: 0.0) / 1000.0
            val dcKw = (data.numAt(avs, ev, "dcChargePileUAct") ?: 0.0) * (data.numAt(avs, ev, "dcChargePileIAct") ?: 0.0) / 1000.0
            val powerKw = maxOf(acKw, dcKw).takeIf { charging == true && it > 0.1 }

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
            )
        }
    }
}
