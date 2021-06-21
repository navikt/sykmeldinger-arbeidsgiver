package no.nav.syfo.dinesykmeldte.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.application.getToken
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService

fun Route.registerDineSykmeldteApi(dineSykmeldteService: DineSykmeldteService) {
    get("dinesykmeldte") {
        val token = "Bearer ${call.getToken()}"
        call.respond(dineSykmeldteService.getDineSykmeldte(token))
    }

    get("dinesykmeldte/{narmestelederId}") {
        val token = "Bearer ${call.getToken()}"
        val narmestelederId = call.parameters["narmestelederId"]!!
        when (val sykmeldt = dineSykmeldteService.getSykmeldt(narmestelederId, token)) {
            null -> call.respond(HttpStatusCode.NotFound)
            else -> {
                call.respond(sykmeldt)
            }
        }
    }
}
