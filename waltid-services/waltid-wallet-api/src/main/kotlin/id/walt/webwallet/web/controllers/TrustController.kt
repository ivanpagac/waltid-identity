package id.walt.webwallet.web.controllers

import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.web.WebBaseRoutes.authenticatedWebWalletRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

fun Application.trustRegistry() = authenticatedWebWalletRoute {
    route("trust", {
        tags = listOf("TrustRegistry")
    }) {
        post("", {
            request {
                body<TrustRequest> {
                    required = true
                    example("Example issuer entity") { value = TrustRequest("did", "VerifiableCredential", "egfUri", true) }
                }
            }
        }) {
            val request = call.receive<TrustRequest>()
            val result = request.isVerifier.takeIf { it }
                ?.let { WalletServiceManager.verifierTrustValidationService.validate(request.did, request.credentialType, request.egfUri) }
                ?: WalletServiceManager.issuerTrustValidationService.validate(request.did, request.credentialType, request.egfUri)
            context.respond(TrustResponse(result))
        }
    }
}

@Serializable
internal data class TrustRequest(
    val did: String,
    val credentialType: String,
    val egfUri: String,
    val isVerifier: Boolean = false,
)

@Serializable
internal data class TrustResponse(
    val trusted: Boolean,
)
