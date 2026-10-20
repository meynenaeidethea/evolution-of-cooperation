package evolution.simulation;

import java.util.LinkedHashMap;
import java.util.Map;

/** One-factor comparisons; baseline values occur only once. */
public final class ExperimentScenarios {
    private ExperimentScenarios() {}
    public static Map<String, SimulationConfig> all(int generations) {
        var b = SimulationConfig.baseline();
        Map<String, SimulationConfig> scenarios = new LinkedHashMap<>();
        scenarios.put("baseline", config(b, 5, 20, .01, .1, 400, PairingMode.LOCAL, generations));
        for (int k : new int[]{1, 3, 10}) scenarios.put("rounds-" + k, config(b,k,20,.01,.1,400,PairingMode.LOCAL,generations));
        for (int a : new int[]{0, 10, 35, 50}) scenarios.put("weight-" + a, config(b,5,a,.01,.1,400,PairingMode.LOCAL,generations));
        scenarios.put("global", config(b,5,20,.01,.1,400,PairingMode.GLOBAL,generations));
        for (double m : new double[]{0, .005, .02, .05}) scenarios.put("mutation-" + m, config(b,5,20,m,.1,400,PairingMode.LOCAL,generations));
        for (double q : new double[]{.02, .05, .25}) scenarios.put("spawn-" + q, config(b,5,20,.01,q,400,PairingMode.LOCAL,generations));
        for (int r : new int[]{200, 800}) scenarios.put("resource-" + r, config(b,5,20,.01,.1,r,PairingMode.LOCAL,generations));
        return java.util.Collections.unmodifiableMap(scenarios);
    }
    private static SimulationConfig config(SimulationConfig b, int k, int a, double m, double q, int r, PairingMode mode, int g) {
        return b.with(k, a, m, q, r, mode, g);
    }
}
