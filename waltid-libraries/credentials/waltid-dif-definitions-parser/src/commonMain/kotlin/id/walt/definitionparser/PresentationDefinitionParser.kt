package id.walt.definitionparser

import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.kotlinx.resolveOrNull
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.definitionparser.PresentationDefinition.InputDescriptor.Constraints.Field
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.OutputCollector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class JsonObjectEnquirer {

    private val compiledJsonPaths = HashMap<String, JsonPath>()

    private fun getJsonPath(path: String): JsonPath =
        compiledJsonPaths.getOrPut(path) { JsonPath.compile(path) }


    fun filterDocumentsByConstraints(documents: List<JsonObject>, constraints: List<Field>): List<JsonObject> =
        documents.filter { document ->
            constraints.all { field ->
                val resolvedPath = field.path.firstNotNullOfOrNull { document.resolveOrNull(getJsonPath(it)) }

                if (resolvedPath == null) {
                    false
                } else {
                    if (field.filter != null) {
                        val schema = JsonSchema.fromJsonElement(field.filter)
                        schema.validate(resolvedPath, OutputCollector.flag()).valid
                    } else true
                }
            }
        }
}

object PresentationDefinitionParser {

    fun matchCredentialsForInputDescriptor(credentials: List<W3CVC>, inputDescriptor: PresentationDefinition.InputDescriptor): List<W3CVC> {

        println("--- Checking descriptor ${inputDescriptor.name} --")

        val enquirer = JsonObjectEnquirer()

        return enquirer.filterDocumentsByConstraints(credentials.map { it.toJsonObject() }, inputDescriptor.constraints.fields!!).map { W3CVC(it) }

    }

    fun matchCredentialsForDefinition(credentials: List<W3CVC>, definition: PresentationDefinition) {

    }

}


