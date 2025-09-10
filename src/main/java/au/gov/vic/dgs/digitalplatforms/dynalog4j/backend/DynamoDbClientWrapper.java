package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * Wrapper interface for DynamoDB operations to enable easier testing.
 */
public interface DynamoDbClientWrapper {
    GetItemResponse getItem(GetItemRequest request);
}
