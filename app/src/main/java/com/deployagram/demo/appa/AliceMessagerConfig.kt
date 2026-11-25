package com.deployagram.demo.appa

/**
 * Where AppA finds AliceMessager.
 *
 * The default points at AliceMessager on the host machine, as seen from the Android emulator (10.0.2.2 is the
 * emulator's alias for the host's loopback). Tests replace it with the address of a test double.
 */
object AliceMessagerConfig {
    @Volatile
    var baseUrl: String = "http://10.0.2.2:8081"
}
