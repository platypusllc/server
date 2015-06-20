var exec = require('cordova/exec');

exports.getVersion = function(success, error) {
    exec(success, error, "Controller", "getVersion", []);
};

exports.isConnected = function(success, error) {
    exec(success, error, "Controller", "isConnected", []);
};

// TODO: Where does the callback function go in here?
exports.addEventListener = function(event, success, error) {
    exec(success, error, "Controller", "addEventListener", [event]);
};

// TODO: Where does the callback function go in here?
exports.removeEventListener = function(event, success, error) {
    exec(success, error, "Controller", "removeEventListener", [event]);
};

exports.send = function(message, success, error) {
    exec(success, error, "Controller", "send", [message]);
};
