import platypus.vehicle
from unittest import TestCase


class TestBehavior(platypus.vehicle.Behavior):
    pass


class BehaviorTest(TestCase):
    def setUp(self):
        # Create a vehicle for use with the behavior unit tests.
        self.vehicle = platypus.vehicle.Vehicle()

    def tearDown(self):
        # Shutdown the vehicle that was created.
        self.vehicle.shutdown()

    def test_activate(self):
        behavior = TestBehavior()

        # If activated with no vehicle, raise Exception.
        with self.assertRaises(ValueError):
            behavior.activate()

        with self.assertRaises(ValueError):
            behavior.active = True

        # Should successfully activate and deactivate with vehicle
        # including updating via observable notification.
        active_value = [False]

        def onActive(new_value, old_value):
            active_value[0] = new_value

        behavior.vehicle = self.vehicle
        behavior.on("active", onActive)

        behavior.activate()
        self.assertTrue(behavior.is_active)
        self.assertTrue(active_value[0])

        behavior.deactivate()
        self.assertFalse(behavior.is_active)
        self.assertFalse(active_value[0])
