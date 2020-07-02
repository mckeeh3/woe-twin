
create table if not exists region_zoom_3 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_3_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_3_zoom on region_zoom_3 (zoom);
create index if not exists region_zoom_3_top_left_lat on region_zoom_3 (top_left_lat);
create index if not exists region_zoom_3_top_left_lng on region_zoom_3 (top_left_lng);
create index if not exists region_zoom_3_bot_right_lat on region_zoom_3 (bot_right_lat);
create index if not exists region_zoom_3_bot_right_lng on region_zoom_3 (bot_right_lng);


create table if not exists region_zoom_4 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_4_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_4_zoom on region_zoom_4 (zoom);
create index if not exists region_zoom_4_top_left_lat on region_zoom_4 (top_left_lat);
create index if not exists region_zoom_4_top_left_lng on region_zoom_4 (top_left_lng);
create index if not exists region_zoom_4_bot_right_lat on region_zoom_4 (bot_right_lat);
create index if not exists region_zoom_4_bot_right_lng on region_zoom_4 (bot_right_lng);


create table if not exists region_zoom_5 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_5_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_5_zoom on region_zoom_5 (zoom);
create index if not exists region_zoom_5_top_left_lat on region_zoom_5 (top_left_lat);
create index if not exists region_zoom_5_top_left_lng on region_zoom_5 (top_left_lng);
create index if not exists region_zoom_5_bot_right_lat on region_zoom_5 (bot_right_lat);
create index if not exists region_zoom_5_bot_right_lng on region_zoom_5 (bot_right_lng);


create table if not exists region_zoom_6 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_6_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_6_zoom on region_zoom_6 (zoom);
create index if not exists region_zoom_6_top_left_lat on region_zoom_6 (top_left_lat);
create index if not exists region_zoom_6_top_left_lng on region_zoom_6 (top_left_lng);
create index if not exists region_zoom_6_bot_right_lat on region_zoom_6 (bot_right_lat);
create index if not exists region_zoom_6_bot_right_lng on region_zoom_6 (bot_right_lng);


create table if not exists region_zoom_7 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_7_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_7_zoom on region_zoom_7 (zoom);
create index if not exists region_zoom_7_top_left_lat on region_zoom_7 (top_left_lat);
create index if not exists region_zoom_7_top_left_lng on region_zoom_7 (top_left_lng);
create index if not exists region_zoom_7_bot_right_lat on region_zoom_7 (bot_right_lat);
create index if not exists region_zoom_7_bot_right_lng on region_zoom_7 (bot_right_lng);


create table if not exists region_zoom_8 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_8_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_8_zoom on region_zoom_8 (zoom);
create index if not exists region_zoom_8_top_left_lat on region_zoom_8 (top_left_lat);
create index if not exists region_zoom_8_top_left_lng on region_zoom_8 (top_left_lng);
create index if not exists region_zoom_8_bot_right_lat on region_zoom_8 (bot_right_lat);
create index if not exists region_zoom_8_bot_right_lng on region_zoom_8 (bot_right_lng);


create table if not exists region_zoom_9 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_9_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_9_zoom on region_zoom_9 (zoom);
create index if not exists region_zoom_9_top_left_lat on region_zoom_9 (top_left_lat);
create index if not exists region_zoom_9_top_left_lng on region_zoom_9 (top_left_lng);
create index if not exists region_zoom_9_bot_right_lat on region_zoom_9 (bot_right_lat);
create index if not exists region_zoom_9_bot_right_lng on region_zoom_9 (bot_right_lng);


create table if not exists region_zoom_10 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_10_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_10_zoom on region_zoom_10 (zoom);
create index if not exists region_zoom_10_top_left_lat on region_zoom_10 (top_left_lat);
create index if not exists region_zoom_10_top_left_lng on region_zoom_10 (top_left_lng);
create index if not exists region_zoom_10_bot_right_lat on region_zoom_10 (bot_right_lat);
create index if not exists region_zoom_10_bot_right_lng on region_zoom_10 (bot_right_lng);


