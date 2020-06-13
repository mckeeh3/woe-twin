package oti.twin;

import org.junit.Test;

import java.util.Set;
import static oti.twin.WorldMap.*;
import static org.junit.jupiter.api.Assertions.*;

public class DeviceTest {
  @Test
  public void tagsForCreatesTagsForZoom3to18() {
    final WorldMap.Region region = regionAtLatLng(18, latLng( 51.5007541,-0.11688530));
    final Set<String> tags = Device.tagsFor(region, 100);

    assertEquals(16, tags.size());
    assertNotNull(tags.stream().filter(t -> t.startsWith("zoom-3-entity-")).findFirst());
    assertNotNull(tags.stream().filter(t -> t.startsWith("zoom-18-entity-")).findFirst());
  }
}
