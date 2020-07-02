
create schema oti_twin;

create table if not exists region (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom on region (zoom);
create index if not exists region_top_left_lat on region (top_left_lat);
create index if not exists region_top_left_lng on region (top_left_lng);
create index if not exists region_bot_right_lat on region (bot_right_lat);
create index if not exists region_bot_right_lng on region (bot_right_lng);
