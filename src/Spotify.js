
import { NativeModules } from 'react-native';
import NativeModuleEvents from 'react-native-events';

const Spotify = NativeModules.Spotify;
NativeModuleEvents.registerNativeModule(Spotify);

export default Spotify;
