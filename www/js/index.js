/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
var app = {
    // Application Constructor
    initialize: function() {
        this.bindEvents();
    },

    // Bind Event Listeners
    //
    // Bind any events that are required on startup. Common events are:
    // 'load', 'deviceready', 'offline', and 'online'.
    bindEvents: function() {
        document.addEventListener('deviceready', this.onDeviceReady, false);

        if (window.DeviceOrientationEvent) {
            window.addEventListener('deviceorientation', this.onDeviceOrientation, false);
        } else {
            console.log("Device does not support orientation events!");
            document.getElementById("hasOrientationEvent").innerHTML = "Not supported."
        }

        if (window.DeviceMotionEvent) {
            window.addEventListener('devicemotion', this.onDeviceMotion, false);
        } else {
            console.log("Device does not support motion events!");
            document.getElementById("hasMotionEvent").innerHTML = "Not supported."
        }
    },

    // deviceready Event Handler
    //
    // The scope of 'this' is the event. In order to call the 'receivedEvent'
    // function, we must explicitly call 'app.receivedEvent(...);'
    onDeviceReady: function() {
        app.receivedEvent('deviceready');
    },

    // Update DOM on a Received Event
    receivedEvent: function(id) {
        var parentElement = document.getElementById(id);
        var listeningElement = parentElement.querySelector('.listening');
        var receivedElement = parentElement.querySelector('.received');

        listeningElement.setAttribute('style', 'display:none;');
        receivedElement.setAttribute('style', 'display:block;');

        console.log('Received Event: ' + id);
    },

    onDeviceOrientation: function(eventData) {
        var info, xyz = "[A, B, G]";

        // Grab the acceleration from the results
        info = xyz.replace("A", eventData.alpha);
        info = info.replace("B", eventData.beta);
        info = info.replace("G", eventData.gamma);
        document.getElementById("orientationPose").innerHTML = info;

        // Grab the refresh interval from the results
        info = eventData.interval;
        document.getElementById("orientationInterval").innerHTML = info;       
    },

    onDeviceMotion: function(eventData) {
        var info, xyz = "[X, Y, Z]";

        // Grab the acceleration from the results
        var acceleration = eventData.acceleration;
        info = xyz.replace("X", acceleration.x);
        info = info.replace("Y", acceleration.y);
        info = info.replace("Z", acceleration.z);
        document.getElementById("motionAccel").innerHTML = info;

        // Grab the rotation rate from the results
        var rotation = eventData.rotationRate;
        info = xyz.replace("X", rotation.alpha);
        info = info.replace("Y", rotation.beta);
        info = info.replace("Z", rotation.gamma);
        document.getElementById("motionRotation").innerHTML = info;

        // Grab the refresh interval from the results
        info = eventData.interval;
        document.getElementById("motionInterval").innerHTML = info;
    }
};

app.initialize();