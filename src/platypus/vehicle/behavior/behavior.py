# TODO: How will resource reservation be handled?


class Behavior(object):
    """
    A behavior is a self-contained vehicle control routine.

    Behaviors can be added to vehicles to enable new functionality. They can
    call each other to create complex chains of reasoning.  Each behavior can
    be independently activated or deactivated.

    When a behavior is active, it is allowed to modify aspects of the vehicle
    and other behaviors.  When it is not active, it should not be able to
    modify or observe the vehicle, and should not consume resources.
    """
    def __init__(self, vehicle=None):
        self._vehicle = vehicle
        self._is_active = False

    @property
    def vehicle(self):
        return self._vehicle

    @vehicle.setter
    def vehicle(self, vehicle):
        self.on('vehicle', self._vehicle, vehicle)
        self._vehicle = vehicle

    @property
    def is_active(self):
        """
        When a behavior is active, it is allowed to modify the vehicle.
        """
        return self._is_active

    @is_active.setter
    def is_active(self, active):
        self.on('is_active', self._is_active, active)
        self._is_active = active

    def activate(self):
        """
        Changes this behavior to be active.
        """
        self.active = True

    def deactivate(self):
        """
        Changes this behavior to be inactive.
        """
        self.active = False
