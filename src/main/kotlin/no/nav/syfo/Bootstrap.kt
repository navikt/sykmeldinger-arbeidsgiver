package no.nav.syfo

import io.prometheus.client.hotspot.DefaultExports
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.database.Database
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.sykmelding.kafka.SykmeldingConsumer
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.util.JacksonKafkaDeserializer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sykmeldinger-arbeidsgiver")

fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    val database = Database(env)
    val kafkaCredentials = env.kafkaCredentials()
    val properties = loadBaseConfig(env, kafkaCredentials).toConsumerConfig(
        env.applicationName + "-consumer",
        JacksonKafkaDeserializer::class
    )
    val kafkaConsumer =
        KafkaConsumer(properties, StringDeserializer(), JacksonKafkaDeserializer(SendtSykmeldingKafkaMessage::class))

    SykmeldingConsumer(
        kafkaConsumer = kafkaConsumer,
        database = database,
        applicationState = applicationState,
        topic = env.syfoSendtSykmeldingTopic
    ).startConsumer()

    applicationState.ready = true
}
