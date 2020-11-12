//
//  AudioFilter.swift
//  alphabeatsapp
//
//  Created by Freddy Snijder on 09/11/2020.
//  Copyright © 2020 Facebook. All rights reserved.
//

import Foundation
import AVFoundation

func sizeof<T:FixedWidthInteger>(_ int:T) -> Int {
    return int.bitWidth/UInt8.bitWidth
}

@objc protocol AudioFilter {
  var _equalizer: AudioUnit { get set }
  var _isEnabled: Bool { get set }
  
  func setup() -> Bool
  
  func reset() -> Bool
  func enable() -> Bool
  func disable() -> Bool
  
  func updateCutoffFrequency(frequency: Float32) -> Bool
}

extension AudioFilter {
  
  func _setParam(parameter: AudioUnitParameterID, toValue: AudioUnitParameterValue, description: String) -> Bool {
    let state = AudioUnitSetParameter(_equalizer, parameter, kAudioUnitScope_Global, 0, toValue, 0);
    if (state != noErr) {
      _log(methodName: "\(#function)", message: "unable to \(description): OSState = \(state)");
      return false;
    }
    
    return true;
  }

  func _setProp(parameter: AudioUnitPropertyID, toValue: UnsafeRawPointer, valueSize: UInt32, description: String) -> Bool {
    let state = AudioUnitSetProperty(_equalizer, parameter, kAudioUnitScope_Global, 0, toValue, valueSize);
    if (state != noErr) {
      _log(methodName: "\(#function)", message: "unable to \(description): OSState = \(state)");
      return false;
    }
    
    return true;
  }

  func _log(methodName: String, message: String) {
    NSLog("[\(self) \(methodName)] :\(message)");
  }
  
}
