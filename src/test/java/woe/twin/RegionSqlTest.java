package woe.twin;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static woe.twin.WorldMap.*;

public class RegionSqlTest {
  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource(config());

  private static Config config() {
    return ConfigFactory.parseString(
        String.format("akka.cluster.seed-nodes = [ \"akka://%s@127.0.0.1:25520\" ] %n", RegionSqlTest.class.getSimpleName())
            + String.format("akka.persistence.snapshot-store.local.dir = \"%s-%s\" %n", "target/snapshot", UUID.randomUUID().toString())
    ).withFallback(ConfigFactory.load("application-test.conf"));
  }

  @Ignore
  @Test
  public void loadTestParis() throws SQLException {
    final int zoom = 8;
    final long devicesPerZoom = Math.round(Math.pow(4, 18 - zoom));
    final DataSource dataSource = dataSource(testKit.system());
    final List<DeviceProjectorSingleZoom.RegionSummary> regionSummaries = regionSummaries(regionAtLatLng(zoom, new WorldMap.LatLng(48.85, 2.35)));

    testKit.system().log().info("{} rows created", String.format("%,d", regionSummaries.size()));
    assertTrue(regionSummaries.size() > devicesPerZoom);

    insertUpdate(dataSource, 1000, regionSummaries);
  }

  @Ignore
  @Test
  public void loadTestAtlanta() throws SQLException {
    final int zoom = 8;
    final long devicesPerZoom = Math.round(Math.pow(4, 18 - zoom));
    final DataSource dataSource = dataSource(testKit.system());
    final List<DeviceProjectorSingleZoom.RegionSummary> regionSummaries = regionSummaries(regionAtLatLng(zoom, new WorldMap.LatLng(33.75, -82.39)));

    testKit.system().log().info("{} rows created", String.format("%,d", regionSummaries.size()));
    assertTrue(regionSummaries.size() > devicesPerZoom);

    insertUpdate(dataSource, 1000, regionSummaries);
  }

  @Ignore
  @Test
  public void t() throws SQLException {
    Map<Integer, Region> queryAreas = zoomedRegionAreas();

    final DataSource dataSource = dataSource(testKit.system());
    for (Map.Entry<Integer, Region> entry : queryAreas.entrySet()) {
      final Integer zoom = entry.getKey();
      final List<Region> regionsInQuery = regionsIn(entry.getValue());
      final String sql = "explain analyze " + HttpServer.sqlInRegions(entry.getValue());
      //testKit.system().log().info("{}", sql);
      try (Connection connection = dataSource.getConnection();
           Statement statement = connection.createStatement()) {
        final ResultSet resultSet = statement.executeQuery(sql);
        System.out.printf("%nzoom %d, regions in query values %,d ================================================================================%n", zoom, regionsInQuery.size());
        while (resultSet.next()) {
          System.out.printf("%s%n", resultSet.getString(1));
        }
        System.out.printf("%nzoom %d, regions in query values %,d ================================================================================%n", zoom, regionsInQuery.size());
      }
    }
  }

  @Ignore
  @Test
  public void coordinatesZoomingOutFromSamePoint() throws SQLException {
    Map<Integer, Region> queryAreas = zoomedRegionAreas();

    final DataSource dataSource = dataSource(testKit.system());
    for (Map.Entry<Integer, Region> entry : queryAreas.entrySet()) {
      final Integer zoom = entry.getKey();
      final String sql = HttpServer.sqlInRegions(entry.getValue());
      //testKit.system().log().info("{}", sql);
      try (Connection connection = dataSource.getConnection();
           Statement statement = connection.createStatement()) {
        final long start = System.nanoTime();
        final ResultSet resultSet = statement.executeQuery(sql);
        int rowCount = 0;
        while (resultSet.next()) {
          rowCount++;
        }
        connection.commit();
        testKit.system().log().info("{}", String.format("%,d, zoom %d, query rows %,d", System.nanoTime() - start, zoom, rowCount));
      }
    }
  }

