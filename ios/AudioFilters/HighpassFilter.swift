//
//  HighpassFilter.swift
//  alphabeatsapp
//
//  Created by Freddy Snijder on 03/11/2020.
//

import Foundation

import AVFoundation


@objc public class HighpassFilter: NSObject, AudioFilter  {
  
  public var audioEqualizer: AudioUnit
  public var isEnabled: Bool
  
  @objc public init(equalizer: AudioUnit) {
    audioEqualizer = equalizer
    isEnabled = false;
  }
  
  deinit {
    AudioUnitUninitialize(audioEqualizer);
  }
  
  @objc public func setup() -> Bool {
    // set first band to kAUNBandEQFilterType_2ndOrderButterworthHighPass
    let success = _setParam(parameter: kAUNBandEQParam_FilterType,
                       toValue: AudioUnitParameterValue(kAUNBandEQFilterType_2ndOrderButterworthHighPass),
                       description: "setup first band as high-pass filter");
    if (!success) {
      return success
    }
    
    // Init the EQ
    let status = AudioUnitInitialize(audioEqualizer);
    if (status != noErr) {
      _log(methodName: "\(#function)", message: "unable to initialize the equalizer: OSState = \(status)");
      return false;
    }
    
    return true
  }
  
  @objc public func reset() -> Bool {
    var success = true
    if (isEnabled) {
      success = updateCutoffFrequency(frequency:0) && success;
    }
    
    success = disable() && success;
    
    return success;
  }

  @objc public func enable() -> Bool {
    let success = _setParam(parameter: kAUNBandEQParam_BypassBand,
                       toValue: 0,
                       description: "enable first equalizer band");
    
    if (success) {
      isEnabled = true
    }
    
    return success
  }

  @objc public func disable() -> Bool {
    let success = _setParam(parameter: kAUNBandEQParam_BypassBand,
                       toValue: 1,
                       description: "disable first equalizer band");
    
    if (success) {
      isEnabled = false
    }
    
    return success
  }

  @objc public func updateCutoffFrequency(frequency: Float32) -> Bool {
    if (!isEnabled) {
      _log(methodName: "updateCutoffFrequency", message: "Error : Filter is disabled unable to update cuttoff frequency")
      return false;
    }
    
    if (frequency < 20) {
      // Disable first band
      return _setParam(parameter: kAUNBandEQParam_BypassBand,
                  toValue: 1,
                  description: "disable first equalizer band");
    }
    
    // Ensure that the first band is enabled
    let success = _setParam(parameter: kAUNBandEQParam_BypassBand,
                       toValue: 0,
                       description: "enable first equalizer band");
    if (!success) {
      return success
    }

    return _setParam(parameter: kAUNBandEQParam_Frequency,
                toValue: AudioUnitParameterValue(frequency),
                description: "set cutoff frequency");
  }
  
}
