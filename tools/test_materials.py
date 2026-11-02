"""Regression checks: a different series must never receive the original study's conclusions."""
import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from finalize_materials import assemble


class MaterialsTests(unittest.TestCase):
    def test_custom_series_uses_its_values_seeds_and_portable_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data, report, output = (root / n for n in ['different-data', 'report', 'portable'])
            data.mkdir()
            report.mkdir()
            contents = {
                'runs.csv': 'scenario_id,seed\nbaseline,42\nbaseline,43\n',
                'generations.csv': 'unused fixture\n',
                'genotype-frequencies.csv': 'unused fixture\n'}
            for name, content in contents.items():
                (data / name).write_text(content)
            (data / 'manifest.txt').write_text('fixture')
            (report / 'analysis.json').write_text(json.dumps({'input_sha256': {
                name: hashlib.sha256((data / name).read_bytes()).hexdigest() for name in contents}}))
            (report / 'summary.csv').write_text('scenario,metric,n,mean,low,high\nbaseline,cooperation,2,0.123,0.1,0.2\n')
            (report / 'effects.csv').write_text('scenario,reference,metric,n,mean,low,high\n')
            (report / 'extinction.csv').write_text('scenario,n,extinctions,high\nbaseline,2,1,0.905\n')
            (report / 'strategy-catalogue.csv').write_text('genotype,conditional,nonconstant,standard_C,standard_payoff\n')
            (report / 'report.html').write_text('<a href="../different-data/manifest.txt">manifest</a>'
                '<h2>Конечная точка</h2><h2>Методика и ограничения</h2><h2>Прослеживаемость</h2>')
            assemble(report, data, None, output)
            text = (output / 'report.html').read_text()
            self.assertIn('0.123', text)
            self.assertIn('seed 42–43', text)
            self.assertIn('1/2', text)
            self.assertNotIn('0.487', text)
            self.assertNotIn('10001', text)
            self.assertIn('href="inputs/manifest.txt"', text)
            self.assertEqual('fixture', (output / 'inputs/manifest.txt').read_text())
            with self.assertRaises(FileExistsError):
                assemble(report, data, None, output)
            (data / 'runs.csv').write_text('tampered')
            with self.assertRaisesRegex(ValueError, 'Input changed'):
                assemble(report, data, None, root / 'tampered-report')
            self.assertFalse((root / 'tampered-report').exists())


if __name__ == '__main__':
    unittest.main()
