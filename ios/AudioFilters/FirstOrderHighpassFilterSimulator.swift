//
//  FirstOrderHighpassFilterSimulator.swift
//  alphabeatsapp
//
//  Created by Freddy Snijder on 11/11/2020.
//

import Foundation

import AVFoundation


let DEFAULT_EQUALIZER_BANDS: [(lower: Float32, upper: Float32)] = [
  (lower: 30, upper: 120),
  (lower: 120, upper: 460),
  (lower: 460, upper: 1800),
  (lower: 1800, upper: 7000),
  (lower: 7000, upper: 20000)
];


class FirstOrderHighpassFilterSimulator: NSObject, AudioFilter  {
  
  var _equalizer: AudioUnit
  var _isEnabled: Bool
  
  var _bands: [(lower: Float32, upper: Float32)]
  
  var _centreFrequencies: [Float32]?
  var _bandwidths: [Float32]?
  var _bandGains: [Float32]?
  
  /// - Parameter equalizer: The created Equalizer AudioUnit
  /// - Parameter bands: Optional array with equalizer bands. Each band is a tuple of an `lower` frequency and an `upper` frequency
  init(equalizer: AudioUnit, bands: [(lower: Float32, upper: Float32)]?) throws {
    _equalizer = equalizer
    
    _bands = bands ?? DEFAULT_EQUALIZER_BANDS;
        
    if (_bands.count == 0) {
      throw AlphaBeatsError.invalidParameter(message: "Empty bands array provided.")
    }
    
    _isEnabled = false;
  }

  /// This initializer can be used from Objective-C
  @objc convenience init(equalizer: AudioUnit, lowerBandFrequencies: [Float32]?, upperBandFrequencies: [Float32]?) throws {
    
    var bands: [(lower: Float32, upper: Float32)]?;
    
    if let lowerBounds = lowerBandFrequencies, let upperBounds = upperBandFrequencies {
      if (lowerBounds.count == 0 || upperBounds.count == 0 || (lowerBounds.count != upperBounds.count)) {
        throw AlphaBeatsError.invalidParameter(message: "lowerBounds.count = \(lowerBounds.count), upperBounds.count = \(upperBounds.count)")
      }
      
      bands = []
      for (lowerBound, upperBound) in zip(lowerBounds, upperBounds) {
        bands?.append((lower: lowerBound, upper: upperBound))
      }
    }
    
    try self.init(equalizer: equalizer, bands: bands)
  }
  
  /// This initializer can be used from Objective-C
  @objc convenience init(equalizer: AudioUnit) throws {
    try self.init(equalizer: equalizer, bands: nil);
  }
  
  deinit {
    AudioUnitUninitialize(_equalizer);
  }
  
  /// Only call once after intstantiation, before your start using the filter
  @objc func setup() -> Bool {
    _bandwidths = _calcBandwiths();
    _centreFrequencies = _calcCentreFrequencies();

    var numBands: UInt32 = UInt32(_bands.count);
    var success = _setProp(parameter: kAUNBandEQProperty_NumberOfBands,
                           toValue: &numBands, valueSize: UInt32(sizeof(numBands)),
                           description: "set number of bands to \(numBands)");
    if (!success) {
      return success
    }
    
    for idx in 0 ..< _bands.count {
      success = _setParam(parameter: kAUNBandEQParam_FilterType + UInt32(idx),
                          toValue: AudioUnitParameterValue(kAUNBandEQFilterType_Parametric),
                          description: "set type of equalizer band \(idx) to parametric") && success;
      
      let f = _centreFrequencies![idx]
      success = _setParam(parameter: kAUNBandEQParam_Frequency + UInt32(idx),
                          toValue: AudioUnitParameterValue(f),
                          description: "set centre frequency of equalizer band \(idx) to \(f) [Hz]") && success;

      let b = _bandwidths![idx]
      success = _setParam(parameter: kAUNBandEQParam_Bandwidth + UInt32(idx),
                          toValue: AudioUnitParameterValue(b),
                          description: "set bandwith of equalizer band \(idx) to \(b) [Octaves]") && success;
    }
    
    success = reset() && success;

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
    _bandGains = [Float32](repeating: 0.0, count: _bands.count)
    
    var success = _updateBandGain();
        
    success = disable() && success;
    
    return success;
  }

  @objc func enable() -> Bool {
    let success = _enableFilter(enable: true);
    
    if (success) {
      _isEnabled = true
    }
    
    return success
  }

  @objc func disable() -> Bool {
    let success = _enableFilter(enable: false);
    
    if (success) {
      _isEnabled = false
    }
    
    return success
  }

  @objc func updateCutoffFrequency(frequency: Float32) -> Bool {
    if (!_isEnabled) {
      _log(methodName: "updateCutoffFrequency", message: "Error: Filter is disabled, unable to update cuttoff frequency")
      return false;
    }
    
    if (frequency < 20) {
      // Disable equalizer
      return _enableFilter(enable: false);
    }
    
    // Ensure the equalizer is enabled
    var success = _enableFilter(enable: true);
    if (!success) {
      return success;
    }
    
    if var bandGains = _bandGains {
      for idx in 0 ..< _bands.count {
        bandGains[idx] = _calcBandGain(cutoffFreq: frequency, atBand: idx);
      }
      
      _bandGains = bandGains
      success = _updateBandGain() && success;
    } else {
      _log(methodName: "updateCutoffFrequency", message: "Error: bandGains not initialized, unable to update cutoff frequency. Did you call setup()?")
      success = false;
    }

    return success;
  }
  
  func _calcBandwiths() -> [Float32] {
    var bandwidth: [Float32] = []
    
    // The bandwidth is specifed as the relationship of the upper bandedge frequency to the
    // lower bandedge frequency in octaves
    for band in _bands {
      let b = log2(band.upper/band.lower);
      
      bandwidth.append(b)
    }
    
    return bandwidth;
  }

  func _calcCentreFrequencies() -> [Float32] {
    var centreFrequencies: [Float32] = []
    
    for band in _bands {
      let c = (band.upper + band.lower)/2;
      
      centreFrequencies.append(c)
    }
    
    return centreFrequencies;
  }
  
  func _enableFilter(enable: Bool) -> Bool {
    var success = true
    for idx in 0 ..< _bands.count {
      success = _setParam(parameter: kAUNBandEQParam_BypassBand + UInt32(idx),
                          toValue: enable ? 0 : 1,
                          description: "enabling equalizer band \(idx)") && success;
    }
    
    return success
  }
  
  func _calcBandGain(cutoffFreq: Float32, atBand bandIdx: Int) -> Float32 {
    let centreFreq = _centreFrequencies![bandIdx];
    
    var gain = 1.0 / (1.0 + (cutoffFreq * cutoffFreq) / ((centreFreq + 1)*(centreFreq + 1)));
    
    gain = 10*log10(gain)
    
    // -96->24
    gain = max(gain, -96.0);
    gain = min(gain, 24.0);

    return gain;
  }
  
  func _updateBandGain() -> Bool {
    var success = true
    for idx in 0 ..< _bands.count {
      let g = _bandGains![idx]
      
      success = _setParam(parameter: kAUNBandEQParam_Gain + UInt32(idx),
                          toValue: AudioUnitParameterValue(g),
                          description: "set gain of equalizer band \(idx)") && success;
    }
    
    return success;
  }

}
