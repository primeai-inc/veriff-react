# Veriff React Native SDK

## Getting started

To start using the Veriff React Native SDK follow the steps described
[here](https://developers.veriff.com/#react-native-sdk-integration).

## Usage

```javascript
import VeriffSdk from '@veriff/react-native-sdk';

var result = await VeriffSdk.launchVeriff({ sessionUrl: SESSION_URL });
switch (result.status) {
  case VeriffSdk.statusDone:
    // user submitted the images and completed the flow
    // note that this does not mean a final decision yet
    break;
  case VeriffSdk.statusCanceled:
    // user canceled the flow before completing
    break;
  case VeriffSdk.statusError:
    // the flow could not be completed due to an error
    console.log("Veriff verification failed with error=" + result.error);
    break;
} 
```

See more details [here](https://developers.veriff.com/#using-the-veriff-react-native-sdk).
