package no.nav.syfo.sykmelding.kafka

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.sykmelding.db.deleteSykmelding
import no.nav.syfo.sykmelding.db.insertOrUpdateSykmelding
import no.nav.syfo.sykmelding.kafka.model.SendtSykmeldingKafkaMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

class SykmeldingConsumerTest : Spek({
    val database = mockk<DatabaseInterface>(relaxed = true)
    val kafkaConsumer = mockk<KafkaConsumer<String, SendtSykmeldingKafkaMessage?>>()
    val topic = "sendt-sykmelding-topic"
    val applicationState = mockk<ApplicationState>()
    val sykmeldingConsumer = SykmeldingConsumer(kafkaConsumer, database, applicationState, topic)
    mockkStatic("no.nav.syfo.sykmelding.db.SykmeldingDbKt")
    afterEachTest {
        clearAllMocks()
    }
    beforeEachTest {
        every { kafkaConsumer.subscribe(any<List<String>>()) } returns Unit
        every { database.insertOrUpdateSykmelding(any()) } returns Unit
    }

    describe("Test handling sykmelding") {
        it("Should insert/update sykmelidng") {
            val consumerRecords = ConsumerRecords<String, SendtSykmeldingKafkaMessage?>(
                mutableMapOf<TopicPartition, List<ConsumerRecord<String, SendtSykmeldingKafkaMessage?>>>(
                    TopicPartition("1", 1) to listOf(ConsumerRecord("topic", 1, 1, "String", mockk()))
                )
            )
            every { applicationState.ready } returns true andThen false
            every { kafkaConsumer.poll(any<Duration>()) } returns consumerRecords
            runBlocking {
                sykmeldingConsumer.start()
            }

            verify(exactly = 1) { database.insertOrUpdateSykmelding(any()) }
        }

        it("Should insert then update") {
            val consumerRecords = ConsumerRecords<String, SendtSykmeldingKafkaMessage?>(
                mutableMapOf<TopicPartition, List<ConsumerRecord<String, SendtSykmeldingKafkaMessage?>>>(
                    TopicPartition("1", 1) to listOf(
                        ConsumerRecord("topic", 1, 1, "String", mockk()),
                        ConsumerRecord("topic", 1, 1, "String", mockk())
                    )
                )
            )
            every { applicationState.ready } returns true andThen false
            every { kafkaConsumer.poll(any<Duration>()) } returns consumerRecords
            runBlocking {
                sykmeldingConsumer.start()
            }

            verify(exactly = 2) { database.insertOrUpdateSykmelding(any()) }
        }

        it("Should insert then delete") {
            val consumerRecords = ConsumerRecords<String, SendtSykmeldingKafkaMessage?>(
                mutableMapOf<TopicPartition, List<ConsumerRecord<String, SendtSykmeldingKafkaMessage?>>>(
                    TopicPartition("1", 1) to listOf(
                        ConsumerRecord("topic", 1, 1, "String", mockk()),
                        ConsumerRecord("topic", 1, 1, "String", null)
                    )
                )
            )
            every { applicationState.ready } returns true andThen false
            every { kafkaConsumer.poll(any<Duration>()) } returns consumerRecords
            runBlocking {
                sykmeldingConsumer.start()
            }

            verify(exactly = 1) { database.insertOrUpdateSykmelding(any()) }
            verify(exactly = 1) { database.deleteSykmelding(any()) }
        }
    }
})
