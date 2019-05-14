package manager;

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

    public static double requestCostEstimation(Request request) {
        //TODO: Estimation of the cost of a given request
        // must be based on the metrics extracted - DYNAMO
        double cost = 0.3;
        request.cost = cost;

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
}