
# Install Yugabyte for use with MiniKube

Follow the [Yugabyte Quick Start](https://docs.yugabyte.com/latest/quick-start/) guide for instructions on installing on your local system.

Yugabyte provides APIs for both Cassandra and PostgreSQL.

## Create Cassandra Tables - Yugabyte Cassandra

Cd into the directory where you cloned the `woe-twin` repo.

~~~bash
$ <path-to-yugabyte>/bin/ycqlsh

Connected to local cluster at localhost:9042.
[ycqlsh 5.0.1 | Cassandra 3.9-SNAPSHOT | CQL spec 3.4.2 | Native protocol v4]
Use HELP for help.
ycqlsh>
~~~

Run script to create the required Akka persistence tables.

~~~bash
ycqlsh> source 'src/main/resources/akka-persistence-journal-create-twin.cql'
~~~

Verify that the tables have been created.

~~~bash
ycqlsh> use woe_twin;
ycqlsh:woe_twin> describe tables;

tag_views  tag_scanning         tag_write_progress
messages   all_persistence_ids  metadata

ycqlsh:woe_twin> quit
~~~

### Create PostgreSQL Table - Yugabyte PostgreSQL

~~~bash
$ <path-to-yugabyte-install-dir>/bin/ysqlsh
ysqlsh (11.2-YB-2.3.1.0-b0)
Type "help" for help.

yugabyte=#
~~~

Execute the create table DDL script to create the projection table.

~~~bash
yugabyte=# \i src/main/resources/region-projection.sql
CREATE TABLE
yugabyte=#
~~~

Execute the create table DDL script tp create the Akka Projection offset table.

~~~bash
yugabyte=# \i src/main/resources/akka-projection-offset-store.sql
CREATE TABLE
CREATE INDEX
yugabyte=#
~~~

Verify that the tables have been created.

~~~bash
yugabyte=# \d
                    List of relations
 Schema |             Name             | Type  |  Owner
--------+------------------------------+-------+----------
 public | AKKA_PROJECTION_OFFSET_STORE | table | yugabyte
 public | region                       | table | yugabyte
(2 rows)

yugabyte=# quit
~~~
