package no.nav.syfo.narmesteleder.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.client.model.Ansatt
import no.nav.syfo.narmesteleder.client.model.AnsatteResponse

class NarmestelederClient(val httpClient: HttpClient, val url: String) {
    suspend fun getAnsatte(bearerToken: String): List<Ansatt> {
        return try {
            val response = httpClient.get<AnsatteResponse>("$url/arbeidsgiver/ansatte") {
                header(HttpHeaders.Authorization, bearerToken)
                parameter("status", "ACTIVE")
            }
            response.ansatte
        } catch (ex: ClientRequestException) {
            log.error("Client faild request", ex)
            emptyList()
        } catch (ex: ServerResponseException) {
            log.error("Server responded with error", ex)
            emptyList()
        }
    }

    suspend fun getAnsatt(narmestelederId: String, bearerToken: String): Ansatt? {
        return try {
            return httpClient.get("$url/arbeidsgiver/ansatte/$narmestelederId") {
                header(HttpHeaders.Authorization, bearerToken)
            }
        } catch (ex: ClientRequestException) {
            log.error("Client faild request", ex)
            null
        } catch (ex: ServerResponseException) {
            log.error("Server responded with error", ex)
            null
        }
    }
}
