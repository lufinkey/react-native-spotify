
import { NativeModules } from 'react-native';
import NativeEventEmitter from 'react-native-event-emitter';

const Spotify = NativeModules.Spotify;

NativeEventEmitter.registerNativeModule(Spotify);

export default Spotify;
