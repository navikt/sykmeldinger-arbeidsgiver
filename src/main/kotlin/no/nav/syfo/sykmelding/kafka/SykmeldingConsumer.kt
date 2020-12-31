package no.nav.syfo.sykmelding.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.sykmelding.db.deleteSykmelding
import no.nav.syfo.sykmelding.db.insertOrUpdateSykmelding
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class SykmeldingConsumer(
    private val kafkaConsumer: KafkaConsumer<String, SendtSykmeldingKafkaMessage?>,
    private val database: DatabaseInterface,
    private val applicationState: ApplicationState,
    private val topic: String,
) {
    suspend fun start() {
        kafkaConsumer.subscribe(listOf(topic))
        while (applicationState.ready) {
            val sykmeldinger = kafkaConsumer.poll(Duration.ZERO)
            sykmeldinger.forEach {
                handleSykmelding(it)
            }
            delay(1)
        }
    }

    private fun handleSykmelding(consumerRecord: ConsumerRecord<String, SendtSykmeldingKafkaMessage?>) {
        if (consumerRecord.value() == null) {
            database.deleteSykmelding(consumerRecord.key())
        } else {
            database.insertOrUpdateSykmelding(consumerRecord.value()!!)
        }
    }
}
