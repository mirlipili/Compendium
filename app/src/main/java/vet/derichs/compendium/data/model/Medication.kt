package vet.derichs.compendium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey
    val id: String, // Keep ID non-null as it's the primary key

    val name: String? = null, // Made nullable

    val firm: String? = null, // Made nullable

    @SerializedName("registration_number")
    val registration_number: String? = null,

    @SerializedName("target_species")
    val target_species: String? = null, // Made nullable

    val composition: String? = null, // Made nullable

    @SerializedName("pharmaceutical_form")
    val pharmaceutical_form: String? = null, // Made nullable

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
