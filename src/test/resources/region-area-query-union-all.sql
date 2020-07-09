
explain analyze select * from region
 where zoom = 12
   and top_left_lat = 47.0703125
   and top_left_lng = 2.5
   and bot_right_lat = 47.03125
   and bot_right_lng = 2.5390625
 union all
select * from region
 where zoom = 12
   and top_left_lat = 47.0703125
   and top_left_lng = 2.5390625
   and bot_right_lat = 47.03125
   and bot_right_lng = 2.578125
 union all
select * from region
 where zoom = 12
   and top_left_lat = 47.109375
   and top_left_lng = 2.5
   and bot_right_lat = 47.0703125
   and bot_right_lng = 2.5390625
 union all
select * from region
 where zoom = 12
   and top_left_lat = 47.109375
   and top_left_lng = 2.5390625
   and bot_right_lat = 47.0703125
   and bot_right_lng = 2.578125
   ;