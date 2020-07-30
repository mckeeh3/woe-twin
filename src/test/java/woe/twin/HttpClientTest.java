package woe.twin;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.SerializationTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import org.junit.ClassRule;
import org.junit.Test;

public class HttpClientTest {
  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void serializeDeserializeSelectionActionRequest() {
    final SerializationTestKit serializationTestKit = ActorTestKit.create(testKit.system()).serializationTestKit();

    final HttpClient.SelectionActionRequest selectionActionRequest = new HttpClient.SelectionActionRequest("create", 100, 3, 1, 1, 0, 0);
    serializationTestKit.verifySerialization(selectionActionRequest, true);
  }
}
