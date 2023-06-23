package no.nav.syfo.sykmelding

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.pdl.exceptions.NameNotFoundInPdlException
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.db.deleteSykmelding
import no.nav.syfo.sykmelding.db.insertOrUpdateSykmeldingArbeidsgiver
import no.nav.syfo.sykmelding.kafka.model.SykmeldingArbeidsgiverKafkaMessage
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

class SykmeldingAivenService(
    private val kafkaConsumer: KafkaConsumer<String, SykmeldingArbeidsgiverKafkaMessage?>,
    private val database: DatabaseInterface,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val pdlPersonService: PdlPersonService,
    private val cluster: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(SykmeldingAivenService::class.java)
        private const val POLL_DURATION_SECONDS = 10L
    }

    private var lastLogTime = Instant.now().toEpochMilli()
    private val logTimer = 60_000L
    private var ignoredSykmeldinger = 0

    @DelicateCoroutinesApi
    fun startConsumer() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (applicationState.ready) {
                try {
                    kafkaConsumer.subscribe(listOf(topic))
                    start()
                } catch (ex: Exception) {
                    log.error(
                        "Error running sykmelding kafka consumer, unsubscribing and waiting 10 seconds for retry",
                        ex
                    )
                    kafkaConsumer.unsubscribe()
                    delay(5_000)
                }
            }
        }
    }

    suspend fun start() {
        var processedMessages = 0
        while (applicationState.ready) {
            val sykmeldinger = kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
            sykmeldinger.forEach { handleSykmelding(it.key(), it.value()) }
            processedMessages += sykmeldinger.count()
            processedMessages = logProcessedMessages(processedMessages)
        }
    }

    private fun logProcessedMessages(processedMessages: Int): Int {
        val currentLogTime = Instant.now().toEpochMilli()
        if (processedMessages > 0 && currentLogTime - lastLogTime > logTimer) {
            log.info(
                "Processed $processedMessages messages, ignored sykmeldinger $ignoredSykmeldinger"
            )
            lastLogTime = currentLogTime
            return 0
        }
        return processedMessages
    }

    private suspend fun handleSykmelding(
        sykmeldingId: String,
        sykmeldingArbeidsgiverKafkaMessage: SykmeldingArbeidsgiverKafkaMessage?
    ) {
        if (sykmeldingArbeidsgiverKafkaMessage == null) {
            database.deleteSykmelding(sykmeldingId)
        } else {
            val latestTom =
                sykmeldingArbeidsgiverKafkaMessage.sykmelding.sykmeldingsperioder.maxOf { it.tom }
            if (skalIgnorereSykmelding(latestTom)) {
                ignoredSykmeldinger += 1
            } else {
                try {
                    val person =
                        pdlPersonService.getPerson(
                            fnr = sykmeldingArbeidsgiverKafkaMessage.kafkaMetadata.fnr,
                            callId = sykmeldingId
                        )
                    database.insertOrUpdateSykmeldingArbeidsgiver(
                        sykmeldingArbeidsgiverKafkaMessage,
                        person,
                        latestTom
                    )
                } catch (ex: NameNotFoundInPdlException) {
                    if (cluster != "dev-gcp") {
                        throw ex
                    } else {
                        log.info(
                            "Ignoring sykmelding when person is not found in pdl for sykmelding: $sykmeldingId"
                        )
                    }
                }
            }
        }
    }

    private fun skalIgnorereSykmelding(latestTom: LocalDate) =
        latestTom.isBefore(LocalDate.now().minusMonths(4))
}
