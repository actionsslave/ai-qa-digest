package app.aiqadigest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Digest(
    @SerialName("generated_at") val generatedAt: String = "",
    val items: List<DigestItem> = emptyList(),
)

@Serializable
data class DigestItem(
    val id: String,
    val title: String,
    val url: String,
    val source: String,
    val excerpt: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("added_at") val addedAt: String = "",
)
