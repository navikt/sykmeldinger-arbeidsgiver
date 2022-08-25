import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.api.db.deleteReadStatus
import no.nav.syfo.dinesykmeldte.api.db.insertOrUpdateReadStatus
import no.nav.syfo.dinesykmeldte.kafka.model.DineSykmeldteLestStatusKafkaMessage
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class DineSykmeldteLestStatusService(
    private val kafkaConsumer: KafkaConsumer<String, DineSykmeldteLestStatusKafkaMessage?>,
    private val database: DatabaseInterface,
    private val applicationState: ApplicationState,
    private val topic: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(DineSykmeldteLestStatusService::class.java)
        private const val POLL_DURATION_SECONDS = 10L
    }

    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L

    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(listOf(topic))
                    start()
                } catch (ex: Exception) {
                    log.error("Error running lest-status kafka consumer, unsubscribing and waiting 10 seconds for retry", ex)
                    kafkaConsumer.unsubscribe()
                    delay(5_000)
                }
            }
        }
    }

    suspend fun start() {
        var processedMessages = 0
        while (applicationState.ready) {
            val lestStatus: ConsumerRecords<String, DineSykmeldteLestStatusKafkaMessage?> =
                kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))

            lestStatus.forEach {
                handleLestStatusMessage(it.key(), it.value())
            }
            processedMessages += lestStatus.count()
            processedMessages = logProcessedMessages(processedMessages)
        }
    }

    private fun logProcessedMessages(processedMessages: Int): Int {
        val currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            log.info("Processed $processedMessages read status messages")
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }

    private fun handleLestStatusMessage(key: String, value: DineSykmeldteLestStatusKafkaMessage?) {
        when (value) {
            null -> database.deleteReadStatus(key)
            else -> database.insertOrUpdateReadStatus(value)
        }
    }
}
