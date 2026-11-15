package com.bane23.iptvplayer.data.xtream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** JSON podešen za Xtream panele, koji često šalju nepoznata polja i mešaju tipove. */
val XtreamJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Xtream paneli isto polje šalju čas kao broj, čas kao tekst (npr. `"category_id": "5"` ili `5`).
 * Ovaj serializer prihvata bilo koju prostu vrednost i vraća je kao tekst.
 */
@OptIn(ExperimentalSerializationApi::class)
object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeString()
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> element.content
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

typealias FlexString = @Serializable(with = FlexibleStringSerializer::class) String?

@Serializable
data class XtreamAccountResponse(
    /** Kod neuspele prijave neki paneli ovde šalju prazan niz umesto objekta. */
    @SerialName("user_info") val userInfoRaw: JsonElement? = null,
) {
    val userInfo: XtreamUserInfo?
        get() = (userInfoRaw as? JsonObject)?.let {
            XtreamJson.decodeFromJsonElement(XtreamUserInfo.serializer(), it)
        }
}

@Serializable
data class XtreamUserInfo(
    val auth: FlexString = null,
    val status: FlexString = null,
    @SerialName("exp_date") val expDate: FlexString = null,
) {
    val isAuthenticated: Boolean get() = auth == "1" || auth.equals("true", ignoreCase = true)

    /** Vreme isteka u sekundama (Unix), ili null ako nalog ne ističe. */
    val expiresAtSeconds: Long? get() = expDate?.toLongOrNull()
}

@Serializable
data class XtreamCategory(
    @SerialName("category_id") val id: FlexString = null,
    @SerialName("category_name") val name: FlexString = null,
)

@Serializable
data class XtreamLiveStream(
    val num: FlexString = null,
    val name: FlexString = null,
    @SerialName("stream_id") val streamId: FlexString = null,
    @SerialName("stream_icon") val icon: FlexString = null,
    @SerialName("epg_channel_id") val epgChannelId: FlexString = null,
    @SerialName("category_id") val categoryId: FlexString = null,
    @SerialName("tv_archive") val tvArchive: FlexString = null,
    @SerialName("tv_archive_duration") val tvArchiveDuration: FlexString = null,
)
