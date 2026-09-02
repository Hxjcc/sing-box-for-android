package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.util.ProfileIcons
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.SubscriptionUserInfo

private const val TRAFFIC_FILL_RUN_DURATION_MILLIS = 520
private const val TRAFFIC_FILL_STOP_DURATION_MILLIS = 320
private const val TRAFFIC_FILL_BRAKE_RATIO = 0.9f

private object ProfileTrafficAnimationTracker {
    private var initialAnimationConsumed = false
    private val profileVersions = mutableMapOf<Long, String>()

    @Synchronized
    fun shouldAnimate(profile: Profile, userInfo: SubscriptionUserInfo): Boolean {
        val version = listOf(
            userInfo.upload,
            userInfo.download,
            userInfo.total,
            userInfo.expire,
            profile.typed.lastUpdated.time,
        ).joinToString(":")
        val previousVersion = profileVersions.put(profile.id, version)
        if (!initialAnimationConsumed) {
            initialAnimationConsumed = true
            return true
        }
        return previousVersion != null && previousVersion != version
    }
}

@Composable
fun ProfileSelectorButton(selectedProfile: Profile?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isDarkTheme = isSystemInDarkTheme()
    val subscriptionUserInfo = selectedProfile?.typed?.subscriptionUserInfo
    val remainingFraction = subscriptionUserInfo?.remainingFraction ?: 0f
    val trafficAnimationKey = if (selectedProfile != null && subscriptionUserInfo != null) {
        "${selectedProfile.id}:${subscriptionUserInfo.upload}:${subscriptionUserInfo.download}:" +
            "${subscriptionUserInfo.total}:${subscriptionUserInfo.expire}:${selectedProfile.typed.lastUpdated.time}"
    } else {
        null
    }
    val animateTraffic = remember(trafficAnimationKey) {
        if (selectedProfile != null && subscriptionUserInfo != null) {
            ProfileTrafficAnimationTracker.shouldAnimate(selectedProfile, subscriptionUserInfo)
        } else {
            false
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDarkTheme) {
            lerp(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.surfaceContainerHigh,
                0.5f,
            )
        } else {
            MaterialTheme.colorScheme.surfaceDim
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ProfileTrafficFill(
                userInfo = subscriptionUserInfo,
                remainingFraction = remainingFraction,
                isDarkTheme = isDarkTheme,
                animationKey = trafficAnimationKey,
                animate = animateTraffic,
            )
            ProfileSelectorContent(selectedProfile, subscriptionUserInfo)
        }
    }
}

@Composable
private fun ProfileTrafficFill(
    userInfo: SubscriptionUserInfo?,
    remainingFraction: Float,
    isDarkTheme: Boolean,
    animationKey: String?,
    animate: Boolean,
) {
    if (userInfo == null) return

    val animatedFraction = remember(animationKey) {
        Animatable(if (animate) 0f else remainingFraction)
    }
    LaunchedEffect(animationKey) {
        if (!animate) {
            animatedFraction.snapTo(remainingFraction)
            return@LaunchedEffect
        }
        animatedFraction.animateTo(
            targetValue = remainingFraction * TRAFFIC_FILL_BRAKE_RATIO,
            animationSpec = tween(
                durationMillis = TRAFFIC_FILL_RUN_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        )
        animatedFraction.animateTo(
            targetValue = remainingFraction,
            animationSpec = tween(
                durationMillis = TRAFFIC_FILL_STOP_DURATION_MILLIS,
                easing = LinearOutSlowInEasing,
            ),
        )
    }

    ProfileTrafficProgressFill(
        userInfo = userInfo,
        remainingFraction = animatedFraction.value,
        isDarkTheme = isDarkTheme,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun ProfileTrafficProgressFill(
    userInfo: SubscriptionUserInfo?,
    remainingFraction: Float,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    if (userInfo == null) return

    val fillColor = MaterialTheme.colorScheme.primaryContainer.copy(
        alpha = if (isDarkTheme) 0.45f else 0.7f,
    )
    Canvas(modifier = modifier) {
        val fraction = remainingFraction.coerceIn(0f, 1f)
        val fillWidth = size.width * fraction
        if (fillWidth <= 0f) return@Canvas
        if (fraction >= 0.995f) {
            drawRect(color = fillColor)
            return@Canvas
        }

        val fadeWidth = minOf(8.dp.toPx(), fillWidth)
        val solidWidth = (fillWidth - fadeWidth).coerceAtLeast(0f)
        if (solidWidth > 0f) {
            drawRect(
                color = fillColor,
                size = Size(width = solidWidth, height = size.height),
            )
        }
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(fillColor, fillColor.copy(alpha = 0f)),
                startX = solidWidth,
                endX = fillWidth,
            ),
            topLeft = Offset(x = solidWidth, y = 0f),
            size = Size(width = fadeWidth, height = size.height),
        )
    }
}

@Composable
private fun ProfileSelectorContent(selectedProfile: Profile?, userInfo: SubscriptionUserInfo?) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileName(selectedProfile)

        if (userInfo != null) {
            Text(
                text = stringResource(
                    R.string.profile_traffic_remaining,
                    SubscriptionUserInfo.formatBytes(userInfo.remaining),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.Default.UnfoldMore,
            contentDescription = stringResource(R.string.expand),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowScope.ProfileName(profile: Profile?) {
    if (profile == null) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.not_selected),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val profileIcon = ProfileIcons.getIconById(profile.icon) ?: Icons.AutoMirrored.Default.InsertDriveFile
    Icon(
        imageVector = profileIcon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
        text = profile.name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
}
