package no.nav.syfo.dinesykmeldte.api

import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService

fun Route.registerDineSykmeldteApi(dineSykmeldteService: DineSykmeldteService) {
    get("dinesykmeldte") {
        val token = call.request.headers[HttpHeaders.Authorization]!!
        call.respond(dineSykmeldteService.getDineSykmeldte(token))
    }
}
