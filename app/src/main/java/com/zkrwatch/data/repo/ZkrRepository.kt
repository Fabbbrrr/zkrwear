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
        val url = "${server()}${ZkrConst.VEHICLESTATUS_URL}?latest=false&target=new"
        val resp = http.appSignedGet(url, extraHeaders = mapOf("X-VIN" to session.encryptedVin(vin)))
        if (!resp.isSuccess()) throw ZkrException("Failed to get vehicle status: $resp")
        VehicleStatus.from(resp.child("data") ?: emptyMap())
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
