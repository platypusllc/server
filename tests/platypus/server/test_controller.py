import platypus.server.controller
from unittest import TestCase


class ControllerTest(TestCase):
    def test_init(self):
        # Initial data to use within the controller.
        data = {
            "m0": {"type": "HobbyKing"},
            "m1": {
                "v": 1.0,
                "type": "SeaKing",
                "enabled": False,
                "meta": {
                    "min": 0.0,
                    "max": 1.0
                }
            },
            "m2": "test_motor",
            "s0": "test_sensor"
        }

        c = platypus.server.controller.Controller(port='/dev/null', data=data)

        # The server has never received data, so it should not be connected.
        self.assertFalse(c.connected)

        # Check that initial data is accessible.
        self.assertItemsEqual(c.keys(), ('m0', 'm1', 'm2', 's0'))

        self.assertItemsEqual(c['m0'].keys(), ('type',))
        self.assertEqual(c['m0']['type'], 'HobbyKing')

        self.assertItemsEqual(c['m1'].keys(), ('v', 'type', 'enabled', 'meta'))
        self.assertEqual(c['m1']['v'], 1.0)
        self.assertEqual(c['m1']['type'], 'SeaKing')
        self.assertEqual(c['m1']['enabled'], False)
        self.assertEqual(c['m1']['meta']['min'], 0.0)
        self.assertEqual(c['m1']['meta']['max'], 1.0)

        self.assertEqual(c['m2'], 'test_motor')
        self.assertEqual(c['s0'], 'test_sensor')

        # Run a setter to confirm that the data is sent to the serial port and not
        # immediately set on this object.
        c['m1']['v'] = 0.0
        self.assertEqual(c['m1']['v'], 1.0)
