package io.nekohasekai.sfa.bg

import io.nekohasekai.sfa.Application

internal fun buildBasicEnvironment(
    sshEnv: Array<out String>?,
    shell: String,
    home: String,
    term: String?,
): Array<String> {
    val env = mutableMapOf<String, String>()
    sshEnv?.forEach { entry ->
        val separator = entry.indexOf('=')
        if (separator > 0) env[entry.substring(0, separator)] = entry.substring(separator + 1)
    }
    env["HOME"] = home
    env["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
    env["SHELL"] = shell
    env["TMPDIR"] = Application.application.cacheDir.absolutePath
    if (!term.isNullOrEmpty()) env["TERM"] = term
    val androidVars = arrayOf(
        "ANDROID_ASSETS", "ANDROID_DATA", "ANDROID_ROOT", "ANDROID_STORAGE",
        "EXTERNAL_STORAGE", "ASEC_MOUNTPOINT", "LOOP_MOUNTPOINT",
        "ANDROID_RUNTIME_ROOT", "ANDROID_ART_ROOT",
        "ANDROID_I18N_ROOT", "ANDROID_TZDATA_ROOT",
        "BOOTCLASSPATH", "DEX2OATBOOTCLASSPATH", "SYSTEMSERVERCLASSPATH",
    )
    for (name in androidVars) {
        System.getenv(name)?.let { env[name] = it }
    }
    return env.map { (key, value) -> "$key=$value" }.toTypedArray()
}
