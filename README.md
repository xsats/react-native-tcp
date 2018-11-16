# TCP in React Native

node's [net](https://nodejs.org/api/net.html) API in React Native

## Install

* Create a new react-native project. [Check react-native getting started](http://facebook.github.io/react-native/docs/getting-started.html#content)

* In your project dir:

```
npm install @staltz/react-native-tcp --save
```
## if using Cocoapods

Update the following line with your path to `node_modules/` and add it to your
podfile:

```ruby
pod 'TcpSockets', :path => '../node_modules/react-native-tcp'
```

## Link in the native dependency

```
react-native link @staltz/react-native-tcp
```

## Additional dependencies

### Due to limitations in the react-native packager, streams need to be hacked in with [rn-nodeify](https://www.npmjs.com/package/rn-nodeify)

1. install rn-nodeify as a dev-dependency
``` npm install --save-dev rn-nodeify ```
2. run rn-nodeify manually
``` rn-nodeify --install stream,process,util --hack ```
3. optionally you can add this as a postinstall script
``` "postinstall": "rn-nodeify --install stream,process,util --hack" ```

## Usage

### package.json

_only if you want to write require('net') or require('tls') in your javascript_

```json
{
  "react-native": {
    "net": "@staltz/react-native-tcp",
    "tls": "@staltz/react-native-tcp/tls"
  }
}
```

### JS

_see/run [index.ios.js/index.android.js](examples/rctsockets) for a complete example, but basically it's just like net_

```js
var net = require('net');
var net = require('tls');
// OR, if not shimming via package.json "react-native" field:
// var net = require('@staltz/react-native-tcp')
// var tls = require('@staltz/react-native-tcp/tls')

var server = net.createServer(function(socket) {
  socket.write('excellent!');
}).listen(12345);

var client = net.createConnection(12345);

client.on('error', function(error) {
  console.log(error)
});

client.on('data', function(data) {
  console.log('message was received', data)
});
```

### TLS support

TLS is only supported in the client interface. To use TLS, use the `tls.connect()`
syntax and not `socket = new tls.Socket()` syntax.

```
const socket = tls.connect({port: 50002, host:'electrum.villocq.com', rejectUnauthorized: false}, () => {
  socket.write('{ "id": 5, "method": "blockchain.estimatefee", "params": [2] }\n')
  console.log('Connected')
})

socket.on('data', (data) => {
  console.log('data:' + data.toString('ascii'))
})
```
