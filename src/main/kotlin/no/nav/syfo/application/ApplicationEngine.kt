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
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.util.UUID
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.application.metrics.monitorHttpRequests
import no.nav.syfo.dinesykmeldte.api.registerDineSykmeldteApi
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.log

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    dineSykmeldteService: DineSykmeldteService,
    jwkProviderTokenX: JwkProvider,
    tokenXIssuer: String,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(
        Netty,
        configure = {
            // Increase timeout of Netty to handle large content bodies
            responseWriteTimeoutSeconds = 40
            connector { port = env.applicationPort }
        }
    ) {
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
            env = env,
            jwkProviderTokenX = jwkProviderTokenX,
            tokenXIssuer = tokenXIssuer,
        )
        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Options)
            env.allowedOrigin.forEach { hosts.add("https://$it") }
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
                swaggerUI(path = "docs", swaggerFile = "openapi/sykmeldinger-arbeidsgiver-api.yaml")
            }

            authenticate("tokenx") {
                route("/api/v2") { registerDineSykmeldteApi(dineSykmeldteService) }
                route("/api") { registerDineSykmeldteApi(dineSykmeldteService) }
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
