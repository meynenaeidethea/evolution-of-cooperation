#!/usr/bin/env python3
"""Post-protocol exploratory diagnostic from saved data; no new model runs."""
import argparse,csv,json
from collections import defaultdict
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
a=p.parse_args();counts=defaultdict(int)
with (a.input/'genotype-frequencies.csv').open() as h:
    for r in csv.DictReader(h):
        if r['scenario_id'] in ['baseline','mutation-zero'] and r['genotype'][0]=='C':counts[r['scenario_id'],int(r['seed']),int(r['generation'])]+=int(r['count'])
result={}
for s in ['baseline','mutation-zero']:
    seeds=sorted({seed for ss,seed,g in counts if ss==s});horizon=max(g for ss,seed,g in counts if ss==s)
    lost={seed:next((g for g in range(1,horizon+1) if counts[s,seed,g]==0),None) for seed in seeds}
    result[s]=dict(n=len(seeds),ever_lost_first_C=sum(g is not None for g in lost.values()),
        absent_at_horizon=sum(counts[s,seed,horizon]==0 for seed in seeds),first_loss_generation=lost)
a.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
