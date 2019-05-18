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
    static double MAX_METRIC_VALUE = 4000000;

    public Request(String algorithm, long area, double distance) {
        this.algorithm = algorithm;
        this.area = area;

        // refers to the distance from the starting point to the most
        // distant point in the given map (NOT THE OBJECTIVE POINT)
        this.distance = distance;
    }

    public static double requestCostEstimation(Request r) {

        /*
         * Temos de pensar da seguinte forma:
         *      - Max load de uma VM é 1 que é equivalente ao custo maximo
         *        de um request.
         *      - Entao significa que o request mais pesado (custo = 1) requer
         *        uma VM só para ele!
         */

        ScanResult result = DynamoDB.getItems(r.algorithm, r.area, r.distance);
        double similarMetric = 0;
        double cost;

        for (Map<String, AttributeValue> item : result.getItems()){
            similarMetric += Double.parseDouble(item.get("metricResult").getN());
        }

        if(result.getItems().size() != 0) {
            similarMetric /= result.getItems().size();
            cost = similarMetric / MAX_METRIC_VALUE;
        }
        else {
            cost = 0.5;
        }

        r.cost = cost;
        System.out.println("[REQUEST] " + r.toString() + " - COST: " + cost);
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
                   && other.distance == this.distance
                   && other.cost == this.cost;
        }
    }

    @Override
    public String toString() {
        return algorithm + "|" + area + "|" + distance;
    }
}
