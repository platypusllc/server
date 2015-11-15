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
        if value is None:
            del destination[key]
        elif key not in destination:
            destination[key] = value
        else:
            if isinstance(value, dict):
                merge(destination[key], value)
            else:
                destination[key] = value

    return destination
