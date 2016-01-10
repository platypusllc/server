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
