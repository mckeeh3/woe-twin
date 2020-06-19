
### Design notes

#### Map Regions
Map regions are a rectangular area on a global map. Regions are bounded
by a top left corner, and a bottom right corner. Corners are defined by latitude
and longitude values.

Map regions are viewed as rectangular tiles when viewed on global maps.
Each map region may be selected to contain IoT devices.

Maps are viewed at various zoom levels, from zoom level 0 to 18. Level 0
represents the entire global map, which is 180 degrees from top to bottom
and 360 degrees from left to right. Top to bottom degrees are the latitudes
and left to right degrees are longitudes.

#### Map Region Selections
Map selections are rectangular areas that are bounded by a top left
corner, and a bottom left corner. Each new map selection is submitted to
a map at the top zoom level.

Each new map selection traverses each map zoom
level to determine what map regions it overlaps. As a map selection
moves through each zoom level the map regions check it to determine if
the selection overlaps the region. When a given map selection does not
overlap a map region the zoom traversal stops. When there is an overlap
the map region determines if the map selection fully or partially overlaps
the map region.

Each fully or partially overlapping  map selection is recorded by the map
region. Recorded map selections are trimmed to fall completely within the
map region.

Once overlaping map selections have been recorded the map will then pass
the map selection on to map sub regions. Typically, each map region
contains 4 sub regions. This recursion starts at zoom level 0 and continues
to zoom level 18.

### Deploy

#### Yugabyte on Kubernetes or MiniKube

Follow the documentation for installing MiniKube and Yugabyte.

#### Create Cassandra and PostgreSQL Tables

Try the following commands to verify access to Cassandra CQL and PostgreSQL
CQL CLI tools once Yugabyte has been installed in a Kubernetes environment.

Cassandra CQL
~~~bash
$ kubectl --namespace yb-demo exec -it yb-tserver-0 -- /home/yugabyte/bin/ycqlsh yb-tserver-0

Defaulting container name to yb-tserver.
Use 'kubectl describe pod/yb-tserver-0 -n yb-demo' to see all of the containers in this pod.
Connected to local cluster at yb-tserver-0:9042.
[ycqlsh 5.0.1 | Cassandra 3.9-SNAPSHOT | CQL spec 3.4.2 | Native protocol v4]
Use HELP for help.
ycqlsh> quit
~~~

PostgreSQL
~~~bash
$ kubectl --namespace yb-demo exec -it yb-tserver-0 -- /home/yugabyte/bin/ysqlsh -h yb-tserver-0  --echo-queries

Defaulting container name to yb-tserver.
Use 'kubectl describe pod/yb-tserver-0 -n yb-demo' to see all of the containers in this pod.
ysqlsh (11.2-YB-2.1.8.1-b0)
Type "help" for help.

yugabyte=# \q
~~~

##### Copy CQL and SQL DDL commands to the Yugabyte server

From the oti-twin project directory.

~~~bash
$ kc cp src/main/resources/akka-persistence-journal.cql yb-demo/yb-tserver-0:/tmp                                                                  
Defaulting container name to yb-tserver.

$ kc cp src/main/resources/akka-projection-offset-store.cql yb-demo/yb-tserver-0:/tmp
Defaulting container name to yb-tserver.

$ kc cp src/main/resources/region-projection.sql yb-demo/yb-tserver-0:/tmp
Defaulting container name to yb-tserver.
~~~

##### Create the Cassandra and PostgreSQL Tables
Cassandra
~~~bash
$ kubectl --namespace yb-demo exec -it yb-tserver-0 -- /home/yugabyte/bin/ycqlsh yb-tserver-0                                                      
~~~
~~~
Defaulting container name to yb-tserver.
Use 'kubectl describe pod/yb-tserver-0 -n yb-demo' to see all of the containers in this pod.
Connected to local cluster at yb-tserver-0:9042.
[ycqlsh 5.0.1 | Cassandra 3.9-SNAPSHOT | CQL spec 3.4.2 | Native protocol v4]
Use HELP for help.
ycqlsh> source '/tmp/akka-persistence-journal.cql'
ycqlsh> source '/tmp/akka-projection-offset-store.cql'
ycqlsh> describe keyspaces;

system_schema  oti_twin  system_auth  system

ycqlsh> use oti_twin;
ycqlsh:oti_twin> describe tables;

tag_views  tag_scanning         offset_store        metadata
messages   all_persistence_ids  tag_write_progress

ycqlsh:oti_twin> quit
~~~

PostgreSQL
~~~bash
$ kubectl --namespace yb-demo exec -it yb-tserver-0 -- /home/yugabyte/bin/ysqlsh -h yb-tserver-0  --echo-queries
~~~
~~~
Defaulting container name to yb-tserver.
Use 'kubectl describe pod/yb-tserver-0 -n yb-demo' to see all of the containers in this pod.
ysqlsh (11.2-YB-2.1.8.1-b0)
Type "help" for help.

yugabyte=# \i /tmp/region-projection.sql
create schema if not exists iot_twin;
CREATE SCHEMA
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
CREATE TABLE
create index region_zoom on region (zoom);
CREATE INDEX
create index region_top_left_lat on region (top_left_lat);
CREATE INDEX
create index region_top_left_lng on region (top_left_lng);
CREATE INDEX
create index region_bot_right_lat on region (bot_right_lat);
CREATE INDEX
create index region_bot_right_lng on region (bot_right_lng);
CREATE INDEX
yugabyte=# \q
~~~

### Enable External Access

Create a load balancer to enable access to the OTI Twin microservice HTTP endpoint.

~~~bash
$ kubectl expose deployment oti-twin --type=LoadBalancer --name=oti-twin-service
~~~

Next, view to external port assignments.

~~~bash
$ kubectl get services oti-twin-service
~~~
~~~
NAME               TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)                                        AGE
oti-twin-service   LoadBalancer   10.106.25.172   <pending>     2552:31029/TCP,8558:32559/TCP,8080:32171/TCP   5h4m
~~~

Note that in this example, the Kubernetes internal port 8080 external port assignment of 32171.

For MiniKube deployments, the full URL to access the HTTP endpoint is constructed using the MiniKube IP and the external port.

~~~bash
$ minikube ip       
~~~
~~~
192.168.99.102
~~~
~~~bash
$ curl -v http://$(minikube ip):32171
~~~
~~~
*   Trying 192.168.99.102:32171...
* Connected to 192.168.99.102 (192.168.99.102) port 32171 (#0)
> GET / HTTP/1.1
> Host: 192.168.99.102:32171
> User-Agent: curl/7.70.0
> Accept: */*
>
* Mark bundle as not supporting multiuse
< HTTP/1.1 200 OK
< Last-Modified: Thu, 18 Jun 2020 14:31:18 GMT
< ETag: "52800172c7d756f0"
< Accept-Ranges: bytes
< Server: akka-http/10.1.12
< Date: Fri, 19 Jun 2020 00:26:36 GMT
< Content-Type: text/html; charset=UTF-8
< Content-Length: 330
<
<!DOCTYPE html>
<html lang="en">

<head>
  <title>Of Things Internet</title>
  <script src="p5.js" type="text/javascript"></script>
  <script src="mappa.js" type="text/javascript"></script>
  <script src="oti.js" type="text/javascript"></script>
  <style> body { padding: 0; margin: 0; }</style>
</head>

<body>
</body>

</html>

* Connection #0 to host 192.168.99.102 left intact
~~~
