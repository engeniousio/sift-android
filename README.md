# sift-android


## Sift - Unit and UI Tests Parallelization

## How to use in local mode:
### Run tests
- `sift config run -c config.json`

Sift file will be created after `./gradlew installDist` in `runner/build/install/sift/bin/` folder. To call command need to enter path from place where now. E.g. if you in `your_project` folder `runner/build/install/sift/bin/sift config run -c apks/config.json`

#### For running tests need to prepare config.json file:
1. Create debugApk(AndroidStudio Terminal `./gradlew assembleDebug`, file will be in folder /your_project/app/build/outputs/apk/debug/) and androidTestApk(AndroidStudio Terminal `./gradlew assembleAndroidTest` file will be in folder /your_project/app/build/outputs/apk/debug/androidTest), add full file path to config.json, appPackage - debugApk(MAC - `"appPackage": "/Users/user/StudioProjects/your_project/apks/debug.apk"`), testPackage - androidTestApk(MAC - `"testPackage": "/Users/user/StudioProjects/your_project/apks/app-debug-androidTest.apk"`)
2. Add path to directory where tests results will be collected(e.g. MAC - `"outputDirectoryPath": "/Users/user/StudioProjects/your_project/reports/"`)
3. Add Node (host local ip - `"host": "127.0.0.1"`, UDID - devices (from AndroidStudio Terminal `./gradlew adb devices` e.g. `"devices": ["emulator-5554"]` ))
4. Add Android SDK path MAC - `"androidSdkPath": "/Users/user/Library/Android/sdk"`
5. Add test list `"tests": ["io.package.app.screentests/StartScreenTests#testStartScreenLightThemeDisplay", "io.package.app.screentests/StartScreenTests#testStartScreenDarkThemeDisplay", "io.package.app.screentests/StartScreenTests#testStartScreenGetStartClick"]`. Test names can get from `list` command
- `io.package.app.screentests/StartScreenTests#testStartScreenGetStartClick` - one test
- `io.package.app.screentests/StartScreenTests#*` - all tests from one Class
- `io.package.app.screentests/*` - all tests from one Package
- `*/*` - all tests from apk

### Print all tests from the test APK
- `sift config list -c config.json`

Result will be e.g.:

`io.package.app.screentests/ForgotPasswordTests#testEnterRightPassword
io.package.app.screentests/ForgotPasswordTests#testEnterWrongPassword
io.package.app.screentests/ForgotPasswordTests#testStopRecoveringPassword
io.package.app.screentests/ForgotPasswordTests#testUserEmailValidation
io.package.app.screentests/LoginTests#testLoginLogoutUsingEmailAccount
io.package.app.screentests/RegistrationTests#testEmailAndPasswordValidation
io.package.app.screentests/RegistrationTests#testRegistrationSwappingSignInTypeButtons
io.package.app.screentests/RegistrationTests#testRegistrationWithEmailAndPassword
io.package.app.screentests/StartScreenTests#testStartScreenLightThemeDisplay`

## How to use with Orchestrator:
[Orchestrator docs](https://orchestrator.engenious.io/docs)

[Orchestrator dashboard](https://dashboard.orchestrator.engenious.io/)

### Example of **config.json** file (JSON format):

```JSON5
{
    "appPackage": "path to the APK of the application under test",
    "testPackage": "path to the test APK (the androidTest one)",
    "outputDirectoryPath": "path to the directory where tests results will be collected",
    "globalRetryLimit": 1, // attempts for retry for all tests in a run
    "testRetryLimit": 1, // attempts for retry for one test in case of fail
    "testsExecutionTimeout": 120, // max test timeout
    "setUpScriptPath": "script to execute on a node before each test bucket", // optional (not implemented yet)
    "tearDownScriptPath": "script to execute on a node after each test bucket", // optional (not implemented yet)
    "reportTitle": "Local HTML report title",
    "reportSubtitle": "Local HTML report subtitle, optional",
    "nodes": // array of nodes with connected devices
    [
        {
            "name": "Node-1", // (not implemented yet)
            "host": "172.22.22.12", // (not implemented yet)
            "port": 22, // (not implemented yet)
            "username": "node-1", // (not implemented yet)
            "deploymentPath": "path where all necessary stuff will be stored on the node", // deploymentPath used for storing files needed for Orchestrator tests run
            "UDID": {
                 "devices": ["device serials, this is an optional key"],
                 "simulators": ["emulators serials, this is an optional key"]
            },
            "androidSdkPath": "path to the Android SDK root directory",
            "environmentVariables": { // additional parameters passed to tests, this is an optional key
                "env1": "value1"
            },
            "authorization": {  // authorization used for connecting Orchestrator to nodes
                "type": "0", // PrivateKey = '0', PrivateKeyComplex = '1'(not implemented yet), Password = '2'(not implemented yet), Agent = '3'(not implemented yet)
                "data": {
                    "username": "",
                    "password": null,
                    "privateKey": null, // path to SSH key/certificate
                    "publicKey": null,
                    "passphrase": null
                    }
                }
            }
    ],
    "tests": [
        "Identifiers of tests to be included in a test run (use 'sift config list -c config.json' to list test identifiers)"
    ]
}

```

### Requirements:
 - Android SDK
 - Java 8
 - Test APK should have `androidTestImplementation 'io.engenious.sift-android:ondevice:$VERSION'` dependency
   (or `'com.github.engeniousio.sift-android:ondevice:$VERSION'` for SNAPSHOT versions, at 30.11.22 - `'com.github.engeniousio.sift-android:ondevice:master-SNAPSHOT'`)
  <br/>(optional, add it to run tests with complicated names or use various QoL helpers)
 - Tests must run with rule - ScreenshotOnFailureRule
 `import io.engenious.sift.ondevice.ScreenshotOnFailureRule`

### How to Build:
- `./gradlew installDist`
