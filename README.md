
# Where On Earth Twin (woe-twin) Microservice

This microservice simulates geographically distributed IoT devices. The microservice handles incoming IoT device telemetry messages. These messages create, delete, and change the state of individual IoT devices. A map UI is used to visualize devices' location and submit commands to manipulate IoT devices.

- [Where On Earth Twin (woe-twin) Microservice](#where-on-earth-twin-woe-twin-microservice)
  - [Installation](#installation)
    - [Kubernetes Environments](#kubernetes-environments)
    - [Database Environments](#database-environments)
  - [Design notes](#design-notes)
  - [Map Regions](#map-regions)
    - [Map Region Selections](#map-region-selections)
  - [WoE Map UI](#woe-map-ui)

## Installation

How you install this Akka microservice depends on your target environment. There are environment specific README documents for each of the tested Kubernetes environments. With each deployment you also have to select which database you want to use. There are also README documents for the tested databases.

### Kubernetes Environments

* [Minikube](https://github.com/mckeeh3/woe-twin/blob/master/README-minikube.md)
* [Amazon EKS](https://github.com/mckeeh3/woe-twin/blob/master/README-amazon-eks.md)
* [Google GKE](https://github.com/mckeeh3/woe-twin/blob/master/README-google-gks.md)

### Database Environments

* [Cassandra local](https://github.com/mckeeh3/woe-twin/blob/master/README-database-cassandra-local.md)
* [Cassandra Amazon](https://github.com/mckeeh3/woe-twin/blob/master/README-database-cassandra-amazon.md)
* [Yugabyte local](https://github.com/mckeeh3/woe-twin/blob/master/README-database-cassandra-amazon.md)
* [Yugabyte Kubernetes](https://github.com/mckeeh3/woe-twin/blob/master/README-database-cassandra-amazon.md)
* [PostGreSQL local](https://github.com/mckeeh3/woe-twin/blob/master/README-database-postgresql-local.md)
* [PostgreSQL Amazon](https://github.com/mckeeh3/woe-twin/blob/master/README-database-postgresql-amazon.md)

## Design notes

TODO

## Map Regions

Map regions are a rectangular area on a global map. Regions are bounded
by a top left corner, and a bottom right corner. Corners are defined by latitude
and longitude values.

Map regions are viewed as rectangular tiles when viewed on global maps.
Each map region may be selected to contain IoT devices.

Maps are viewed at various zoom levels, from zoom level 0 to 18. Level 0
represents the entire global map, which is 180 degrees from top to bottom
and 360 degrees from left to right. Top to bottom degrees are the latitudes
and left to right degrees are longitudes.

### Map Region Selections

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

## WoE Map UI

The WoE Map UI is based on Open Street Map. This is a zoom-able map similar to other web
maps, such as Google Maps.
