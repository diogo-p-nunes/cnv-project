package manager;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import database.DynamoDB;

import java.util.Map;

public class Request {
    public String algorithm;
    public long area;
    public double distance;
    public double cost;

    public Request(String algorithm, long area, double distance) {
        this.algorithm = algorithm;
        this.area = area;

        // refers to the distance from the starting point to the most
        // distant point in the given map (NOT THE OBJECTIVE POINT)
        this.distance = distance;
    }

    public static double requestCostEstimation(Request r) {

        ScanResult result = DynamoDB.getItems(r.algorithm, r.area, r.distance);
        double similarMetric = 0;
        double cost;

        for (Map<String, AttributeValue> item : result.getItems()){
            similarMetric += Double.parseDouble(item.get("metricResult").getN());
        }

        if(result.getItems().size() != 0) {
            similarMetric /= result.getItems().size();
            cost = similarMetric / Manager.MAX_METRIC_VALUE;
        }
        else {
            cost = 0.5;
        }

        if(cost > 1) {
            System.out.println("[REQUEST] Updated max metric.");
            Manager.MAX_METRIC_VALUE = similarMetric / cost;
            cost = 1;
        }

        r.cost = cost;
        //System.out.println("[REQUEST] " + r.toString() + " - COST: " + cost);
        return cost;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        else {
            Request other = (Request) obj;
            return other.algorithm.equals(this.algorithm)
                   && other.area == this.area
                   && other.distance == this.distance;
        }
    }

    @Override
    public String toString() {
        return algorithm + "|" + area + "|" + distance;
    }
}
