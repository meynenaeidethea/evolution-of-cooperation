#!/usr/bin/env python3
"""Package auditable study results into the repository without committing expanded CSVs."""
import argparse
import gzip
import hashlib
import io
import json
import platform
import shutil
import tarfile
from pathlib import Path
import matplotlib
import numpy
import scipy


def package(source, destination):
    destination.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for name in ['runs.csv', 'generations.csv', 'genotype-frequencies.csv']:
        hashes[name] = hashlib.sha256((source / name).read_bytes()).hexdigest()
    with (destination / 'raw-csv.tar.gz').open('wb') as raw:
        with gzip.GzipFile(fileobj=raw, mode='wb', mtime=0, filename='') as compressed:
            with tarfile.open(fileobj=compressed, mode='w') as archive:
                for name in hashes:
                    data = (source / name).read_bytes()
                    info = tarfile.TarInfo(name)
                    info.size = len(data)
                    info.mode = 0o644
                    archive.addfile(info, io.BytesIO(data))
    for name in ['runs.csv', 'summary.csv', 'comparisons.csv', 'report.md', 'demo.html']:
        shutil.copyfile(source / name, destination / name)
    shutil.copytree(source / 'figures', destination / 'figures', dirs_exist_ok=True)
    snapshots = destination / 'snapshots'
    snapshots.mkdir(exist_ok=True)
    for image in (source / 'snapshots').glob('baseline-*.svg'):
        shutil.copyfile(image, snapshots / image.name)
    environment = {'python': platform.python_version(), 'numpy': numpy.__version__,
                   'scipy': scipy.__version__, 'matplotlib': matplotlib.__version__, 'sha256': hashes}
    (destination / 'manifest.json').write_text(json.dumps(environment, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(destination)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    package(args.source, args.destination)
