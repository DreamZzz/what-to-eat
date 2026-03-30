/**
 * @format
 */

import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';

// Import gesture handler (required for React Navigation)
import 'react-native-gesture-handler';

AppRegistry.registerComponent(appName, () => App);
