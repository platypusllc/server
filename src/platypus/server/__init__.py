import argparse
from . import Controller, Navigator


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
    ws_server = WebsocketServer(controller, navigator)

    while True:
        import time
        time.sleep(0.1)

        # TODO: Implement web server interface.

        # Run the navigator control loop.
        navigator.control()