create table if not exists region_zoom_11 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_11_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_11_zoom on region_zoom_11 (zoom);
create index if not exists region_zoom_11_top_left_lat on region_zoom_11 (top_left_lat);
create index if not exists region_zoom_11_top_left_lng on region_zoom_11 (top_left_lng);
create index if not exists region_zoom_11_bot_right_lat on region_zoom_11 (bot_right_lat);
create index if not exists region_zoom_11_bot_right_lng on region_zoom_11 (bot_right_lng);


create table if not exists region_zoom_12 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_12_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_12_zoom on region_zoom_12 (zoom);
create index if not exists region_zoom_12_top_left_lat on region_zoom_12 (top_left_lat);
create index if not exists region_zoom_12_top_left_lng on region_zoom_12 (top_left_lng);
create index if not exists region_zoom_12_bot_right_lat on region_zoom_12 (bot_right_lat);
create index if not exists region_zoom_12_bot_right_lng on region_zoom_12 (bot_right_lng);


create table if not exists region_zoom_13 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_13_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_13_zoom on region_zoom_13 (zoom);
create index if not exists region_zoom_13_top_left_lat on region_zoom_13 (top_left_lat);
create index if not exists region_zoom_13_top_left_lng on region_zoom_13 (top_left_lng);
create index if not exists region_zoom_13_bot_right_lat on region_zoom_13 (bot_right_lat);
create index if not exists region_zoom_13_bot_right_lng on region_zoom_13 (bot_right_lng);


create table if not exists region_zoom_14 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_14_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_14_zoom on region_zoom_14 (zoom);
create index if not exists region_zoom_14_top_left_lat on region_zoom_14 (top_left_lat);
create index if not exists region_zoom_14_top_left_lng on region_zoom_14 (top_left_lng);
create index if not exists region_zoom_14_bot_right_lat on region_zoom_14 (bot_right_lat);
create index if not exists region_zoom_14_bot_right_lng on region_zoom_14 (bot_right_lng);


create table if not exists region_zoom_15 (
    zoom            integer,
    top_left_lat    double precision,
    top_left_lng    double precision,
    bot_right_lat   double precision,
    bot_right_lng   double precision,
    device_count    integer,
    happy_count     integer,
    sad_count       integer,
    constraint region_zoom_15_pkey primary key (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)
);

create index if not exists region_zoom_15_zoom on region_zoom_15 (zoom);
create index if not exists region_zoom_15_top_left_lat on region_zoom_15 (top_left_lat);
create index if not exists region_zoom_15_top_left_lng on region_zoom_15 (top_left_lng);
create index if not exists region_zoom_15_bot_right_lat on region_zoom_15 (bot_right_lat);
create index if not exists region_zoom_15_bot_right_lng on region_zoom_15 (bot_right_lng);


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


create table if not exists region_zoom_17 (
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

create index if not exists region_zoom_17_zoom on region_zoom_17 (zoom);
create index if not exists region_zoom_17_top_left_lat on region_zoom_17 (top_left_lat);
create index if not exists region_zoom_17_top_left_lng on region_zoom_17 (top_left_lng);
create index if not exists region_zoom_17_bot_right_lat on region_zoom_17 (bot_right_lat);
create index if not exists region_zoom_17_bot_right_lng on region_zoom_17 (bot_right_lng);


create table if not exists region_zoom_18 (
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

create index if not exists region_zoom_18_zoom on region_zoom_18 (zoom);
create index if not exists region_zoom_18_top_left_lat on region_zoom_18 (top_left_lat);
create index if not exists region_zoom_18_top_left_lng on region_zoom_18 (top_left_lng);
create index if not exists region_zoom_18_bot_right_lat on region_zoom_18 (bot_right_lat);
create index if not exists region_zoom_18_bot_right_lng on region_zoom_18 (bot_right_lng);
