package net.corda.training.state

import net.corda.core.crypto.keys
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.training.contract.IdentityContract
import java.security.PublicKey
import java.util.*

data class IdentityState(val identity: Identity,
                         override val linearId: UniqueIdentifier = UniqueIdentifier(identity.idNo)) : LinearState {

    val allowedParties: ArrayList<Party> = arrayListOf()

    override val contract get() = IdentityContract()

    override fun isRelevant(ourKeys: Set<PublicKey>): Boolean {
        //Track the state in your vault if you belong to any of the allowed parties of this state
        val partyKeys = allowedParties.toList().map { it.owningKey }
        return ourKeys.intersect(partyKeys).isNotEmpty()
    }

    override val participants: List<AbstractParty> get() = allowedParties.toList()

    fun addParty(party: Party) {
        allowedParties.add(party)
    }
}