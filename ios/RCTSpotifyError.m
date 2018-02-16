//
//  RCTSpotifyError.m
//  RCTSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import "RCTSpotifyError.h"

@implementation RCTSpotifyError

@synthesize code = _code;
@synthesize message = _message;

-(id)initWithCode:(NSString*)code message:(NSString*)message
{
	if(self = [super init])
	{
		_code = [NSString stringWithString:code];
		_message = [NSString stringWithString:message];
	}
	return self;
}

@end
