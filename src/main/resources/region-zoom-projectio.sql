
create schema if not exists oti_twin_zoom_15;

create table if not exists oti_twin_zoom_15.region (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index region_zoom on oti_twin_zoom_15.region (zoom);
create index region_top_left_lat on oti_twin_zoom_15.region (top_left_lat);
create index region_top_left_lng on oti_twin_zoom_15.region (top_left_lng);
create index region_bot_right_lat on oti_twin_zoom_15.region (bot_right_lat);
create index region_bot_right_lng on oti_twin_zoom_15.region (bot_right_lng);



create schema if not exists oti_twin_zoom_16;

create table if not exists oti_twin_zoom_16.region (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index region_zoom on oti_twin_zoom_16.region (zoom);
create index region_top_left_lat on oti_twin_zoom_16.region (top_left_lat);
create index region_top_left_lng on oti_twin_zoom_16.region (top_left_lng);
create index region_bot_right_lat on oti_twin_zoom_16.region (bot_right_lat);
create index region_bot_right_lng on oti_twin_zoom_16.region (bot_right_lng);



create schema if not exists oti_twin_zoom_17;

create table if not exists oti_twin_zoom_17.region (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index region_zoom on oti_twin_zoom_17.region (zoom);
create index region_top_left_lat on oti_twin_zoom_17.region (top_left_lat);
create index region_top_left_lng on oti_twin_zoom_17.region (top_left_lng);
create index region_bot_right_lat on oti_twin_zoom_17.region (bot_right_lat);
create index region_bot_right_lng on oti_twin_zoom_17.region (bot_right_lng);



create schema if not exists oti_twin_zoom_18;

create table if not exists oti_twin_zoom_18.region (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index region_zoom on oti_twin_zoom_18.region (zoom);
create index region_top_left_lat on oti_twin_zoom_18.region (top_left_lat);
create index region_top_left_lng on oti_twin_zoom_18.region (top_left_lng);
create index region_bot_right_lat on oti_twin_zoom_18.region (bot_right_lat);
create index region_bot_right_lng on oti_twin_zoom_18.region (bot_right_lng);

