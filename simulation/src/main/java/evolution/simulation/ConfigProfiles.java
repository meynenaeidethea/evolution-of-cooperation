package evolution.simulation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/** Strict, complete external profiles: energy is integer hundredths. */
public final class ConfigProfiles {
    private ConfigProfiles() {}
    public static SimulationConfig load(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) { return read(reader); }
    }
    public static SimulationConfig read(java.io.Reader reader) throws IOException {
        var p = new Properties();
        p.load(reader);
        var keys = Set.of("sideLength", "populationSize", "ticksPerGeneration", "roundsPerMatch",
                "initialEnergyHundredths", "resourceEnergyHundredths", "livingCostHundredths",
                "resourceSpawnProbability", "gamePayoffWeightHundredths", "crossoverProbability",
                "mutationProbability", "tournamentSize", "maximumGenerations");
        if (!p.stringPropertyNames().equals(keys)) throw new IllegalArgumentException("Profile must contain exactly: " + keys);
        return new SimulationConfig(i(p,"sideLength"), i(p,"populationSize"), i(p,"ticksPerGeneration"),
                i(p,"roundsPerMatch"), i(p,"initialEnergyHundredths"), i(p,"resourceEnergyHundredths"),
                i(p,"livingCostHundredths"), d(p,"resourceSpawnProbability"), i(p,"gamePayoffWeightHundredths"),
                d(p,"crossoverProbability"), d(p,"mutationProbability"), i(p,"tournamentSize"), i(p,"maximumGenerations"));
    }
    public static String format(SimulationConfig c) {
        return "sideLength="+c.sideLength()+"\npopulationSize="+c.populationSize()
                +"\nticksPerGeneration="+c.ticksPerGeneration()+"\nroundsPerMatch="+c.roundsPerMatch()
                +"\ninitialEnergyHundredths="+c.initialEnergyHundredths()+"\nresourceEnergyHundredths="+c.resourceEnergyHundredths()
                +"\nlivingCostHundredths="+c.livingCostHundredths()+"\nresourceSpawnProbability="+c.resourceSpawnProbability()
                +"\ngamePayoffWeightHundredths="+c.gamePayoffWeightHundredths()+"\ncrossoverProbability="+c.crossoverProbability()
                +"\nmutationProbability="+c.mutationProbability()+"\ntournamentSize="+c.tournamentSize()
                +"\nmaximumGenerations="+c.maximumGenerations()+"\n";
    }
    private static int i(Properties p,String key) { return Integer.parseInt(p.getProperty(key)); }
    private static double d(Properties p,String key) { return Double.parseDouble(p.getProperty(key)); }
}
