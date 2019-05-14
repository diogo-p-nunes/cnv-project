package manager;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.*;

import java.util.*;

@SuppressWarnings("Duplicates")
public class AutoScaler {
    private int INSTANCE_STARTUP_TIME = 30; //in seconds

    AutoScaler() throws Exception {
        initInstances();
    }

    private void initInstances() throws Exception {
        Set<Instance> instances = null;

        try {
            // Get all instances
            DescribeInstancesResult describeInstancesRequest = Manager.ec2.describeInstances();
            List<Reservation> reservations = describeInstancesRequest.getReservations();
            instances = new HashSet<>();
            for (Reservation reservation : reservations) {
                instances.addAll(reservation.getInstances());
            }

            // Populate map of IP-List<Request> with each running instance
            for(Instance instance : instances) {
                if(instance.getState().getName().equals("running")) {
                    Manager.addWS(instance.getPublicIpAddress(), instance.getInstanceId());
                }
            }
            if(Manager.wsRequests.size() < Manager.MIN_INSTANCES) {
                createInstances(Manager.MIN_INSTANCES - Manager.wsRequests.size());
            }
        }
        catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    public void createInstances(int amount) throws Exception {
        if(amount == 0) return;

        System.out.println("[AS] Starting " + amount + " new instance(s).");
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId("ami-0a24150cf40086795")
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(amount)
                .withKeyName("CNV-aws-freetier")
                .withSecurityGroups("CNV-ssh+http");

        RunInstancesResult runInstancesResult = Manager.ec2.runInstances(runInstancesRequest);
        List<Instance> instances = runInstancesResult.getReservation().getInstances();

        boolean done_init = false;
        while(!done_init) {
            // Instances startup time
            Thread.sleep(INSTANCE_STARTUP_TIME * 1000);
            done_init = true;
            for( Instance instance : instances) {
                System.out.print("[AS] Instance: " + instance.getInstanceId());
                if(instance.getState().getName().equals("running")) {
                    Manager.addWS(instance.getPublicIpAddress(), instance.getInstanceId());
                }
                else {
                    done_init = false;
                }
                System.out.println(" " + instance.getState().getName() + ".");
            }
        }

        System.out.println("[AS] Done.");
    }

    public void removeInstance(String instanceId) {
        System.out.println("[AS] Terminating instance.");
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        Manager.ec2.terminateInstances(termInstanceReq);
    }

    public void removeInstances() {
        while (getSystemPercentLoad() < Manager.MIN_SYSTEM_LOAD
                && Manager.wsRequests.size() > Manager.MIN_INSTANCES) {

            for(String ip : Manager.wsRequests.keySet()) {
                //if not running any request
                if(Manager.wsRequests.get(ip).size() == 0) {
                    String id = Manager.removeWS(ip);
                    removeInstance(id);
                    break;
                }
            }
        }
    }

    public double getSystemPercentLoad() {
        double totalLoad = Manager.getSystemTotalLoad();
        double maxLoad = Manager.MAX_CAPACITY * Manager.wsRequests.size();
        return totalLoad / maxLoad;
    }

    public int calculateAmountOfNeededInstances() {
        double totalLoad = Manager.getSystemTotalLoad();
        double maxLoad = Manager.MAX_CAPACITY * Manager.wsRequests.size();
        return (int) Math.ceil((totalLoad/Manager.MAX_SYSTEM_LOAD) - maxLoad);
    }
}
