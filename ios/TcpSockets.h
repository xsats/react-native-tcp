/**
 * Copyright (c) 2015-present, Peel Technologies, Inc.
 * All rights reserved.
 */

#import "TcpSocketClient.h"

#import <React/RCTEventEmitter.h>
#import "CocoaAsyncSocketCustomSSL/GCDAsyncSocketCustomSSL.h"

@interface TcpSockets : RCTEventEmitter<SocketClientDelegate>

@end
