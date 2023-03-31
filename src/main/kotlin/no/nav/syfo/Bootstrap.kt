package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.narmesteleder.db.NarmestelederDB
import no.nav.syfo.narmesteleder.kafka.NarmestelederConsumer
import no.nav.syfo.narmesteleder.kafka.model.Narmesteleder
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.DeleteSykmeldingService
import no.nav.syfo.sykmelding.SykmeldingAivenService
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.kafka.model.SykmeldingArbeidsgiverKafkaMessage
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sykmeldinger-arbeidsgiver")

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val database = Database(env)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn("Retrying for statuscode ${response.status.value}, for url ${request.url}")
                    true
                } else {
                    false
                }
            }
        }
    }
    val httpClient = HttpClient(Apache, config)

    val wellKnown = getWellKnown(httpClient, env.loginserviceIdportenDiscoveryUrl)
    val jwkProviderLoginservice = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val wellKnownTokenX = getWellKnownTokenX(httpClient, env.tokenXWellKnownUrl)
    val jwkProviderTokenX = JwkProviderBuilder(URL(wellKnownTokenX.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val sykmeldingService = SykmeldingService(database = database)

    val dineSykmeldteService = DineSykmeldteService(sykmeldingService)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
        jwkProvider = jwkProviderLoginservice,
        loginserviceIssuer = wellKnown.issuer,
        dineSykmeldteService = dineSykmeldteService,
        jwkProviderTokenX = jwkProviderTokenX,
        tokenXIssuer = wellKnownTokenX.issuer,
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    val aivenKafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "100"
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        }.toConsumerConfig("sykmeldinger-arbeidsgiver", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(Narmesteleder::class),
    )
    val narmestelederDB = NarmestelederDB(database)
    val narmestelederConsumer = NarmestelederConsumer(
        narmestelederDB,
        aivenKafkaConsumer,
        env.narmestelederLeesahTopic,
        applicationState,
    )

    narmestelederConsumer.startConsumer()

    val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, env.clientId, env.clientSecret, httpClient)
    val pdlClient = PdlClient(
        httpClient,
        env.pdlGraphqlPath,
        PdlClient::class.java.getResource("/graphql/getPerson.graphql")!!.readText().replace(Regex("[\n\t]"), ""),
    )
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, env.pdlScope)

    val aivenKafkaSykmeldingConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "100"
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        }.toConsumerConfig("sykmeldinger-arbeidsgiver", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(SykmeldingArbeidsgiverKafkaMessage::class),
    )

    SykmeldingAivenService(
        aivenKafkaSykmeldingConsumer,
        database,
        applicationState,
        env.syfoSendtSykmeldingTopicAiven,
        pdlPersonService,
        env.cluster,
    ).startConsumer()

    val leaderElection = LeaderElection(httpClient, env.electorPath)
    DeleteSykmeldingService(database, leaderElection, applicationState).start()
    applicationServer.start()
}

fun getWellKnown(httpClient: HttpClient, wellKnownUrl: String) =
    runBlocking { httpClient.get(wellKnownUrl).body<WellKnown>() }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String,
)

fun getWellKnownTokenX(httpClient: HttpClient, wellKnownUrl: String) =
    runBlocking { httpClient.get(wellKnownUrl).body<WellKnownTokenX>() }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnownTokenX(
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String,
)
