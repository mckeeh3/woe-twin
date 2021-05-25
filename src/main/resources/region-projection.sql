
create table if not exists woe_twin_region (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint woe_twin_region_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);
