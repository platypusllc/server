import argparse
from .controller import Controller
from .navigator import Navigator
from . import io


def main():
    """
    Launch a server from the command line.
    """
    parser = argparse.ArgumentParser()
    parser.add_argument('-p', '--port', type='str',
                        help="serial port for controller",
                        default='/dev/ttyACM0')
    args = argparse.parse_args()

    controller = Controller(port=args.port)
    navigator = Navigator(controller)

    ws_server = io.WebsocketServer(controller, navigator)
    ws_server.start()

    while True:
        import time
        time.sleep(0.1)

        # TODO: Implement web server interface.

        # Run the navigator control loop.
        navigator.control()
