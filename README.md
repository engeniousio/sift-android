# sift-android


## Sift - Unit and UI Tests Parallelization

### How to use in local mode:
- `sift config run -c config.json` run tests
- `sift config list -c config.json` print all tests from the test APK

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
   (or `'com.github.engeniousio.sift-android:ondevice:$VERSION'` for SNAPSHOT versions)
  <br/>(optional, add it to run tests with complicated names or use various QoL helpers)

### How to Build:
- `./gradlew installDist`
