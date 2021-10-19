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
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.narmesteleder.client.NarmestelederClient
import no.nav.syfo.narmesteleder.db.NarmestelederDB
import no.nav.syfo.narmesteleder.kafka.NarmestelederConsumer
import no.nav.syfo.narmesteleder.kafka.model.Narmesteleder
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.kafka.SykmeldingConsumer
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
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

    val narmestelederClient = NarmestelederClient(httpClient, env.narmestelederUrl)

    val sykmeldingService = SykmeldingService(database = database)

    val dineSykmeldteService = DineSykmeldteService(narmestelederClient, sykmeldingService)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
        jwkProvider = jwkProviderLoginservice,
        loginserviceIssuer = wellKnown.issuer,
        dineSykmeldteService = dineSykmeldteService
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    val kafkaCredentials = env.kafkaCredentials()
    val properties = loadBaseConfig(env, kafkaCredentials).toConsumerConfig(
        env.applicationName + "-consumer-v2",
        JacksonKafkaDeserializer::class
    )
    properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
    properties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "100"
    val kafkaConsumer =
        KafkaConsumer(properties, StringDeserializer(), JacksonKafkaDeserializer(SendtSykmeldingKafkaMessage::class))

    val aivenKafkaConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig().also {
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "100"
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        }.toConsumerConfig("narmesteleder-arbeidsforhold", JacksonKafkaDeserializer::class),
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
    narmestelederConsumer.startConsumer()

    SykmeldingConsumer(
        kafkaConsumer = kafkaConsumer,
        database = database,
        applicationState = applicationState,
        topic = env.syfoSendtSykmeldingTopic
    ).startConsumer()

    applicationState.ready = true
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
