package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.CORS
import io.ktor.features.CallId
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.api.setupSwaggerDocApi
import no.nav.syfo.application.metrics.monitorHttpRequests
import no.nav.syfo.dinesykmeldte.api.registerDineSykmeldteApi
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.log
import java.util.UUID

@KtorExperimentalAPI
fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    dineSykmeldteService: DineSykmeldteService,
    jwkProvider: JwkProvider,
    loginserviceIssuer: String
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
            }
        }

        setupAuth(jwkProviderLoginservice = jwkProvider, env = env, loginserviceIssuer = loginserviceIssuer)
        install(CORS) {
            method(HttpMethod.Get)
            method(HttpMethod.Options)
            host(if (env.cluster == "dev-gcp") { "*" } else { env.allowedOrigin }, schemes = listOf("https"))
            header("nav_csrf_protection")
            allowCredentials = true
            allowNonSimpleContentTypes = true
        }
        install(CallId) {
            generate { UUID.randomUUID().toString() }
            verify { callId: String -> callId.isNotEmpty() }
            header(HttpHeaders.XCorrelationId)
        }
        install(StatusPages) {
            exception<Throwable> { cause ->
                log.error("Caught exception", cause)
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            }
        }

        routing {
            registerNaisApi(applicationState)
            if (env.cluster == "dev-gcp") {
                setupSwaggerDocApi()
            }
            authenticate {
                registerDineSykmeldteApi(dineSykmeldteService)
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
