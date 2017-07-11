package net.corda.training.plugin

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.CordaPluginRegistry
import net.corda.training.api.IdentityApi
import java.util.function.Function

class IdentityPlugin : CordaPluginRegistry() {
    /**
     * A list of classes that expose web APIs.
     */
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::IdentityApi))

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     * The template's web frontend is accessible at /web/template.
     */
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the identityWeb directory in resources to /web/template
            "identity" to javaClass.classLoader.getResource("identityWeb").toExternalForm()
    )
}