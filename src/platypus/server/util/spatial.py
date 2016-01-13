"""
Platypus spatial utility functions.
"""


def compute_great_arc(start, end):
    """
    Compute a local frame distance and angle from one lat/lon to another.

    :param start: a tuple of (latitude, longitude)
    :type  start: (float, float)
    :param end: a tuple of (latutude, longitude)
    :type  end: (float, float)
    :return: a tuple of (distance, angle) where distance is in meters and
             angle is in radians in an ENU ("east/north/up") coordinate frame
    :rtype:  (float, float)
    """
    # TODO: implement this function.
    pass


def subtract_angle(a, b):
    """
    Compute the minimum angle difference between two angles.
    Returns the wrapped solution for (a - b)

    :param a: the angle from which to subtract
    :type  a: float
    :param b: the angle that will be subtracted
    :type  b: float
    :return: an angle which if added to `b` will result in `a`
    :rtype:  float
    """
    # TODO: implement this function.
    pass
