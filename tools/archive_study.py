#!/usr/bin/env python3
"""Deterministic local archive of source and selected artifacts, with embedded checksums."""
import argparse,gzip,hashlib,io,json,os,subprocess,tarfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--include',type=Path,nargs='+',required=True);a=p.parse_args()
root=Path.cwd();paths=set()
for base in [Path(x) for x in ['simulation/src','profiles','tools','docs','research','.github','gradle']]+a.include:
    resolved=base.resolve()
    if not resolved.is_relative_to(root):raise ValueError('Archive paths must be in the project')
    if not base.exists():raise FileNotFoundError(base)
    for f in (base.rglob('*') if base.is_dir() else [base]):
        if f.is_file() and not f.is_symlink() and (base in a.include or '__pycache__' not in f.parts):paths.add(f)
for name in ['README.md','.gitignore','.gitattributes','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','simulation/build.gradle.kts']:
    paths.add(Path(name))
def git(*args):
    r=subprocess.run(['git',*args],capture_output=True,text=True);return r.stdout.strip() if r.returncode==0 else 'unavailable'
metadata=json.dumps(dict(revision=git('rev-parse','HEAD'),working_tree_status=git('status','--short'),
                         model='genetic-v2-controls',schema=2,includes=[str(x) for x in a.include],
                         note='Primary run source snapshot is in its data/source; repository root holds final deliverable source.'),ensure_ascii=False,indent=2).encode()
manifest=[]
a.output.parent.mkdir(parents=True,exist_ok=True)
def entry(tar,name,data,mode=0o644):
    i=tarfile.TarInfo('cooperation-study/'+name);i.size=len(data);i.mtime=0;i.mode=mode;i.uid=i.gid=0;i.uname=i.gname=''
    tar.addfile(i,io.BytesIO(data));manifest.append(hashlib.sha256(data).hexdigest()+'  '+name)
with a.output.open('xb') as raw:
    with gzip.GzipFile(filename='',mode='wb',compresslevel=6,fileobj=raw,mtime=0) as gz:
        with tarfile.open(fileobj=gz,mode='w|') as tar:
            for f in sorted(paths):entry(tar,str(f),f.read_bytes(),0o755 if os.access(f,os.X_OK) else 0o644)
            entry(tar,'bundle.json',metadata)
            entry(tar,'bundle-SHA256SUMS',('\n'.join(manifest)+'\n').encode())
digest=hashlib.sha256(a.output.read_bytes()).hexdigest()
Path(str(a.output)+'.sha256').write_text(digest+'  '+a.output.name+'\n');print(a.output,digest,a.output.stat().st_size)
