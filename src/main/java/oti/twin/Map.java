package oti.twin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

interface Map {
  static LatLng topLeft(double lat, double lng) {
    return new LatLng(lat, lng);
  }

  static LatLng botRight(double lat, double lng) {
    return new LatLng(lat, lng);
  }

  static Region region(LatLng topLeft, LatLng botRight) {
    return new Region(topLeft, botRight);
  }

  static LatLng tickLen(int zoom) {
    switch (zoom) {
      case 0:
        return new LatLng(180, 360);
      case 1:
        return new LatLng(180, 180);
      case 2:
        return new LatLng(60, 60);
      default:
        int totalLatLines = (int) (9 * Math.pow(2, zoom - 3));
        int totalLngLines = (int) (18 * Math.pow(2, zoom - 3));
        return new LatLng(180.0 / totalLatLines, 360.0 / totalLngLines);
    }
  }

  static List<ZoomRegion> subZoomRegionsFor(ZoomRegion zoomRegion) {

    return null;
  }

  class LatLng {
    final double lat;
    final double lng;

    LatLng(double lat, double lng) {
      this.lat = lat;
      this.lng = lng;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LatLng latLng = (LatLng) o;
      return Double.compare(latLng.lat, lat) == 0 &&
          Double.compare(latLng.lng, lng) == 0;
    }

    @Override
    public int hashCode() {
      return Objects.hash(lat, lng);
    }

    @Override
    public String toString() {
      return String.format("%s[lat %f, lng %f]", getClass().getSimpleName(), lat, lng);
    }
  }

  class Region {
    final LatLng topLeft;
    final LatLng botRight;

    Region(LatLng topLeft, LatLng botRight) {
      if (topLeft.lat <= botRight.lat) {
        throw new IllegalArgumentException("Top left latitude must be greater than bottom right latitude.");
      }
      if (topLeft.lng >= botRight.lng) {
        throw new IllegalArgumentException("Top left longitude must be less than bottom right longitude.");
      }
      this.topLeft = topLeft;
      this.botRight = botRight;
    }

    boolean overlaps(Region region) {
      return !isThisAbove(region) && !isThisBelow(region) && !isThisLeft(region) && !isThisRight(region);
    }

    private boolean isThisAbove(Region region) {
      return botRight.lat >= region.topLeft.lat;
    }

    private boolean isThisBelow(Region region) {
      return topLeft.lat <= region.botRight.lat;
    }

    private boolean isThisLeft(Region region) {
      return botRight.lng <= region.topLeft.lng;
    }

    private boolean isThisRight(Region region) {
      return topLeft.lng >= region.botRight.lng;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Region region = (Region) o;
      return Objects.equals(topLeft, region.topLeft) &&
          Objects.equals(botRight, region.botRight);
    }

    @Override
    public int hashCode() {
      return Objects.hash(topLeft, botRight);
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %s]", getClass().getSimpleName(), topLeft, botRight);
    }
  }

  class Selection {
    final int zoom;
    final Region region;

    Selection(int zoom, Region region) {
      this.zoom = zoom;
      this.region = region;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Selection selection = (Selection) o;
      return zoom == selection.zoom &&
          region.equals(selection.region);
    }

    @Override
    public String toString() {
      return String.format("%s[zoom %d, %s]", getClass().getSimpleName(), zoom, region);
    }

    @Override
    public int hashCode() {
      return Objects.hash(zoom, region);
    }
  }

  class Selections {
    private final List<Selection> selections = new ArrayList<>();

    List<Selection> add(Selection selection) {
      selections.add(selection);
      return new ArrayList<Selection>(selections);
    }
  }

  class ZoomRegion {
    final int zoom;
    final Region region;
    final Selections selections = new Selections();

    public ZoomRegion(int zoom, Region region) {
      this.zoom = zoom;
      this.region = region;
    }
  }
}
