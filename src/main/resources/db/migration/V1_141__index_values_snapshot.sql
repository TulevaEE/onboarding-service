create table index_values_snapshot
(
    id                bigint generated always as identity not null,
    snapshot_time     timestamptz                         not null,
    key               text                                not null,
    date              date                                not null,
    value             numeric(16, 5)                      not null,
    provider          text                                not null,
    source_updated_at timestamptz                         not null,
    created_at        timestamptz                         not null,
    constraint index_values_snapshot_pkey primary key (id)
);

create index index_values_snapshot_snapshot_time_idx on index_values_snapshot (snapshot_time);