  private Map<Integer, Region> zoomedRegionAreas() {
    Map<Integer, Region> queryAreas = new HashMap<>();
    queryAreas.put(18, new Region(18, topLeft(51.50458547797352, -0.12260913848876955), botRight(51.497659673380355, -0.11235773563385011)));
    queryAreas.put(17, new Region(18, topLeft(51.50804798558264, -0.12773752212524417), botRight(51.49419637638168, -0.10723471641540529)));
    queryAreas.put(16, new Region(18, topLeft(51.51497888812548, -0.1379942893981934), botRight(51.4872756736659, -0.09698867797851564)));
    queryAreas.put(15, new Region(17, topLeft(51.52882418070984, -0.15848636627197268), botRight(51.47341775085057, -0.0764751434326172)));
    queryAreas.put(14, new Region(16, topLeft(51.55652882123261, -0.19947052001953128), botRight(51.44571601895113, -0.03544807434082032)));
    queryAreas.put(13, new Region(15, topLeft(51.61183421075632, -0.28152465820312506), botRight(51.39020854602782, 0.04652023315429688)));
    queryAreas.put(12, new Region(14, topLeft(51.722349458282906, -0.44563293457031256), botRight(51.27909868682927, 0.21045684814453125)));
    queryAreas.put(11, new Region(13, topLeft(51.94257177774757, -0.7738494873046876), botRight(51.056070541830934, 0.5383300781250001)));
    queryAreas.put(10, new Region(12, topLeft(52.379790828551016, -1.4295959472656252), botRight(50.60677419392376, 1.1947631835937502)));
    queryAreas.put(9, new Region(11, topLeft(53.24056438248143, -2.7410888671875004), botRight(49.69428518147923, 2.5076293945312504)));
    queryAreas.put(8, new Region(10, topLeft(54.911356424188476, -5.366821289062501), botRight(47.81684332352077, 5.130615234375001)));
    queryAreas.put(7, new Region(9, topLeft(58.05463191137292, -10.612792968750002), botRight(43.8503744993026, 10.382080078125002)));
    queryAreas.put(6, new Region(8, topLeft(63.597447665602004, -21.11572265625), botRight(35.08395557927643, 20.874023437500004)));
    queryAreas.put(5, new Region(7, topLeft(72.08743247624157, -42.09960937500001), botRight(14.562317701914855, 41.87988281250001)));
    queryAreas.put(4, new Region(6, topLeft(81.8612078903467, -84.11132812500001), botRight(-29.45873118535533, 83.84765625000001)));
    queryAreas.put(3, new Region(5, topLeft(88.33883839556603, -168.04687500000003), botRight(-76.43460358351301, 167.87109375000003)));
    return queryAreas;
  }

  private static DataSource dataSource(ActorSystem<?> actorSystem) {
    final String dbUrl = actorSystem.settings().config().getString("woe.twin.sql.url");
    final String username = actorSystem.settings().config().getString("woe.twin.sql.username");
    final String password = actorSystem.settings().config().getString("woe.twin.sql.password");
    final int maxPoolSize = actorSystem.settings().config().getInt("woe.twin.sql.max-pool-size");

    final HikariConfig config = new HikariConfig();
    config.setJdbcUrl(dbUrl);
    config.setUsername(username);
    config.setPassword(password);
    config.setMaximumPoolSize(maxPoolSize);
    config.setAutoCommit(false);

    return new HikariDataSource(config);
  }

  private static List<DeviceProjectorSingleZoom.RegionSummary> regionSummaries(WorldMap.Region region) {
    List<DeviceProjectorSingleZoom.RegionSummary> regionSummaries = new ArrayList<>();
    final Map<Integer, DeviceProjectorSingleZoom.RegionSummaries> regionSummariesMap = regionSummariesFor(region);
    regionSummariesMap
        .forEach((zoom, summaries) -> regionSummaries.addAll(summaries.asList()));
    return regionSummaries;
  }

  private static Map<Integer, DeviceProjectorSingleZoom.RegionSummaries> regionSummariesFor(WorldMap.Region region) {
    final Map<Integer, DeviceProjectorSingleZoom.RegionSummaries> regionSummariesMap = new HashMap<>();
    final List<Device.DeviceActivated> devicesActivated = devicesFor(region);

    IntStream.rangeClosed(3, 18).forEach(zoom -> regionSummariesMap.put(zoom, new DeviceProjectorSingleZoom.RegionSummaries(zoom)));
    devicesActivated
        .forEach(device -> IntStream.rangeClosed(3, 18)
            .forEach(zoom -> regionSummariesMap.get(zoom).add(device)));
    return regionSummariesMap;
  }

  private static List<Device.DeviceActivated> devicesFor(WorldMap.Region region) {
    if (region.isDevice()) {
      return Collections.singletonList(new Device.DeviceActivated(region));
    } else {
      List<Device.DeviceActivated> devices = new ArrayList<>();
      subRegionsFor(region)
          .forEach(subRegion -> devices.addAll(devicesFor(subRegion)));
      return devices;
    }
  }

  private static void insertUpdate(DataSource dataSource, int increment, List<DeviceProjectorSingleZoom.RegionSummary> regionSummaries) throws SQLException {
    for (int i = 0; i < regionSummaries.size(); i += increment) {
      insertUpdate(dataSource, regionSummaries.subList(i, Math.min(i + increment, regionSummaries.size())));
      testKit.system().log().info("{}", String.format("commit %,d", i + increment));
    }
  }

  private static void insertUpdate(DataSource dataSource, List<DeviceProjectorSingleZoom.RegionSummary> regionSummaries) throws SQLException {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      final String sql = DeviceProjectorSingleZoom.DeviceEventHandler.sql(regionSummaries);
      statement.executeUpdate(sql);
      connection.commit();
    }
  }

  private long expectedRegionsForZoom(int zoomSelection, int zoomRegion) {
    return Math.round(Math.pow(4, 18 - zoomSelection) / Math.pow(4, 18 - Math.max(zoomSelection, zoomRegion)));
  }

  private long regionsForZoom(int zoom, List<DeviceProjectorSingleZoom.RegionSummary> regionSummaries) {
    return regionSummaries.stream()
        .filter(r -> r.region.zoom == zoom)
        .count();
  }

  private long devicesForZoom(int zoom, List<DeviceProjectorSingleZoom.RegionSummary> regionSummaries) {
    return regionSummaries.stream()
        .filter(r -> r.region.zoom == zoom)
        .map(rs -> rs.deviceCount)
        .reduce(0, Integer::sum);
  }
}
