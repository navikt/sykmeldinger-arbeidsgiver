package no.nav.syfo.dinesykmeldte.service

import DineSykmeldteLestStatusService
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.api.db.deleteReadStatus
import no.nav.syfo.dinesykmeldte.api.db.insertOrUpdateReadStatus
import no.nav.syfo.dinesykmeldte.kafka.model.DineSykmeldteLestStatusKafkaMessage
import no.nav.syfo.dinesykmeldte.kafka.model.KafkaMetadata
import no.nav.syfo.dinesykmeldte.kafka.model.NLReadCount
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

class DineSykmeldteLestStatusServiceTest : FunSpec({
    val database = mockk<DatabaseInterface>(relaxed = true)
    val kafkaConsumer = mockk<KafkaConsumer<String, DineSykmeldteLestStatusKafkaMessage?>>()
    val topic = "teamsykmelding.dinesykmeldte-lest-status"
    val applicationState = mockk<ApplicationState>()

    val consumer = DineSykmeldteLestStatusService(
        kafkaConsumer, database, applicationState, topic
    )

    mockkStatic("no.nav.syfo.dinesykmeldte.api.db.DineSykmeldteDbKt")

    afterTest {
        clearMocks(database, kafkaConsumer, applicationState)
        mockkStatic("no.nav.syfo.dinesykmeldte.api.db.DineSykmeldteDbKt")
    }

    beforeTest {
        every { database.insertOrUpdateReadStatus(any()) } returns Unit
        every { database.deleteReadStatus(any()) } returns Unit
    }

    context("Test handling read status message") {
        test("should read and store messages") {
            val consumerRecords = ConsumerRecords<String, DineSykmeldteLestStatusKafkaMessage?>(
                mutableMapOf<TopicPartition, List<ConsumerRecord<String, DineSykmeldteLestStatusKafkaMessage?>>>(
                    TopicPartition("1", 1) to listOf(
                        ConsumerRecord(
                            "topic", 1, 1, "example-key",
                            createDineSykmeldteLestStatusKafkaMessage(),
                        )
                    )
                )
            )
            every { applicationState.ready } returns true andThen false
            every { kafkaConsumer.poll(any<Duration>()) } returns consumerRecords

            consumer.start()

            verify(exactly = 1) { database.insertOrUpdateReadStatus(any()) }
            verify(exactly = 0) { database.deleteReadStatus(any()) }
        }

        test("should delete when message is null") {
            val consumerRecords = ConsumerRecords<String, DineSykmeldteLestStatusKafkaMessage?>(
                mutableMapOf<TopicPartition, List<ConsumerRecord<String, DineSykmeldteLestStatusKafkaMessage?>>>(
                    TopicPartition("1", 1) to listOf(
                        ConsumerRecord(
                            "topic", 1, 1, "example-key",
                            null,
                        )
                    )
                )
            )
            every { applicationState.ready } returns true andThen false
            every { kafkaConsumer.poll(any<Duration>()) } returns consumerRecords

            consumer.start()

            verify(exactly = 1) { database.deleteReadStatus("example-key") }
            verify(exactly = 0) { database.insertOrUpdateReadStatus(any()) }
        }
    }
})

fun createDineSykmeldteLestStatusKafkaMessage(
    narmestelederId: String = "narmeste-leder",
) =
    DineSykmeldteLestStatusKafkaMessage(
        KafkaMetadata(
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            source = "leder",
        ),
        NLReadCount(
            narmestelederId = narmestelederId,
            unreadSykmeldinger = 1,
            unreadSoknader = 2,
            unreadDialogmoter = 3,
            unreadOppfolgingsplaner = 4,
            unreadMeldinger = 5,
        )
    )
