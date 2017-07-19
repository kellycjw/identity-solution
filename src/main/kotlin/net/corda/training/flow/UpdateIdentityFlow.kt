package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.seconds
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.SignTransactionFlow
import net.corda.training.contract.IdentityContract
import net.corda.training.state.Document
import java.util.*
import net.corda.training.state.Identity
import net.corda.training.state.IdentityState

@InitiatingFlow
@StartableByRPC
class UpdateIdentityFlow(val identity: Identity) : FlowLogic<IdentityFlowResult>() {

    @Suspendable
    override fun call(): IdentityFlowResult {

        try {
            // 1. Search for identity using the idNo on ledger
            val idx = serviceHub.vaultService.linearHeadsOfType<IdentityState>().values.indexOfFirst { it.state.data.identity.idNo.compareTo(identity.idNo) == 0 }

            // 2. If identity cannot be found, return error message
            if (idx < 0)
                return IdentityFlowResult.Failure("Identification no. cannot be found on ledger: " + identity.idNo)

            // 3. If identity can be found, get the state and reference to it
            val oldStateRef = serviceHub.vaultService.linearHeadsOfType<IdentityState>().values.elementAt(idx)

            // 4. Create a copy of the old identity state but change identity to the updated one
            val newState = oldStateRef.state.data.copy(identity = identity)

            // 5. Get the notary of the old identity state
            val notary = oldStateRef.state.notary

            // 6. Add participants that can consume the new state
            identity.participants
                .filter(String::isNotEmpty)
                .forEach { p -> serviceHub.networkMapCache.partyNodes
                    .filter { it.legalIdentity.name.toString().toUpperCase() == p.toUpperCase() }
                    .forEach {
                        newState.addParty(it.legalIdentity)
                    }
                }

            // 7. Creates the transaction - the nodes that already have access to the old state must sign the transaction
            val utx = TransactionType.General.Builder(notary)
                    .withItems(oldStateRef, newState, Command(IdentityContract.Commands.Update(), oldStateRef.state.data.allowedParties.map { it.owningKey }))

            // 8. If the updated identity has no documents and the old identity has documents, that means there is no updated document
            if (identity.documents.orEmpty().size == 0 && oldStateRef.state.data.identity.documents.orEmpty().size > 0)
            {
                newState.identity.documents = oldStateRef.state.data.identity.documents
            }

            // 9. Add documents in the updated identity (that are active and has a hash) to the proposed transaction
            identity.documents.orEmpty()
                    .filter { it.active && it.secureHash != null }
                    .forEach {
                        utx.addAttachment(SecureHash.parse(it.secureHash!!))
                    }

            // 10. Add timestamp to the proposed transaction
            val currentTime = serviceHub.clock.instant()
            utx.addTimeWindow(currentTime, 30.seconds)

            // 11. Verify and sign the transaction
            utx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
            var stx = serviceHub.signInitialTransaction(utx)

            // 12. Collect signatures
            stx = subFlow(CollectSignaturesFlow(stx))

            // 13. Get all the parties that need to receive the transaction: newly allowed parties + previously allowed parties
            var broadcastList: ArrayList<Party> = newState.allowedParties
            oldStateRef.state.data.allowedParties
                    .filter { p -> broadcastList.indexOfFirst { it.name == p.name } == -1 } //those that are not already in the broadcast list
                    .forEach { broadcastList.add(it) }

            // 14. Use FinalityFlow to notarize, send and record to all parties' ledger
            subFlow(FinalityFlow(stx, broadcastList.toSet()))

            return IdentityFlowResult.Success("Transaction id ${stx.id} committed to ledger.")

        } catch(ex: Exception) {
            ex.printStackTrace()
            return IdentityFlowResult.Failure(ex.message)
        }
    }
}

/**
 * This is the flow which signs the Update Identity Flow.
 * The signing is handled by the [CollectSignaturesFlow].
 */
@InitiatedBy(UpdateIdentityFlow::class)
class UpdateIdentityFlowResponder(val otherParty: Party): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(otherParty) {
            override fun checkTransaction(stx: SignedTransaction) {
                // Define checking logic.
            }
        }

        subFlow(signTransactionFlow)
    }
}
