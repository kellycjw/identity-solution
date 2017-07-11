package net.corda.training.api

import net.corda.client.rpc.notUsed
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.RPCException
import net.corda.training.flow.*
import net.corda.training.state.Identity
import net.corda.training.state.IdentityState
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.Logger
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("identity")
class IdentityApi(val services: CordaRPCOps) {
    val SERVICE_NODE_NAMES = listOf(X500Name("CN=Controller,O=R3,L=London,C=UK"), X500Name("CN=NetworkMapService,O=R3,L=London,C=UK"))
    private val myLegalName = services.nodeIdentity().legalIdentity.name

    companion object {
        private val logger: Logger = loggerFor<IdentityApi>()
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<X500Name>> {
        val (nodeInfo, nodeUpdates) = services.networkMapUpdates()
        nodeUpdates.notUsed()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentity.name }
                .filter { it != myLegalName && it !in SERVICE_NODE_NAMES })
    }

    @GET
    @Path("identities")
    @Produces(MediaType.APPLICATION_JSON)
    fun getIdentities() : List<StateAndRef<ContractState>> {
        val (vaultInfo, vaultUpdates) = services.vaultAndUpdates()
        vaultUpdates.notUsed()
        // Filter by state type: Identity.
        return vaultInfo.filter { it.state.data is IdentityState }
    }

    //this is used to upload the attachment
    @POST
    @Path("document")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    fun createDocument(@Context req: HttpServletRequest): Response {
        var secureHash: SecureHash? = null

        //check if uploaded content is multipart
        val isMultiPart = ServletFileUpload.isMultipartContent(req)
        if (isMultiPart) {
            val fileIterator = ServletFileUpload().getItemIterator(req)
            //check if there are any files
            if (!fileIterator.hasNext()) {
                logger.info("createDocument() - No file to upload")
            } else {
                //we are only allowing single file upload here so we will only take the next file
                val item = fileIterator.next()
                logger.info("createDocument() - Receiving: ${item.name} of content type ${item.contentType}")
                //check if file content type is zip as we only allow this type now
                if (item.contentType == "application/x-zip-compressed"){
                    try {
                        secureHash = services.uploadAttachment(item.openStream())
                        logger.info("createDocument() - File, " + secureHash + ", uploaded successfully.")
                    } catch (ex: Exception) {
                        when (ex) {
                            is RPCException -> {
                                logger.info("createDocument() - File, " + ex.message + ", already exists.")
                                return Response
                                        .status(Response.Status.OK)
                                        .entity(ex.message)
                                        .build()
                            }
                            else -> {
                                ex.printStackTrace()
                                logger.error(ex.toString())
                                return Response
                                        .status(Response.Status.BAD_REQUEST)
                                        .entity(ex.message)
                                        .build()
                            }
                        }
                    }
                } else {
                    logger.info("createDocument() - Unallowed file content type: ${item.contentType}")
                    return Response
                            .status(Response.Status.BAD_REQUEST)
                            .entity("Please select zip file only.")
                            .build()
                }
            }
        } else {
            logger.info("createDocument() - Content is NOT MultiPart")
        }
        return Response
                .status(Response.Status.OK)
                .entity(secureHash.toString())
                .build()
    }

    //create an identity
    @POST
    @Path("create")
    fun createIdentity(identity: Identity): Response {
        //get my identity and add it to the list of participants
        val me = services.nodeIdentity().legalIdentity.name.toString()
        //identity.me = me
        //add myself to the list of participants as the parameter only includes other nodes that we are sharing with
        identity.participants.add(me)

        //create output state with the identity
        val state = IdentityState(identity)

        //initiate the create identity flow, log the result
        var result: IdentityFlowResult;
        result = services.startFlowDynamic(CreateIdentityFlow::class.java, state).returnValue.get();
        logger.info("createIdentity() - Result: " + result.toString())

        when (result) {
            is IdentityFlowResult.Success ->
                return Response
                        .status(Response.Status.CREATED)
                        .entity(result.message)
                        .build()
            is IdentityFlowResult.Failure ->
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(result.message)
                        .build()
        }
    }

    //retrieves the attachment for downloading
    @GET
    @Path("document/{secureHash}/{fileName}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    fun getDocument(@PathParam("secureHash") secureHash: String, @PathParam("fileName") fileName: String): Response {
        val id = SecureHash.parse(secureHash)
        val attachment = services.openAttachment(id)
        if (attachment == null) {
            logger.info("getDocument() - Attachment is null")
        } else {
            logger.info("getDocument() - Attachment found")
        }

        val response = Response.ok(attachment!!.readBytes()).build()
        response.headers.add("Content-Disposition", "attachment; filename=\"" + fileName  + "\"")
        return response
    }

    //searches for this identity on the ledger
    @GET
    @Path("find/{idNo}")
    @Produces(MediaType.APPLICATION_JSON)
    fun findIdentity(@PathParam("idNo") idNo: String): Response {
        try {
            logger.info("findIdentity() ==> Start: Search " + idNo)

            //Returns a pair of head states in the vault and an observable of future updates to the vault.
            val (vaultInfo, vaultUpdates) = services.vaultAndUpdates()
            //vaultUpdates.notUsed()
            //get the index of the first state with matching idNo, returns -1 if there is no state matching the idNo
            val idx = vaultInfo.indexOfFirst { it.state.data is IdentityState && (it.state.data as IdentityState).identity.idNo.compareTo(idNo) == 0 }
            val stateAndRef: StateAndRef<IdentityState>

            if (idx >= 0) {
                stateAndRef = (vaultInfo.first { it.state.data is IdentityState && (it.state.data as IdentityState).identity.idNo.compareTo(idNo) == 0 }) as StateAndRef<IdentityState>
                logger.info("findIdentity() - Result: " + stateAndRef.toString())
                return Response
                        .ok()
                        .entity(stateAndRef.state.data.identity)
                        .build()
            } else {
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity("idNo not found!")
                        .build()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ex.message)
                    .build()
        }
    }

    @POST
    @Path("update")
    fun updateIdentity(identity: Identity): Response {
        val me = services.nodeIdentity().legalIdentity.name.toString()
        identity.participants.add(me)
        val result: IdentityFlowResult = services.startFlowDynamic(UpdateIdentityFlow::class.java, identity).returnValue.get()
        logger.info("updateIdentity() - Result: " + result.toString())
        when (result) {
            is IdentityFlowResult.Success ->
                return Response
                        .status(Response.Status.OK)
                        .entity(identity)
                        .build()
            is IdentityFlowResult.Failure ->
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(result.message)
                        .build()
        }
    }

    @POST
    @Path("delete")
    fun deleteIdentity(identity: Identity): Response {
        val result: IdentityFlowResult = services.startFlowDynamic(DeleteIdentityFlow::class.java, identity).returnValue.get()
        logger.info("deleteIdentity() - Result: " + result.toString())
        when (result) {
            is IdentityFlowResult.Success ->
                return Response
                        .status(Response.Status.OK)
                        .entity(result.message)
                        .build()
            is IdentityFlowResult.Failure ->
                return Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(result.message)
                        .build()
        }
    }
}