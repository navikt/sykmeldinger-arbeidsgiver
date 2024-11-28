package no.nav.syfo.dinesykmeldte.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import java.nio.file.Paths
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.dinesykmeldte.model.Sykmeldt
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.log
import no.nav.syfo.util.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import testutil.generateJWT

class DineSykmeldteApiKtTest :
    FunSpec({
        val path = "src/test/resources/jwkset.json"
        val uri = Paths.get(path).toUri().toURL()
        val jwkProvider = JwkProviderBuilder(uri).build()
        val env =
            Environment(
                cluster = "dev-gcp",
                tokenXWellKnownUrl = "https://tokenx",
                clientIdTokenX = "clientId",
                allowedOrigin = emptyList(),
                dbHost = "",
                dbPort = "",
                dbName = "",
                databasePassword = "",
                databaseUsername = "",
                pdlScope = "",
                pdlGraphqlPath = "",
                aadAccessTokenUrl = "",
                clientId = "",
                clientSecret = "",
                electorPath = "",
            )
        val dineSykmeldteService = mockk<DineSykmeldteService>()

        context("Test av Dine Sykmeldte API") {
            test("Skal returnere sykmeldt") {
                testApplication {
                    application {
                        setupAuth(
                            env = env,
                            jwkProviderTokenX = jwkProvider,
                            tokenXIssuer = "issuer",
                        )

                        routing {
                            authenticate("tokenx") {
                                route("/api") { registerDineSykmeldteApi(dineSykmeldteService) }
                            }
                        }

                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                            }
                        }
                        install(StatusPages) {
                            exception<Throwable> { call, cause ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    cause.message ?: "Unknown error"
                                )
                                log.error("Caught exception", cause)
                                throw cause
                            }
                        }
                    }
                    coEvery { dineSykmeldteService.getDineSykmeldte(any()) } returns
                        listOf(
                            Sykmeldt(
                                "lederId",
                                "orgnr",
                                "fnr",
                                "Navn Navnesen",
                                null,
                                aktivSykmelding = true,
                            ),
                        )
                    val response =
                        client.get("api/dinesykmeldte") {
                            header("Accept", "application/json")
                            header("Content-Type", "application/json")
                            header(
                                HttpHeaders.Authorization,
                                "Bearer ${generateJWT("client",
                                    "clientId", subject = "12345678912", issuer = "issuer")}"
                            )
                        }

                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val sykmeldt = objectMapper.readValue<List<Sykmeldt>>(response.bodyAsText())

                    sykmeldt shouldBeEqualTo
                        listOf(
                            Sykmeldt(
                                "lederId",
                                "orgnr",
                                "fnr",
                                "Navn Navnesen",
                                null,
                                aktivSykmelding = true,
                            ),
                        )
                }
            }

            test("Skal returnere sykmeldt") {
                testApplication {
                    application {
                        setupAuth(
                            env = env,
                            jwkProviderTokenX = jwkProvider,
                            tokenXIssuer = "issuer",
                        )

                        routing {
                            authenticate("tokenx") {
                                route("/api") { registerDineSykmeldteApi(dineSykmeldteService) }
                            }
                        }

                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                            }
                        }
                        install(StatusPages) {
                            exception<Throwable> { call, cause ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    cause.message ?: "Unknown error"
                                )
                                log.error("Caught exception", cause)
                                throw cause
                            }
                        }
                    }
                    coEvery { dineSykmeldteService.getSykmeldt(any(), any()) } returns
                        Sykmeldt(
                            "lederId",
                            "orgnr",
                            "fnr",
                            "Navn Navnesen",
                            null,
                            aktivSykmelding = true,
                        )

                    val response =
                        client.get("api/dinesykmeldte/lederId") {
                            header("Accept", "application/json")
                            header("Content-Type", "application/json")
                            header(
                                HttpHeaders.Authorization,
                                "Bearer ${generateJWT("client",
                                    "clientId", subject = "12345678912", issuer = "issuer")}"
                            )
                        }

                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val sykmeldt = objectMapper.readValue<Sykmeldt>(response.bodyAsText())

                    sykmeldt shouldBeEqualTo
                        Sykmeldt(
                            "lederId",
                            "orgnr",
                            "fnr",
                            "Navn Navnesen",
                            null,
                            aktivSykmelding = true,
                        )
                }
            }

            test("Skal returnere 401 Unauthorized hvis auth header mangler") {
                testApplication {
                    application {
                        setupAuth(
                            env = env,
                            jwkProviderTokenX = jwkProvider,
                            tokenXIssuer = "issuer",
                        )

                        routing {
                            authenticate("tokenx") {
                                route("/api") { registerDineSykmeldteApi(dineSykmeldteService) }
                            }
                        }

                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                            }
                        }
                        install(StatusPages) {
                            exception<Throwable> { call, cause ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    cause.message ?: "Unknown error"
                                )
                                log.error("Caught exception", cause)
                                throw cause
                            }
                        }
                    }

                    val response =
                        client.get("api/dinesykmeldte/lederId") {
                            header("Accept", "application/json")
                            header("Content-Type", "application/json")
                        }

                    response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    })
