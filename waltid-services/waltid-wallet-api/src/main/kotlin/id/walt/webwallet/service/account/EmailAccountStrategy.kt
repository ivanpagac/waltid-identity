package id.walt.webwallet.service.account

import de.mkammerer.argon2.Argon2Factory
import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.web.UnauthorizedException
import id.walt.webwallet.web.controllers.ByteLoginRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object EmailAccountStrategy : PasswordAccountStrategy<EmailAccountRequest>() {

    override suspend fun register(tenant: String, request: EmailAccountRequest): Result<RegistrationResult> = runCatching {
        val name = request.name ?: throw IllegalArgumentException("No name provided!")
        val email = request.email

        require(!AccountsService.hasAccountEmail(tenant, email)) { "Account already exists!" }

        val hash = hashPassword(ByteLoginRequest(request).password)

        val createdAccountId = transaction {
            Accounts.insert {
                it[Accounts.tenant] = tenant
                it[id] = UUID.generateUUID()
                it[Accounts.name] = name
                it[Accounts.email] = email
                it[password] = hash
                it[createdOn] = Clock.System.now().toJavaInstant()
            }[Accounts.id]
        }

        RegistrationResult(createdAccountId)
    }


    //override suspend fun authenticate(tenant: String, request: EmailAccountRequest): AuthenticatedUser = UsernameAuthenticatedUser(UUID("a218913e-b8ec-4ef4-a945-7e9ada448ff9"), request.email)
    override suspend fun authenticate(tenant: String, request: EmailAccountRequest): AuthenticatedUser =
        ByteLoginRequest(request).let { req ->
            val email = request.email

            val (matchedAccount, pwHash) = transaction {

                val accounts = Accounts.selectAll().where { (Accounts.tenant eq tenant) and (Accounts.email eq email) }
                if (accounts.empty()) {
                    throw UnauthorizedException("Unknown user \"${req.username}\".")
                }
                val matchedAccount = accounts.first()

                val pwHash = matchedAccount[Accounts.password]
                    ?: throw UnauthorizedException("User \"${req.username}\" does not have password authentication enabled.")

                Pair(matchedAccount, pwHash)
            }

            val passwordMatches = Argon2Factory.create().run {
                verify(pwHash, req.password).also {
                    wipeArray(req.password)
                }
            }

            if (passwordMatches) {
                val id = matchedAccount[Accounts.id]
                // TODO: change id to wallet-id (also in the frontend)
                return UsernameAuthenticatedUser(id, req.username)
            } else {
                throw UnauthorizedException("Invalid password for \"${req.username}\"!")
            }
        }
}
