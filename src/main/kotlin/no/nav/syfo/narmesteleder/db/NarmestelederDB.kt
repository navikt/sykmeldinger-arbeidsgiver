package no.nav.syfo.narmesteleder.db

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.narmesteleder.kafka.model.Narmesteleder

class NarmestelederDB(
    private val database: DatabaseInterface,
) {
    fun insertOrUpdate(narmesteleder: Narmesteleder) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
               insert into narmesteleder(narmeste_leder_id, pasient_fnr, leder_fnr, orgnummer) 
               values (?, ?, ?, ?) on conflict (narmeste_leder_id) do nothing ;
            """,
                )
                .use { preparedStatement ->
                    preparedStatement.setString(1, narmesteleder.narmesteLederId.toString())
                    preparedStatement.setString(2, narmesteleder.fnr)
                    preparedStatement.setString(3, narmesteleder.narmesteLederFnr)
                    preparedStatement.setString(4, narmesteleder.orgnummer)
                    preparedStatement.executeUpdate()
                }
            connection.commit()
        }
    }

    fun deleteNarmesteleder(narmesteleder: Narmesteleder) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
               delete from narmesteleder where narmeste_leder_id = ?;
            """,
                )
                .use { ps ->
                    ps.setString(1, narmesteleder.narmesteLederId.toString())
                    ps.executeUpdate()
                }
            connection.commit()
        }
    }
}
