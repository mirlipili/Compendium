package vet.derichs.compendium.data.model

import androidx.room.Entity
import com.google.gson.annotations.SerializedName

@Entity(tableName = "medications", primaryKeys = ["id", "language"])
data class Medication(
    val id: String,
    val language: String = "",  // set via .copy(language = lang) before insert; never from JSON

    val name: String? = null,
    val firm: String? = null,

    @SerializedName("registration_number")
    val registration_number: String? = null,

    @SerializedName("target_species")
    val target_species: String? = null,

    val composition: String? = null,

    @SerializedName("pharmaceutical_form")
    val pharmaceutical_form: String? = null,

    @SerializedName("administration_route")
    val administration_route: String? = null,

    val posology: String? = null,

    @SerializedName("withdrawal_period")
    val withdrawal_period: String? = null,

    val packaging: String? = null,
    val prescription: String? = null,

    @SerializedName("rcp_link")
    val rcp_link: String? = null
)
