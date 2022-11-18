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

### Print all tests from the test APK
- `sift config list -c config.json`

Result will be e.g.:

`io.package.app.screentests.ForgotPasswordTests#testEnterRightPassword
io.package.app.screentests.ForgotPasswordTests#testEnterWrongPassword
io.package.app.screentests.ForgotPasswordTests#testStopRecoveringPassword
io.package.app.screentests.ForgotPasswordTests#testUserEmailValidation
io.package.app.screentests.LoginTests#testLoginLogoutUsingEmailAccount
io.package.app.screentests.RegistrationTests#testEmailAndPasswordValidation
io.package.app.screentests.RegistrationTests#testRegistrationSwappingSignInTypeButtons
io.package.app.screentests.RegistrationTests#testRegistrationWithEmailAndPassword
io.package.app.screentests.StartScreenTests#testStartScreenLightThemeDisplay`

Before add this list to `config.json` file need to change `.` before test class to `/`

### Example of **config.json** file (JSON format):

```JSON5
{
    "appPackage": "path to the APK of the application under test",
    "testPackage": "path to the test APK (the androidTest one)",
    "outputDirectoryPath": "path to the directory where tests results will be collected",
    "globalRetryLimit": 1, // attempts for retry for all tests in a run
    "testRetryLimit": 1, // attempts for retry for one test
    "testsExecutionTimeout": 120, // test timeout
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
            "pathToCertificate": "path to SSH key/certificate", // (not implemented yet)
            "deploymentPath": "path where all necessary stuff will be stored on the node", // (not implemented yet)
            "UDID": {
                        "devices": ["device serials, this is an optional key"],
                        "simulators": ["(not supported yet) names of emulators (AVDs) to use for tests, this is an optional key)"]
            },
            "androidSdkPath": "path to the Android SDK root directory",
            "environmentVariables": { // additional parameters passed to tests, this is an optional key
                "env1": "value1"
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
   (or `'com.github.engeniousio.sift-android:ondevice:$VERSION'` for SNAPSHOT versions, at 18.11.22 - `'com.github.engeniousio.sift-android:ondevice:master-SNAPSHOT'`)
  <br/>(optional, add it to run tests with complicated names or use various QoL helpers)

### How to Build:
- `./gradlew installDist`
