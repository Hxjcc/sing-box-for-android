package io.nekohasekai.sfa.database

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.room.TypeConverter
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.database.TypedProfile.Type.values
import io.nekohasekai.sfa.ktx.marshall
import io.nekohasekai.sfa.ktx.unmarshall
import java.util.Date

class TypedProfile() : Parcelable {
    enum class Type {
        Local,
        Remote,
        ;

        fun getString(context: Context): String = when (this) {
            Local -> context.getString(R.string.profile_type_local)
            Remote -> context.getString(R.string.profile_type_remote)
        }

        companion object {
            fun valueOf(value: Int): Type {
                for (it in values()) {
                    if (it.ordinal == value) {
                        return it
                    }
                }
                return Local
            }
        }
    }

    var path = ""
    var type = Type.Local
    var remoteURL: String = ""
    var lastUpdated: Date = Date(0)
    var autoUpdate: Boolean = false
    var autoUpdateInterval = 60
    var subscriptionUpload: Long = UNKNOWN_SUBSCRIPTION_TRAFFIC
    var subscriptionDownload: Long = UNKNOWN_SUBSCRIPTION_TRAFFIC
    var subscriptionTotal: Long = UNKNOWN_SUBSCRIPTION_TRAFFIC

    val subscriptionUserInfo: SubscriptionUserInfo?
        get() {
            if (subscriptionUpload < 0L || subscriptionDownload < 0L || subscriptionTotal <= 0L) return null
            return SubscriptionUserInfo(
                upload = subscriptionUpload,
                download = subscriptionDownload,
                total = subscriptionTotal,
            )
        }

    fun setSubscriptionUserInfo(userInfo: SubscriptionUserInfo?) {
        subscriptionUpload = userInfo?.upload ?: UNKNOWN_SUBSCRIPTION_TRAFFIC
        subscriptionDownload = userInfo?.download ?: UNKNOWN_SUBSCRIPTION_TRAFFIC
        subscriptionTotal = userInfo?.total ?: UNKNOWN_SUBSCRIPTION_TRAFFIC
    }

    constructor(reader: Parcel) : this() {
        val version = reader.readInt()
        path = reader.readString() ?: ""
        type = Type.valueOf(reader.readInt())
        remoteURL = reader.readString() ?: ""
        autoUpdate = reader.readInt() == 1
        lastUpdated = Date(reader.readLong())
        if (version >= 1) {
            autoUpdateInterval = reader.readInt()
        }
        if (version >= 2) {
            subscriptionUpload = reader.readLong()
            subscriptionDownload = reader.readLong()
            subscriptionTotal = reader.readLong()
        }
    }

    override fun writeToParcel(writer: Parcel, flags: Int) {
        writer.writeInt(2)
        writer.writeString(path)
        writer.writeInt(type.ordinal)
        writer.writeString(remoteURL)
        writer.writeInt(if (autoUpdate) 1 else 0)
        writer.writeLong(lastUpdated.time)
        writer.writeInt(autoUpdateInterval)
        writer.writeLong(subscriptionUpload)
        writer.writeLong(subscriptionDownload)
        writer.writeLong(subscriptionTotal)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TypedProfile> {
        private const val UNKNOWN_SUBSCRIPTION_TRAFFIC = -1L

        override fun createFromParcel(parcel: Parcel): TypedProfile = TypedProfile(parcel)

        override fun newArray(size: Int): Array<TypedProfile?> = arrayOfNulls(size)
    }

    class Convertor {
        @TypeConverter
        fun marshall(profile: TypedProfile) = profile.marshall()

        @TypeConverter
        fun unmarshall(content: ByteArray) = content.unmarshall(::TypedProfile)
    }
}
