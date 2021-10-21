package no.nav.syfo.dinesykmeldte.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import no.nav.syfo.dinesykmeldte.service.DineSykmeldteService

fun Route.registerDineSykmeldteApi(dineSykmeldteService: DineSykmeldteService) {
    get("api/dinesykmeldte") {
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        call.respond(dineSykmeldteService.getDineSykmeldte(fnr))
    }

    get("api/dinesykmeldte/{narmestelederId}") {
        val narmestelederId = call.parameters["narmestelederId"]!!
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        when (val sykmeldt = dineSykmeldteService.getSykmeldt(narmestelederId, fnr)) {
            null -> call.respond(HttpStatusCode.NotFound)
            else -> {
                call.respond(sykmeldt)
            }
        }
    }
}
