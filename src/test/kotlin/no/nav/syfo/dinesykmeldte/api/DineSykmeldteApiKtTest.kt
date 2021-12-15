package no.nav.syfo.dinesykmeldte.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.dinesykmeldte.model.Sykmeldt
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.log
import no.nav.syfo.util.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testutil.generateJWTLoginservice
import java.nio.file.Paths

internal class DineSykmeldteApiKtTest : Spek({

    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()
    val env = mockk<Environment>() {
        every { loginserviceIdportenAudience } returns listOf("loginService")
    }
    val dineSykmeldteService = mockk<DineSykmeldteService>()

    describe("Test av Dine Sykmeldte API") {

        with(TestApplicationEngine()) {
            start()

            application.setupAuth(
                env = env,
                jwkProviderLoginservice = jwkProvider,
                loginserviceIssuer = "iss"
            )

            application.routing {
                authenticate {
                    registerDineSykmeldteApi(dineSykmeldteService)
                }
            }

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }

            it("Skal returnere sykmeldt") {
                coEvery { dineSykmeldteService.getDineSykmeldte(any()) } returns
                    listOf(
                        Sykmeldt(
                            "lederId",
                            "orgnr",
                            "fnr",
                            "Navn Navnesen",
                            null,
                            aktivSykmelding = true
                        )
                    )
                with(
                    handleRequest(HttpMethod.Get, "api/dinesykmeldte") {
                        addHeader("Accept", "application/json")
                        addHeader("Content-Type", "application/json")
                        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWTLoginservice("2", env.loginserviceIdportenAudience.first(), subject = "12345678912")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmeldt = objectMapper.readValue<List<Sykmeldt>>(response.content!!)

                    sykmeldt shouldBeEqualTo listOf(
                        Sykmeldt(
                            "lederId",
                            "orgnr",
                            "fnr",
                            "Navn Navnesen",
                            null,
                            aktivSykmelding = true
                        )
                    )
                }
            }

            it("Skal returnere sykmeldt") {
                coEvery { dineSykmeldteService.getSykmeldt(any(), any()) } returns
                    Sykmeldt(
                        "lederId",
                        "orgnr",
                        "fnr",
                        "Navn Navnesen",
                        null,
                        aktivSykmelding = true
                    )
                with(
                    handleRequest(HttpMethod.Get, "api/dinesykmeldte/lederId") {
                        addHeader("Accept", "application/json")
                        addHeader("Content-Type", "application/json")
                        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWTLoginservice("2", env.loginserviceIdportenAudience.first(), subject = "12345678912")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    val sykmeldt = objectMapper.readValue<Sykmeldt>(response.content!!)

                    sykmeldt shouldBeEqualTo Sykmeldt(
                        "lederId",
                        "orgnr",
                        "fnr",
                        "Navn Navnesen",
                        null,
                        aktivSykmelding = true
                    )
                }
            }

            it("Skal returnere 401 Unauthorized hvis auth header mangler") {
                with(
                    handleRequest(HttpMethod.Get, "api/dinesykmeldte/lederId") {
                        addHeader("Accept", "application/json")
                        addHeader("Content-Type", "application/json")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})