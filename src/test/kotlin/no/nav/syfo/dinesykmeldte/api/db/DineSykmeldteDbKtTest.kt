package no.nav.syfo.dinesykmeldte.api.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dinesykmeldte.service.createDineSykmeldteLestStatusKafkaMessage
import no.nav.syfo.sykmelding.db.PsqlContainer
import org.amshove.kluent.shouldBeNull

class DineSykmeldteDbKtTest : FunSpec({

    val mockEnv = mockk<Environment>(relaxed = true)
    every { mockEnv.databaseUsername } returns "username"
    every { mockEnv.databasePassword } returns "password"

    val psqlContainer = PsqlContainer()
        .withExposedPorts(5432)
        .withUsername("username")
        .withPassword("password")
        .withDatabaseName("database")
        .withInitScript("db/testdb-init.sql")
        .apply {
            start();
            println("Database: jdbc:postgresql://localhost:${firstMappedPort}/database startet opp, credentials: username og password")
        }

    every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl

    val database: DatabaseInterface = Database(mockEnv)

    context("test dine sykmeldte database") {
        test("shall save read status") {
            val readStatusKafkaMessage = createDineSykmeldteLestStatusKafkaMessage(
                narmestelederId = "test-1"
            )

            database.insertOrUpdateReadStatus(readStatusKafkaMessage);

            val readStatus = database.getReadStatusForNarmesteleder("test-1")

            readStatus?.narmestelederId shouldBe "test-1"
            readStatus?.unreadSykmeldinger shouldBe 1
            readStatus?.unreadSoknader shouldBe 2
            readStatus?.unreadDialogmoter shouldBe 3
            readStatus?.unreadOppfolgingsplaner shouldBe 4
            readStatus?.unreadMeldinger shouldBe 5
        }

        test("shall delete") {
            val readStatusKafkaMessage = createDineSykmeldteLestStatusKafkaMessage(
                narmestelederId = "test-1"
            )
            database.insertOrUpdateReadStatus(readStatusKafkaMessage);
            val readStatus = database.getReadStatusForNarmesteleder("test-1")
            readStatus?.narmestelederId shouldBe "test-1"

            database.deleteReadStatus("test-1");

            val deletedReadStatus = database.getReadStatusForNarmesteleder("test-1")
            deletedReadStatus?.narmestelederId.shouldBeNull()
        }
    }
})
