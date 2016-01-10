import platypus.server.util
from unittest import TestCase


class UtilTest(TestCase):
    def test_merge(self):

        # Create test datasets to use in the merge operation.
        A = {
            "m0": {"type": "HobbyKing"},
            "m1": {
                "v": 1.0,
                "type": "HobbyKing",
                "enabled": False
            },
            "m2": "test_motor",
            "s0": "test_sensor"
        }

        B = {
            "m0": {"v": 2.0},
            "m1": {
                "v": 3.0,
                "enabled": None,
                "comment": "test_attr"
            },
            "s0": None,
            "s1": {"type": "sensor"}
        }

        # Merge B into A using utility function.
        platypus.server.util.merge(A, B)

        # Make sure the correct keys are in the result.
        self.assertEqual(A["m0"]["type"], "HobbyKing")
        self.assertEqual(A["m0"]["v"], 2.0)

        self.assertEqual(A["m1"]["v"], 3.0)
        self.assertEqual(A["m1"]["type"], "HobbyKing")
        self.assertNotIn("enabled", A["m1"])
        self.assertEqual(A["m1"]["comment"], "test_attr")

        self.assertEqual(A["m2"], "test_motor")
        self.assertNotIn("s0", A)

        self.assertEqual(A["s1"]["type"], "sensor")
