"""Small synthetic/mutation checks of scientific validation and estimators (unittest)."""
import csv, math, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
import numpy as np
import analyze_study as a

class AnalysisTests(unittest.TestCase):
    def test_bootstrap_and_missingness(self):
        self.assertEqual((3,2.,2.,2.),a.boot([2,2,2,math.nan]))
        self.assertEqual(0,a.boot([math.nan])[0])
        self.assertLess(a.boot([-2,-1,0,1,2])[2],0)
        self.assertGreater(a.boot([-2,-1,0,1,2])[3],0)
    def test_zero_events_do_not_imply_zero_risk(self):
        p,lo,hi=a.wilson(0,40);self.assertEqual(p,0);self.assertLess(lo,1e-12);self.assertGreater(hi,.08)
    def test_standard_tft_and_reaction_exposure(self):
        acts,pa,pb,uses=a.match('CCDCD','DDDDD',5)
        self.assertEqual(['CD','DD','DD','DD','DD'],acts);self.assertEqual((4,9),(pa,pb))
        self.assertEqual([0,1,0,3],uses)
    def test_genotype_classification_and_k1_equivalence(self):
        self.assertEqual(4,sum(g.startswith('CCD') for g in a.GENOTYPES))
        for g in a.GENOTYPES:
            self.assertEqual([g[0]+'D'],a.match(g,'DDDDD',1)[0])
    def test_all_extinct_report_retains_zero_survival_and_undefined_C(self):
        import os
        report=Path(os.environ.get('STUDY_EXTINCTION_REPORT','results/extinction-report-20261020-v2'))
        if not report.exists(): self.skipTest('CI supplies the all-extinct report')
        endpoint=list(a.rows(report/'endpoints.csv'))
        self.assertEqual(2,len(endpoint))
        for r in endpoint:
            self.assertEqual('0',r['completed']);self.assertEqual('0',r['survival'])
            self.assertEqual('',r['cooperation']);self.assertEqual('',r['energy']);self.assertEqual('1',r['extinction_tick'])
        usage=list(a.rows(report/'gene-usage.csv'))
        self.assertTrue(all(r['total']=='0' for r in usage))
        self.assertTrue((report/'report.html').exists())

    def test_validation_rejects_tampered_counters(self):
        # Use CI's independently generated smoke dataset, not research results.
        import os
        p=Path(os.environ.get('STUDY_TEST_INPUT','results/pilot-20261020'))
        if not p.exists():self.skipTest('No smoke CSV; CI sets STUDY_TEST_INPUT')
        runs=list(a.rows(p/'runs.csv'));generations=list(a.rows(p/'generations.csv'));genotypes=list(a.rows(p/'genotype-frequencies.csv'))
        original=a.rows
        def altered(path):
            if path.name=='generations.csv':
                rr=[dict(r) for r in generations];rr[0]['cc_count']=str(int(rr[0]['cc_count'])+1);return iter(rr)
            return iter(runs if path.name=='runs.csv' else genotypes)
        with patch.object(a,'rows',altered):
            with self.assertRaisesRegex(ValueError,'Outcome sum'):a.load(p)
        # Negative frequency with unchanged count must fail even if sums still match.
        def altered_frequency(path):
            if path.name=='genotype-frequencies.csv':
                rr=[dict(r) for r in genotypes];rr[0]['frequency']='-1';return iter(rr)
            return iter(runs if path.name=='runs.csv' else generations)
        with patch.object(a,'rows',altered_frequency):
            with self.assertRaisesRegex(ValueError,'Genotype frequency'):a.load(p)

if __name__=='__main__':unittest.main()
