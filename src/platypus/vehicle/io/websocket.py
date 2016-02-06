"""
Platypus websocket vehicle interface
"""


class WebsocketServer(object):
    """
    A websocket server that controls a vehicle and returns sensor information.
    """
    def __init__(self, controller, navigator):
        """
        Create a websocket server that controls a vehicle.

        :param controller: a Platypus vehicle controller
        :type  controller: platypus.vehicle.Controller
        :param navigator: a Platypus navigation system
        :type  navigator: platypus.vehicle.Navigation
        """
        self._controller = controller
        self._navigator = navigator

        # TODO: implement the rest of this class.