fun main() {
    //language=JSON
    val credentials = """
        [
          {
            "@context": "https://www.w3.org/2018/credentials/v1",
            "id": "https://business-standards.org/schemas/employment-history.json",
            "type": [
              "VerifiableCredential",
              "GenericEmploymentCredential"
            ],
            "issuer": "did:foo:123",
            "issuanceDate": "2010-01-01T19:73:24Z",
            "credentialSubject": {
              "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
              "active": true
            },
            "proof": {
              "type": "EcdsaSecp256k1VerificationKey2019",
              "created": "2017-06-18T21:19:10Z",
              "proofPurpose": "assertionMethod",
              "verificationMethod": "https://example.edu/issuers/keys/1",
              "jws": "..."
            }
          },
          {
            "@context": "https://www.w3.org/2018/credentials/v1",
            "id": "https://eu.com/claims/DriversLicense",
            "type": [
              "EUDriversLicense"
            ],
            "issuer": "did:foo:123",
            "issuanceDate": "2010-01-01T19:73:24Z",
            "credentialSubject": {
              "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
              "license": {
                "number": "34DGE352",
                "dob": "07/13/80"
              }
            },
            "proof": {
              "type": "RsaSignature2018",
              "created": "2017-06-18T21:19:10Z",
              "proofPurpose": "assertionMethod",
              "verificationMethod": "https://example.edu/issuers/keys/1",
              "jws": "..."
            }
          }
        ]
    """.trimIndent()

    //language=JSON
    val inputDescriptors = """
        [
          {
            "id": "banking_input_1",
            "name": "Bank Account Information",
            "purpose": "Bank Account required to remit payment.",
            "group": [
              "A"
            ],
            "constraints": {
              "limit_disclosure": "required",
              "fields": [
                {
                  "path": [
                    "${'$'}.issuer",
                    "${'$'}.vc.issuer",
                    "${'$'}.iss"
                  ],
                  "purpose": "We can only verify bank accounts if they are attested by a trusted bank, auditor or regulatory authority.",
                  "filter": {
                    "type": "string",
                    "pattern": "^did:example:123${'$'}|^did:example:456${'$'}"
                  }
                },
                {
                  "path": [
                    "${'$'}.credentialSubject.account[*].account_number",
                    "${'$'}.vc.credentialSubject.account[*].account_number",
                    "${'$'}.account[*].account_number"
                  ],
                  "purpose": "We can only remit payment to a currently-valid bank account in the US, France, or Germany, submitted as an ABA Acct # or IBAN.",
                  "filter": {
                    "type": "string",
                    "pattern": "^[0-9]{10-12}|^(DE|FR)[0-9]{2}\\s?([0-9a-zA-Z]{4}\\s?){4}[0-9a-zA-Z]{2}${'$'}"
                  },
                  "intent_to_retain": true
                },
                {
                  "path": [
                    "${'$'}.credentialSubject.portfolio_value",
                    "${'$'}.vc.credentialSubject.portfolio_value",
                    "${'$'}.portfolio_value"
                  ],
                  "purpose": "A current portfolio value of at least one million dollars is required to insure your application",
                  "filter": {
                    "type": "number",
                    "minimum": 1000000
                  },
                  "intent_to_retain": true
                }
              ]
            }
          },
          {
            "id": "banking_input_2",
            "name": "Bank Account Information",
            "purpose": "We can only remit payment to a currently-valid bank account.",
            "group": [
              "A"
            ],
            "constraints": {
              "fields": [
                {
                  "path": [
                    "${'$'}.issuer",
                    "${'$'}.vc.issuer",
                    "${'$'}.iss"
                  ],
                  "purpose": "We can only verify bank accounts if they are attested by a trusted bank, auditor or regulatory authority.",
                  "filter": {
                    "type": "string",
                    "pattern": "^did:example:123${'$'}|^did:example:456${'$'}"
                  }
                },
                {
                  "path": [
                    "${'$'}.credentialSubject.account[*].id",
                    "${'$'}.vc.credentialSubject.account[*].id",
                    "${'$'}.account[*].id"
                  ],
                  "purpose": "We can only remit payment to a currently-valid bank account in the US, France, or Germany, submitted as an ABA Acct # or IBAN.",
                  "filter": {
                    "type": "string",
                    "pattern": "^[0-9]{10-12}|^(DE|FR)[0-9]{2}\\s?([0-9a-zA-Z]{4}\\s?){4}[0-9a-zA-Z]{2}${'$'}"
                  },
                  "intent_to_retain": true
                },
                {
                  "path": [
                    "${'$'}.credentialSubject.account[*].route",
                    "${'$'}.vc.credentialSubject.account[*].route",
                    "${'$'}.account[*].route"
                  ],
                  "purpose": "We can only remit payment to a currently-valid account at a US, Japanese, or German federally-accredited bank, submitted as an ABA RTN or SWIFT code.",
                  "filter": {
                    "type": "string",
                    "pattern": "^[0-9]{9}|^([a-zA-Z]){4}([a-zA-Z]){2}([0-9a-zA-Z]){2}([0-9a-zA-Z]{3})?${'$'}"
                  },
                  "intent_to_retain": true
                }
              ]
            }
          },
          {
            "id": "employment_input",
            "name": "Employment History",
            "purpose": "We are only verifying one current employment relationship, not any other information about employment.",
            "group": [
              "B"
            ],
            "constraints": {
              "limit_disclosure": "required",
              "fields": [
                {
                  "path": [
                    "${'$'}.jobs[*].active"
                  ],
                  "filter": {
                    "type": "boolean",
                    "pattern": "true"
                  }
                }
              ]
            }
          },
          {
            "id": "drivers_license_input_1",
            "name": "EU Driver's License",
            "group": [
              "C"
            ],
            "constraints": {
              "fields": [
                {
                  "path": [
                    "${'$'}.issuer",
                    "${'$'}.vc.issuer",
                    "${'$'}.iss"
                  ],
                  "purpose": "We can only accept digital driver's licenses issued by national authorities of EU member states or trusted notarial auditors.",
                  "filter": {
                    "type": "string",
                    "pattern": "did:example:gov1|did:example:gov2"
                  }
                },
                {
                  "path": [
                    "${'$'}.credentialSubject.dob",
                    "${'$'}.credentialSubject.license.dob",
                    "${'$'}.vc.credentialSubject.dob",
                    "${'$'}.dob"
                  ],
                  "purpose": "We must confirm that the driver was at least 21 years old on April 16, 2020.",
                  "filter": {
                    "type": "string",
                    "format": "date"
                  }
                }
              ]
            }
          },
          {
            "id": "drivers_license_input_2",
            "name": "Driver's License from one of 50 US States",
            "group": [
              "C"
            ],
            "constraints": {
              "fields": [
                {
                  "path": [
                    "${'$'}.issuer",
                    "${'$'}.vc.issuer",
                    "${'$'}.iss"
                  ],
                  "purpose": "We can only accept digital driver's licenses issued by the 50 US states' automative affairs agencies.",
                  "filter": {
                    "type": "string",
                    "pattern": "did:example:gov1|did:web:dmv.ca.gov|did:example:oregonDMV"
                  }
                },
                {
                  "path": [
                    "${'$'}.credentialSubject.birth_date",
                    "${'$'}.vc.credentialSubject.birth_date",
                    "${'$'}.birth_date"
                  ],
                  "purpose": "We must confirm that the driver was at least 21 years old on April 16, 2020.",
                  "filter": {
                    "type": "string",
                    "format": "date",
                    "forrmatMaximum": "1999-05-16"
                  }
                }
              ]
            }
          },
          {
        "id": "wa_driver_license",
        "name": "Washington State Business License",
        "purpose": "We can only allow licensed Washington State business representatives into the WA Business Conference",
        "constraints": {
          "fields": [
            {
              "path": [
                "${'$'}.credentialSubject.dateOfBirth",
                "${'$'}.credentialSubject.dob",
                "${'$'}.vc.credentialSubject.dateOfBirth",
                "${'$'}.vc.credentialSubject.dob"
              ]
            }
          ]
        }
      }
        ]
    """.trimIndent()

    val credentialList = Json.decodeFromString<List<W3CVC>>(credentials)
    val inputDescriptorList = Json.decodeFromString<List<PresentationDefinition.InputDescriptor>>(inputDescriptors)

    inputDescriptorList.forEach {
        val matched = PresentationDefinitionParser.matchCredentialsForInputDescriptor(credentialList, it)
        println("Matched for ${it.name}: $matched")
    }
}
