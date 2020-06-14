
CREATE KEYSPACE IF NOT EXISTS oti_twin WITH replication = {'class': 'NetworkTopologyStrategy', '<your_dc_name>' : 3 };

CREATE TABLE IF NOT EXISTS oti_twin.offset_store (
  projection_name text,
  partition int,
  projection_key text,
  offset text,
  manifest text,
  last_updated timestamp,
  PRIMARY KEY ((projection_name, partition), projection_key))

