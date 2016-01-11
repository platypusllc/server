from __future__ import print_function

import collections
import json
import serial
import threading
import time
from . import util

class ControllerDict(collections.MutableMapping):
    """
    Wrapper class for ObservableDict that intercepts setter calls.

    Instead of directly applying these changes, they are sent to the controller
    to be dispatched to the connected hardware.
    """
    def __init__(self, controller, obs_dict):
        self._controller = controller
        self._dict = obs_dict

    def __delitem__(self, key):
        self.__setitem__(key, None)

    def __setitem__(self, key, value):
        d = self.path_to_dict(key, value, self._dict.path)
        self._controller._write(d)

    def __getitem__(self, key):
        value = self._dict[key]
        if isinstance(value, util.ObservableDict):
            return ControllerDict(controller=self._controller, obs_dict=value)
        else:
            return value

    def __iter__(self):
        return iter(self._dict)

    def __len__(self):
        return len(self._dict)

    def merge(self, d):
        self._dict.merge(d)

    @staticmethod
    def path_to_dict(key, value, path):
        """
        Recursively creates dicts to represent a nested key-value.
        """
        root = collections.defaultdict(lambda: collections.defaultdict())
        d = root
        if path is not None:
            for element in path:
                d = d[element]
        d[key] = value
        return root


class Controller(ControllerDict):
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
        observable_dict = util.ObservableDict(entries=data)
        ControllerDict.__init__(self, controller=self, obs_dict=observable_dict)

        self._device_lock = threading.Lock()
        self._device = serial.serial_for_url(port, do_not_open=True)
        self._device.baudrate = baud
        try:
            self._device.open()
        except:
            self._device.close()
        self._last_timestamp = float('-inf')

        # Start a thread to receive data from the port.
        self._running = True
        t = threading.Thread(target=self._receive)
        t.setDaemon(True)
        t.start()

        # Set a connectivity timeout.
        self.timeout = timeout

    def shutdown(self):
        """
        Closes hardware connection and shuts down listening thread.
        """
        with self._device_lock:
            self._running = False
            self._device.close()

    @property
    def connected(self):
        """
        Returns whether the device is currently connected.

        This checks whether the port is open, and if a timeout is set, also
        whether data was received as recently as the timeout window.
        """
        with self._device_lock:
            if not self._device.isOpen:
                return False
            else:
                return ((self.timeout is None) or
                    (self._last_timestamp + self.timeout > time.clock()))

    @property
    def port(self):
        with self._device_lock:
            return self._device.port

    @port.setter
    def port(self, value):
        # Disconnect existing port.
        with self._device_lock:
            try:
                self._device.port = value
            except OSError:
                pass

    def _write(self, value):
        """
        Serializes a python dict-like to the controller as JSON.

        :param value: the value that will be serialized
        :type  value: dict-like object
        """
        with self._device_lock:
            if not self._device.isOpen:
                try:
                    self._device.open()
                except:
                    self._device.close()
                    raise IOError("Port could not be opened.")

            try:
                self._device.write(json.dumps(value).encode('ascii'))
                self._device.write('\n')
                self._device.flushOutput()
            except serial.SerialException as e:
                self._device.close()
                raise IOError("Write failed.", e)

    def _read(self):
        """
        Read a line of text from the controller and interpret it as JSON.

        Only JSON objects are allowed as top-level containers in this protocol,
        so the return type should always be a Python dict.

        :return: a Python objcct that represents the interpreted JSON
        :rtype: dict
        """
        with self._device_lock:
            if not self._device.isOpen:
                try:
                    self._device.open()
                except:
                    self._device.close()
                    raise IOError("Port could not be opened.")
            device = self._device

        try:
            line = device.readline().decode('ascii')
            update = json.loads(line)
            self.merge(update)
            self._last_timestamp = time.clock()
        except Exception as e:
            raise IOError("Read failed.", e)

    def _receive(self):
        """
        Loops to receive data from the controller hardware.
        """
        while self._running:
            try:
                self._read()
            except IOError:
                pass
