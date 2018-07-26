/**
 * Copyright (c) 2017-present, Edge.
 * All rights reserved.
 *
 * @providesModule TlsSockets
 * @flow
 */

'use strict';

var ipRegex = require('ip-regex');

var Socket = require('../TcpSocket');
var Server = require('../TcpServer');

exports.createServer = function(connectionListener: (socket: Socket)  => void) : Server {
  return new Server(connectionListener);
};

// TODO : determine how to properly overload this with flow
exports.connect = exports.createConnection = function() : Socket {
  if (arguments[0] !== null && typeof arguments[0] === 'object' && arguments[0].socket != null) {
    var existingSocket = arguments[0].socket;
    return existingSocket._upgradeToSecure(arguments[1]);
  }
  var tcpSocket = new Socket();
  tcpSocket._enableSsl()
  console.log('creating new tls', arguments);
  return Socket.prototype.connect.apply(tcpSocket, tcpSocket._normalizeConnectArgs(arguments));
};

exports.isIP = function(input: string) : number {
  var result = 0;
  if (ipRegex.v4({exact: true}).test(input)) {
    result = 4;
  } else if (ipRegex.v6({exact: true}).test(input)) {
    result = 6;
  }
  return result;
};

exports.isIPv4 = function(input: string) : boolean {
  return exports.isIP(input) === 4;
};

exports.isIPv6 = function(input: string) : boolean {
  return exports.isIP(input) === 6;
};

exports.Socket = Socket;
exports.Server = Server;
