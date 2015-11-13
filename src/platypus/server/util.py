import collections

"""
a = ObservableDict()
a["foo"]["bar"]["baz"]
b = a["foo"] = AttributeProxy(a, "foo")
c = a["foo"]["bar"] = AttributeProxy(b, "bar")
d = a["foo"]["bar"]["baz"] = AttributeProxy(c, "baz")

a["foo"]["bar"]["baz"] = 3
a["foo"]["bar"].__setitem__("baz", 3)
"""


class AttributeProxy(object):
    """
    Lazy placeholder for an attribute path within an ObservableDict.

    This placeholder exists to represent a link to a particular attribute
    which could be set to a value, without having to actually instantiate the
    tree to this value.

    This prevents key pollution if non-existent keys are queried.
    """
    def __init__(self, parent, key):
        self._parent = parent
        self._key = key
        pass

    def __setitem__(self, key, value):
        self._parent[self._key] = ObservableDict()

    def __getitem__(self, key):
        # If the parent contains this key, then query it.

        # If the parent does not contain this key, then create a proxy.
        return AttributeProxy(self, key)

    def __bool__(self):
        """
        Evaluate to False, so that checks treat this like a missing value.
        """
        return False


class ObservableDict(collections.defaultdict):
    """
    A recursive multi-level default dictionary with observable keys.

    Based on:
    http://stackoverflow.com/q/1904351
    http://stackoverflow.com/q/5369723
    """
    def __init__(self):
        """
        Create a recursive multi-level default dictionary.
        """
        pass

    def __missing__(self, key):
        """
        Return a proxy object if a missing value is queried.

        This proxy object does not add anything to the dict, but remembers
        the path to which it is referring such that it can access this
        path if a value is set on it later.

        :param key: the key that was not found in the dictionary
        :type  key: any
        :return: a proxy for this value
        :rtype: AttributeProxy
        """
        value = ObservableDict()
        self[key] = value
        return value

    def __setitem__(self, key, value):
        print "SET: ", key, value
        super(ObservableDict, self).__setitem__(key, value)


def merge(destination, source):
    """
    Recursively merges one dict into another.

    If conflicting entries are detected, the values from the `source` dict
    will override those in the `destination`.  If a value is not set in the
    `source` dict, then it will remain unchanged in the `destination`.

    To remove entries with this method, set a value explicitly to `None`.

    :param destination: the dictionary into which values will be inserted
    :type  destination: dict
    :param source: the dictionary from which value will be taken
    :type  source: dict
    :return: the destination dict after the merge
    :rtype:  dict
    """
    for key, value in source.items():
        if key not in destination:
            destination[key] = value
        else:
            if isinstance(value, dict):
                merge(destination[key], value)
            else:
                destination[key] = value

    return destination
