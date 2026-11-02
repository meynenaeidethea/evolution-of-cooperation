import hashlib
import io
import tarfile
import tempfile
import unittest
from pathlib import Path
from verify_archive import verify


class ArchiveIntegrityTests(unittest.TestCase):
    def archive(self, root, content, listed_digest):
        archive = root / 'study.tar.gz'
        with tarfile.open(archive, 'w:gz') as tar:
            for name, data in [('data.csv', content),
                               ('bundle-SHA256SUMS', (listed_digest + '  data.csv\n').encode())]:
                info = tarfile.TarInfo('cooperation-study/' + name)
                info.size = len(data)
                tar.addfile(info, io.BytesIO(data))
        Path(str(archive) + '.sha256').write_text(hashlib.sha256(archive.read_bytes()).hexdigest() + '  study.tar.gz\n')
        return archive

    def test_accepts_valid_bundle_and_detects_outer_corruption(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = self.archive(Path(directory), b'original', hashlib.sha256(b'original').hexdigest())
            self.assertEqual(1, verify(archive))
            archive.write_bytes(archive.read_bytes() + b'tampered')
            with self.assertRaisesRegex(ValueError, 'Archive checksum'):
                verify(archive)

    def test_detects_inner_corruption_even_with_valid_outer_checksum(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = self.archive(Path(directory), b'tampered', hashlib.sha256(b'original').hexdigest())
            with self.assertRaisesRegex(ValueError, 'Internal file checksums'):
                verify(archive)
