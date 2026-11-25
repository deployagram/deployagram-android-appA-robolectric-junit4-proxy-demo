package com.deployagram.demo.appa

/**
 * Where AppA finds BookOverflow.
 *
 * The default points at BookOverflow on the host machine, as seen from the Android emulator (10.0.2.2 is the
 * emulator's alias for the host's loopback). Tests replace it with the address of a test double.
 */
object BookOverflowConfig {
    @Volatile
    var baseUrl: String = "http://10.0.2.2:8080"
}
