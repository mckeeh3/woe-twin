package woe.twin;

import org.junit.Ignore;
import org.junit.Test;

import java.util.stream.IntStream;

public class MapZoomLevelsTest {
  @Ignore
  @Test
  public void zoomInfo() {
    IntStream.range(0, 19).forEach(this::zoom);
  }

  private void zoom(int zoom) {
    int regions;
    double tickLenLat;
    double tickLenLng;

    switch (zoom) {
      case 0:
        regions = 1;
        tickLenLat = 180;
        tickLenLng = 360;
        break;
      case 1:
        regions = 2;
        tickLenLat = 180;
        tickLenLng = 180;
        break;
      case 2:
        regions = 6;
        tickLenLat = 60;
        tickLenLng = 60;
        break;
      default:
        int totalLatLines = (int) (9 * Math.pow(2, zoom - 3));
        int totalLngLines = (int) (18 * Math.pow(2, zoom - 3));
        regions = 4;
        tickLenLat = 180.0 / totalLatLines;
        tickLenLng = 360.0 / totalLngLines;
    }
    System.out.printf("Zoom %2d, regions %d", zoom, regions);
    System.out.printf(", tick len lat %9.5f, lng %9.5f", tickLenLat, tickLenLng);
    System.out.printf(", total regions %,.0f%n", 180 / tickLenLat * 360 / tickLenLng);
  }
}
