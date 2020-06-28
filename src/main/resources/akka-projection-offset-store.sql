
create schema if not exists oti_twin;

create table if not exists akka_projection_offset_store (
  "PROJECTION_NAME"   varchar(255) not null,
  "PROJECTION_KEY"    varchar(255) not null,
  "OFFSET"            varchar(255) not null,
  "MANIFEST"          varchar(4) not null,
  "MERGEABLE"         boolean not null,
  "LAST_UPDATED"      timestamp(9) with time zone not null,
  constraint pk_projection_id primary key ("PROJECTION_NAME", "PROJECTION_KEY")
);

create index projection_name_index on akka_projection_offset_store ("PROJECTION_NAME");

