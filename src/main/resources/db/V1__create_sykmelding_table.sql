CREATE TABLE sykmelding (
    sykmelding_id VARCHAR primary key not null,
    pasient_fnr VARCHAR not null,
    orgnummer VARCHAR not null,
    juridisk_orgnummer VARCHAR null,
    timestamp TIMESTAMP with time zone not null,
    leder_id VARCHAR null,
    sykmelding JSONB not null
)
