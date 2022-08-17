package no.nav.syfo.dinesykmeldte.kafka.model

import java.time.OffsetDateTime

class KafkaMetadata(val timestamp: OffsetDateTime, val source: String)

data class DineSykmeldteLestStatusKafkaMessage(
    val kafkaMetadata: KafkaMetadata,
    val nlReadCount: NLReadCount,
)

data class NLReadCount(
    val narmestelederId: String,
    val unreadSykmeldinger: Int,
    val unreadSoknader: Int,
    val unreadDialogmoter: Int,
    val unreadOppfolgingsplaner: Int,
    val unreadMeldinger: Int,
)
