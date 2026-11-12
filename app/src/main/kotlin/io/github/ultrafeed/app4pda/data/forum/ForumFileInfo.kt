package io.github.ultrafeed.app4pda.data.forum

import android.os.Parcel
import android.os.Parcelable
import io.github.ultrafeed.app4pda.feature.downloads.DownloadableInfo
import io.github.ultrafeed.app4pda.feature.downloads.data.DownloadLinks
import java.util.Date

open class ForumFileInfo() : DownloadableInfo, Comparable<ForumFileInfo>, Parcelable {
    private var url = ""
    var fileId: Int = 0
    override var topicId: Int = 0
    override var postId: Int = 0
    private var sizeText = ""
    override val displaySize: String
        get() = sizeText

    private var countText = ""
    override val downloadCount: String
        get() = countText

    private var uploaded = Date(0)
    private var uploadedAt: Date
        get() = Date(uploaded.time)
        set(value) {
            uploaded = Date(value.time)
        }

    private var applicationName = ""
    private var versionName = ""
    private var abiFlags = 0
    var crc32: Long = 0L
    private var packageId = ""
    private var versionCode = 0
    override var minSdk: Int = 0
    private var signatureStatus = -1

    constructor(url: String, topicId: Int) : this() {
        setDownloadUrl(url)
        this.topicId = topicId
    }

    override val downloadUrl: String
        get() = url

    override val fileName: String
        get() = DownloadLinks.fileName(url)

    override val name: String
        get() = applicationName

    override val version: String
        get() = versionName

    override val flags: Int
        get() = abiFlags

    override val packageName: String
        get() = packageId

    override fun hasPackageName(): Boolean = packageId.isNotEmpty()

    override fun hasSignatureStatus(status: Int): Boolean = signatureStatus == status

    open val fileVersionCode: Int
        get() = versionCode

    fun supportsAbi(deviceFlags: Int): Boolean = abiFlags == 0 || deviceFlags and abiFlags != 0

    val hasRealVersionCode: Boolean
        get() = !Regex("^9{4,}$").matches("$versionCode")

    fun setDownloadCount(value: String?) {
        countText = value.orEmpty()
    }

    fun setDisplaySize(value: String?) {
        sizeText = value.orEmpty().trim()
    }

    fun setDownloadUrl(value: String?) {
        url = ForumUrls.normalize(value.orEmpty())
        fileId = DownloadLinks.fileIdOrChecksum(url, "/forum/dl/post/", "/")
    }

    override fun compareTo(other: ForumFileInfo): Int = other.uploadedAt.compareTo(uploadedAt)

    override fun toString(): String = "$url ($uploadedAt)"

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeInt(fileId)
        parcel.writeInt(topicId)
        parcel.writeInt(postId)
        parcel.writeString(displaySize)
        parcel.writeString(downloadCount)
        parcel.writeLong(uploadedAt.time)
        parcel.writeString(versionName)
        parcel.writeString(applicationName)
        parcel.writeInt(minSdk)
        parcel.writeInt(abiFlags)
        parcel.writeLong(crc32)
        parcel.writeString(packageId)
        parcel.writeInt(versionCode)
        parcel.writeInt(signatureStatus)
    }

    internal constructor(parcel: Parcel) : this() {
        url = parcel.readString().orEmpty()
        fileId = parcel.readInt()
        topicId = parcel.readInt()
        postId = parcel.readInt()
        sizeText = parcel.readString().orEmpty()
        countText = parcel.readString().orEmpty()
        uploadedAt = Date(parcel.readLong())
        versionName = parcel.readString().orEmpty()
        applicationName = parcel.readString().orEmpty()
        minSdk = parcel.readInt()
        abiFlags = parcel.readInt()
        crc32 = parcel.readLong()
        packageId = parcel.readString().orEmpty()
        versionCode = parcel.readInt()
        signatureStatus = parcel.readInt()
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ForumFileInfo> =
            object : Parcelable.Creator<ForumFileInfo> {
                override fun createFromParcel(parcel: Parcel) = ForumFileInfo(parcel)

                override fun newArray(size: Int): Array<ForumFileInfo?> = arrayOfNulls(size)
            }
    }
}
