
select * from region
 where zoom = 12
   and top_left_lat <= 47.4
   and top_left_lng >= 2.5
   and bot_right_lat >= 47.0
   and bot_right_lng <= 2.6
   and device_count > 0;
