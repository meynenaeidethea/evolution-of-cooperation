#!/usr/bin/env python3
"""Run the six additional profiles fixed in docs/robustness-protocol.md."""
import argparse
import json
import re
import subprocess
import tempfile
from pathlib import Path

from analyze_study import load
from finalize_materials import assemble

PROFILES = ('reference', 'small', 'large', 'sparse', 'dense', 'long')


def run(label, resume):
    if not re.fullmatch(r'[A-Za-z0-9_-]+', label):
        raise ValueError('Label must contain only letters, digits, underscore or dash')
    root = Path('results') / label
    root.mkdir(parents=True, exist_ok=resume)
    for name in PROFILES:
        data, report, materials = (root / (name + suffix) for suffix in ('-data', '-report', '-materials'))
        scenarios = ('baseline,global,global-matched,resource-poor,resource-rich' if name == 'reference'
                     else 'baseline,normalized-1,normalized-10,global,global-matched')
        if not (data / 'completed.txt').exists():
            options = ['--profile', f'profiles/robustness/{name}.properties', '--output', str(data),
                       '--repetitions', '80' if name == 'reference' else '40', '--seed-start', '20001',
                       '--threads', '2', '--scenarios', scenarios]
            if data.exists():
                if not resume:
                    raise FileExistsError(data)
                options.append('--resume')
            subprocess.run(['./gradlew', ':simulation:runStudy', '--args=' + ' '.join(options)], check=True)
        load(data)  # Always validate completed data, including resume invocations.
        if not report.exists():
            pending = Path(tempfile.mkdtemp(prefix='.analysis-', dir=root)) / 'report'
            subprocess.run(['python3', 'tools/analyze_study.py', '--input', str(data), '--output', str(pending)], check=True)
            pending.rename(report)
            pending.parent.rmdir()
        if not materials.exists():
            pending = Path(tempfile.mkdtemp(prefix='.materials-', dir=root)) / 'report'
            assemble(report, data, None, pending)
            pending.rename(materials)
            pending.parent.rmdir()
        else:
            import hashlib
            info = json.loads((materials / 'analysis.json').read_text())
            if any(hashlib.sha256((data / filename).read_bytes()).hexdigest() != digest
                   for filename, digest in info['input_sha256'].items()):
                raise ValueError('Stale materials: ' + str(materials))
    print('Completed robustness pipeline:', root, flush=True)


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--label', required=True)
    p.add_argument('--resume', action='store_true')
    a = p.parse_args()
    run(a.label, a.resume)
