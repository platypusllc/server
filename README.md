# Platypus Server #

This is an Apache Cordova-based version of the Platypus Server.  The Server is
a service that runs on mobile devices, connecting to hardware via Platypus
Controllers and providing a REST API for control of a Platypus Vehicle.

## Development ##
* Download and install Android Studio on your machine.
* Download and install Apache Cordova for your machine.
* Checkout this git repository.
* Checkout the platypus-controller-plugin to same parent directory.
  * From: `cordova plugin add ../../platypus-controller-plugin/ --link  --save`
* `cd` into this git repository and run `cordova build`.

## License ##
Copyright 2015 Platypus LLC.  All rights reserved.
Released under New BSD License. See [LICENSE](LICENSE) for details.
