package no.nav.syfo.sykmelding

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sykmelding.db.getSykmeldingArbeidsgiverKafkaMessage
import no.nav.syfo.sykmelding.db.insertOrUpdateSykmeldingArbeidsgiver
import no.nav.syfo.sykmelding.kafka.model.SykmeldingArbeidsgiverKafkaMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.time.LocalDate

class SykmeldingAivenServiceTest : FunSpec({
    val database = mockk<DatabaseInterface>(relaxed = true)
    val kafkaConsumer = mockk<KafkaConsumer<String, SykmeldingArbeidsgiverKafkaMessage?>>()
    val topic = "teamsykmelding.sendt-sykmelding-topic"
    val applicationState = mockk<ApplicationState>()
    val pdlService = mockk<PdlPersonService>()

    val sykmeldingConsumer = SykmeldingAivenService(kafkaConsumer, database, applicationState, topic, pdlService, "test")
    mockkStatic("no.nav.syfo.sykmelding.db.SykmeldingDbKt")
    afterTest {
        clearMocks(database, kafkaConsumer, pdlService, applicationState)
        mockkStatic("no.nav.syfo.sykmelding.db.SykmeldingDbKt")
    }
    beforeTest {
        every { kafkaConsumer.subscribe(any<List<String>>()) } returns Unit
        coEvery { pdlService.getPerson(any(), any()) } returns PdlPerson(Navn("fornavn", null, "etternavn"), null)
        every { database.insertOrUpdateSykmeldingArbeidsgiver(any(), any(), any()) } returns Unit
    }
    context("Test handling sykmelding") {
        test("Test lagring av sykmelding") {
            val consumerRecords = ConsumerRecords<String, SykmeldingArbeidsgiverKafkaMessage?>(
                mutableMapOf<TopicPartition, List<ConsumerRecord<String, SykmeldingArbeidsgiverKafkaMessage?>>>(
                    TopicPartition("1", 1) to listOf(
                        ConsumerRecord(
                            "topic",
                            1,
                            1,
                            "String",
                            getSykmeldingArbeidsgiverKafkaMessage(
                                LocalDate.now().minusMonths(5),
                                LocalDate.now().minusMonths(4),
                            ),
                        ),
                    ),
                ),
            )
            every { applicationState.ready } returns true andThen false
            every { kafkaConsumer.poll(any<Duration>()) } returns consumerRecords

            sykmeldingConsumer.start()

            verify(exactly = 1) { database.insertOrUpdateSykmeldingArbeidsgiver(any(), any(), any()) }
        }
        test("test ignore old sykmelding") {
            val consumerRecords = ConsumerRecords<String, SykmeldingArbeidsgiverKafkaMessage?>(
                mutableMapOf<TopicPartition, List<ConsumerRecord<String, SykmeldingArbeidsgiverKafkaMessage?>>>(
                    TopicPartition("1", 1) to listOf(
                        ConsumerRecord(
                            "topic",
                            1,
                            1,
                            "String",
                            getSykmeldingArbeidsgiverKafkaMessage(
                                fom = LocalDate.now().minusMonths(5),
                                tom = LocalDate.now().minusMonths(4).minusDays(1),
                            ),
                        ),
                    ),
                ),
            )
            every { applicationState.ready } returns true andThen false
            every { kafkaConsumer.poll(any<Duration>()) } returns consumerRecords

            sykmeldingConsumer.start()

            verify(exactly = 0) { database.insertOrUpdateSykmeldingArbeidsgiver(any(), any(), any()) }
        }
    }
})
