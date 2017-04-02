# Platypus Server
[![Build Status](https://travis-ci.org/platypusllc/server.svg?branch=master)](https://travis-ci.org/platypusllc/server)

This repository contains source code for the Platypus Android Server. The
Platypus Server provides core functionality for Platypus autonomous platforms,
connecting to various vehicles types and providing a standard interface for
controlling the platforms using high-level commands.

----

Code in this repository is automatically built by Travis-CI.  The `master`
branch is compiled to an APK and uploaded to the Google Play store.

In order to do signed builds, the following environment variables must be set:
```
ANDROID_PLAY_JSON_FILE
ANDROID_RELEASE_STORE_FILE
ANDROID_RELEASE_STORE_PASSWORD
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
```
