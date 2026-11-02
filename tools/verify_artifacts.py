#!/usr/bin/env python3
"""Cross-check independent analyzers, replay/parallel execution, and checksums."""
import argparse,csv,hashlib,json
from pathlib import Path

def read(path):
    with path.open(newline='') as f:return list(csv.DictReader(f))

def checksums(directory):
    count=0
    for line in (directory/'SHA256SUMS').read_text().splitlines():
        digest,filename=line.split('  ',1);p=directory/filename
        if hashlib.sha256(p.read_bytes()).hexdigest()!=digest:raise ValueError('Checksum mismatch: '+str(p))
        count+=1
    return count

def verify(root):
    a=root/'results/main-report-20261020';b=root/'results/java-report-20261020'
    py={(r['scenario'],r['metric']):r for r in read(a/'summary.csv')}
    discrepancies=[]
    by_metric={}
    for r in read(b/'summary.csv'):
        if r['metric'] not in ['cooperation','energy','survival']:continue
        rr=py[r['scenario'],r['metric']]
        if abs(float(rr['mean'])-float(r['mean']))>1e-12:raise ValueError('Analyzer means differ')
        ds=[abs(float(rr[k])-float(r[j])) for k,j in [('low','ci_low'),('high','ci_high')]]
        discrepancies.extend(ds);by_metric[r['metric']]=max(by_metric.get(r['metric'],0),*ds)
    pairs={(r['scenario'],r['metric']):r for r in read(a/'effects.csv') if r['reference']=='baseline'}
    for r in read(b/'paired-effects.csv'):
        rr=pairs[r['scenario'],r['metric']]
        if abs(float(rr['mean'])-float(r['mean']))>1e-12 or rr['n']!=r['n']:raise ValueError('Paired means/n differ')
    parallel=root/'results/ci-local-20261020';sequential=root/'results/ci-local-sequential-20261020'
    for filename in ['runs.csv','generations.csv','genotype-frequencies.csv']:
        if (parallel/filename).read_bytes()!=(sequential/filename).read_bytes():raise ValueError('Parallel CSV differs: '+filename)
    replay=root/'results/replay-main-20261020'
    replay_ok=None
    if replay.exists():
        for filename in ['runs.csv','generations.csv','genotype-frequencies.csv']:
            with (root/'results/main-20261020'/filename).open(newline='') as h:
                expected=[r for r in csv.DictReader(h) if r['scenario_id']=='baseline' and int(r['seed']) in [10001,10002]]
            if expected!=read(replay/filename):raise ValueError('Final source replay differs: '+filename)
        replay_ok=True
    out=dict(final_source_replays_first_two_main_seeds=replay_ok,primary_checksums_verified=checksums(root/'results/main-20261020'),
             pilot_checksums_verified=checksums(root/'results/pilot-20261020'),
             java_python_means_agree=True,java_python_paired_means_n_agree=True,
             largest_95ci_boundary_difference=max(discrepancies),largest_95ci_boundary_difference_by_metric=by_metric,parallel_sequential_csv_identical=True)
    print(json.dumps(out,indent=2));return out

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--root',type=Path,default=Path('.'));p.add_argument('--output',type=Path)
    args=p.parse_args();data=verify(args.root)
    if args.output:args.output.write_text(json.dumps(data,indent=2)+'\n')
