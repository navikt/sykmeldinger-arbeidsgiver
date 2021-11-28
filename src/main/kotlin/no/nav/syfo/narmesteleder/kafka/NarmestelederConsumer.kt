package no.nav.syfo.narmesteleder.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.db.NarmestelederDB
import no.nav.syfo.narmesteleder.kafka.model.Narmesteleder
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.time.Duration

class NarmestelederConsumer(
    private val narmestelederDB: NarmestelederDB,
    private val kafkaConsumer: KafkaConsumer<String, Narmesteleder>,
    private val narmestelederTopic: String,
    private val applicationState: ApplicationState
) {
    companion object {
        private val log = LoggerFactory.getLogger(NarmestelederConsumer::class.java)
        private const val POLL_DURATION_SECONDS = 10L
    }

    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    start()
                } catch (ex: Exception) {
                    log.error("Error running kafka consumer, unsubscribing and waiting 10 seconds for retry", ex)
                    kafkaConsumer.unsubscribe()
                    delay(10_000)
                }
            }
        }
    }

    private fun start() {
        kafkaConsumer.subscribe(listOf(narmestelederTopic))
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS)).map { it.value() }.forEach {
                when (it.aktivTom) {
                    null -> narmestelederDB.insertOrUpdate(it)
                    else -> narmestelederDB.deleteNarmesteleder(it)
                }
            }
        }
    }
}
