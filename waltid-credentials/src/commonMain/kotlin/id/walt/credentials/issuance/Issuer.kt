package id.walt.credentials.issuance

import id.walt.credentials.utils.W3CDataMergeUtils
import id.walt.credentials.utils.W3CDataMergeUtils.mergeWithMapping
import id.walt.credentials.utils.W3CVcUtils.overwrite
import id.walt.credentials.utils.W3CVcUtils.update
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.Key
import id.walt.did.utils.randomUUID
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object Issuer {

    /**
     * @param id: id
     */
    @Serializable
    data class ExtraData(
        val idLocation: String = "id",


        )

    /**
     * Manually set data and issue credential
     */
    suspend fun W3CVC.baseIssue(
        key: Key,
        did: String,
        subject: String,

        dataOverwrites: Map<String, JsonElement>,
        dataUpdates: Map<String, Map<String, JsonElement>>,
        additionalJwtHeader: Map<String, String>,
        additionalJwtOptions: Map<String, JsonElement>
    ): String {
        val overwritten = overwrite(dataOverwrites)
        var updated = overwritten
        dataUpdates.forEach { (k, v) -> updated = updated.update(k, v) }

        return signJws(
            issuerKey = key,
            issuerDid = did,
            subjectDid = subject,
            additionalJwtHeader = additionalJwtHeader,
            additionalJwtOptions = additionalJwtOptions
        )
    }


    val dataFunctions = mapOf<String, suspend (call: W3CDataMergeUtils.FunctionCall) -> JsonElement>(
        "subjectDid" to { it.fromContext() },
        "issuerDid" to { it.fromContext() },

        "context" to { it.context[it.args!!]!! },

        "timestamp" to { JsonPrimitive(Clock.System.now().toString()) },
        "timestamp-seconds" to { JsonPrimitive(Clock.System.now().epochSeconds) },

        "timestamp-in" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).toString()) },
        "timestamp-in-seconds" to { JsonPrimitive((Clock.System.now() + Duration.parse(it.args!!)).epochSeconds) },

        "timestamp-before" to { JsonPrimitive((Clock.System.now() - Duration.parse(it.args!!)).toString()) },
        "timestamp-before-seconds" to { JsonPrimitive((Clock.System.now() - Duration.parse(it.args!!)).epochSeconds) },

        "uuid" to { JsonPrimitive("urn:uuid:${randomUUID()}") },
        "webhook" to { JsonPrimitive(HttpClient().get(it.args!!).bodyAsText()) },
        "webhook-json" to { Json.parseToJsonElement(HttpClient().get(it.args!!).bodyAsText()) },

        "last" to {
            it.history?.get(it.args!!)
                ?: throw IllegalArgumentException("No such function in history or no history: ${it.args}")
        }
    )

    /**
     * Merge data with mappings and issue
     */
    suspend fun W3CVC.mergingIssue(
        key: Key,
        issuerDid: String,
        subjectDid: String,

        mappings: JsonObject,

//
//        dataOverwrites: Map<String, JsonElement>,
//        dataUpdates: Map<String, Map<String, JsonElement>>,
        additionalJwtHeader: Map<String, String>,
        additionalJwtOptions: Map<String, JsonElement>,

        completeJwtWithDefaultCredentialData: Boolean = true
    ): String {

        /*val jwtMappings = mappings.filterKeys { it.startsWith("jwt:") }.mapKeys { it.key.removePrefix("jwt:") }
        println("JWT MAPPINGS: $jwtMappings")

        val dataMappings = JsonObject(mappings.filterKeys { !it.startsWith("jwt") })*/

        val context = mapOf(
            "issuerDid" to JsonPrimitive(issuerDid),
            "subjectDid" to JsonPrimitive(subjectDid)
        )

        val mapped = this.mergeWithMapping(mappings, context, dataFunctions)

        val vc = mapped.vc
        val jwtRes = mapped.results.mapKeys { it.key.removePrefix("jwt:") }.toMutableMap()

        fun completeJwtAttributes(attribute: String, completer: () -> JsonElement?) {
            if (attribute !in jwtRes) {
                val completed = completer.invoke()

                if (completed != null) {
                    jwtRes[attribute] = completed
                }
            }
        }

        if (completeJwtWithDefaultCredentialData) {
            completeJwtAttributes("jti") { vc["id"] }
            completeJwtAttributes("exp") {
                vc["expirationDate"]?.let { Instant.parse(it.jsonPrimitive.content) }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
            }
            completeJwtAttributes("iat") {
                vc["issuanceDate"]?.let { Instant.parse(it.jsonPrimitive.content) }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
            }
            completeJwtAttributes("nbf") {
                vc["issuanceDate"]?.let { Instant.parse(it.jsonPrimitive.content) - 90.seconds }
                    ?.epochSeconds?.let { JsonPrimitive(it) }
            }
        }

        return vc.signJws(
            issuerKey = key,
            issuerDid = issuerDid,
            subjectDid = subjectDid,
            additionalJwtHeader = additionalJwtHeader.toMutableMap().apply {
                put("typ", "JWT")
            },
            additionalJwtOptions = additionalJwtOptions.toMutableMap().apply {
                putAll(jwtRes)
            }
        )
    }
}
