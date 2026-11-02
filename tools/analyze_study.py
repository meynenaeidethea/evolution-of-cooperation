#!/usr/bin/env python3
"""Validate schema-2 data, resample runs, and build a local research report.
No simulation or selection of scenarios is performed here.
"""
import argparse, csv, hashlib, html, json, math, platform, sys, os, colorsys
os.environ.setdefault("MPLCONFIGDIR", "/tmp/cooperation-matplotlib")
from collections import defaultdict
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

B = 20000
ANALYSIS_SEED = 20261020
GENOTYPES = [format(i, '05b').replace('0','C').replace('1','D') for i in range(32)]
INDEX = {g:i for i,g in enumerate(GENOTYPES)}
LABELS = {'baseline':'Базовый', 'rounds-1':'K=1, α=0.20', 'rounds-10':'K=10, α=0.20',
          'normalized-1':'K=1, α=1.00', 'normalized-10':'K=10, α=0.10',
          'game-zero':'Без игрового дохода', 'mutation-zero':'Мутация 0', 'mutation-high':'Мутация 0.05',
          'resource-poor':'Ресурсы q=0.02', 'resource-rich':'Ресурсы q=0.25',
          'global':'Глобальные', 'global-matched':'Глобальные с бюджетом'}
COLORS = dict(zip(LABELS, ['#1b4965','#b54b4b','#3f7e44','#dd8a24','#4488bb','#7d5b9b',
                         '#2d8075','#c36a9f','#8b641f','#5a923e','#795ca4','#d15f31']))
CONFIG = ['side_length','population_size','maximum_generations','ticks_per_generation','rounds_per_match',
          'initial_energy_hundredths','resource_energy_hundredths','living_cost_hundredths',
          'resource_spawn_probability','game_payoff_weight_hundredths','crossover_probability',
          'mutation_probability','tournament_size','pairing_mode','model_version','data_schema',
          'java_version','random_generator']

def require(ok, message):
    if not ok: raise ValueError(message)

def f(row, key):
    v = row[key]
    x = float(v) if v else math.nan
    require(not v or math.isfinite(x), f'Nonfinite {key}: {v}')
    return x

def rows(path):
    with path.open(newline='',encoding='utf-8') as h:
        yield from csv.DictReader(h)

def write_csv(path, data):
    data = list(data)
    if not data: return
    with path.open('w',newline='',encoding='utf-8') as h:
        w=csv.DictWriter(h,fieldnames=list(data[0]));w.writeheader()
        for r in data: w.writerow({k:('' if isinstance(v,(float,np.floating)) and not np.isfinite(v) else v) for k,v in r.items()})

