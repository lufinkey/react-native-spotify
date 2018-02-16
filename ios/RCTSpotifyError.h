//
//  RCTSpotifyError.h
//  RCTSpotify
//
//  Created by Luis Finke on 2/15/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface RCTSpotifyError : NSObject

-(id)initWithCode:(NSString*)code message:(NSString*)message;
-(id)initWithError:(NSError*)error;

+(instancetype)errorWithCode:(NSString*)code message:(NSString*)message;
+(instancetype)errorWithError:(NSError*)error;

@property (nonatomic, readonly) NSString* code;
@property (nonatomic, readonly) NSString* message;

@end
