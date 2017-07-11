package net.corda.training.state

import net.corda.core.serialization.CordaSerializable
import java.time.LocalDate
import java.util.*

@CordaSerializable
data class Document(val name: String,
                    var secureHash: String?,
                    var active: Boolean)

@CordaSerializable
data class Identity(val idNo: String,
                    val name: String,
                    val dob: LocalDate,
                    val address: String,
                    val phoneNo: String,
                    val email: String,
                    val passportNo: String,
                    var participants: ArrayList<String>,
                    var documents: List<Document>?
)