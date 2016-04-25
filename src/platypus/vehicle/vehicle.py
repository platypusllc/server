import threading
import six


class Vehicle(object):
    """
    Platypus vehicle implementation.

    This is the primary class for controlling platypus vehicles.  It consists
    of a reference to a controller and a a list of behaviors that can be
    activated at any given time.
    """
    def __init__(self, controller=None, behaviors=dict()):
        """
        Create a vehicle with the specified controller and specified behaviors.

        :param controller: a Platypus hardware controller for this vehicle
        :type  controller: platypus.vehicle.controller
        :param behaviors: a dict of behaviors to make available on the vehicle
        :type  behaviors: {str: platypus.vehicle.Behavior}
        """
        self.behaviors = dict(behaviors)
        self.controller = controller
        self.shutdown_event = threading.Event()
        # TODO: locking for controllers and behaviors (adding and removing)?

    @property
    def controller(self):
        return self._controller

    @controller.setter
    def controller(self, controller):
        # TODO: register new event handler for this controller?
        self._controller = controller

    def shutdown(self):
        """
        Releases resources used by this vehicle and terminates 'spin()' calls.

        This will trigger shutdown of ALL attached controllers and behaviors.
        """
        # If the vehicle is already shutdown, don't need to do anything.
        if self.shutdown_event.is_set():
            return

        # Shutdown attached controller.
        if self.controller:
            self.controller.shutdown()

        # Shutdown attached behaviors.
        for behavior in six.viewvalues(self.behaviors):
            behavior.active = False

        # Signal that the vehicle is shutdown.
        self.shutdown_event.set()

    def spin(self):
        """
        Blocks a thread until the vehicle is shutdown.

        This is a convenient wait-implementation for situations where a vehicle
        has been created in a main thread, and no remaining work exists other
        than to wait for the vehicle to be shutdown.
        """
        self.shutdown_event.wait()
