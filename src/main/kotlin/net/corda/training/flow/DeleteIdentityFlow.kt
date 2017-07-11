package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
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
import net.corda.training.state.Identity
import net.corda.training.state.IdentityState

@InitiatingFlow
@StartableByRPC
class DeleteIdentityFlow(val identity: Identity) : FlowLogic<IdentityFlowResult>() {

    @Suspendable
    override fun call(): IdentityFlowResult {

        try {

            // Find the old state
            val idx = serviceHub.vaultService.linearHeadsOfType<IdentityState>().values.indexOfFirst { it.state.data.identity.idNo.compareTo(identity.idNo) == 0 }
            if (idx < 0)
                return IdentityFlowResult.Failure("idNo cannot be found on ledger: " + identity.idNo)

            val oldState = serviceHub.vaultService.linearHeadsOfType<IdentityState>().values.elementAt(idx)
            val notary = oldState.state.notary

            // Creates the transaction, using the nodes that already have the old state
            // They need to sign the transaction.
            // Delete => no OUTPUT state
            val utx = TransactionType.General.Builder(notary)
                    .withItems(oldState, Command(IdentityContract.Commands.Delete(), oldState.state.data.participants.map { it.owningKey }))

            // The node signs it
            val currentTime = serviceHub.clock.instant()
            utx.addTimeWindow(currentTime, 30.seconds)
            val ptx = serviceHub.signInitialTransaction(utx)

            // Sends the transaction to the ones that already have the states to sign it
            val stx = subFlow(CollectSignaturesFlow(ptx))

            // Verifing transaction
            val wtx: WireTransaction = stx.verifySignatures(notary.owningKey)
            wtx.toLedgerTransaction(serviceHub).verify()

            // FinalityFlow notarizes, records and broadcasts the transaction
            subFlow(FinalityFlow(stx, oldState.state.data.allowedParties.toSet()))
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
@InitiatedBy(DeleteIdentityFlow::class)
class DeleteIdentityFlowResponder(val otherParty: Party): FlowLogic<Unit>() {
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
