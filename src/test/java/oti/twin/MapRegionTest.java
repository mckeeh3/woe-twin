package oti.twin;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MapRegionTest {
  /*  4 + lat
   *    |
   *  3 +
   *    | 2,1
   *  2 +  +--+
   *    |  |  |
   *  1 +  +--+
   *    |    1,2
   *  0 +--+--+--+--+--+ lng
   *    0  1  2  3  4  5
   */
  @Test
  public void overlapRegionSelf() {
    Map.Region region = Map.region(Map.topLeft(2, 1), Map.botRight(1, 2));

    assertTrue(region.overlaps(region));
  }

  /*  4 + lat
   *    |
   *  3 +
   *    | 2,1   2,3
   *  2 +  +--+  +--+
   *    |  |  |  |  |
   *  1 +  +--+  +--+
   *    |    1,2   1,4
   *  0 +--+--+--+--+--+ lng
   *    0  1  2  3  4  5
   */
  @Test
  public void nonOverlappingRegionsSideBySide() {
    Map.Region regionLeft = Map.region(Map.topLeft(2, 1), Map.botRight(1, 2));
    Map.Region regionRight = Map.region(Map.topLeft(2, 3), Map.botRight(1, 4));

    assertFalse(regionRight.overlaps(regionLeft));
    assertFalse(regionLeft.overlaps(regionRight));
  }

  /*  6 + lat
   *    |       5,3
   *  5 +        +--+
   *    |        |  |
   *  4 +        +--+
   *    |          4,4
   *  3 +
   *    |       2,3
   *  2 +        +--+
   *    |        |  |
   *  1 +        +--+
   *    |          1,4
   *  0 +--+--+--+--+--+ lng
   *    0  1  2  3  4  5
   */
  @Test
  public void noOverlappingRegionsAboveAndBelow() {
    Map.Region regionAbove = Map.region(Map.topLeft(5, 3), Map.botRight(4, 4));
    Map.Region regionBelow = Map.region(Map.topLeft(2, 3), Map.botRight(1, 4));

    assertFalse(regionBelow.overlaps(regionAbove));
    assertFalse(regionAbove.overlaps(regionBelow));
  }

  /*            4,3
   *  4 + lat    +--+
   *    |        |  |
   *  3 +        +--+
   *    | 2,1      3,4
   *  2 +  +--+
   *    |  |  |
   *  1 +  +--+
   *    |    1,2
   *  0 +--+--+--+--+--+ lng
   *    0  1  2  3  4  5
   */
  @Test
  public void nonOverlappingRegionsLowerLeftAndUpperRight() {
    Map.Region regionLowerLeft = Map.region(Map.topLeft(2, 1), Map.botRight(1, 2));
    Map.Region regionUpperRight = Map.region(Map.topLeft(4, 3), Map.botRight(3, 4));

    assertFalse(regionUpperRight.overlaps(regionLowerLeft));
    assertFalse(regionLowerLeft.overlaps(regionUpperRight));
  }

  /*  5 + lat
   *    | 4,1
   *  4 +  +--+--+--+
   *    |  | 3,2    |
   *  3 +  +  +--+  +
   *    |  |  |  |  |
   *  2 +  +  +--+  +
   *    |  |    2,3 |
   *  1 +  +--+--+--+
   *    |          1,4
   *  0 +--+--+--+--+--+ lng
   *    0  1  2  3  4  5
   */
  @Test
  public void overlappingRegions() {
    Map.Region regionOutside = Map.region(Map.topLeft(4, 1), Map.botRight(1, 4));
    Map.Region regionInside = Map.region(Map.topLeft(3, 2), Map.botRight(2, 3));

    assertTrue(regionOutside.overlaps(regionInside));
    assertTrue(regionInside.overlaps(regionOutside));
  }

  /*  5 + lat
   *    |    4,2
   *  4 +     +--+
   *    | 3,1 |  |
   *  3 +  +--+--+--+
   *    |  |  |  |  |
   *  2 +  +--+--+--+
   *    |     |  | 2,4
   *  1 +     +--+
   *    |       1,3
   *  0 +--+--+--+--+--+ lng
   *    0  1  2  3  4  5
   */
  @Test
  public void nonOverlappingAdjoiningLeftRightAboveBelow() {
    Map.Region regionCenter = Map.region(Map.topLeft(3, 2), Map.botRight(2, 3));
    Map.Region regionUpperAdjoining = Map.region(Map.topLeft(4, 2), Map.botRight(3, 3));
    Map.Region regionLowerAdjoining = Map.region(Map.topLeft(2, 2), Map.botRight(1, 3));
    Map.Region regionLeftAdjoining = Map.region(Map.topLeft(3, 1), Map.botRight(2, 2));
    Map.Region regionRightAdjoining = Map.region(Map.topLeft(3, 3), Map.botRight(2, 4));

    assertFalse(regionCenter.overlaps(regionUpperAdjoining));
    assertFalse(regionCenter.overlaps(regionLowerAdjoining));
    assertFalse(regionCenter.overlaps(regionLeftAdjoining));
    assertFalse(regionCenter.overlaps(regionRightAdjoining));

    assertFalse(regionUpperAdjoining.overlaps(regionCenter));
    assertFalse(regionLowerAdjoining.overlaps(regionCenter));
    assertFalse(regionLeftAdjoining.overlaps(regionCenter));
    assertFalse(regionRightAdjoining.overlaps(regionCenter));
  }

  /*  5 + lat
   *    |    4,2
   *  4 +     +--+--+
   *    | 3,1 |     |
   *  3 +  +--+--+  +
   *    |  |  |  |  |
   *  2 +  +  +--+--+
   *    |  |     | 2,4
   *  1 +  +--+--+
   *    |       1,3
   *  0 +--+--+--+--+--+ lng
   *    0  1  2  3  4  5
   */
  @Test
  public void partiallyOverlappingRegions() {
    Map.Region regionLowerLeft = Map.region(Map.topLeft(3, 1), Map.botRight(1, 3));
    Map.Region regionUpperRight = Map.region(Map.topLeft(4, 2), Map.botRight(2, 4));

    assertTrue(regionLowerLeft.overlaps(regionUpperRight));
    assertTrue(regionUpperRight.overlaps(regionLowerLeft));
  }
}
