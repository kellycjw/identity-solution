package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.seconds
import net.corda.flows.FinalityFlow
import net.corda.training.contract.IdentityContract
import net.corda.training.state.IdentityState

@InitiatingFlow
@StartableByRPC
class CreateIdentityFlow(val identityState: IdentityState) : FlowLogic<IdentityFlowResult>() {

    @Suspendable
    override fun call(): IdentityFlowResult {

        try {
            // 1. Get the notary
            val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity

            //2. Check if identity with this identification no. already exists. If so, return error.
            val idx = serviceHub.vaultService.linearHeadsOfType<IdentityState>().values.indexOfFirst { it.state.data.identity.idNo.compareTo(identityState.identity.idNo) == 0 }
            if( idx >= 0 ) {
                return IdentityFlowResult.Failure("Possible duplicate. An identity with this identification no. already exists.")
            }

            // 3. Add participants which can see/consume the state
            identityState.identity.participants
                    .filter { it.isNotEmpty() }
                    .forEach { p ->
                        serviceHub.networkMapCache.partyNodes
                                .filter { it.legalIdentity.name.toString().toUpperCase() == p.toUpperCase() }
                                .forEach {
                                    identityState.addParty(it.legalIdentity)
                                }
                    }

            // 4. Propose the transaction
            val utx = TransactionType.General.Builder(notary)
                        .withItems(identityState, Command(IdentityContract.Commands.Create(), serviceHub.legalIdentityKey))

            // 5. Add attachments to the proposed transaction
            for(attachment in identityState.identity.documents.orEmpty()) {
                utx.addAttachment(SecureHash.parse(attachment.secureHash!!))
            }

            // 6. Add timestamp to the proposed transaction
            val currentTime = serviceHub.clock.instant()
            utx.addTimeWindow(currentTime, 30.seconds)

            // 7. Verify and sign the transaction
            utx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
            val stx = serviceHub.signInitialTransaction(utx)

            // 8. Use FinalityFlow to notarize, send and record to all parties' ledger
            subFlow(FinalityFlow(stx, identityState.allowedParties.toSet()))

            return IdentityFlowResult.Success("Transaction id ${stx.id} committed to ledger.")

        } catch(ex: Exception) {
            ex.printStackTrace()
            return IdentityFlowResult.Failure(ex.message)
        }
    }
}
