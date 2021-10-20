package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sykmeldinger-arbeidsgiver"),
    val databaseUsername: String = getEnvVar("NAIS_DATABASE_USERNAME"),
    val databasePassword: String = getEnvVar("NAIS_DATABASE_PASSWORD"),
    val dbHost: String = getEnvVar("NAIS_DATABASE_HOST"),
    val dbPort: String = getEnvVar("NAIS_DATABASE_PORT"),
    val dbName: String = getEnvVar("NAIS_DATABASE_DATABASE"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    val syfoSendtSykmeldingTopic: String = "syfo-sendt-sykmelding",
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val loginserviceIdportenAudience: List<String> = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE").split(","),
    val allowedOrigin: List<String> = getEnvVar("ALLOWED_ORIGIN").split(","),
    val narmestelederUrl: String = getEnvVar("NARMESTELEDER_URL"),
    val narmestelederLeesahTopic: String = "teamsykmelding.syfo-narmesteleder-leesah"
) : KafkaConfig {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }

    fun kafkaCredentials(): KafkaCredentials {
        return object : KafkaCredentials {
            override val kafkaPassword: String
                get() = getEnvVar("SERVICEUSER_PASSWORD")
            override val kafkaUsername: String
                get() = getEnvVar("SERVICEUSER_USERNAME")
        }
    }
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
