package evolution.replay;

import evolution.game.*;
import evolution.simulation.*;
import evolution.world.CellPosition;
import java.io.*;
import java.util.*;

/** Explicit versioned values; no Java object deserialization or dependency on a PRNG. */
final class SnapshotCodec {
    private SnapshotCodec() {}

    static void write(DataOutput d, SimulationSnapshot s) throws IOException {
        d.writeInt(s.sideLength()); d.writeLong(s.seed()); d.writeInt(s.generation());
        d.writeInt(s.tick()); d.writeLong(s.elapsedTicks()); d.writeUTF(s.status());
        d.writeInt(s.agents().size());
        for (var a : s.agents()) {
            d.writeLong(a.agentId()); position(d, a.position()); d.writeInt(a.energyHundredths());
            var g=a.genotype();
            d.writeUTF(""+g.firstMove()+g.afterCC()+g.afterCD()+g.afterDC()+g.afterDD());
        }
        d.writeInt(s.resources().size());
        for (var p : s.resources()) position(d,p);
        var m=s.metrics(); var g=m.generationMetrics();
        d.writeInt(m.livingAgents()); d.writeDouble(m.averageEnergyHundredths());
        d.writeInt(m.resourceCount()); d.writeLong(m.resourceEnergyHundredths()); d.writeLong(m.gamePayoff());
        d.writeInt(g.generation()); d.writeInt(g.livingAgents()); d.writeDouble(g.averageEnergyHundredths());
        d.writeLong(g.resourceEnergyHundredths()); d.writeLong(g.gamePayoff());
        d.writeInt(g.genotypeCounts().size());
        for (var entry : new TreeMap<>(g.genotypeCounts()).entrySet()) {d.writeUTF(entry.getKey()); d.writeInt(entry.getValue());}
        d.writeInt(g.ticks()); d.writeDouble(g.survivalFraction()); d.writeDouble(g.maximumEnergyHundredths());
        games(d,g.gameStatistics()); d.writeDouble(g.averageResourceEnergyHundredths());
        d.writeDouble(g.averageGamePayoff()); d.writeLong(g.gameEnergyHundredths());
        d.writeDouble(g.spatialClustering()); d.writeBoolean(g.extinct()); d.writeLong(g.agentTicks());
        d.writeLong(g.afterCCUses()); d.writeLong(g.afterCDUses()); d.writeLong(g.afterDCUses()); d.writeLong(g.afterDDUses());
        var t=m.lastTick(); d.writeLong(t.resourceEnergyHundredths()); d.writeLong(t.gamePayoff());
        games(d,t.gameStatistics()); d.writeInt(t.deaths());
    }

    static SimulationSnapshot read(DataInput d) throws IOException {
        int side=d.readInt(); long seed=d.readLong(); int generation=d.readInt(), tick=d.readInt();
        long elapsed=d.readLong(); String status=d.readUTF();
        if (side<3 || !Set.of("running","completed","extinct").contains(status)) throw new IOException("Invalid snapshot header");
        // ReplayFile already bounds the decoded frame size. Counts here are limited by the grid.
        int cells=(int)Math.min(Integer.MAX_VALUE,(long)side*side);
        int count=count(d,cells); var agents=new ArrayList<SimulationSnapshot.AgentState>();
        for(int i=0;i<count;i++) {
            long id=d.readLong(); var p=position(d,side); int energy=d.readInt();
            agents.add(new SimulationSnapshot.AgentState(id,p,energy,genotype(d.readUTF())));
        }
        count=count(d,cells); var resources=new ArrayList<CellPosition>();
        for(int i=0;i<count;i++) resources.add(position(d,side));
        int alive=d.readInt(); double average=d.readDouble(); int resourceCount=d.readInt();
        long resourceEnergy=d.readLong(), payoff=d.readLong();
        int gen=d.readInt(), living=d.readInt(); double genAverage=d.readDouble();
        long genResources=d.readLong(), genPayoff=d.readLong();
        count=count(d,32); var counts=new LinkedHashMap<String,Integer>();
        for(int i=0;i<count;i++) {String key=d.readUTF(); genotype(key); counts.put(key,d.readInt());}
        var g=new GenerationMetrics(gen,living,genAverage,genResources,genPayoff,counts,
                d.readInt(),d.readDouble(),d.readDouble(),games(d),d.readDouble(),d.readDouble(),
                d.readLong(),d.readDouble(),d.readBoolean(),d.readLong(),d.readLong(),d.readLong(),d.readLong(),d.readLong());
        var t=new SimulationSnapshot.TickMetrics(d.readLong(),d.readLong(),games(d),d.readInt());
        return new SimulationSnapshot(side,seed,generation,tick,elapsed,status,agents,resources,
                new SimulationSnapshot.Metrics(alive,average,resourceCount,resourceEnergy,payoff,g,t));
    }
    private static int count(DataInput d,int max) throws IOException {
        int n=d.readInt(); if(n<0 || n>max) throw new IOException("Invalid snapshot collection length"); return n;
    }
    private static Genotype genotype(String s) throws IOException {
        if(!s.matches("[CD]{5}")) throw new IOException("Invalid genotype");
        return new Genotype(action(s,0),action(s,1),action(s,2),action(s,3),action(s,4));
    }
    private static Action action(String s,int i) {return s.charAt(i)=='C'?Action.C:Action.D;}
    private static void position(DataOutput d,CellPosition p) throws IOException {d.writeInt(p.row());d.writeInt(p.column());}
    private static CellPosition position(DataInput d,int side) throws IOException {
        int r=d.readInt(),c=d.readInt(); if(r<0 || c<0 || r>=side || c>=side) throw new IOException("Invalid position");
        return new CellPosition(r,c);
    }
    private static void games(DataOutput d,GameStatistics g) throws IOException {
        d.writeLong(g.matches());d.writeLong(g.firstRoundCooperativeActions());
        d.writeLong(g.cc());d.writeLong(g.cd());d.writeLong(g.dc());d.writeLong(g.dd());
    }
    private static GameStatistics games(DataInput d) throws IOException {
        return new GameStatistics(d.readLong(),d.readLong(),d.readLong(),d.readLong(),d.readLong(),d.readLong());
    }
}
