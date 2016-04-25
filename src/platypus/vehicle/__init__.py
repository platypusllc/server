import argparse
from . import io
from .behavior import Behavior
from .behavior.failsafe import Failsafe
from .behavior.navigator import Navigator
from .controller import Controller
from .vehicle import Vehicle

__all__ = [Behavior, Failsafe, Navigator, Controller, Vehicle]


def main():
    """
    Launch a server from the command line.
    """
    parser = argparse.ArgumentParser()
    parser.add_argument('-p', '--port', type='str',
                        help="serial port for controller",
                        default='/dev/ttyACM0')
    args = argparse.parse_args()

    # Create a hardware controller on the specified port.
    controller = Controller(port=args.port)

    # Create a default waypoint navigation behavior.
    navigator = Navigator()

    # Create a vehicle using the the controller and navigator.
    vehicle = Vehicle(controller, {
        'navigator': navigator,
    })

    # Add a new behavior to return to a failsafe location after 30s
    # without connectivity.
    vehicle.behaviors['failsafe'] = Failsafe('192.168.1.1', 30.0)

    # Start a websocket server to control the vehicle.
    ws_server = io.WebsocketServer(vehicle)
    ws_server.start()

    # Block until the vehicle is shutdown.
    vehicle.spin()
