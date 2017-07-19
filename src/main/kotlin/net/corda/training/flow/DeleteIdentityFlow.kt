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

            // 1. Find the old state
            val idx = serviceHub.vaultService.linearHeadsOfType<IdentityState>().values.indexOfFirst { it.state.data.identity.idNo.compareTo(identity.idNo) == 0 }
            if (idx < 0)
                return IdentityFlowResult.Failure("idNo cannot be found on ledger: " + identity.idNo)

            val oldState = serviceHub.vaultService.linearHeadsOfType<IdentityState>().values.elementAt(idx)
            val notary = oldState.state.notary

            // 2. Creates the transaction, using the nodes that already have the old state
            val utx = TransactionType.General.Builder(notary)
                    .withItems(oldState, Command(IdentityContract.Commands.Delete(), oldState.state.data.participants.map { it.owningKey }))

            // 3. Add timestamp to the transaction
            val currentTime = serviceHub.clock.instant()
            utx.addTimeWindow(currentTime, 30.seconds)

            // 4. Verify and sign the transaction
            utx.toWireTransaction().toLedgerTransaction(serviceHub).verify()
            val ptx = serviceHub.signInitialTransaction(utx)

            // 5. Collect signatures from counterparties
            val stx = subFlow(CollectSignaturesFlow(ptx))

            // 6. Use FinalityFlow to notarize, send and record to all parties' ledger
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
