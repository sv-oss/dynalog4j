package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Default implementation of DynamoDbClientWrapper that delegates to the real AWS SDK client.
 */
public class DefaultDynamoDbClientWrapper implements DynamoDbClientWrapper {
    private final DynamoDbClient client;
    
    public DefaultDynamoDbClientWrapper(DynamoDbClient client) {
        this.client = client;
    }
    
    @Override
    public GetItemResponse getItem(GetItemRequest request) {
        return client.getItem(request);
    }
    
    @Override
    public QueryResponse query(QueryRequest request) {
        return client.query(request);
    }
}