def load(source, strict=False):
    run, gen, counts = {}, {}, {}
    by_s = defaultdict(list)
    for r in rows(source/'runs.csv'):
        s, seed = r['scenario_id'], int(r['seed']); key=(s,seed)
        require(key not in run,'Duplicate seed in scenario')
        require(r['status'] in ['completed','extinct'],'Invalid status')
        require(r['model_version']=='genetic-v2-controls' and r['data_schema']=='2','Incompatible model/schema')
        require(r['pairing_mode'] in ['local','global','global_matched'],'Invalid pairing')
        require(r['extinct']==str(r['status']=='extinct').lower(),'Extinction flag')
        for old in by_s[s][:1]:
            require(all(r[k]==old[k] for k in CONFIG),'Within-scenario config mismatch')
        by_s[s].append(r); run[key]=r
    require(bool(run),'No runs')
    identities={(r['scenario_id'],r['repetition']) for r in run.values()}
    require(len(identities)==len(run),'Duplicate repetition')
    horizons={int(r['maximum_generations']) for r in run.values()}
    versions={(r['model_version'],r['data_schema'],r['random_generator'],r['java_version']) for r in run.values()}
    require(len(horizons)==1 and len(versions)==1,'Incompatible horizon/environment')
    horizon=horizons.pop()
    if strict:
        require(set(by_s)==set(LABELS),'Main scenarios differ from protocol')
        require(horizon==60,'Main horizon differs')
        for s, rr in by_s.items():
            require({int(r['seed']) for r in rr}==set(range(10001,10041)),f'Main seeds differ: {s}')
            expected=dict(side_length=20,population_size=160,ticks_per_generation=50,rounds_per_match=5,
                initial_energy_hundredths=1000,resource_energy_hundredths=400,living_cost_hundredths=100,
                resource_spawn_probability=.05,game_payoff_weight_hundredths=20,crossover_probability=.8,
                mutation_probability=.01,tournament_size=3)
            if s in ['rounds-1','normalized-1']:expected['rounds_per_match']=1
            if s in ['rounds-10','normalized-10']:expected['rounds_per_match']=10
            if s=='normalized-1':expected['game_payoff_weight_hundredths']=100
            if s=='normalized-10':expected['game_payoff_weight_hundredths']=10
            if s=='game-zero':expected['game_payoff_weight_hundredths']=0
            if s=='mutation-zero':expected['mutation_probability']=0
            if s=='mutation-high':expected['mutation_probability']=.05
            if s=='resource-poor':expected['resource_spawn_probability']=.02
            if s=='resource-rich':expected['resource_spawn_probability']=.25
            require(all(float(rr[0][k])==v for k,v in expected.items()),'Main parameters differ: '+s)
            require(rr[0]['pairing_mode']==({'global':'global','global-matched':'global_matched'}.get(s,'local')),'Main pairing differs')
    for r in rows(source/'generations.csv'):
        key=(r['scenario_id'],int(r['seed'])); g=int(r['generation']); full=(*key,g)
        require(key in run and full not in gen,'Generation link/duplicate')
        m=run[key]; n=int(m['population_size']); t=int(r['ticks']); alive=int(r['living_agents'])
        require(r['repetition']==m['repetition'],'Generation repetition link')
        require(0<=alive<=n and 1<=t<=int(m['ticks_per_generation']),'Population/ticks bounds')
        require(alive==0 or t==int(m['ticks_per_generation']),'Truncated surviving generation')
        mat=int(r['matches']); k=int(m['rounds_per_match']); total=int(r['played_rounds'])
        cc,cd,dc,dd=(int(r[x+'_count']) for x in ['cc','cd','dc','dd'])
        require(min(cc,cd,dc,dd,mat)>=0 and cc+cd+dc+dd==total==mat*k,'Outcome sum/rounds')
        require(int(r['played_actions'])==2*total and int(r['first_round_actions'])==2*mat,'Action sum')
        coop=int(r['cooperative_actions']); first=int(r['first_round_cooperative_actions'])
        require(coop==2*cc+cd+dc and 0<=first<=2*mat,'Cooperation sum')
        for field,num,den in [('cooperation_rate',coop,2*total),('first_round_cooperation_rate',first,2*mat),
                              ('cc_fraction',cc,total),('cd_fraction',cd,total),('dc_fraction',dc,total),('dd_fraction',dd,total)]:
            require((math.isnan(f(r,field)) if den==0 else abs(f(r,field)-num/den)<1e-10),'Invalid rate '+field)
        payoff=6*cc+5*(cd+dc)+2*dd
        require(int(r['game_payoff'])==payoff and int(r['game_energy_hundredths'])==payoff*int(m['game_payoff_weight_hundredths']),'Payoff/energy sum')
        require(int(r['resource_energy_hundredths'])>=0,'Resource energy')
        require(abs(f(r,'survival_fraction')-alive/n)<1e-10,'Survival fraction')
        require(r['extinct']==str(alive==0).lower(),'Generation extinction flag')
        energy=f(r,'average_energy_hundredths'); maximum=f(r,'maximum_energy_hundredths')
        require((math.isnan(energy) and math.isnan(maximum)) if alive==0 else 0<energy<=maximum,'Energy bounds')
        at=int(r['agent_ticks']); require(2*mat<=at<=n*t and at>=alive*t,'Agent-ticks bounds')
        require(abs(f(r,'participation_rate')-2*mat/at)<1e-10,'Participation')
        require(abs(f(r,'matches_per_initial_agent_tick')-mat/(n*t))<1e-10,'Match frequency')
        require(sum(int(r['after_'+x+'_uses']) for x in ['cc','cd','dc','dd'])==2*mat*(k-1),'Reaction-use sum')
        require(all(int(r['after_'+x+'_uses'])>=0 for x in ['cc','cd','dc','dd']),'Negative uses')
        require(abs(f(r,'average_resource_energy_hundredths')-int(r['resource_energy_hundredths'])/n)<1e-10,'Resource mean')
        require(abs(f(r,'average_game_payoff')-payoff/n)<1e-10,'Game mean')
        cluster=f(r,'spatial_clustering');require(math.isnan(cluster) or 0<=cluster<=1,'Clustering bounds')
        gen[full]=r;counts[full]=np.full(32,-1,dtype=int)
    for r in rows(source/'genotype-frequencies.csv'):
        full=(r['scenario_id'],int(r['seed']),int(r['generation']));g=r['genotype']
        require(full in gen and g in INDEX,'Genotype link/format')
        require(r['repetition']==run[full[:2]]['repetition'],'Genotype repetition link')
        idx=INDEX[g];c=int(r['count']);alive=int(gen[full]['living_agents'])
        require(counts[full][idx]==-1 and 0<=c<=alive,'Genotype duplicate/count')
        freq=f(r,'frequency')
        require(math.isnan(freq) if alive==0 else abs(freq-c/alive)<1e-10,'Genotype frequency')
        counts[full][idx]=c
    for key,m in run.items():
        recorded=int(m['completed_generations']); actual=sorted(g for s,seed,g in gen if (s,seed)==key)
        require(actual==list(range(1,recorded+1)) and 1<=recorded<=horizon,'Generation completeness')
        for g in actual:
            full=(*key,g);cs=counts[full];alive=int(gen[full]['living_agents'])
            require(np.all(cs>=0) and cs.sum()==alive,'32 genotypes/sum')
            require(g==recorded or alive>0,'Continuation after extinction')
        alive=int(gen[(*key,recorded)]['living_agents'])
        require((alive>0 and recorded==horizon and m['extinction_generation']=='') if m['status']=='completed'
                else (alive==0 and int(m['extinction_generation'])==recorded),'Status/horizon')
    return run,gen,counts,by_s,horizon

