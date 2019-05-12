package loadbalancer;
/* 2016-18 Edited by Luis Veiga and Joao Garcia */
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.Date;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;

@SuppressWarnings("Duplicates")
public class EC2LaunchMeasureCPU {

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

    static AmazonEC2 ec2;
    static AmazonCloudWatch cloudWatch;

    /**
     * The only information needed to create a client are security credentials
     * consisting of the AWS Access Key ID and Secret Access Key. All other
     * configuration, such as the service endpoints, are performed
     * automatically. Client parameters, such as proxies, can be specified in an
     * optional ClientConfiguration object when constructing a client.
     *
     * @see com.amazonaws.auth.BasicAWSCredentials
     * @see com.amazonaws.auth.PropertiesCredentials
     * @see com.amazonaws.ClientConfiguration
     */
    private static void init() throws Exception {

        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        ec2 = AmazonEC2ClientBuilder.standard()
                                    .withRegion("us-east-1")
                                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                                    .build();

        cloudWatch = AmazonCloudWatchClientBuilder.standard()
                                                  .withRegion("us-east-1")
                                                  .withCredentials(new AWSStaticCredentialsProvider(credentials))
                                                  .build();
    }


    public static void main(String[] args) throws Exception {
        init();
        try {
            DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
            List<Reservation> reservations = describeInstancesResult.getReservations();
            Set<Instance> instances = new HashSet<Instance>();

            System.out.println("total reservations = " + reservations.size());
            for (Reservation reservation : reservations) {
                instances.addAll(reservation.getInstances());
            }

            System.out.println("total instances = " + instances.size());
            /* total observation time in milliseconds */
            long offsetInMilliseconds = 1000 * 600;

            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
            dims.add(instanceDimension);

            for (Instance instance : instances) {
                String name = instance.getInstanceId();
                String state = instance.getState().getName();

                if (state.equals("running")) {

                    System.out.println("running instance id = " + name);
                    instanceDimension.setValue(name);

                    GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                            .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                            .withNamespace("AWS/EC2")
                            .withPeriod(60)
                            .withMetricName("CPUUtilization")
                            .withStatistics("Average")
                            .withDimensions(instanceDimension)
                            .withEndTime(new Date());

                    GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);

                     List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
                     for (Datapoint dp : datapoints) {
                       System.out.println(" CPU utilization for instance " + name + " = " + dp.getAverage());
                     }
                }
                else {
                    System.out.println("instance id = " + name);
                }
                System.out.println("Instance State : " + state +".");
            }


            ListMetricsRequest request = new ListMetricsRequest();

            boolean done = false;

            while(!done) {
                ListMetricsResult response = cloudWatch.listMetrics(request);

                for(Metric metric : response.getMetrics()) {
                    System.out.printf("Retrieved metric %s\n", metric.getMetricName());
                }

                request.setNextToken(response.getNextToken());

                if(response.getNextToken() == null) {
                    done = true;
                }
            }

        }
        catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }
}
