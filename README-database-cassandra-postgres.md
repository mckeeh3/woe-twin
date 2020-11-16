
# Installation and Setup of Cassandra and PostgreSQL

Follow these instructions for setting up the `woe-twin` microservice databases.

## Akka Persistence Cassandra

There are a number of available database services to choose from. Akka Persistence provides multiple
[Persistence Plugins](https://doc.akka.io/docs/akka/current/persistence-plugins.html).

This section provides instructions for setting up Cassandra. Please see the the
[Akka Persistence Cassandra](https://doc.akka.io/docs/akka-persistence-cassandra/current/)
documentation for additional details.

See either
[README-database-cassandra-postgres.md](https://github.com/mckeeh3/woe-sim/blob/master/README-database-cassandra-postgres.md)
or
[README-database-yugabyte.md](https://github.com/mckeeh3/woe-sim/blob/master/README-database-yugabyte.md)
for instructions for setting up Cassandra.

### Amazon Keyspaces

[Amazon Keyspaces](https://aws.amazon.com/keyspaces/) is a serverless AWS service for Cassandra. The service provides a web interface for creating keypsaces and tables.

Use the online CQL Editor to create each of the siz required Cassandra tables. To access the CQL Editor, from the
[Amazon Keyspaces](https://aws.amazon.com/keyspaces/)
page, click **Get started with Amazon Keyspaces**.

On the left panel, click **Keypsaces**. Then on the right click **Create keyspace**. Create a keyspace  with the name `woe-sim`.

Next, on the left panel, click **CQL editor**. From the `/src/main/resources/akka-persistence-journal-create.cql` file copy the lines for creating each table and past them into the CQL editor then click **Run command**. Repeat these steps for each of the siz tables.

### Local Cassandra for Minikube

With Minikube you can use a local Cassandra installation. First,
[download Cassandra](https://cassandra.apache.org/download/). Follow the
[Installation Instructions](https://cassandra.apache.org/doc/latest/getting_started/installing.html).

**Important:** change the `listen_address` setting in the `<install-dir>/conf/conf/cassandra.yaml` configuration file to an IP address that is accessible from within Minikube. This is usually the IP of your local system. Processes running within Minikube cannot access the default localhost that Cassandra is configured to use.

**Important:** change the seed configuration setting. Open the configuration file as described above. Find the `seed_provider` property, then look for `seeds`. Change from localhost to the IP used above.

**Important:** comment out the `rpc_address` line. Open the configuration file as described above. Find the `rpc_address` property, and comment out this line.

~~~bash
#rpc_address: localhost
~~~
### Other Cassandra Providers

We will update this document as we test with other Cassandra installations.

If you have tried other Cassandra providers please submit a pull request.

## Akka Projection

The `woe-twin` service uses a SQL table to store summarized event data. Please see the
[Akka Projection](https://doc.akka.io/docs/akka-projection/current/)
documentation for more details on what it is and how it works.

### PostgreSQL

As with Cassandra, for PostgreSQL there are also a number of available database service to choose from. A good place to start is the
[PostgreSQL download page](https://www.postgresql.org/download/).

### Install SQL Workbench

[SQL Workbench](https://www.sql-workbench.eu/) is a SQL query tool. You can use SQL Workbench to connect to local or remote PostgreSQL databases. We will be using this tool to create the SQL tables required for the `woe-twin` tables.

### Amazon RDS

[Amazon Aurora](https://aws.amazon.com/rds/aurora)
provides MySQL and PostgreSQL-compatible databases. Click **Get started with Amazon Aurora**. Next, click **Create database** and follow the steps to create an Amazon Aurora PostgreSQL database. Name the database `woe-twin`.

Once the database has been created, create the two required tables. Run the SQL Workbench tool, configure it to access the database using the endpoint and port provided on the Aurora RDS dashboard. The endpoint is shown when you click on the `writer` role in the databases view.

### Local PostgreSQL for Minikube

Install PostgreSQL locally. There are many ways to install PostgreSQL depending on your local environment. The most common approach is to use your OS specific software installer.

Once PostgreSQL has been installed perform the following steps.

Initialize a database. The `initdb` utility created a new directory and initializes it with the required files, including the configuration file.

~~~bash
initdb -D <your-db-directory>
~~~

Next, edit the configuration file and make the follow adjustments.

Open `<your-db-directory>/postgresql.conf` in an editor.

Search for `listen_addresses` and add this setting:

~~~bash
listen_addresses = '*'
~~~

Search for `max_connections` and add this setting:

~~~bash
max_connections = 1000
~~~

Search for `unix_socket_directories` and add this setting:

~~~bash
unix_socket_directories = '<your-db-directory>'
~~~

Open `<your-db-directory>/pg_hba.conf` in an editor.

Append the following lines at the end of this file. Change the IP to your IP.

~~~bash
host    all             all             <your-IP>/32        trust
host    replication     all             <your-IP>/32        trust
~~~

### Run PostgreSQL

Start the PostgreSQL process.

~~~bash
postgres -D <your-da-directory>
~~~

### Create the Database

~~~bash
createdb --host=<your-IP> woe_twin
~~~

### Create the SQL Projection Tables

You can create the tables using the CLI tool `psql` or use SQL Workbench.

#### Create Tables Using psql

From the `woe_twin` project directory start `psql`.

~~~bash
psql --host=<your-IP> woe_twin
~~~

Next, create the required tables.

~~~bash
woe_twin=# \i src/main/resources/akka-projection-offset-store.sql
CREATE TABLE
CREATE INDEX
woe_twin=# \i src/main/resources/region-projection.sql
CREATE TABLE
woe_twin=# \q
~~~

#### Create Tables Using SQL Workbench

Start the SQL workbench tool, configure the connection, and login.

For Amazon Aurora, the `jdbc` URL endpoint host and port is provided on the dashboard. From the AWS Console, search for `RDS`, click on `RDS` in the search results, then click **Databases** on the left panel. Click the DB Identifier with the `writer` role. Below you should see the endpoint and port. Use this information to configure the `jdbc` URL. For example:

Local

~~~bash
jdbc:postgresql://192.168.7.136:5432/woe_twin
~~~

Amazon RDS

~~~bash
jdbc:postgresql://woe-twin-instance-1.ch9gohzg86t2.us-east-1.rds.amazonaws.com:5432/woe_twin
~~~

Once connected to the database create the two SQL tables. Copy the DDL from the file `src/main/resources/region-projection.sql` and paste it to a statement tab in the SQL Workbench tool. Then press the run button followed by the commit button. Repeat these steps for the second table found in the `src/main/resources/akka-projection-offset-store.sql` file.