_indices={}
def boot(values, alpha=.05):
    a=np.asarray(values,dtype=float);a=a[np.isfinite(a)];n=len(a)
    if not n:return (0,math.nan,math.nan,math.nan)
    if n not in _indices:
        _indices[n]=np.random.default_rng(ANALYSIS_SEED+n).integers(0,n,(B,n))
    means=a[_indices[n]].mean(axis=1)
    lo,hi=np.quantile(means,[alpha/2,1-alpha/2])
    return (n,float(a.mean()),float(lo),float(hi))

def finite_mean(values):
    a=np.asarray(values,dtype=float);a=a[np.isfinite(a)]
    return float(a.mean()) if len(a) else math.nan

def wilson(events,n):
    z=1.959963984540054;p=events/n;d=1+z*z/n
    center=(p+z*z/(2*n))/d;half=z*math.sqrt(p*(1-p)/n+z*z/(4*n*n))/d
    return p,(0. if events==0 else max(0,center-half)),(1. if events==n else min(1,center+half))

METRICS=['cooperation','conditional','survival','energy','maximum_energy','first_cooperation','entropy',
         'tft','wsls','nonconstant','participation','matches_per_tick','resource_per_agent','game_per_agent',
         'resource_per_agent_tick','game_per_agent_tick','clustering','clustering_excess']

def measures(r, cs, meta):
    n=int(meta['population_size']);alive=int(r['living_agents']);p=cs/alive if alive else np.full(32,np.nan)
    q=p[p>0];null=(float((cs*(cs-1)).sum())/(alive*(alive-1))) if alive>1 else math.nan
    return dict(cooperation=f(r,'cooperation_rate') if alive else math.nan,
        conditional=float(sum(p[i] for i,g in enumerate(GENOTYPES) if g.startswith('CCD'))),
        survival=alive/n,energy=f(r,'average_energy_hundredths')/100,
        maximum_energy=f(r,'maximum_energy_hundredths')/100,first_cooperation=f(r,'first_round_cooperation_rate') if alive else math.nan,
        entropy=-float(np.sum(q*np.log(q)))/math.log(32) if alive else math.nan,
        tft=p[INDEX['CCDCD']],wsls=p[INDEX['CCDDC']],
        nonconstant=sum(p[i] for i,g in enumerate(GENOTYPES) if len(set(g[1:]))>1),
        participation=f(r,'participation_rate'),matches_per_tick=f(r,'matches_per_initial_agent_tick'),
        resource_per_agent=int(r['resource_energy_hundredths'])/(100*n),
        game_per_agent=int(r['game_energy_hundredths'])/(100*n),
        resource_per_agent_tick=int(r['resource_energy_hundredths'])/(100*int(r['agent_ticks'])),
        game_per_agent_tick=int(r['game_energy_hundredths'])/(100*int(r['agent_ticks'])),
        clustering=f(r,'spatial_clustering'),clustering_excess=f(r,'spatial_clustering')-null)

def match(a,b,k):
    x,y=a[0],b[0];acts=[];uses=[0]*4;pa=pb=0
    for r in range(k):
        acts.append(x+y);pa+=3 if x+y=='CC' else 0 if x+y=='CD' else 5 if x+y=='DC' else 1
        pb+=3 if x+y=='CC' else 5 if x+y=='CD' else 0 if x+y=='DC' else 1
        if r<k-1:
            ia=2*(x=='D')+(y=='D');ib=2*(y=='D')+(x=='D');uses[ia]+=1
            x,y=a[1+ia],b[1+ib]
    return acts,pa,pb,uses

def catalogue(out):
    data=[];equiv=[]
    for k in [1,5,10]:
        signatures=defaultdict(list)
        for a in GENOTYPES:
            signatures[tuple(''.join(p[0] for p in match(a,b,k)[0]) for b in GENOTYPES)].append(a)
        for j,gs in enumerate(signatures.values()):
            for g in gs:equiv.append(dict(rounds=k,genotype=g,class_id=j,class_size=len(gs),members=' '.join(gs)))
    for a in GENOTYPES:
        games=[match(a,b,10) for b in GENOTYPES]
        data.append(dict(genotype=a,initial_C=int(a[0]=='C'),after_CC=a[1],after_CD=a[2],after_DC=a[3],after_DD=a[4],
            conditional=int(a.startswith('CCD')),nonconstant=int(len(set(a[1:]))>1),
            standard_C=sum(sum(p[0]=='C' for p in game[0]) for game in games)/320,
            standard_payoff=sum(game[1] for game in games)/320,
            standard_after_CC=sum(game[3][0] for game in games),standard_after_CD=sum(game[3][1] for game in games),
            standard_after_DC=sum(game[3][2] for game in games),standard_after_DD=sum(game[3][3] for game in games)))
    write_csv(out/'strategy-catalogue.csv',data);write_csv(out/'behavioral-equivalence.csv',equiv)
    write_csv(out/'standard-matches.csv',(dict(focal=a,opponent=b,rounds=10,sequence=' '.join(match(a,b,10)[0]),payoff=match(a,b,10)[1]) for a in GENOTYPES for b in GENOTYPES))

