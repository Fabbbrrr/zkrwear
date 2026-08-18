package com.zkrwatch.data.repo

import com.squareup.moshi.Moshi
import com.zkrwatch.data.auth.ZkrLogin
import com.zkrwatch.data.model.VehicleStatus
import com.zkrwatch.data.net.ZkrConst
import com.zkrwatch.data.net.ZkrException
import com.zkrwatch.data.net.ZkrHttp
import com.zkrwatch.data.net.ZkrSession
import com.zkrwatch.data.net.child
import com.zkrwatch.data.net.isSuccess
import com.zkrwatch.data.net.list
import com.zkrwatch.data.net.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * High-level, coroutine-friendly API over the Zkr client. All network work
 * runs on [Dispatchers.IO] and is serialized by [mutex] so login mutations to
 * the shared [ZkrSession] never race.
 */
class ZkrRepository(
    private val session: ZkrSession,
    private val http: ZkrHttp,
    private val login: ZkrLogin,
    private val moshi: Moshi,
) {
    private val mutex = Mutex()
    private val anyAdapter = moshi.adapter(Any::class.java)

    private fun server(): String =
        session.regionLoginServer ?: throw ZkrException("Not logged in (no region server)")

    suspend fun connect() = io { login.login() }

    /** Returns the VIN of the first (or only) vehicle on the account. */
    suspend fun firstVin(): String = io {
        val url = "${server()}${ZkrConst.VEHLIST_URL}?needSharedCar=true"
        val resp = http.appSignedGet(url)
        if (!resp.isSuccess()) throw ZkrException("Failed to get vehicle list: $resp")
        val first = resp.list("data")?.firstOrNull()
            ?: throw ZkrException("No vehicles on account")
        @Suppress("UNCHECKED_CAST")
        (first as Map<String, Any?>).str("vin") ?: throw ZkrException("Vehicle has no VIN")
    }

    suspend fun status(vin: String): VehicleStatus = io {
        VehicleStatus.from(fetchStatusData(vin))
    }

    /**
     * Vehicle status plus the extras that live on separate endpoints — currently
     * the remote-control state (sentry mode). The extra fetch is best-effort: a
     * failure leaves those fields null rather than breaking the whole refresh.
     */
    suspend fun statusWithExtras(vin: String): VehicleStatus = io {
        val data = fetchStatusData(vin).toMutableMap()
        // Sentry state comes from getVehicleState, not status/latest. Merge it under
        // additionalVehicleStatus.remoteControlState so VehicleStatus.from can read it
        // (mirrors the HA coordinator merge).
        runCatching { fetchRemoteControlState(vin) }.getOrNull()?.let { rcs ->
            @Suppress("UNCHECKED_CAST")
            val avs = ((data["additionalVehicleStatus"] as? Map<String, Any?>) ?: emptyMap())
                .toMutableMap()
            avs["remoteControlState"] = rcs
            data["additionalVehicleStatus"] = avs
        }
        VehicleStatus.from(data)
    }

    /** Raw `data` object of status/latest. Non-suspend so it composes under a single [io] lock. */
    private fun fetchStatusData(vin: String): Map<String, Any?> {
        val url = "${server()}${ZkrConst.VEHICLESTATUS_URL}?latest=false&target=new"
        val resp = http.appSignedGet(url, extraHeaders = mapOf("X-VIN" to session.encryptedVin(vin)))
        if (!resp.isSuccess()) throw ZkrException("Failed to get vehicle status: $resp")
        return resp.child("data") ?: emptyMap()
    }

    /** Raw `data` of getVehicleState (holds `vstdModeState` for sentry). Null on failure. */
    private fun fetchRemoteControlState(vin: String): Map<String, Any?>? {
        val url = "${server()}${ZkrConst.REMOTECONTROLSTATE_URL}"
        val resp = http.appSignedGet(url, extraHeaders = mapOf("X-VIN" to session.encryptedVin(vin)))
        if (!resp.isSuccess()) return null
        return resp.child("data")
    }

    suspend fun lock(vin: String): Boolean =
        remoteControl(vin, command = "start", serviceId = "RDL", doorAll())

    suspend fun unlock(vin: String): Boolean =
        remoteControl(vin, command = "stop", serviceId = "RDU", doorAll())

    /** Opens the tailgate/trunk (RDU with target=trunk, per the HA integration). */
    suspend fun openTrunk(vin: String): Boolean =
        remoteControl(
            vin,
            command = "stop",
            serviceId = "RDU",
            setting = linkedMapOf("serviceParameters" to listOf(param("target", "trunk"))),
        )

    /** Climate precondition. [on]=false stops it; temp/duration ignored when off. */
    suspend fun climate(vin: String, on: Boolean, tempC: Int = 22, durationMin: Int = 15): Boolean {
        val params = if (on) {
            listOf(
                param("AC", "true"),
                param("AC.temp", tempC.toString()),
                param("AC.duration", durationMin.toString()),
            )
        } else {
            listOf(param("AC", "false"))
        }
        val setting = linkedMapOf<String, Any?>("serviceParameters" to params)
        return remoteControl(vin, command = "start", serviceId = "ZAF", setting)
    }

    /** Arms/disarms sentry (surveillance) mode. serviceId RSM; only the verb differs. */
    suspend fun setSentry(vin: String, on: Boolean): Boolean =
        remoteControl(
            vin,
            command = if (on) "start" else "stop",
            serviceId = "RSM",
            setting = linkedMapOf("serviceParameters" to listOf(param("rsm", "6"))),
        )

    private suspend fun remoteControl(
        vin: String,
        command: String,
        serviceId: String,
        setting: Map<String, Any?>,
    ): Boolean = io {
        val endpoint = if (serviceId == "RCS") ZkrConst.CHARGE_CONTROL_URL else ZkrConst.REMOTECONTROL_URL
        val body = linkedMapOf<String, Any?>(
            "command" to command,
            "serviceId" to serviceId,
            "setting" to setting,
        )
        val json = anyAdapter.toJson(body)
        val resp = http.appSignedPost(
            "${server()}$endpoint",
            json,
            extraHeaders = mapOf("X-VIN" to session.encryptedVin(vin)),
        )
        resp.isSuccess()
    }

    private fun doorAll(): Map<String, Any?> =
        linkedMapOf("serviceParameters" to listOf(param("door", "all")))

    private fun param(key: String, value: String): Map<String, Any?> =
        linkedMapOf("key" to key, "value" to value)

    private suspend inline fun <T> io(crossinline block: () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }
}
