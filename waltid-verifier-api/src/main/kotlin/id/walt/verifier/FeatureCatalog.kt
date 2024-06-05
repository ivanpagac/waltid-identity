package id.walt.verifier

import id.walt.featureflag.BaseFeature
import id.walt.featureflag.OptionalFeature
import id.walt.featureflag.ServiceFeatureCatalog
import id.walt.verifier.config.OIDCVerifierServiceConfig
import id.walt.verifier.entra.EntraConfig

object FeatureCatalog : ServiceFeatureCatalog {

    val verifierService = BaseFeature("verifier-service", "Verifier Service Implementation", OIDCVerifierServiceConfig::class)

    val entra = OptionalFeature("entra", "Enable Microsoft Entra support", EntraConfig::class, false)


    override val baseFeatures = listOf(verifierService)
    override val optionalFeatures = listOf(entra)
}
