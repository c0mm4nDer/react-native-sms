//@flow
'use strict';

import { NativeModules, PermissionsAndroid, Platform } from 'react-native'

async function send(options: Object, callback: (completed: boolean, cancelled: boolean, error: boolean) => void) {
  if (Platform.OS === 'android') {
    try {
      let authorized = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.READ_SMS)
    } catch (error) {

    }
  }
  NativeModules.SendSMS.send(options, callback);
}

async function getSims() {
  if (Platform.OS === 'android') {
    try {
      let authorized = await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE)
    } catch (error) {

    }
    return await NativeModules.SendSMS.getSims();
  }
  return 1;
}

let SendSMS = {
  send,
  getSims
}

module.exports = SendSMS;
