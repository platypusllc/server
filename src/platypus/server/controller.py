from __future__ import print_function

import serial
import json
import threading
from . import util


class _ControllerAttribute(object):
    """
    Lazily-evaluated proxy to a controller attribute.
    """
    def __init__(self, controller, path=[]):
        self._controller = controller
        self._path = path

    def __setitem__(self, key, value):
        root = dict()

        d = root
        for element in self._path:
            d[element] = dict()
            d = root[element]
        d[key] = value

        self._controller._write(root)

    def __getitem__(self, key):
        d = self._controller._data
        for element in self._path:
            d = d.get(element)
        return d.get(key)


class Controller(object):
    """
    Interface to vehicle controller over serial.
    """
    def __init__(self, port='/dev/ttyACM0', baud=250000):
        """
        Create an interface to a vehicle controller over serial.

        :param port: serial device path on which controller is connected
        :type  port: str
        :param baud: baud rate of serial connection
        :type  baud: int
        """
        # Create an internal data dictionary that stores controller state.
        self._data = dict()
        self._port = port
        self._baud = baud

        self._device_lock = threading.Lock()
        self._device = None

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
                util.merge(self._data, update)
            except Exception as e:
                print("Exception: {:s}".format(e))

    def __getattr__(self, key):
        return _ControllerAttribute(self)[key]

    def __setattr__(self, key, value):
        _ControllerAttribute(self)[key] = value
