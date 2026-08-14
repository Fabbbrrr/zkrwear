package com.zkrwatch.data

import com.zkrwatch.data.net.ZkrKeys
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * OPTIONAL live end-to-end check against the real Zkr cloud. Runs on the plain
 * JVM (OkHttp) via `./gradlew :app:testDebugUnitTest` — no watch required.
 *
 * SKIPPED unless all credentials are provided as environment variables, so it
 * never runs in CI or a normal build:
 *
 *   ZKR_USERNAME, ZKR_PASSWORD,
 *   ZKR_HMAC_ACCESS_KEY, ZKR_HMAC_SECRET_KEY, ZKR_PASSWORD_PUBLIC_KEY,
 *   ZKR_PROD_SECRET, ZKR_VIN_KEY, ZKR_VIN_IV
 *   (optional ZKR_COUNTRY_CODE, default AU)
 *
 * Use the DEDICATED watch account (car shared to it) — logging in here boots any
 * other active session, including the phone app or your HA integration.
 *
 * Read-only: login -> vehicle-list -> status. Issues no lock/unlock/climate.
 */
class ZkrLiveIntegrationTest {

    private fun env(name: String): String = System.getenv(name)?.trim().orEmpty()

    @Test
    fun live_login_and_status() = runBlocking {
        val username = env("ZKR_USERNAME")
        val password = env("ZKR_PASSWORD")
        val keys = ZkrKeys(
            hmacAccessKey = env("ZKR_HMAC_ACCESS_KEY"),
            hmacSecretKey = env("ZKR_HMAC_SECRET_KEY"),
            passwordPublicKey = env("ZKR_PASSWORD_PUBLIC_KEY"),
            prodSecret = env("ZKR_PROD_SECRET"),
            vinKey = env("ZKR_VIN_KEY"),
            vinIv = env("ZKR_VIN_IV"),
        )
        val country = env("ZKR_COUNTRY_CODE").ifEmpty { "AU" }

        assumeTrue(
            "Live test skipped: set ZKR_* env vars to run",
            username.isNotEmpty() && password.isNotEmpty() && keys.isComplete,
        )

        val repo = ZkrClientFactory.create(username, password, keys, country, "integration-test-device")
        repo.connect()
        val vin = repo.firstVin()
        assertNotNull(vin)
        println("[live] VIN = $vin")

        val status = repo.status(vin)
        println(
            "[live] SOC=${status.socPercent}%  range=${status.rangeKm}km  " +
                "locked=${status.locked}  climate=${status.climateActive}",
        )
        assertNotNull(status)
    }
}
