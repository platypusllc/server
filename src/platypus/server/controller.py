from __future__ import print_function

import json
import serial
import threading
from . import util


class Controller(util.ObservableDict):
    """
    Interface to vehicle controller over serial.

    The protocol to this device is assumed to be sending and receiving
    properly escaped JSON, with each packet being a full JSON object
    terminated by a single newline ('\n') character.

    Received JSON packets are collated into a dict-like, with conflicting
    entries replacing old ones.  To remove an entry in this dict, the
    device can transmit a message in which the corresponding key is set to
    the JSON value `null`.
    """
    def __init__(self, port='/dev/ttyACM0', baud=250000,
                 timeout=None, data=dict()):
        """
        Create an interface to a vehicle controller over serial.

        :param port: serial device path on which controller is connected
        :type  port: str
        :param baud: baud rate of serial connection
        :type  baud: int
        :param timeout: if this is not None, a message must be have been
            received within this time (in seconds) for the controller to
            be considered `connected`
        :type  timeout: float or None
        :param data: optional initial data to use in the controller
        :type  data: dict
        """
        # Create an internal data dictionary that stores controller state.
        util.ObservableDict.__init__(self, entries=data)

        self._port = port
        self._baud = baud

        self._device_lock = threading.Lock()
        self._device = None

        self._timeout = timeout
        if self._timeout is not None:
            # TODO: implement connectivity timeout.
            raise NotImplementedError("Timeout is not yet supported.")

    @property
    def timeout(self):
        return self._timeout

    @timeout.setter
    def timeout(self, value):
        self._timeout = value

    @property
    def connected(self):
        with self._device_lock:
            return self._device is not None

    @property
    def port(self):
        return self._port

    @port.setter
    def port(self, value):
        # Disconnect existing port.
        with self._device_lock:
            self._device = None
        self._port = value

    def _write(self, value):
        """
        Serializes a python dict-like to the controller as JSON.

        :param value: the value that will be serialized
        :type  value: dict-like object
        """
        with self._device_lock:
            if not self._device:
                self._device.port = serial.Serial(self._port, self._baud)

            try:
                self._device.write(json.dumps(value))
                self._device.write('\n')
                self._device.flush()
            except IOError:
                self._device = None
                raise Exception("Write failed.")

    def _read(self):
        """
        Read a line of text from the controller and interpret it as JSON.

        Only JSON objects are allowed as top-level containers in this protocol,
        so the return type should always be a Python dict.

        :return: a Python objcct that represents the interpreted JSON
        :rtype: dict
        """
        with self._device_lock:
            try:
                line = self._device.readline()
                update = json.parse(line)
                self.merge(update)
            except Exception as e:
                print("Exception: {:s}".format(e))