plt.rcParams.update({'font.family':'DejaVu Sans','font.size':10,'axes.spines.top':False,'axes.spines.right':False,
                     'axes.titleweight':'bold','figure.dpi':110,'savefig.dpi':190,'svg.fonttype':'none'})
def save(fig,out,name):
    for ext in ['png','svg','pdf']:fig.savefig(out/f'{name}.{ext}',bbox_inches='tight',metadata={'Creator':'Cooperation study'})
    plt.close(fig)

def genotype_color(g):
    i=int(g.replace('C','0').replace('D','1'),2)
    return colorsys.hsv_to_rgb((i*137.508%360)/360,.70,.85 if i%2==0 else .65)

def render_genotypes(figdir,horizon,tr,genotype_means,labels):
    fig,axs=plt.subplots(2,2,figsize=(13,8),layout='constrained')
    for ax,s in zip(axs.flat,['baseline','global-matched','mutation-zero','mutation-high']):
        if s not in tr:ax.axis('off');continue
        gg=[r for r in genotype_means if r['scenario']==s];last={r['genotype']:r['frequency'] for r in gg if r['generation']==horizon}
        top=sorted(GENOTYPES,key=lambda g:(-last.get(g,0),g))[:5]
        ys=[[next(r['frequency'] for r in gg if r['generation']==g and r['genotype']==gt) for g in range(1,horizon+1)] for gt in top]
        ys.append(1-np.sum(ys,axis=0));ax.stackplot(range(1,horizon+1),ys,labels=top+['Остальные'],colors=[genotype_color(g) for g in top]+['#c9d2db'],alpha=.95)
        ax.set(title=labels[s],xlabel='Поколение',ylabel='Средняя частота генотипов',ylim=(0,1));ax.legend(fontsize=8,loc='upper left',ncol=2)
    save(fig,figdir,'genotypes')

