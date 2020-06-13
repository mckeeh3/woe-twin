package oti.twin;

import akka.actor.FSM;

public class DeviceProjection {
  static class State {
    final WorldMap.Region region;
    final int deviceCount;
    final int happyCount;
    final int sadCount;

    State(WorldMap.Region region, int deviceCount, int happyCount, int sadCount) {
      this.region = region;
      this.deviceCount = deviceCount;
      this.happyCount = happyCount;
      this.sadCount = sadCount;
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %d, %d, %d]", getClass().getSimpleName(), region, deviceCount, happyCount, sadCount);
    }
  }
}
