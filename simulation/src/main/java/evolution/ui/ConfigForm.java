package evolution.ui;

import evolution.simulation.SimulationConfig;
import java.math.BigDecimal;
import java.util.*;

/** Values shown to a person: energy units and percentages, never internal hundredths. */
final class ConfigForm {
    record Field(String key,String label,String hint) {}
    static final List<Field> FIELDS=List.of(
            new Field("side","Сторона мира, клеток","Целое число ≥ 3. Мир замкнут по краям."),
            new Field("population","Агентов в поколении","От 1 до числа клеток мира."),
            new Field("ticks","Тактов в поколении","Целое число ≥ 1."),
            new Field("generations","Поколений","Целое число ≥ 1."),
            new Field("rounds","Раундов во встрече","Целое число ≥ 1. Память действует внутри встречи."),
            new Field("initial","Начальная энергия, ед.","Положительное число с точностью до 0,01."),
            new Field("resource","Энергия ресурса, ед.","Неотрицательное число с точностью до 0,01."),
            new Field("cost","Стоимость такта, ед.","Неотрицательное число с точностью до 0,01."),
            new Field("weight","Энергия за балл игры, ед.","Неотрицательное число с точностью до 0,01."),
            new Field("spawn","Появление ресурса, % за такт","От 0 до 100 на каждой клетке без ресурса."),
            new Field("crossover","Скрещивание, %","От 0 до 100 на потомка."),
            new Field("mutation","Мутация, % на ген","От 0 до 100, независимо для каждого гена."),
            new Field("tournament","Размер турнира","Целое число ≥ 1. Число кандидатов при выборе родителя."));
    static Map<String,String> values(SimulationConfig c) {
        var m=new LinkedHashMap<String,String>();
        m.put("side",""+c.sideLength());m.put("population",""+c.populationSize());m.put("ticks",""+c.ticksPerGeneration());
        m.put("generations",""+c.maximumGenerations());m.put("rounds",""+c.roundsPerMatch());
        m.put("initial",units(c.initialEnergyHundredths()));m.put("resource",units(c.resourceEnergyHundredths()));
        m.put("cost",units(c.livingCostHundredths()));m.put("weight",units(c.gamePayoffWeightHundredths()));
        m.put("spawn",percent(c.resourceSpawnProbability()));m.put("crossover",percent(c.crossoverProbability()));
        m.put("mutation",percent(c.mutationProbability()));m.put("tournament",""+c.tournamentSize());return m;
    }
    static SimulationConfig parse(Map<String,String> m) {
        int side=positive(m,"side",3),population=positive(m,"population",1),ticks=positive(m,"ticks",1),
                rounds=positive(m,"rounds",1),generations=positive(m,"generations",1),tournament=positive(m,"tournament",1);
        if(population>(long)side*side) throw error("population","не может превышать число клеток мира");
        int initial=energy(m,"initial"),resource=energy(m,"resource"),cost=energy(m,"cost"),weight=energy(m,"weight");
        if(initial<=0) throw error("initial","должна быть больше нуля");
        for(var key:List.of("resource","cost","weight")) if(energy(m,key)<0) throw error(key,"не может быть отрицательной");
        try {
            return new SimulationConfig(side,population,ticks,rounds,initial,resource,cost,probability(m,"spawn"),weight,
                    probability(m,"crossover"),probability(m,"mutation"),tournament,generations);
        } catch(IllegalArgumentException e) {
            if(e.getMessage().startsWith("Energy")) throw new IllegalArgumentException("Энергия может переполнить допустимый диапазон: уменьшите длительность поколения или поступление энергии.");
            throw e;
        }
    }
    private static int positive(Map<String,String> m,String key,int minimum) {
        int n=integer(m,key);if(n<minimum) throw error(key,"нужно целое число ≥ "+minimum);return n;
    }
    private static BigDecimal number(Map<String,String> m,String key) {
        try {return new BigDecimal(m.get(key).strip().replace(',','.'));}
        catch(Exception e) {throw error(key,"введите число");}
    }
    private static int integer(Map<String,String> m,String key) {
        try {return number(m,key).intValueExact();} catch(ArithmeticException e) {throw error(key,"нужно целое число в диапазоне int");}
    }
    private static int energy(Map<String,String> m,String key) {
        try {return number(m,key).movePointRight(2).intValueExact();}
        catch(ArithmeticException e) {throw error(key,"точность до 0,01 и значение в допустимом диапазоне");}
    }
    private static double probability(Map<String,String> m,String key) {
        var n=number(m,key);
        if(n.signum()<0 || n.compareTo(BigDecimal.valueOf(100))>0) throw error(key,"допустимо от 0 до 100 %");
        return n.movePointLeft(2).doubleValue();
    }
    private static IllegalArgumentException error(String key,String message) {
        return new IllegalArgumentException(FIELDS.stream().filter(f->f.key().equals(key)).findFirst().orElseThrow().label()+": "+message);
    }
    private static String units(int n) {return BigDecimal.valueOf(n,2).stripTrailingZeros().toPlainString();}
    private static String percent(double n) {return BigDecimal.valueOf(n).movePointRight(2).stripTrailingZeros().toPlainString();}
}
