import platypus.server.controller
import time
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

        c = platypus.server.controller.Controller(port='loop://', data=data)

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

        # The server has not received data, so 'disconnected' if we set a timeout.
        c.timeout = 0.1
        self.assertFalse(c.connected)

        # Run a setter to confirm that the data is sent to the serial port and not
        # immediately set on this object.
        c['m1']['v'] = 0.0
        self.assertEqual(c['m1']['v'], 1.0)

        # The server has received data now, so it should be connected.
        time.sleep(0.05)
        self.assertTrue(c.connected)

        # Wait for the timeout to cause the server to report disconnection.
        time.sleep(0.2)
        self.assertFalse(c.connected)

        # If we disable the timeout, the server should report connection again.
        c.timeout = None
        self.assertTrue(c.connected)