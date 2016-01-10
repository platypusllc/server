import argparse
from platypus.server.controller import Controller


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

    while True:
        import time
        time.sleep(0.1)
