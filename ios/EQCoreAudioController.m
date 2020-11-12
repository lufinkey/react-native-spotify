//
//  EQCoreAudioController.m
//  SimplePlayer with EQ
//
//  Created by Daniel Kennett on 02/04/2012.
//
//
//  Adapted by Freddy Snijder, 31102020
//
//
/*
 Copyright (c) 2011, Spotify AB
 All rights reserved.
 
 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright
 notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 notice, this list of conditions and the following disclaimer in the
 documentation and/or other materials provided with the distribution.
 * Neither the name of Spotify AB nor the names of its contributors may 
 be used to endorse or promote products derived from this software 
 without specific prior written permission.
 
 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL SPOTIFY AB BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#import "EQCoreAudioController.h"

#import <AVFoundation/AVFoundation.h>

#import "alphabeatsapp-Swift.h"

@implementation EQCoreAudioController {
	AUNode _eqNode;
	AudioUnit _eqUnit;
  
  id<AudioFilter> _audioFilter;
}

@synthesize audioFilter = _audioFilter;

- (id)init {
    self = [super init];
 
    if (self) {
      _eqNode = 0;
      _eqUnit = NULL;
      _audioFilter = NULL;
    }
 
    return self;
}

#pragma mark - Setting up the EQ

-(BOOL)connectOutputBus:(UInt32)sourceOutputBusNumber ofNode:(AUNode)sourceNode toInputBus:(UInt32)destinationInputBusNumber ofNode:(AUNode)destinationNode inGraph:(AUGraph)graph error:(NSError **)error {
	
	// Override this method to connect the source node to the destination node via an EQ node.
  
  NSLog( @"Connecting equalizer ...");
  
  // TODO : Would it be possible to use the audioUnit of this equalizer in the graph?
  // AVAudioUnitEQ* equalizer = [[AVAudioUnitEQ alloc] initWithNumberOfBands:5];
	
	// A description for the EQ Device
  // AudioComponentDescription eqDescription = [equalizer audioComponentDescription];
  AudioComponentDescription eqDescription;
	eqDescription.componentType = kAudioUnitType_Effect;
  eqDescription.componentSubType = kAudioUnitSubType_NBandEQ;
	eqDescription.componentManufacturer = kAudioUnitManufacturer_Apple;
	eqDescription.componentFlags = 0;
  eqDescription.componentFlagsMask = 0;
  	
	// Add the EQ node to the AUGraph
	OSStatus status = AUGraphAddNode(graph, &eqDescription, &_eqNode);
	if (status != noErr) {
    NSLog(@"[%@ %@]: %@", NSStringFromClass([self class]), NSStringFromSelector(_cmd), @"Couldn't add EQ node");
		return NO;
  }
  	
	// Get the EQ Audio Unit from the node so we can set bands directly later
	status = AUGraphNodeInfo(graph, _eqNode, NULL, &_eqUnit);
	if (status != noErr) {
    NSLog(@"[%@ %@]: %@", NSStringFromClass([self class]), NSStringFromSelector(_cmd), @"Couldn't get EQ unit");
    return NO;
  }
  
  NSError* filterError;
  _audioFilter = [[FirstOrderHighpassFilterSimulator alloc] initWithEqualizer:_eqUnit error:&filterError];
  
  if (filterError) {
    NSLog(@"[%@ %@]: %@ : %@", NSStringFromClass([self class]), NSStringFromSelector(_cmd), @"Failed to create high-pass filter", filterError);
    return NO;
  }
  
  if (![_audioFilter setup]) {
    NSLog(@"[%@ %@]: %@", NSStringFromClass([self class]), NSStringFromSelector(_cmd), @"Failed to setup high-pass filter");
    return NO;
  }
  
  // TEST
  //  if (![_audioFilter updateCutoffFrequencyWithFrequency:10000]) {
  //    NSLog(@"[%@ %@]: %@", NSStringFromClass([self class]), NSStringFromSelector(_cmd), @"high-pass _audioFilter TEST FAILED");
  //  }
    
  [self describeEqualizer];
  	
	// Connect the output of the source node to the input of the EQ node
	status = AUGraphConnectNodeInput(graph, sourceNode, sourceOutputBusNumber, _eqNode, 0);
	if (status != noErr) {
    NSLog(@"[%@ %@]: %@", NSStringFromClass([self class]), NSStringFromSelector(_cmd), @"Couldn't connect converter to eq");
    return NO;
  }
	
	// Connect the output of the EQ node to the input of the destination node, thus completing the chain.
	status = AUGraphConnectNodeInput(graph, _eqNode, 0, destinationNode, destinationInputBusNumber);
	if (status != noErr) {
    NSLog(@"[%@ %@]: %@", NSStringFromClass([self class]), NSStringFromSelector(_cmd), @"Couldn't connect eq to output");
    return NO;
  }
	
	return YES;
}

-(void)disposeOfCustomNodesInGraph:(AUGraph)graph {
	
	// Shut down our unit.
  _audioFilter = NULL;
	_eqUnit = NULL;
	
	// Remove the unit's node from the graph.
	AUGraphRemoveNode(graph, _eqNode);
	_eqNode = 0;
}

-(void)describeEqualizer {
  if (!_eqUnit) {
    NSLog(@"[%@ %@]: No equalizer available, unable to describe", NSStringFromClass([self class]), NSStringFromSelector(_cmd));
    return;
  }
  
  AudioUnitParameterValue value;
  OSStatus status = AudioUnitGetParameter(_eqUnit,
                                          kAUNBandEQParam_GlobalGain,
                                          kAudioUnitScope_Global,
                                          0,
                                          &value);
  if (status != noErr) {
    NSLog(@"[%@ %@]: Global gain : Couldn't get value", NSStringFromClass([self class]), NSStringFromSelector(_cmd));
  } else {
    NSLog(@"[%@ %@]: Global gain : %f", NSStringFromClass([self class]), NSStringFromSelector(_cmd), value);
  }
  
  AudioUnitParameterValue numBands;
  UInt32 propSize = sizeof(numBands);
  status = AudioUnitGetProperty(_eqUnit,
                                kAUNBandEQProperty_NumberOfBands,
                                kAudioUnitScope_Global,
                                0,
                                &numBands,
                                &propSize);
  if (status != noErr) {
    NSLog(@"[%@ %@]: %@", NSStringFromClass([self class]), NSStringFromSelector(_cmd), @"Couldn't get number of bands of equalizer");
  } else {
    NSLog(@"[%@ %@]: Number of equalizer bands : %f", NSStringFromClass([self class]), NSStringFromSelector(_cmd), numBands);
    if (numBands < 1.0) {
      NSLog(@"[%@ %@]: Number of equalizer is zero, assuming 1 band, to read out some values ...", NSStringFromClass([self class]), NSStringFromSelector(_cmd));
      numBands = 1.0;
    }
  }

  [self getValuesFor:kAUNBandEQParam_FilterType forNumBands:(int)numBands withName:@"FilterType"];
  [self getValuesFor:kAUNBandEQParam_Bandwidth forNumBands:(int)numBands withName:@"Bandwidth"];
  [self getValuesFor:kAUNBandEQParam_BypassBand forNumBands:(int)numBands withName:@"BypassBand"];
  [self getValuesFor:kAUNBandEQParam_Frequency forNumBands:(int)numBands withName:@"Frequency"];
  [self getValuesFor:kAUNBandEQParam_Gain forNumBands:(int)numBands withName:@"Gain"];
}

-(void)getValuesFor:(AudioUnitParameterID)paramID forNumBands:(int)numBands withName:(NSString*)name {
  OSStatus status;
  AudioUnitParameterValue value;
  
  for (int bandIdx = 0; bandIdx < numBands; bandIdx++) {
    AudioUnitParameterID parameterID = paramID + bandIdx;
    status = AudioUnitGetParameter(_eqUnit,
                                   parameterID,
                                   kAudioUnitScope_Global,
                                   0,
                                   &value);
    if (status != noErr) {
      NSLog(@"[%@ %@]: %@ : Band %i : Couldn't get value", NSStringFromClass([self class]), NSStringFromSelector(_cmd), name, bandIdx);
    } else {
      NSLog(@"[%@ %@]: %@ : Band %i : %f", NSStringFromClass([self class]), NSStringFromSelector(_cmd), name, bandIdx, value);
    }
  }
}


@end
