package oti.twin;

import akka.Done;
import akka.projection.eventsourced.EventEnvelope;
import akka.projection.javadsl.Handler;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DeviceProjection {
  static class RegionSummary {
    final WorldMap.Region region;
    final int deviceCount;
    final int happyCount;
    final int sadCount;

    RegionSummary(WorldMap.Region region, int deviceCount, int happyCount, int sadCount) {
      this.region = region;
      this.deviceCount = deviceCount;
      this.happyCount = happyCount;
      this.sadCount = sadCount;
    }

    @Override
    public String toString() {
      return String.format("%s[%s, %d, %d, %d]", getClass().getSimpleName(), region, deviceCount, happyCount, sadCount);
    }

    RegionSummary activated() {
      return new RegionSummary(region, deviceCount + 1, happyCount + 1, sadCount);
    }

    RegionSummary deactivatedHappy() {
      return new RegionSummary(region, deviceCount - 1, happyCount - 1, sadCount);
    }

    RegionSummary deactivatedSad() {
      return new RegionSummary(region, deviceCount - 1, happyCount, sadCount - 1);
    }

    RegionSummary madeHappy() {
      return new RegionSummary(region, deviceCount, happyCount + 1, sadCount - 1);
    }

    RegionSummary madeSad() {
      return new RegionSummary(region, deviceCount, happyCount - 1, sadCount + 1);
    }
  }

  static class DeviceEventHandler extends Handler<List<EventEnvelope<Device.Event>>> {
    @Override
    public CompletionStage<Done> process(List<EventEnvelope<Device.Event>> eventEnvelopes) {

      begin();
      eventEnvelopes.forEach(eventEventEnvelope -> {
        final Device.Event event = eventEventEnvelope.event();

        if (event instanceof Device.DeviceActivated) {
          update((Device.DeviceActivated) event);
        } else if (event instanceof Device.DeviceDeactivatedHappy) {
          update((Device.DeviceDeactivatedHappy) event);
        } else if (event instanceof Device.DeviceDeactivatedSad) {
          update((Device.DeviceDeactivatedSad) event);
        } else if (event instanceof Device.DeviceMadeHappy) {
          update((Device.DeviceMadeHappy) event);
        } else if (event instanceof Device.DeviceMadeSad) {
          update((Device.DeviceMadeSad) event);
        }
      });
      commit();

      return CompletableFuture.completedFuture(Done.getInstance());
    }

    private void update(Device.DeviceActivated event) {
      update(read(event).activated());
    }

    private void update(Device.DeviceDeactivatedHappy event) {
      update(read(event).deactivatedHappy());
    }

    private void update(Device.DeviceDeactivatedSad event) {
      update(read(event).deactivatedSad());
    }

    private void update(Device.DeviceMadeHappy event) {
      update(read(event).madeHappy());
    }

    private void update(Device.DeviceMadeSad event) {
      update(read(event).madeSad());
    }

    private void begin() {
      // TODO add SQL begin transaction isolation level repeatable read
    }

    private RegionSummary read(Device.DeviceEvent event) {
      // TODO add SQL select
      return new RegionSummary(event.region, 0, 0, 0);
    }

    private void update(RegionSummary regionSummary) {
      // TODO add SQL insert or update
    }

    private void commit() {
      // TODO add SQL commit transaction
    }
  }
}
