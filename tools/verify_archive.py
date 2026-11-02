#!/usr/bin/env python3
"""Verify an exported study archive and every internal checksum without extracting it."""
import argparse
import hashlib
import tarfile
from pathlib import Path


def verify(path):
    expected = Path(str(path) + '.sha256').read_text().split()[0]
    with path.open('rb') as raw:
        actual = hashlib.file_digest(raw, 'sha256').hexdigest()
    if actual != expected:
        raise ValueError('Archive checksum mismatch: ' + str(path))
    files = {}
    manifest = None
    prefix = 'cooperation-study/'
    with tarfile.open(path, 'r:gz') as archive:
        for member in archive:
            if not member.isfile() or not member.name.startswith(prefix):
                raise ValueError('Unexpected archive member: ' + member.name)
            name = member.name[len(prefix):]
            if Path(name).is_absolute() or '..' in Path(name).parts or name in files:
                raise ValueError('Unsafe or duplicate archive member: ' + name)
            with archive.extractfile(member) as handle:
                if name == 'bundle-SHA256SUMS':
                    if manifest is not None:
                        raise ValueError('Duplicate manifest')
                    manifest = handle.read().decode('utf-8')
                else:
                    files[name] = hashlib.file_digest(handle, 'sha256').hexdigest()
    if manifest is None:
        raise ValueError('Missing internal manifest')
    listed = {}
    for line in manifest.splitlines():
        digest, name = line.split('  ', 1)
        if name in listed:
            raise ValueError('Duplicate checksum: ' + name)
        listed[name] = digest
    if listed != files:
        raise ValueError('Internal file checksums differ')
    return len(files)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('archive', type=Path)
    args = parser.parse_args()
    print(f'Verified {verify(args.archive)} files in {args.archive}')
