package woe.twin;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.persistence.cassandra.query.javadsl.CassandraReadJournal;
import akka.persistence.query.Offset;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.javadsl.SourceProvider;
import akka.projection.testkit.javadsl.ProjectionTestKit;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;

import static woe.twin.WorldMap.*;

public class RegionProjectionTest {
  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(config());
  ProjectionTestKit projectionTestKit = ProjectionTestKit.create(testKit.system());

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
  public void regionInsert() throws SQLException {
    final HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:postgresql://192.168.7.98:5433/");
    config.setUsername("yugabyte");
    config.setPassword("yugabyte");

    final DataSource dataSource = new HikariDataSource(config);
    //String jdbcUrl = "jdbc:postgresql://192.168.7.98:5433/";
    //try (Connection connection = DriverManager.getConnection(jdbcUrl, "yugabyte", "yugabyte");
    //     Statement statement = connection.createStatement()
    try (final Connection connection = dataSource.getConnection();
         final Statement statement = connection.createStatement()) {
      insert(statement, regionForZoom0());
      insert(statement, regionAtLatLng(18, latLng(51.50083552, -0.11656344)));
      insert(statement, regionAtLatLng(18, latLng(51.50036467, -0.11946023)));
      insert(statement, regionAtLatLng(18, latLng(51.49881516, -0.11891842)));
      insert(statement, regionAtLatLng(18, latLng(52.50083552, -0.11656344)));
      insert(statement, regionAtLatLng(18, latLng(52.50036467, -0.11946023)));
      insert(statement, regionAtLatLng(18, latLng(52.49881516, -0.11891842)));
    }
  }

  private void insert(Statement statement, WorldMap.Region region) throws SQLException {
    String sql = String.format("insert into region"
            + " (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng, device_count, happy_count, sad_count)"
            + " values (%d, %1.9f, %1.9f, %1.9f, %1.9f, %d, %d, %d)"
            //+ " on conflict (zoom, top_left_lat, top_left_lng, bot_right_lat, bot_right_lng)"
            + " on conflict on constraint region_pkey"
            + " do update set"
            + " device_count = %d,"
            + " happy_count = %d,"
            + " sad_count = %d",
        region.zoom, region.topLeft.lat, region.topLeft.lng, region.botRight.lat, region.botRight.lng, 0, 0, 0, 2, 1, 1);
    statement.executeUpdate(sql);
  }

  @Ignore
  @Test
  public void regionSelect() throws SQLException {
    String jdbcUrl = "jdbc:postgresql://192.168.7.98:5433/";
    //YBClusterAwareDataSource ds = new YBClusterAwareDataSource(jdbcUrl);
    //try (Connection connection = ds.getConnection();
    try (Connection connection = DriverManager.getConnection(jdbcUrl, "yugabyte", "yugabyte");
         Statement statement = connection.createStatement()
    ) {
      try (final ResultSet resultSet = statement.executeQuery("SELECT * FROM region")) {
        while (resultSet.next()) {
          System.out.println(resultSet.getString(1));
        }
      }
    }
  }

  @Ignore
  @Test
  public void projectorDaemonStartUp() {
    final List<String> tags = Device.tagsAll(testKit.system());
    IntStream.rangeClosed(0, 31).forEach(id -> {
      final String tag = tags.get(id / 16);
      final int zoom = 3 + id % 16;
      System.out.printf("%2d %s-%d%n", id, tag, zoom);
    });
  }
}
