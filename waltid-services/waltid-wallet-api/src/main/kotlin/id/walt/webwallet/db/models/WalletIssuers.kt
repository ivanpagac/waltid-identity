package id.walt.webwallet.db.models

import org.jetbrains.exposed.dao.id.UUIDTable

object WalletIssuers : UUIDTable("wallet_issuers") {
    val wallet = reference("wallet", Wallets)
    val did = varchar("did", 1024)
    val description = text("description").nullable().default("no description")
    val uiEndpoint = varchar("ui", 128)
    val configurationEndpoint = varchar("configuration", 256)
    val authorized = bool("authorized").default(false)//authorized to push credentials silently

    init {
        uniqueIndex(wallet, did)
    }
}