def analyze(source,out,strict):
    run,gen,counts,by_s,horizon=load(source,strict);out.mkdir(parents=True,exist_ok=False)
    (out/'figures').mkdir();figdir=out/'figures'
    scenarios=list(by_s);endpoints=[];values={};end={};survival=[];trajectory=[]
    labels={s:LABELS.get(s,s) for s in scenarios}
    for s in scenarios:
        if s.startswith(('rounds-', 'normalized-')):
            m=by_s[s][0]
            labels[s]=f"K={m['rounds_per_match']}, α={int(m['game_payoff_weight_hundredths'])/100:.2f}"
    family_size=22 if strict else max(1,2*sum(s!='baseline' for s in scenarios))
    scope=('Пилот отделён от основной серии. Первичные исходы: C и CCD?? в последнем поколении, '
           '22 сравнения с baseline.' if strict else
           f'Дополнительная или техническая серия. Сравнения описательные; поправка внутри отчёта '
           f'учитывает {family_size} сравнений двух конечных показателей с baseline. '
           'Это не общее подтверждающее семейство для всех исследуемых профилей.')
    for full,r in gen.items():values[full]=measures(r,counts[full],run[full[:2]])
    for s in scenarios:
        seeds=sorted(int(r['seed']) for r in by_s[s]);n=len(seeds)
        for seed in seeds:
            m=run[s,seed];recorded=int(m['completed_generations']);completed=m['status']=='completed'
            empty={x:math.nan for x in METRICS};empty['survival']=0
            v=values[s,seed,horizon].copy() if completed else empty
            end[s,seed]=v
            tail=[values[s,seed,g]['cooperation'] if (s,seed,g) in values else math.nan for g in range(max(1,horizon-9),horizon+1)]
            # Complete-case temporal summary; no replacement for undefined generations.
            tailmean=float(np.mean(tail)) if all(np.isfinite(tail)) else math.nan
            persistence=int(all(np.isfinite(tail)) and all(c>.5 for c in tail))
            endpoints.append(dict(scenario=s,seed=seed,completed=int(completed),extinction_generation='' if completed else recorded,
                extinction_tick='' if completed else sum(int(gen[s,seed,g]['ticks']) for g in range(1,recorded+1)),
                last10_cooperation=tailmean,last10_all_above_half=persistence,**v))
        events=sum(r['status']=='extinct' for r in by_s[s]);p,lo,hi=wilson(events,n)
        # Discrete restricted mean generations alive, administrative censoring at horizon.
        rmst=np.mean([int(r['completed_generations']) if r['status']=='extinct' else horizon for r in by_s[s]])
        survival.append(dict(scenario=s,n=n,extinctions=events,risk=p,low=lo,high=hi,rmst_generations=rmst))
        for g in range(1,horizon+1):
            record=dict(scenario=s,generation=g,n_runs=n,not_extinct=sum((s,seed,g) in gen and int(gen[s,seed,g]['living_agents'])>0 for seed in seeds)/n)
            for metric in ['cooperation','conditional','survival','energy','entropy','clustering_excess']:
                vv=[values[s,seed,g][metric] if (s,seed,g) in values else (0 if metric=='survival' else math.nan) for seed in seeds]
                nn,mean,low,high=boot(vv)
                record.update({metric+'_n':nn,metric:mean,metric+'_low':low,metric+'_high':high})
            trajectory.append(record)
    summary=[]
    for s in scenarios:
        for metric in METRICS+['last10_cooperation','last10_all_above_half']:
            vv=[e[metric] for e in endpoints if e['scenario']==s];n,mean,lo,hi=boot(vv)
            summary.append(dict(scenario=s,metric=metric,n=n,mean=mean,low=lo,high=hi))
    effects=[];comparisons=[]
    if 'baseline' in scenarios:
        comparisons=[(s,'baseline',m,'primary' if m in ['cooperation','conditional'] else 'secondary')
                     for s in scenarios if s!='baseline' for m in ['cooperation','conditional','survival']]
    # Comparisons among control arms are secondary, not added after seeing their signs.
    for a,b in [('global-matched','global'),('normalized-1','rounds-1'),('normalized-10','rounds-10')]:
        if a in scenarios and b in scenarios:comparisons.extend((a,b,m,'secondary-control') for m in ['cooperation','survival','conditional'])
    for s,ref,metric,family in comparisons:
        pairs=sorted({seed for ss,seed in end if ss==s}&{seed for ss,seed in end if ss==ref})
        diff=[end[s,seed][metric]-end[ref,seed][metric] for seed in pairs]
        n,mean,lo,hi=boot(diff);_,_,bl,bh=boot(diff,.05/family_size)
        effects.append(dict(scenario=s,reference=ref,metric=metric,family=family,seed_pairs=len(pairs),n=n,mean=mean,
            low=lo,high=hi,simultaneous_low=bl if family=='primary' else math.nan,simultaneous_high=bh if family=='primary' else math.nan))
    write_csv(out/'endpoints.csv',endpoints);write_csv(out/'summary.csv',summary);write_csv(out/'effects.csv',effects)
    write_csv(out/'trajectory.csv',trajectory);write_csv(out/'extinction.csv',survival)
    catalogue(out)
    # Retain the mean frequency of every genotype per scenario/generation; equal run weighting.
    genotype_means=[]
    for s in scenarios:
        seeds=sorted(int(r['seed']) for r in by_s[s])
        for g in range(1,horizon+1):
            ps=[counts[s,seed,g]/int(gen[s,seed,g]['living_agents']) for seed in seeds if (s,seed,g) in gen and int(gen[s,seed,g]['living_agents'])>0]
            mean=np.mean(ps,axis=0) if ps else np.full(32,np.nan)
            for j,gt in enumerate(GENOTYPES):genotype_means.append(dict(scenario=s,generation=g,genotype=gt,n=len(ps),frequency=mean[j]))
    write_csv(out/'genotype-means.csv',genotype_means)
    sm={(r['scenario'],r['metric']):r for r in summary}
    tr=defaultdict(list)
    for r in trajectory:tr[r['scenario']].append(r)
    groups=[['baseline','rounds-1','rounds-10'],['baseline','normalized-1','normalized-10'],
            ['baseline','global','global-matched'],['baseline','game-zero','resource-poor','resource-rich'],
            ['baseline','mutation-zero','mutation-high']]
    fig,axs=plt.subplots(2,3,figsize=(15,8),layout='constrained')
    for ax,group in zip(axs.flat,groups):
        for s in group:
            if s not in tr:continue
            rr=tr[s];x=[r['generation'] for r in rr]
            ax.plot(x,[r['cooperation'] for r in rr],label=labels[s],color=COLORS[s])
            ax.fill_between(x,[r['cooperation_low'] for r in rr],[r['cooperation_high'] for r in rr],color=COLORS[s],alpha=.15)
        ax.set(ylim=(0,1),xlabel='Поколение',ylabel='Доля действий C');ax.legend(fontsize=8,loc='best');ax.grid(alpha=.2)
    axs.flat[-1].axis('off');axs.flat[-1].text(0,1,'Средние по независимым запускам\nПоточечные bootstrap 95% CI\nЧисло наблюдений — в trajectory.csv\nПолосы не одновременные',va='top',linespacing=1.8)
    save(fig,figdir,'dynamics')
    fig,axs=plt.subplots(1,2,figsize=(14,6),layout='constrained')
    jitter=np.random.default_rng(17)
    for j,s in enumerate(scenarios):
        for ax,metric in zip(axs,['cooperation','conditional']):
            vv=np.array([e[metric] for e in endpoints if e['scenario']==s]);vv=vv[np.isfinite(vv)]
            ax.scatter(vv,j+jitter.uniform(-.18,.18,len(vv)),s=16,color=COLORS[s],alpha=.55)
            r=sm[s,metric]
            if np.isfinite(r['mean']):ax.errorbar(r['mean'],j,xerr=[[r['mean']-r['low']],[r['high']-r['mean']]],fmt='D',color='black',capsize=3,ms=4)
    for ax,title in zip(axs,['C во всех раундах','Условные генотипы CCD??']):
        ax.set(yticks=range(len(scenarios)),yticklabels=[labels.get(s,s) for s in scenarios],xlim=(-.03,1.03),title=title,xlabel='Конечная доля; точки — seed, ромб — среднее и 95% CI');ax.invert_yaxis();ax.grid(axis='x',alpha=.2)
    save(fig,figdir,'endpoints')
    fig,axs=plt.subplots(1,2,figsize=(14,6),layout='constrained')
    for ax,metric in zip(axs,['cooperation','conditional']):
        ee=[e for e in effects if e['family']=='primary' and e['metric']==metric]
        for j,e in enumerate(ee):
            ax.plot([e['simultaneous_low'],e['simultaneous_high']],[j,j],color='#aab6be',lw=4)
            ax.plot([e['low'],e['high']],[j,j],color=COLORS[e['scenario']],lw=2)
            ax.plot(e['mean'],j,'o',color=COLORS[e['scenario']])
        ax.axvline(0,color='black',lw=1);ax.set(yticks=range(len(ee)),yticklabels=[labels[e['scenario']] for e in ee],xlabel='Разность долей относительно baseline',title=metric+'; цвет 95%, серый Bonferroni');ax.invert_yaxis();ax.grid(axis='x',alpha=.2)
    save(fig,figdir,'effects')
    fig,axs=plt.subplots(1,3,figsize=(15,5),layout='constrained')
    for s in scenarios:
        axs[0].plot([r['generation'] for r in tr[s]],[r['not_extinct'] for r in tr[s]],color=COLORS[s],label=labels.get(s,s),alpha=.85)
    axs[0].set(xlabel='Поколение',ylabel='Доля невымерших запусков',ylim=(-.03,1.03),title='Дожитие до горизонта')
    yy=np.arange(len(scenarios));sv=[sm[s,'survival'] for s in scenarios]
    axs[1].errorbar([r['mean'] for r in sv],yy,xerr=[[r['mean']-r['low'] for r in sv],[r['high']-r['mean'] for r in sv]],fmt='o',capsize=3)
    axs[1].set(yticks=yy,yticklabels=[labels.get(s,s) for s in scenarios],xlabel='Доля живых агентов в G',xlim=(-.03,1.03),title='Выживание агентов, 95% CI');axs[1].invert_yaxis()
    for j,r in enumerate(survival):
        axs[2].errorbar(r['risk'],j,xerr=[[r['risk']-r['low']],[r['high']-r['risk']]],fmt='o',color=COLORS[r['scenario']],capsize=3)
    axs[2].set(yticks=yy,yticklabels=[labels.get(s,s) for s in scenarios],xlabel='Вероятность вымирания к G',title='Риск, Wilson 95% CI',xlim=(-.03,1.03));axs[2].invert_yaxis()
    save(fig,figdir,'survival')
    fig,axs=plt.subplots(1,3,figsize=(15,5),layout='constrained')
    for j,s in enumerate(scenarios):
        res,game=sm[s,'resource_per_agent'],sm[s,'game_per_agent']
        axs[0].barh(j,res['mean'],color='#d1a33a');axs[0].barh(j,game['mean'],left=res['mean'],color=COLORS[s])
        for ax,metric in zip(axs[1:],['participation','energy']):
            r=sm[s,metric];ax.errorbar(r['mean'],j,xerr=[[r['mean']-r['low']],[r['high']-r['mean']]],fmt='o',color=COLORS[s],capsize=3)
    for ax in axs:ax.set(yticks=range(len(scenarios)),yticklabels=[labels.get(s,s) for s in scenarios]);ax.invert_yaxis();ax.grid(axis='x',alpha=.15)
    axs[0].set(title='Доход за последнее поколение',xlabel='Энергия / начальный агент; золото — ресурс')
    axs[1].set(title='Охват встреч, 95% CI',xlabel='Участия / живой агент-такт',xlim=(0,1.03))
    axs[2].set(title='Энергия живых, 95% CI',xlabel='Обычные единицы энергии')
    save(fig,figdir,'energy-encounters')
    fig,axs=plt.subplots(2,2,figsize=(13,8),layout='constrained')
    for s in ['baseline','global','global-matched','mutation-high','mutation-zero']:
        if s not in tr:continue
        for ax,metric in zip(axs.flat,['conditional','entropy','clustering','clustering_excess']):
            if metric=='clustering':
                x=range(1,horizon+1); yy=[finite_mean([values[s,int(r['seed']),g][metric] for r in by_s[s] if (s,int(r['seed']),g) in values]) for g in x]
            else:x=[r['generation'] for r in tr[s]];yy=[r[metric] for r in tr[s]]
            ax.plot(x,yy,label=labels[s],color=COLORS[s])
    for ax,title in zip(axs.flat,['Доля CCD??','Shannon H / ln(32)','Доля одинаковых соседей','Избыток над случайной перестановкой']):
        ax.set(xlabel='Поколение',ylabel=title);ax.legend(fontsize=8);ax.grid(alpha=.2)
    save(fig,figdir,'composition')
    render_genotypes(figdir,horizon,tr,genotype_means,labels)
    fig,axs=plt.subplots(1,2,figsize=(13,5),layout='constrained')
    for ax,ss in zip(axs,[['baseline','global','global-matched'],['rounds-1','normalized-1','baseline','rounds-10','normalized-10']]):
        present=[s for s in ss if s in by_s]
        for metric,mark in [('cooperation','o'),('conditional','s')]:
            for j,s in enumerate(present):
                r=sm[s,metric];ax.errorbar(j+(-.08 if mark=='o' else .08),r['mean'],yerr=[[r['mean']-r['low']],[r['high']-r['mean']]],fmt=mark,color=COLORS[s],capsize=4)
        ax.set(xticks=range(len(present)),xticklabels=[labels[s] for s in present],ylim=(-.03,1.03),ylabel='Доля; круг C, квадрат CCD??');ax.tick_params(axis='x',rotation=25);ax.grid(axis='y',alpha=.2)
    axs[0].set_title('Локальность и бюджет встреч');axs[1].set_title('Обычный и нормированный K')
    save(fig,figdir,'controls')
    # Gene expression is a count of actual decisions, not genotype frequencies.
    fig,ax=plt.subplots(figsize=(10,5),layout='constrained');usage=[]
    for s in scenarios:
        rr=[r for (ss,seed,g),r in gen.items() if ss==s and g==horizon]
        totals=np.array([[int(r['after_'+x+'_uses']) for x in ['cc','cd','dc','dd']] for r in rr],dtype=np.int64).reshape(-1,4).sum(axis=0)
        denom=totals.sum();fractions=totals/denom if denom else np.zeros(4)
        usage.append(dict(scenario=s,**{x:int(totals[i]) for i,x in enumerate(['cc','cd','dc','dd'])},total=int(denom)))
        left=0
        for j,(name,color) in enumerate(zip(['CC','CD','DC','DD'],['#3f7e44','#de9b42','#8064aa','#515b66'])):
            ax.barh(scenarios.index(s),fractions[j],left=left,color=color,label=name if s==scenarios[0] else None);left+=fractions[j]
    ax.set(yticks=range(len(scenarios)),yticklabels=[labels.get(s,s) for s in scenarios],xlabel='Доля всех использований условных генов (объединённые счётчики)',xlim=(0,1));ax.invert_yaxis();ax.legend(ncol=4)
    save(fig,figdir,'gene-expression');write_csv(out/'gene-usage.csv',usage)
    # Results fragment links every rounded claim to machine-readable estimates.
    def fmt(x):return '—' if not np.isfinite(x) else f'{x:.3f}'
    lines=['# Численные результаты основной серии' if strict else '# Проверочная серия',
           '',f'Проверено {len(run)} запусков, {len(gen)} поколений, {len(gen)*32} строк генотипов. Горизонт {horizon}.',
           '', '| Сценарий | C [95% CI] | CCD?? [95% CI] | Выживаемость | n C |', '|---|---|---|---|---|']
    for s in scenarios:
        c,q,v=(sm[s,m] for m in ['cooperation','conditional','survival'])
        lines.append(f"| {s} | {fmt(c['mean'])} [{fmt(c['low'])}; {fmt(c['high'])}] | {fmt(q['mean'])} [{fmt(q['low'])}; {fmt(q['high'])}] | {fmt(v['mean'])} | {c['n']} |")
    lines+=['','Частоты — доли, разности — разности долей (×100 для процентных пунктов).',
            'Все значения: summary.csv; парные разности: effects.csv; отдельные seed: endpoints.csv.',
            f'Семейство внутри отчёта: {family_size} сравнений. simultaneous_* — приближённые Bonferroni bootstrap CI.',
            '95% CI в рисунках описательные; отсутствие обнаруженного отличия не доказывает эквивалентность.',
            'C и генотипные частоты условны на сохранение популяции. После вымирания выживаемость равна 0, остальные конечные показатели неопределены.']
    (out/'results.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    metadata={'source':str(source.resolve()),'runs':len(run),'generations':len(gen),'resamples':B,'analysis_seed':ANALYSIS_SEED,'family_size':family_size,
              'analysis_source_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'python':sys.version,'numpy':np.__version__,'matplotlib':matplotlib.__version__,'platform':platform.platform(),
              'input_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [source/'runs.csv',source/'generations.csv',source/'genotype-frequencies.csv']}}
    (out/'analysis.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2))
    images=['dynamics','endpoints','effects','survival','energy-encounters','composition','genotypes','controls','gene-expression']
    table='<table><tr><th>Сценарий</th><th>C, 95% CI</th><th>CCD??, 95% CI</th><th>n</th></tr>'
    for s in scenarios:
        c,q=sm[s,'cooperation'],sm[s,'conditional']
        table+=f"<tr><td>{html.escape(labels.get(s,s))}</td><td>{fmt(c['mean'])} [{fmt(c['low'])}; {fmt(c['high'])}]</td><td>{fmt(q['mean'])} [{fmt(q['low'])}; {fmt(q['high'])}]</td><td>{c['n']}</td></tr>"
    table+='</table>'
    params='<table><tr>'+''.join('<th>'+x+'</th>' for x in ['scenario','K','α','μ','q','pairing'])+'</tr>'
    for s in scenarios:
        m=by_s[s][0];params+='<tr>'+''.join('<td>'+str(v)+'</td>' for v in [s,m['rounds_per_match'],int(m['game_payoff_weight_hundredths'])/100,m['mutation_probability'],m['resource_spawn_probability'],m['pairing_mode']])+'</tr>'
    params+='</table>'
    body=''.join(f'<figure><img src="figures/{x}.svg" alt="{x}"><figcaption>{x}: <a href="figures/{x}.png">PNG</a> · <a href="figures/{x}.pdf">PDF</a></figcaption></figure>' for x in images)
    report=f'''<!doctype html><html lang="ru"><meta charset="utf-8"><title>Эволюция сотрудничества — исследование</title>
<style>body{{max-width:1200px;margin:40px auto;padding:0 24px;color:#183447;font:17px/1.6 sans-serif;background:#f9fbfc}}h1,h2{{line-height:1.2}}img{{width:100%;background:white}}table{{border-collapse:collapse;font-size:14px}}td,th{{padding:8px;border-bottom:1px solid #cbd5db;text-align:left}}figure{{margin:36px 0}}a{{color:#126ba0}}</style>
<h1>Эволюция сотрудничества</h1><p>{'Основная зафиксированная серия' if strict else 'Проверочная серия'}: {len(run)} запусков, горизонт {horizon}. Независимая единица — seed.</p>
<p>Модель genetic-v2-controls, схема 2. Полные параметры и окружение: <a href="../{html.escape(source.name)}/manifest.txt">manifest</a> (если каталоги рядом); <a href="analysis.json">отпечатки входных CSV и версии анализа</a>. L={by_s[scenarios[0]][0]['side_length']}, N={by_s[scenarios[0]][0]['population_size']}, T={by_s[scenarios[0]][0]['ticks_per_generation']}.</p>
{params}<h2>Конечная точка</h2>{table}<p>Средние запусков, percentile bootstrap 20000 повторов, 95% CI. Узкая категория CCD??: первый C, C после CC, D после CD. Она отличается от доли действий C.</p>
<p><a href="summary.csv">Оценки</a> · <a href="effects.csv">Парные эффекты и Bonferroni</a> · <a href="endpoints.csv">Все seed</a> · <a href="trajectory.csv">Динамика и эффективное n</a> · <a href="extinction.csv">Вымирание</a> · <a href="strategy-catalogue.csv">Все 32 стратегии</a> · <a href="standard-matches.csv">1024 стандартные встречи</a> · <a href="behavioral-equivalence.csv">Поведенческая эквивалентность</a></p>
<h2>Методика и ограничения</h2><p>{scope} Серые интервалы эффектов используют поправку Bonferroni; покрытие bootstrap приближённое. Динамические полосы поточечные, не одновременные. Другие показатели описательные. Нет p-значений и утверждений об эквивалентности.</p>
<p>После вымирания S/N=0, C и энергия неопределены. Эффективное n и число пар записаны в таблицах. Глобальный бюджет равен числу локальных пар в собственном текущем мире, а не во внешней контрольной траектории; выбор участников также меняется. Нормировка αK=1 контролирует масштаб выплаты, но не поведенческую последовательность. Память фиксирована одним раундом. Случайное расселение каждого поколения ограничивает наследование пространственных кластеров.</p>
{body}<h2>Прослеживаемость</h2><p>Все численные значения восстановимы из трёх входных CSV. Графики и таблицы не используют данные пилота. Полный протокол и пояснительная записка находятся в docs проекта. Карты отдельного seed — только иллюстрации; статистика выше основана на всех запусках.</p></html>'''
    (out/'report.html').write_text(report,encoding='utf-8')
    print(f'Validated {len(run)} runs / {len(gen)} generations; report: {out}/report.html')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--input',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--strict-main',action='store_true');a=parser.parse_args();analyze(a.input,a.output,a.strict_main)
