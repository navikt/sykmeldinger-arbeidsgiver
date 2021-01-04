package no.nav.syfo.sykmelding.kafka

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.sykmelding.db.deleteSykmelding
import no.nav.syfo.sykmelding.db.insertOrUpdateSykmelding
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.time.Instant

class SykmeldingConsumer(
    private val kafkaConsumer: KafkaConsumer<String, SendtSykmeldingKafkaMessage?>,
    private val database: DatabaseInterface,
    private val applicationState: ApplicationState,
    private val topic: String,
) {

    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L

    fun startConsumer() {
        GlobalScope.launch {
            try {
                start()
            } catch (ex: Exception) {
                log.error("Error running kafka consumer", ex)
            }
        }
    }

    suspend fun start() {
        kafkaConsumer.subscribe(listOf(topic))
        var processedMessages = 0

        while (applicationState.ready) {
            val sykmeldinger = kafkaConsumer.poll(Duration.ZERO)
            sykmeldinger.forEach {
                handleSykmelding(it)
            }
            processedMessages += sykmeldinger.count()
            processedMessages = logProcessedMessages(processedMessages)
            delay(1)
        }
    }

    private fun logProcessedMessages(processedMessages: Int): Int {
        var currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            log.info("Processed $processedMessages messages")
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }

    private fun handleSykmelding(consumerRecord: ConsumerRecord<String, SendtSykmeldingKafkaMessage?>) {
        if (consumerRecord.value() == null) {
            database.deleteSykmelding(consumerRecord.key())
        } else {
            database.insertOrUpdateSykmelding(consumerRecord.value()!!)
        }
    }
}
