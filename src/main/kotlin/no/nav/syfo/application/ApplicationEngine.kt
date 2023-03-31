package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.api.setupSwaggerDocApi
import no.nav.syfo.application.metrics.monitorHttpRequests
import no.nav.syfo.dinesykmeldte.api.registerDineSykmeldteApi
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.log
import java.util.UUID

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    dineSykmeldteService: DineSykmeldteService,
    jwkProvider: JwkProvider,
    loginserviceIssuer: String,
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort, configure = {
        // Increase timeout of Netty to handle large content bodies
        responseWriteTimeoutSeconds = 40
    }) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
            }
        }

        setupAuth(
            jwkProviderLoginservice = jwkProvider,
            env = env,
            loginserviceIssuer = loginserviceIssuer,
            jwkProviderTokenX = jwkProviderTokenX,
            tokenXIssuer = tokenXIssuer,
        )
        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Options)
            env.allowedOrigin.forEach {
                hosts.add("https://$it")
            }
            allowHeader("nav_csrf_protection")
            allowCredentials = true
            allowNonSimpleContentTypes = true
        }
        install(CallId) {
            generate { UUID.randomUUID().toString() }
            verify { callId: String -> callId.isNotEmpty() }
            header(HttpHeaders.XCorrelationId)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                log.error("Caught exception", cause)
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            }
        }

        routing {
            registerNaisApi(applicationState)
            if (env.cluster == "dev-gcp") {
                setupSwaggerDocApi()
            }
            authenticate("loginservice") {
                route("/api") {
                    registerDineSykmeldteApi(dineSykmeldteService)
                }
            }
            authenticate("tokenx") {
                route("/api/v2") {
                    registerDineSykmeldteApi(dineSykmeldteService)
                }
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
