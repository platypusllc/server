from . import Behavior


class Failsafe(Behavior):
    """
    Implements a vehicle failsafe that returns to a known home location.

    The failsafe behavior, when active, monitors the connectivity of a
    particular IP address, and activates waypoint navigation to return to a
    'home' location as safely as possible if connectivity is interrupted for an
    extended period of time.

    This behavior requires a 'navigator' behavior to be available.
    """
    def __init__(self, hostname=None, timeout=30, **kwargs):
        super(Failsafe, self).__init__(**kwargs)

        self.hostname = hostname
        self.timeout = timeout

# TODO: implement the rest of this behavior.
