package net.corda.training.flow

import net.corda.core.serialization.CordaSerializable

sealed class IdentityFlowResult {

    @CordaSerializable
    class Success(val message: String?) : IdentityFlowResult() {
        override fun toString(): String = "Success($message)"
    }

    @CordaSerializable
    class Failure(val message: String?) : IdentityFlowResult() {
        override fun toString(): String = "Failure($message)"
    }
}