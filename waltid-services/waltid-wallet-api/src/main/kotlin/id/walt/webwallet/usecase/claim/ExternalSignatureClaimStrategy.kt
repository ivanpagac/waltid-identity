package id.walt.webwallet.usecase.claim

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.SSIKit2WalletService
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.exchange.IssuanceService
import id.walt.webwallet.usecase.event.EventLogUseCase
import kotlinx.uuid.UUID

class ExternalSignatureClaimStrategy(
    private val issuanceService: IssuanceService,
    private val credentialService: CredentialsService,
    private val eventUseCase: EventLogUseCase,
) {

    suspend fun prepareCredentialClaim(
        tenantId: String,
        accountId: UUID,
        walletId: UUID,
        did: String,
        keyId: String,
        offer: String,
    ) = issuanceService.prepareExternallySignedOfferRequest(
        offer = offer,
        did = did,
        keyId = keyId,
        credentialWallet = SSIKit2WalletService.getCredentialWallet(did),
    )

    suspend fun submitCredentialClaim(
        tenantId: String,
        accountId: UUID,
        walletId: UUID,
        pending: Boolean = true,
    ): List<WalletCredential> {
        val offerCredentialDataResults = issuanceService.submitExternallySignedOfferRequest()
        return ClaimCommons.mapCredentialDataResultsToWalletCredentials(
            offerCredentialDataResults,
            walletId,
            tenantId,
            accountId,
            pending,
            eventUseCase,
            credentialService,
        )
    }
}