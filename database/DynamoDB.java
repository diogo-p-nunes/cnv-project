package database;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

/**
 * This sample demonstrates how to perform a few simple operations with the
 * Amazon DynamoDB service.
 */
public class DynamoDB {

    /*
     * Before running the code:
     *      Fill in your AWS access credentials in the provided credentials
     *      file template, and be sure to move the file to the default location
     *      (~/.aws/credentials) where the sample code will load the
     *      credentials from.
     *      https://console.aws.amazon.com/iam/home?#security_credential
     *
     * WARNING:
     *      To avoid accidental leakage of your credentials, DO NOT keep
     *      the credentials file in your source directory.
     */

    static AmazonDynamoDB dynamoDB;

    private static void init() throws Exception {
        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider();
        try {
            credentialsProvider.getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
    }

    public static void main(String[] args) throws Exception {
        init();

        try {
            String tableName = "metrics";

            // Read capacity - number of strongly consistent reads per second or two eventually consistent reads per second.
            // 1L - we specified that we need 1 strongly consistent reads per second (or 2 eventually consistent reads).

            // Write capacity - number of 1KB writes per second. Packages of data that are smaller than 1KB are rounded up,
            // hence writing only 500 bytes is counted as a 1KB write.
            // 1L - we tell DynamoDB to scale the system such that we can write up to 1 times per second about 1KB of data.

            // Create a table with a primary hash key named 'description', which holds a string

            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                    .withKeySchema(new KeySchemaElement().withAttributeName("description").withKeyType(KeyType.HASH))
                    .withAttributeDefinitions(new AttributeDefinition().withAttributeName("description").withAttributeType(ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

            // Create table if it does not exist yet
            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            // wait for the table to move into ACTIVE state
            TableUtils.waitUntilActive(dynamoDB, tableName);

            // Describe our new table
            DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
            TableDescription tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
            System.out.println("Table Description: " + tableDescription);

            // addItem(tableName, "DFS|1048576|544.7531551078893=3579442.0", "DFS", 1048576, 544.7531551078893, 3579442.0);

        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which means your request made it "
                    + "to AWS, but was rejected with an error response for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }

    public static Map<String, AttributeValue> newItem(String description, String algorithm, int pixelsSearchArea, double distFromStartToEnd, double metricResult) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("description", new AttributeValue(description));
        item.put("algorithm", new AttributeValue(algorithm));
        item.put("pixelsSearchArea", new AttributeValue().withN(Integer.toString(pixelsSearchArea)));
        item.put("distFromStartToEnd", new AttributeValue().withN(Double.toString(distFromStartToEnd)));
        item.put("metricResult", new AttributeValue().withN(Double.toString(metricResult)));

        return item;
    }

    public static void addItem(String tableName, String description, String algorithm, int pixelsSearchArea, double distFromStartToEnd, double metricResult){

        // Add an item
        Map<String, AttributeValue> item = newItem(description, algorithm, pixelsSearchArea, distFromStartToEnd, metricResult);
        PutItemRequest putItemRequest = new PutItemRequest(tableName, item);
        PutItemResult putItemResult = dynamoDB.putItem(putItemRequest);
        System.out.println("Result: " + putItemResult);

//        // Scan items for movies with a year attribute greater than 1985
//        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
//        Condition condition = new Condition()
//            .withComparisonOperator(ComparisonOperator.GT.toString())
//            .withAttributeValueList(new AttributeValue().withN("1985"));
//        scanFilter.put("year", condition);
//        ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
//        ScanResult scanResult = dynamoDB.scan(scanRequest);
//        System.out.println("Result: " + scanResult);

        return;
    }
}
