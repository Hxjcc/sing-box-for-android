package io.nekohasekai.sfa.vendor

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PackageQueryManager {

    val requiresShizuku: Boolean = BuildConfig.FLAVOR == "play"

    val shizukuInstalled: StateFlow<Boolean> = MutableStateFlow(false)
    val shizukuBinderReady: StateFlow<Boolean> = MutableStateFlow(false)
    val shizukuPermissionGranted: StateFlow<Boolean> = MutableStateFlow(false)

    fun isShizukuAvailable(): Boolean = false

    fun registerListeners() {
    }

    fun unregisterListeners() {}

    fun requestShizukuPermission() {}

    fun refreshShizukuState() {}

    suspend fun getInstalledPackages(flags: Int, retryFlags: Int): List<PackageInfo> =
        getPackagesViaPackageManager(flags, retryFlags)

    private fun getPackagesViaPackageManager(flags: Int, retryFlags: Int): List<PackageInfo> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Application.packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            Application.packageManager.getInstalledPackages(flags)
        }
    } catch (_: RuntimeException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Application.packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(retryFlags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            Application.packageManager.getInstalledPackages(retryFlags)
        }
    }
}
