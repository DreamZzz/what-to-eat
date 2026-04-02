/**
 * @format
 */

import { AppRegistry } from 'react-native';
import * as Sentry from '@sentry/react-native';
import App from './App';
import { name as appName } from './app.json';
import runtimeConfig from './src/app/config/runtime';

// Import gesture handler (required for React Navigation)
import 'react-native-gesture-handler';

if (runtimeConfig.sentryDsn) {
  Sentry.init({
    dsn: runtimeConfig.sentryDsn,
    environment: runtimeConfig.environment,
    // Only enable tracing in production to avoid noise in local/dev
    tracesSampleRate: runtimeConfig.environment === 'production' ? 0.2 : 0,
  });
}

AppRegistry.registerComponent(appName, () => App);
