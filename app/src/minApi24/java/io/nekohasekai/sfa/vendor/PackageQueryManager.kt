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

    val shizukuInstalled: StateFlow<Boolean> get() = ShizukuPackageManager.shizukuInstalled
    val shizukuBinderReady: StateFlow<Boolean> get() = ShizukuPackageManager.binderReady
    val shizukuPermissionGranted: StateFlow<Boolean> get() = ShizukuPackageManager.permissionGranted

    fun isShizukuAvailable(): Boolean = ShizukuPackageManager.isAvailable() && ShizukuPackageManager.checkPermission()

    fun registerListeners() {
        ShizukuPackageManager.registerListeners()
    }

    fun unregisterListeners() {
        ShizukuPackageManager.unregisterListeners()
    }

    fun requestShizukuPermission() {
        ShizukuPackageManager.requestPermission()
    }

    fun refreshShizukuState() {
        ShizukuPackageManager.refresh()
    }

    suspend fun getInstalledPackages(flags: Int, retryFlags: Int): List<PackageInfo> =
        if (requiresShizuku) {
            ShizukuPackageManager.getInstalledPackages(flags)
        } else {
            getPackagesViaPackageManager(flags, retryFlags)
        }

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
