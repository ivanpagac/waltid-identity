package id.walt.credentials.verification.policies

import id.walt.credentials.Claims
import id.walt.credentials.JwtClaims
import id.walt.credentials.VcClaims
import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import id.walt.credentials.verification.DatePolicyUtils.checkJwt
import id.walt.credentials.verification.DatePolicyUtils.checkVc
import id.walt.credentials.verification.ExpirationDatePolicyException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class ExpirationDatePolicy : CredentialWrapperValidatorPolicy(
    "expired", "Verifies that the credentials expiration date (`exp` for JWTs) has not been exceeded."
) {
    private val vcClaims = listOf<Claims>(VcClaims.V2.NotAfter, VcClaims.V1.NotAfter)
    private val jwtClaims = listOf(JwtClaims.NotAfter)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val (key, exp) = getExpirationKeyValuePair(data) ?: return buildPolicyUnavailableResult()

        val now = Clock.System.now()

        return if (now > exp) {
            buildFailureResult(now, exp, key)
        } else {
            buildSuccessResult(now, exp, key)
        }
    }

    private fun getExpirationKeyValuePair(data: JsonObject): Pair<Claims, Instant>? =
        checkVc(data["vc"]?.jsonObject, vcClaims) ?: checkVc(data, vcClaims) ?: checkJwt(data, jwtClaims)

    private fun buildPolicyUnavailableResult() = Result.success(
        JsonObject(mapOf("policy_available" to JsonPrimitive(false)))
    )

    private fun buildFailureResult(now: Instant, exp: Instant, key: Claims) = (now - exp).let {
        Result.failure<ExpirationDatePolicyException>(
            ExpirationDatePolicyException(
                date = exp,
                dateSeconds = exp.epochSeconds,
                expiredSince = it,
                expiredSinceSeconds = it.inWholeSeconds,
                key = key.getValue(),
                policyAvailable = true
            )
        )
    }

    private fun buildSuccessResult(now: Instant, exp: Instant, key: Claims) = (exp - now).let {
        Result.success(
            JsonObject(
                mapOf(
                    "date" to JsonPrimitive(exp.toString()),
                    "date_seconds" to JsonPrimitive(exp.epochSeconds),
                    "expires_in" to JsonPrimitive(it.toString()),
                    "expires_in_seconds" to JsonPrimitive(it.inWholeSeconds),
                    "used_key" to JsonPrimitive(key.getValue()),
                    "policy_available" to JsonPrimitive(true)
                )
            )
        )
    }
}
