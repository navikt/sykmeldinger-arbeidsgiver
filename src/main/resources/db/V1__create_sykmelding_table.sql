CREATE TABLE sykmelding (
    sykmelding_id VARCHAR primary key not null,
    pasient_fnr VARCHAR not null,
    orgnummer VARCHAR not null,
    timestamp TIMESTAMP with time zone not null,
    sykmelding JSONB not null
)
