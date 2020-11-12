//
//  HighpassFilter.swift
//  alphabeatsapp
//
//  Created by Freddy Snijder on 03/11/2020.
//

import Foundation

import AVFoundation


class HighpassFilter: NSObject, AudioFilter  {
  
  var _equalizer: AudioUnit
  var _isEnabled: Bool
  
  @objc init(equalizer: AudioUnit) {
    _equalizer = equalizer
    _isEnabled = false;
  }
  
  deinit {
    AudioUnitUninitialize(_equalizer);
  }
  
  @objc func setup() -> Bool {
    // set first band to kAUNBandEQFilterType_2ndOrderButterworthHighPass
    let success = _setParam(parameter: kAUNBandEQParam_FilterType,
                       toValue: AudioUnitParameterValue(kAUNBandEQFilterType_2ndOrderButterworthHighPass),
                       description: "setup first band as high-pass filter");
    if (!success) {
      return success
    }
    
    // Init the EQ
    let status = AudioUnitInitialize(_equalizer);
    if (status != noErr) {
      _log(methodName: "\(#function)", message: "unable to initialize the equalizer: OSState = \(status)");
      return false;
    }
    
    return true
  }
  
  @objc func reset() -> Bool {
    var success = true
    if (_isEnabled) {
      success = updateCutoffFrequency(frequency:0) && success;
    }
    
    success = disable() && success;
    
    return success;
  }

  @objc func enable() -> Bool {
    let success = _setParam(parameter: kAUNBandEQParam_BypassBand,
                       toValue: 0,
                       description: "enable first equalizer band");
    
    if (success) {
      _isEnabled = true
    }
    
    return success
  }

  @objc func disable() -> Bool {
    let success = _setParam(parameter: kAUNBandEQParam_BypassBand,
                       toValue: 1,
                       description: "disable first equalizer band");
    
    if (success) {
      _isEnabled = false
    }
    
    return success
  }

  @objc func updateCutoffFrequency(frequency: Float32) -> Bool {
    if (!_isEnabled) {
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
