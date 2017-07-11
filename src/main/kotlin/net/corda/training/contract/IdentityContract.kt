package net.corda.training.contract

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.training.state.IdentityState
import java.time.LocalDate

open class IdentityContract : Contract {

    override val legalContractReference: SecureHash = SecureHash.sha256("Some legal prose.")

    interface Commands : CommandData {
        class Update : TypeOnlyCommandData(), Commands
        class Create : TypeOnlyCommandData(), Commands
        class Delete : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForContract) {
        //verifies that transaction must have at least a valid command
        val command = tx.commands.requireSingleCommand<Commands>()

        var inputs = tx.inputs
        var outputs = tx.outputs

        //validate the transaction according to the command
        when (command.value) {
            is Commands.Create -> {
                requireThat {
                    "No input states can be consumed." using (inputs.isEmpty())
                    "There must be one and only one output state." using (outputs.size == 1)
                    val output = outputs.single() as IdentityState
                    "Name must be filled." using (!output.identity.name.isNullOrBlank())
                    "Passport no. must be filled." using (!output.identity.passportNo.isNullOrBlank())
                    "Email must be filled." using (!output.identity.email.isNullOrBlank())
                    "Identification no. must be filled." using (!output.identity.idNo.isNullOrBlank())
                    "This person must be older than 18 years old." using (!output.identity.dob.isAfter(LocalDate.now().minusYears(18)))
                }
            }

            is Commands.Update -> {
                requireThat {
                    "One input state must be consumed." using (inputs.size == 1)
                    "There must be one and only one output state." using (outputs.size == 1)
                    val output = outputs.single() as IdentityState
                    "Name must be filled." using (!output.identity.name.isNullOrBlank())
                    "Passport no. must be filled." using (!output.identity.passportNo.isNullOrBlank())
                    "Email must be filled." using (!output.identity.email.isNullOrBlank())
                    "Identification no. must be filled." using (!output.identity.idNo.isNullOrBlank())
                    "This person must be older than 18 years old." using (!output.identity.dob.isAfter(LocalDate.now().minusYears(18)))
                }
            }

            is Commands.Delete -> {
                requireThat {
                    "One input state must be consumed." using (inputs.size == 1)
                    "There must be no output state." using (outputs.size == 0)
                }
            }

            else -> throw IllegalArgumentException("Unrecognised command.")
        }

    }
}
