from . import Behavior
from .util import spatial
import enum
import numpy as np


# TODO: use python enum34 here.
@enum.unique
class VehicleType(enum.Enum):
    differential = 'differential'
    vectored = 'vectored'


class Navigator(Behavior):
    """
    Implements a vehicle navigation system that attempts to follow waypoints.
    """
    def __init__(self, vehicle_type=VehicleType.differential, **kwargs):
        """
        Create a new vehicle navigation system.

        :param controller: a Platypus vehicle
        :type  controller: platypus.vehicle.Vehicle
        """
        super(Navigator, self).__init__(**kwargs)

        # TODO: implement a navigation lock?
        self.vehicle_type = vehicle_type
        self.waypoints = []
        self.enabled = True

    def control(self):
        """
        Apply a simple control loop to go to the next waypoint in the queue.
        """
        # Do nothing if this system is not enabled.
        if not self.enabled:
            return

        # If there are no waypoints, stop moving and exit this loop.
        if not len(self.waypoints):
            self._controller['m0']['v'] = 0.0
            self._controller['m1']['v'] = 0.0
            return

        # Get next waypoint to which to navigate.
        waypoint = self.waypoints[0]

        # Get current pose and heading of the vehicle.
        position = (self._controller['gps']['lon'],
                    self._controller['gps']['lat'])
        yaw = self._controller['imu']['y']

        # Get range and bearing to next waypoint using great arc distance.
        distance, angle = spatial.compute_great_arc(start=position,
                                                    end=waypoint)

        # If within a minimum range in meters, stop moving and exit this loop.
        if distance < 2.0:
            self._controller['m0']['v'] = 0.0
            self._controller['m1']['v'] = 0.0
            return

        # Compute angular correction.
        delta_yaw = spatial.subtract_angle(angle - yaw)

        # Apply simple waypoint following rules here:
        if self.vehicle_type == VehicleType.differential:
            # Create differential thrust command for the vehicle.
            # M0 = Left, M1 = Right
            self.vehicle.controller['m0']['v'] = np.clip(-delta_yaw, -1.0, 1.0)
            self.vehicle.controller['m1']['v'] = np.clip(delta_yaw, -1.0, 1.0)
        elif self.vehicle_type == VehicleType.vectored:
            # Create vectored thrust command for the vehicle.
            # M0 = Thrust, M1 = Rudder
            self.vehicle.controller['m0']['v'] = np.clip(distance, -1.0, 1.0)
            self.vehicle.controller['m1']['v'] = np.clip(delta_yaw, -1.0, 1.0)
