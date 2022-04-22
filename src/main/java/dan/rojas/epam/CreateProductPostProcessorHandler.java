package dan.rojas.epam;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import dan.rojas.epam.html.ProductHtmlPage;
import org.jsoup.nodes.Document;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CreateProductPostProcessorHandler implements RequestHandler<DynamodbEvent, Void> {

  public static final String INSERT = "INSERT";
  public static final String UPDATE = "MODIFY";
  private final S3Client s3Client;

  public CreateProductPostProcessorHandler() {
    s3Client = DependencyFactory.s3Client();
  }

  @Override
  public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {
    System.out.println(dynamodbEvent);

    Optional.ofNullable(dynamodbEvent)
        .map(DynamodbEvent::getRecords)
        .orElse(new ArrayList<>())
        .stream()
        .findFirst()
        .map(this::processStreamRecord)
        .ifPresent(this::uploadDocument);

    return null;
  }

  private Document processStreamRecord(final DynamodbEvent.DynamodbStreamRecord dynamodbStreamRecord) {
    System.out.println("DynamodbStreamRecord -> " + dynamodbStreamRecord);
    final Map<String, AttributeValue> newImage = dynamodbStreamRecord.getDynamodb().getNewImage();
    Document document = null;
    if (Objects.nonNull(newImage)) {
      final Map<String, String> itemMap = getItemMap(newImage);
      switch (dynamodbStreamRecord.getEventName()) {
        case INSERT:
          document = ProductHtmlPage.appendNewItem(s3Client, itemMap);
          break;
        case UPDATE:
          document = ProductHtmlPage.updateExistingItem(s3Client, itemMap);
          break;
      }
    }
    return document;
  }

  private Map<String, String> getItemMap(final Map<String, AttributeValue> image) {
    final Map<String, String> itemMap = new HashMap<>();
    image.forEach((key, attribute) -> itemMap.put(key, attribute.getS()));
    return itemMap;
  }

  private void uploadDocument(final Document document) {
    System.out.println("Uploading the webpage to S3");
    s3Client.putObject(PutObjectRequest.builder().bucket(S3Constants.BUKET_NAME)
        .key(S3Constants.PRODUCTS_HTML_KEY).build(), RequestBody.fromString(document.toString(),
        StandardCharsets.UTF_8));
  }

}
