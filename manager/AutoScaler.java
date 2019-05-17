package manager;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.*;

import java.util.*;

@SuppressWarnings("Duplicates")
public class AutoScaler {
    private int INSTANCE_STARTUP_TIME = 30; //in seconds - grace period

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
            if(Manager.getNumberOfInstances() < Manager.MIN_INSTANCES) {
                createInstances(Manager.MIN_INSTANCES - Manager.getNumberOfInstances());
            }
        }
        catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    public void createInstances(int amount) throws InterruptedException {
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
        List<Instance> newInstances = runInstancesResult.getReservation().getInstances();
        List<Instance> instances = null;

        boolean done_init = false;
        while(!done_init) {

            // Instances startup time
            Thread.sleep(INSTANCE_STARTUP_TIME * 1000);

            // Request AWS for all instances states
            DescribeInstancesResult describeInstancesRequest = Manager.ec2.describeInstances();
            List<Reservation> reservations = describeInstancesRequest.getReservations();
            instances = new ArrayList<>();
            for (Reservation reservation : reservations) {
                instances.addAll(reservation.getInstances());
            }

            // check if the newly created instance is running or not
            done_init = true;
            for( Instance instance : instances) {
                boolean isNewInstance = false;
                for(Instance newInst : newInstances) {
                    if(instance.getInstanceId().equals(newInst.getInstanceId())) {
                        isNewInstance = true;
                    }
                }
                if(isNewInstance && instance.getState().getName().equals("running")) {
                    Manager.addWS(instance.getPublicIpAddress(), instance.getInstanceId());
                    System.out.println("[AS] Instance: " + instance.getInstanceId() + " is ready.");
                }
                else if (isNewInstance){
                    done_init = false;
                    System.out.println("[AS] Instance: " + instance.getInstanceId() + " " + instance.getState().getName() + ".");
                }
            }
        }
        System.out.println("[AS] Done.");
    }

    public void terminateInstance(String instanceId) {
        System.out.println("[AS] Terminating instance.");
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        Manager.ec2.terminateInstances(termInstanceReq);
    }

    public double getSystemPercentLoad() {
        double totalLoad = Manager.getSystemTotalLoad();
        double maxLoad = Manager.MAX_CAPACITY * Manager.getNumberOfInstances();
        return totalLoad / maxLoad;
    }

    public int calculateAmountOfNeededInstances() {
        double totalLoad = Manager.getSystemTotalLoad();
        double maxLoad = Manager.MAX_CAPACITY * Manager.getNumberOfInstances();
        return (int) Math.ceil((totalLoad/Manager.MAX_SYSTEM_LOAD) - maxLoad);
    }

    public void performSystemHealthCheck() throws InterruptedException {
        for(RunningInstance i : Manager.getRunningInstances()) {
            // Terminate ill instances - PRIORITY
            if(i.getHealthState().equals("unhealthy")) {
                terminateInstance(i.getId());
            }
            // Terminate unnecessary instances
            else if(i.isUnnecessary()) {
                terminateInstance(i.getId());
            }
        }

        // CAREFUL - Terminating ill instances on the loop above
        // may lead to an apparent increase on system load!
        // Never let system get too much load
        double systemLoad = getSystemPercentLoad();
        if(systemLoad > Manager.MAX_SYSTEM_LOAD) {
            int amount = calculateAmountOfNeededInstances();
            createInstances(amount);
        }
    }
}
