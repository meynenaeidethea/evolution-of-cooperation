import unittest
import numpy as np
from analyze import interval, holm

class StatisticalTests(unittest.TestCase):
    def test_empty_and_singleton_do_not_invent_intervals(self):
        self.assertEqual(interval([])[0], 0)
        self.assertTrue(np.isnan(interval([1])[2]))
        self.assertEqual(interval([1, 1])[1:], (1, 1, 1))

    def test_t_interval_uses_replicates(self):
        n, mean, low, high = interval([1, 2, 3, float('nan')])
        self.assertEqual(n, 3)
        self.assertEqual(mean, 2)
        self.assertAlmostEqual(low, -0.4841377117)
        self.assertAlmostEqual(high, 4.4841377117)

    def test_holm_preserves_input_order(self):
        np.testing.assert_allclose(holm([.04, .001, .03]), [.06, .003, .06])
        self.assertEqual(len(holm([])), 0)

if __name__ == '__main__':
    unittest.main()
