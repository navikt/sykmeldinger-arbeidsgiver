package no.nav.syfo.narmesteleder.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.client.model.Ansatt
import no.nav.syfo.narmesteleder.client.model.AnsatteResponse

class NarmestelederClient(val httpClient: HttpClient, val url: String) {
    suspend fun getAnsatte(bearerToken: String): List<Ansatt> {
        return try {
            val response = httpClient.get<AnsatteResponse>(url) {
                header(HttpHeaders.Authorization, bearerToken)
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
}
