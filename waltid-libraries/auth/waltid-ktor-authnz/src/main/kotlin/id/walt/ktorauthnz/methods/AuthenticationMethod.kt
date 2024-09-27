package id.walt.ktorauthnz.methods

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.AccountIdentifier
import id.walt.ktorauthnz.methods.data.AuthMethodStoredData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.SessionManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

abstract class AuthenticationMethod(open val id: String) {
    abstract fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext)


    suspend fun ApplicationCall.handleAuthSuccess(session: AuthSession, accountId: String?) {
        accountId?.let { session.accountId = it }
        session.progressFlow(this@AuthenticationMethod)

        if (session.status == AuthSessionStatus.OK) {
            check(session.token != null) { "Session token does not exist after successful authentication?" }
            this.response.cookies.append(Cookie("ktor-authnz-auth", session.token!!))
        }

        this.respond(session.toInformation())
    }


    inline fun <reified V : AuthMethodStoredData> lookupStoredData(identifier: AccountIdentifier): V {
        val storedData = KtorAuthnzManager.accountStore.lookupStoredDataFor(identifier, this) ?: error("No stored data for method: $id")
        return (storedData as? V) ?: error("${storedData::class.simpleName} is not requested ${V::class.simpleName}")
    }

    inline fun <reified V : AuthMethodStoredData> lookupStoredMultiData(session: AuthSession): V {
        val storedData = KtorAuthnzManager.accountStore.lookupStoredMultiDataForAccount(session, this) ?: error("No stored data for method: $id")
        return (storedData as? V) ?: error("${storedData::class.simpleName} is not requested ${V::class.simpleName}")
    }

    suspend fun PipelineContext<Unit, ApplicationCall>.getSession(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext): AuthSession {
        val currentContext = authContext.invoke(this)

        val session = if (currentContext.implicitSessionGeneration && currentContext.sessionId == null) {
            // Implicit session start
            SessionManager.openImplicitGlobalSession(currentContext.initialFlow!!)
        } else {
            // Session was started explicitly
            KtorAuthnzManager.sessionStore.resolveSessionId(currentContext.sessionId ?: error("No session id"))
        }

        return session
    }
}


fun Route.registerAuthenticationMethod(
    method: AuthenticationMethod,
    authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext,

    ) {
    method.apply {
        register(authContext)
    }
}

fun Route.registerAuthenticationMethods(
    methods: List<AuthenticationMethod>,
    authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext,
) {
    methods.forEach {
        it.apply {
            register(authContext)
        }
    }
}