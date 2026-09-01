package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.SubscriptionUserInfo
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

object ProfileUpdater {
    data class Result(
        val profile: Profile,
        val contentChanged: Boolean,
    )

    private val updateMutex = Mutex()

    suspend fun update(profile: Profile): Result = updateMutex.withLock {
        require(profile.typed.type == TypedProfile.Type.Remote) { "Profile is not remote" }

        withContext(Dispatchers.IO) {
            val response = HTTPClient().use { it.getStringWithHeaders(profile.typed.remoteURL) }
            val content = response.content
            Libbox.checkConfig(content)
            if (response.headers != null) {
                profile.typed.setSubscriptionUserInfo(
                    SubscriptionUserInfo.parse(response.header(SubscriptionUserInfo.HEADER_NAME)),
                )
            }

            val configFile = File(profile.typed.path)
            val contentChanged = !configFile.exists() || configFile.readText() != content
            if (contentChanged) {
                configFile.parentFile?.mkdirs()
                configFile.writeText(content)
            }

            profile.typed.lastUpdated = Date()
            ProfileManager.update(profile)
            Result(profile = profile, contentChanged = contentChanged)
        }
    }
}
