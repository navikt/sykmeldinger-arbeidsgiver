CREATE TABLE sykmelding_arbeidsgiver (
    sykmelding_id VARCHAR primary key not null,
    pasient_fnr VARCHAR not null,
    orgnummer VARCHAR not null,
    juridisk_orgnummer VARCHAR null,
    timestamp TIMESTAMP with time zone not null,
    latest_tom Date not null,
    orgnavn VARCHAR null,
    sykmelding JSONB not null
);

create index sykmelding_arbeidsgiver_fnr_idx on sykmelding_arbeidsgiver(pasient_fnr);
create index sykmelding_arbeidsgiver_latest_tom_idx on sykmelding_arbeidsgiver(latest_tom);
