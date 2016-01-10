import platypus.server.controller
from unittest import TestCase


class ControllerTest(TestCase):
    def test_data(self):
        # Initial data to use within the controller.
        data = {
            "m0": {"type": "HobbyKing"},
            "m1": {
                "v": 1.0,
                "type": "SeaKing",
                "enabled": False
            },
            "m2": "test_motor",
            "s0": "test_sensor"
        }

        # Create the controller object.
        c = platypus.server.controller.Controller(port='/dev/null', data=data)

        # The server has never received data, so it should not be connected.
        self.assertFalse(c.connected)

        # Check that initial data is accessible.
        self.assertCountEqual(c.keys(), ('m0', 'm1', 'm2', 's0'))

        self.assertCountEqual(c['m0'].keys(), ('type'))
        self.assertEqual(c['m0']['type'], 'HobbyKing')

        self.assertCountEqual(c['m1'].keys(), ('v', 'type', 'enabled'))
        self.assertEqual(c['m0']['v'], 1.0)
        self.assertEqual(c['m0']['type'], 'SeaKing')
        self.assertEqual(c['m0']['enabled'], False)

        self.assertEqual(c['m2'], 'test_motor')
        self.assertEqual(c['s0'], 'test_sensor')

        # Create observers and try to change some data.
        should_be_updated = False
        should_not_be_updated = False

        def observer(key, old_value, new_value):
            global should_be_updated
            should_be_updated = True

        def unobserver(key, old_value, new_value):
            global should_not_be_updated
            should_not_be_updated = True

        c.observe('m0', observer)
        c.observe('m0', unobserver)
        c.unobserve('m0', unobserver)

        c.receive({'m0': {'type': 'SeaKing'}})

        self.assertEqual(c['m0']['type'], 'SeaKing')
        self.assertTrue(should_be_updated)
        self.assertFalse(should_not_be_updated)

