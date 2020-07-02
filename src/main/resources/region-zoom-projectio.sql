
create schema if not exists oti_twin;

create table if not exists oti_twin.region_zoom_15 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_15 primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index region_zoom on oti_twin.region_zoom_15 (zoom);
create index region_top_left_lat on oti_twin.region_zoom_15 (top_left_lat);
create index region_top_left_lng on oti_twin.region_zoom_15 (top_left_lng);
create index region_bot_right_lat on oti_twin.region_zoom_15 (bot_right_lat);
create index region_bot_right_lng on oti_twin.region_zoom_15 (bot_right_lng);

create table if not exists oti_twin.region_zoom_16 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_16 primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index region_zoom on oti_twin.region_zoom_16 (zoom);
create index region_top_left_lat on oti_twin.region_zoom_16 (top_left_lat);
create index region_top_left_lng on oti_twin.region_zoom_16 (top_left_lng);
create index region_bot_right_lat on oti_twin.region_zoom_16 (bot_right_lat);
create index region_bot_right_lng on oti_twin.region_zoom_16 (bot_right_lng);

create table if not exists oti_twin.region_zoom_17 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_17_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index region_zoom on oti_twin.region_zoom_17 (zoom);
create index region_top_left_lat on oti_twin.region_zoom_17 (top_left_lat);
create index region_top_left_lng on oti_twin.region_zoom_17 (top_left_lng);
create index region_bot_right_lat on oti_twin.region_zoom_17 (bot_right_lat);
create index region_bot_right_lng on oti_twin.region_zoom_17 (bot_right_lng);

create table if not exists oti_twin.region_zoom_18 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_18_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index region_zoom on oti_twin.region_zoom_18 (zoom);
create index region_top_left_lat on oti_twin.region_zoom_18 (top_left_lat);
create index region_top_left_lng on oti_twin.region_zoom_18 (top_left_lng);
create index region_bot_right_lat on oti_twin.region_zoom_18 (bot_right_lat);
create index region_bot_right_lng on oti_twin.region_zoom_18 (bot_right_lng);

