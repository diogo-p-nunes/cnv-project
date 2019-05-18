package database;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import manager.Manager;

@SuppressWarnings("Duplicates")
public class DynamoDB {
    private static AmazonDynamoDB dynamoDB;
    public static String TABLE_METRICS = "metrics";
    private static double SPACE = 0.05;

    private static void init() {
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        try {
            credentialsProvider.getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. "
                    + "Please make sure that your credentials file is at the correct "
                    + "location (~/.aws/credentials), and is in valid format.", e);
        }
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion(Manager.REGION)
                .build();
    }

    public DynamoDB() throws Exception {
        init();
        try {

            /*
             * Read capacity - number of strongly consistent reads per second or two eventually consistent reads per second.
             * 1L - we specified that we need 1 strongly consistent reads per second (or 2 eventually consistent reads).
             * Write capacity - number of 1KB writes per second. Packages of data that are smaller than 1KB are rounded up,
             * hence writing only 500 bytes is counted as a 1KB write.
             * 1L - we tell DynamoDB to scale the system such that we can write up to 1 times per second about 1KB of data.
             * Create a table with a primary hash key named 'description', which holds a string
             */

            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(TABLE_METRICS)
                    .withKeySchema(new KeySchemaElement().withAttributeName("description")
                            .withKeyType(KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition().withAttributeName("description")
                            .withAttributeType(ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L)
                            .withWriteCapacityUnits(1L));

            // Create table if it does not exist yet
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);

            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, TABLE_METRICS);
            //System.out.println("[DB] Database is active.");

            //addItem(TABLE_METRICS, "DFS", 1048576, 544.7531551078893, 3579442.0);
        }
        catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        }
        catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }

    private static Map<String, AttributeValue> newItem(String description, String algorithm, long pixelsSearchArea,
                                                      double distFromStartToEnd, double metricResult) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("description", new AttributeValue(description));
        item.put("algorithm", new AttributeValue(algorithm));
        item.put("pixelsSearchArea", new AttributeValue().withN(Long.toString(pixelsSearchArea)));
        item.put("distFromStartToEnd", new AttributeValue().withN(Double.toString(distFromStartToEnd)));
        item.put("metricResult", new AttributeValue().withN(Double.toString(metricResult)));
        return item;
    }

    public static void addItem(String tableName, String algorithm, long pixelsSearchArea,
                               double distFromStartToEnd, double metricResult) {
        // Add an item
        String description = algorithm + "|" + pixelsSearchArea + "|" + distFromStartToEnd;
        Map<String, AttributeValue> item = newItem(description, algorithm, pixelsSearchArea, distFromStartToEnd, metricResult);
        PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
        dynamoDB.putItem(putItemRequest);
        System.out.println("[DB] Added entry: " + item.toString());
    }

    public static ScanResult getItems(String algorithm, long pixelsSearchArea, double distFromStartToEnd) {

        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
        ScanRequest scanRequest = null;
        ScanResult scanResult = null;

        // First check if there is an equal request exactly
        String description = algorithm + "|" + pixelsSearchArea + "|" + distFromStartToEnd;
        Condition eq_request = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
                                              .withAttributeValueList(new AttributeValue(description));
        scanFilter.put("description", eq_request);
        scanRequest = new ScanRequest(TABLE_METRICS).withScanFilter(scanFilter);
        scanResult = dynamoDB.scan(scanRequest);
        if(scanResult.getItems().size() != 0) {
            //System.out.println("[DB] Found exact same request.");
            return scanResult;
        }

        // Scan items with the same algorithm
        //System.out.println("[DB] Getting similar requests.");
        scanFilter = new HashMap<String, Condition>();
        Condition eq_algorithm = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString())
                                                .withAttributeValueList(new AttributeValue(algorithm));
        scanFilter.put("algorithm", eq_algorithm);

        long interval = (long) SPACE * pixelsSearchArea;
        Condition sim_pixelsSearchArea = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN.toString())
                    .withAttributeValueList(new AttributeValue().withN(Long.toString(pixelsSearchArea - interval)),
                                            new AttributeValue().withN(Long.toString(pixelsSearchArea + interval)));
        scanFilter.put("pixelsSearchArea", sim_pixelsSearchArea);

        double interval_d = SPACE * distFromStartToEnd;
        Condition sim_distFromStartToEnd = new Condition().withComparisonOperator(ComparisonOperator.BETWEEN.toString())
              .withAttributeValueList(new AttributeValue().withN(Double.toString(distFromStartToEnd - interval_d)),
                                      new AttributeValue().withN(Double.toString(distFromStartToEnd + interval_d)));
        scanFilter.put("distFromStartToEnd", sim_distFromStartToEnd);


        scanRequest = new ScanRequest(TABLE_METRICS).withScanFilter(scanFilter);
        scanResult = dynamoDB.scan(scanRequest);
        //System.out.println("[DB] Query results: " + scanResult.toString());
        return scanResult;
    }
}
