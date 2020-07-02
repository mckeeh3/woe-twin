
create table if not exists region_zoom_16 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_16_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_16_zoom on region_zoom_16 (zoom);
create index if not exists region_zoom_16_top_left_lat on region_zoom_16 (top_left_lat);
create index if not exists region_zoom_16_top_left_lng on region_zoom_16 (top_left_lng);
create index if not exists region_zoom_16_bot_right_lat on region_zoom_16 (bot_right_lat);
create index if not exists region_zoom_16_bot_right_lng on region_zoom_16 (bot_right_lng);

