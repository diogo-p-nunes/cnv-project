package manager;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.*;

import java.util.*;

@SuppressWarnings("Duplicates")
public class AutoScaler {
    private int INSTANCE_STARTUP_TIME = 60; //in seconds - grace period
    private boolean performingUrgentScaleUp = false;

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
                    if(instance.getPublicIpAddress().equals(Manager.getLoadBalancerIp())) continue;
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

    public List<String> createInstances(int amount) throws InterruptedException {
        List<String> newIps = new ArrayList<>();
        if(amount == 0) return new ArrayList<>();
        System.out.println("[AS] Starting " + amount + " new instance(s).");

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        runInstancesRequest.withImageId("ami-05ef107833e56a8e4")
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
                    newIps.add(instance.getPublicIpAddress());
                    System.out.println("[AS] Instance: " + instance.getInstanceId() + " is ready.");
                }
                else if (isNewInstance){
                    done_init = false;
                    System.out.println("[AS] Instance: " + instance.getInstanceId() + " " + instance.getState().getName() + ".");
                }
            }
        }
        performingUrgentScaleUp = false;
        System.out.println("[AS] Done.");
        return newIps;
    }

    public void terminateInstance(String instanceId) {
        System.out.println("[AS] Terminating instance " + instanceId);
        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        Manager.ec2.terminateInstances(termInstanceReq);
    }

    public double getSystemLoad() {
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
        System.out.println("[AS] Performing System Health Check.");
        boolean allGood = true;

        // Terminate ill instances - PRIORITY
        for(RunningInstance i : Manager.getRunningInstances()) {
            if(i.getHealthState().equals("unhealthy")) {
                System.out.println("[AS] Instance " + i.getId() + " is rendered unhealthy - terminating it.");
                Manager.removeWS(i.getIp());
                terminateInstance(i.getId());
                allGood = false;
            }
        }

        if(!performingUrgentScaleUp) {

            // CAREFUL - Terminating ill instances on the loop above
            // may lead to an apparent increase on system load!
            // Never let system get too much load
            // SCALE UP
            double systemLoad = getSystemLoad();
            if (systemLoad > Manager.MAX_SYSTEM_LOAD) {
                System.out.println("[AS] Scaling up due to MAX_SYSTEM_LOAD breach.");
                int amount = calculateAmountOfNeededInstances();
                createInstances(amount);
                allGood = false;
            }

            // Terminate unnecessary instances - SCALE DOWN
            // We want to maintain a minimum number of instances even if not being used
            // We dont want to scale down if scaling down would make sys_load > MAX
            for (RunningInstance i : Manager.getRunningInstances()) {
                if (Manager.getNumberOfInstances() > Manager.MIN_INSTANCES) {
                    // Load of the system if we were to terminate this supposedly unnecessary instance
                    double causedLoad = Manager.getSystemTotalLoad() / (Manager.getSystemMaxCapacity() - Manager.MAX_CAPACITY);

                    if (i.isUnnecessary() && causedLoad <= (Manager.MAX_SYSTEM_LOAD - 0.10)) {
                        System.out.println("[AS] Scaling down due to un-necessity.");
                        Manager.removeWS(i.getIp());
                        terminateInstance(i.getId());
                        allGood = false;
                    }
                }
            }

            if (Manager.getNumberOfInstances() < Manager.MIN_INSTANCES) {
                System.out.println("[AS] Scaling up due to MIN_INSTANCES breach.");
                createInstances(Manager.MIN_INSTANCES - Manager.getNumberOfInstances());
            }

        }

        if(allGood) {
            System.out.println("[AS] Sys Health Check terminated - all was good.");
        }
        else {
            System.out.println("[AS] Sys Health Check terminated - repairments were done.");
        }
    }

    public String urgentInstanceLaunch() throws InterruptedException {
        performingUrgentScaleUp = true;
        return createInstances(1).get(0);
    }
}
