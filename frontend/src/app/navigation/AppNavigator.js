import React from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { NavigationContainer, DefaultTheme } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createStackNavigator } from '@react-navigation/stack';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/Ionicons';
import { useAuth } from '../providers/AuthContext';
import LoginScreen from '../../features/auth/screens/LoginScreen';
import RegisterScreen from '../../features/auth/screens/RegisterScreen';
import ForgotPasswordScreen from '../../features/auth/screens/ForgotPasswordScreen';
import HomeScreen from '../../features/meal/screens/HomeScreen';
import MealFormScreen from '../../features/meal/screens/MealFormScreen';
import MealResultsScreen from '../../features/meal/screens/MealResultsScreen';
import RecipeDetailScreen from '../../features/meal/screens/RecipeDetailScreen';
import ProfileScreen from '../../features/profile/screens/ProfileScreen';

const RootStack = createStackNavigator();
const AppStack = createStackNavigator();
const Tab = createBottomTabNavigator();

const navigationTheme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    background: '#FFF8F1',
    card: '#FFFDF9',
    border: '#F0D8C4',
    primary: '#B85C38',
    text: '#281B13',
  },
};

const TabIcon = ({ name, focused }) => (
  <Icon
    name={focused ? name : `${name}-outline`}
    size={20}
    color={focused ? '#B85C38' : '#9B7B66'}
  />
);

const HeaderBackIcon = ({ tintColor }) => (
  <Icon
    name="chevron-back"
    size={22}
    color={tintColor || '#281B13'}
    style={styles.headerBackIcon}
  />
);

const renderTabBarIcon = (routeName, focused) => {
  if (routeName === 'HomeTab') {
    return <TabIcon name="home" focused={focused} />;
  }

  return <TabIcon name="person-circle" focused={focused} />;
};

function HomeStackNavigator() {
  return (
    <AppStack.Navigator
      screenOptions={{
        headerStyle: { backgroundColor: '#FFFDF9' },
        headerTintColor: '#281B13',
        headerTitleStyle: { fontWeight: '800' },
        contentStyle: { backgroundColor: '#FFF8F1' },
        headerBackImage: HeaderBackIcon,
      }}
    >
      <AppStack.Screen
        name="Home"
        component={HomeScreen}
        options={{ headerShown: false }}
      />
      <AppStack.Screen
        name="MealForm"
        component={MealFormScreen}
        options={{ title: '完善偏好' }}
      />
      <AppStack.Screen
        name="MealResults"
        component={MealResultsScreen}
        options={{ title: '菜谱结果' }}
      />
    </AppStack.Navigator>
  );
}

function TabsNavigator() {
  const insets = useSafeAreaInsets();

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: '#B85C38',
        tabBarInactiveTintColor: '#9B7B66',
        tabBarStyle: {
          backgroundColor: '#FFFDF9',
          borderTopColor: '#F0D8C4',
          paddingBottom: Math.max(10, insets.bottom),
          height: 62 + insets.bottom,
        },
        tabBarIcon: ({ focused }) => renderTabBarIcon(route.name, focused),
      })}
    >
      <Tab.Screen
        name="HomeTab"
        component={HomeStackNavigator}
        options={{ tabBarLabel: '首页' }}
      />
      <Tab.Screen
        name="ProfileTab"
        component={ProfileScreen}
        options={{ tabBarLabel: '我的' }}
      />
    </Tab.Navigator>
  );
}

const LoadingGate = () => (
  <View style={styles.loadingGate}>
    <ActivityIndicator size="large" color="#B85C38" />
    <Text style={styles.loadingGateText}>
      正在准备 What To Eat…
    </Text>
  </View>
);

export default function AppNavigator() {
  const { isAuthenticated, loading } = useAuth();
  const insets = useSafeAreaInsets();

  if (loading) {
    return <LoadingGate />;
  }

  return (
    <NavigationContainer theme={navigationTheme}>
      <RootStack.Navigator
        screenOptions={{
          headerStyle: { backgroundColor: '#FFFDF9' },
          headerTintColor: '#281B13',
          headerTitleStyle: { fontWeight: '800' },
          contentStyle: { backgroundColor: '#FFF8F1' },
          headerStatusBarHeight: insets.top,
          headerBackImage: HeaderBackIcon,
        }}
      >
        {isAuthenticated ? (
          <>
            <RootStack.Screen
              name="HomeTabs"
              component={TabsNavigator}
              options={{ headerShown: false }}
            />
            <RootStack.Screen
              name="RecipeDetail"
              component={RecipeDetailScreen}
              options={{ title: '菜谱详情' }}
            />
          </>
        ) : (
          <>
            <RootStack.Screen
              name="Login"
              component={LoginScreen}
              options={{ headerShown: false }}
            />
            <RootStack.Screen
              name="Register"
              component={RegisterScreen}
              options={{ title: '注册' }}
            />
            <RootStack.Screen
              name="ForgotPassword"
              component={ForgotPasswordScreen}
              options={{ title: '找回密码' }}
            />
          </>
        )}
      </RootStack.Navigator>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  loadingGate: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFF8F1',
  },
  loadingGateText: {
    marginTop: 12,
    color: '#6E5849',
    fontWeight: '600',
  },
  headerBackIcon: {
    marginLeft: 2,
  },
});
