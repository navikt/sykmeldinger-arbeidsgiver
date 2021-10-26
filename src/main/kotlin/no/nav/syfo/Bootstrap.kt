package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.database.Database
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

fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val database = Database(env)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
    val httpClient = HttpClient(Apache, config)

    val wellKnown = getWellKnown(httpClient, env.loginserviceIdportenDiscoveryUrl)

    val jwkProviderLoginservice = JwkProviderBuilder(URL(wellKnown.jwks_uri))
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
        dineSykmeldteService = dineSykmeldteService
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    val aivenKafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "100"
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }.toConsumerConfig("sykmeldinger-arbeidsgiver", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(Narmesteleder::class)
    )
    val narmestelederDB = NarmestelederDB(database)
    val narmestelederConsumer = NarmestelederConsumer(
        narmestelederDB,
        aivenKafkaConsumer,
        env.narmestelederLeesahTopic,
        applicationState
    )

    applicationState.ready = true

    narmestelederConsumer.startConsumer()

    val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, env.clientId, env.clientSecret, httpClient)
    val pdlClient = PdlClient(
        httpClient,
        env.pdlGraphqlPath,
        PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    )
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClient, env.pdlScope)

    val aivenKafkaSykmeldingConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "100"
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }.toConsumerConfig("sykmeldinger-arbeidsgiver", JacksonKafkaDeserializer::class),
        StringDeserializer(),
        JacksonKafkaDeserializer(SykmeldingArbeidsgiverKafkaMessage::class)
    )

    SykmeldingAivenService(
        aivenKafkaSykmeldingConsumer, database, applicationState, env.syfoSendtSykmeldingTopicAiven, pdlPersonService, env.cluster
    ).startConsumer()

    DeleteSykmeldingService(database, applicationState).start()
}

fun getWellKnown(httpClient: HttpClient, wellKnownUrl: String) =
    runBlocking { httpClient.get<WellKnown>(wellKnownUrl) }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
