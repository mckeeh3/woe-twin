package oti.twin;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal;
import akka.persistence.query.Offset;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.javadsl.SourceProvider;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.yugabyte.ysql.YBClusterAwareDataSource;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class RegionProjectionTest {
  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(config());

  private static Config config() {
    return ConfigFactory.parseString(
        String.format("akka.cluster.seed-nodes = [ \"akka://%s@127.0.0.1:25520\" ] %n", HttpServerTest.class.getSimpleName())
            + String.format("akka.persistence.snapshot-store.local.dir = \"%s-%s\" %n", "target/snapshot", UUID.randomUUID().toString())
    ).withFallback(ConfigFactory.load("application-test.conf"));
  }

  @Ignore
  @Test
  public void t() {
    SourceProvider<Offset, EventEnvelope<Object>> sourceProvider =
        EventSourcedProvider.eventsByTag(testKit.system(), CassandraReadJournal.Identifier(), "TODO");
    sourceProvider.source(this::offset);
  }

  private CompletionStage<Optional<Offset>> offset() {
    return null;
  }

  @Ignore
  @Test
  public void y() throws SQLException {
    String jdbcUrl = "";
    YBClusterAwareDataSource ds = new YBClusterAwareDataSource(jdbcUrl);
    try (Connection connection = ds.getConnection();
         Statement statement = connection.createStatement();
    ) {
      try (final ResultSet resultSet = statement.executeQuery("SELECT * FROM mytable WHERE key = 1");) {
        while (resultSet.next()) {
          System.out.println(resultSet.getString(1));
        }
      }
    }
  }
}
