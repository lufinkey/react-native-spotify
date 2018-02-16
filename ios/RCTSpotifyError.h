//
//  RCTSpotifyError.h
//  RCTSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface RCTSpotifyError : NSObject

-(id)initWithError:(NSError*)error;
-(id)initWithCode:(NSString*)code message:(NSString*)message;

@property (nonatomic, readonly) NSString* code;
@property (nonatomic, readonly) NSString* message;

@end
