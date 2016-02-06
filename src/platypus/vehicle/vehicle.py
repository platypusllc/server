class Vehicle(object):
    """
    Platypus Vehicle.

    This is the primary class for controlling platypus vehicles.  It consists
    of a reference to a controller and a a list of behaviors that can be
    activated at any given time.
    """
    def __init__(self, controller=None, behaviors=None):
        """
        Create a vehicle with the specified controller and specified behaviors.

        :param controller: a Platypus hardware controller for this vehicle
        :type  controller: platypus.vehicle.controller
        """
        pass
