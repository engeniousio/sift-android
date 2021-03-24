# sift-android


## Sift - Unit and UI Tests Parallelization

### How to use:
- `sift run --config config.json` run tests
- `sift list --config config.json` print all tests from the test APK

### Example of **config.json** file (JSON format):

```JSON5
{
    "token": "Orchestrator token",
    "testPlan": "Orchestrator test plan name",
    "status": "test status to include in the run (enabled, disabled, quarantined)",
    "outputDirectoryPath": "path to the directory where tests results will be collected",
    "applicationPackage": "path to the APK of the application under test",
    "testApplicationPackage": "path to the test APK (the androidTest one)",
    "rerunFailedTest": 1, // attempts for retry
    "testsBucket": 1, // number of tests which will be send on each executor at the same time (not implemented yet)
    "testsExecutionTimeout": 120, // timeout (not implemented yet, hardcoded to 30s)
    "setUpScriptPath": "script to execute on a node before each tests bucket", // optional (not implemented yet)
    "tearDownScriptPath": "script to execute on a node after each tests bucket", // optional (not implemented yet)
    "nodes": // array of nodes with connected devices
    [ // only UDID.devices of the first node is used currently
        {
            "name": "Node-1", // (not implemented yet)
            "host": "172.22.22.12", // (not implemented yet)
            "port": 22, // (not implemented yet)
            "username": "node-1", // (not implemented yet)
            "password": "password", // (not implemented yet)
            "androidSdkPath": "path to the Android SDK root directory",
            "deploymentPath": "path where all necessary stuff will be stored on the node", // (not implemented yet)
            "UDID": {
                        "devices": ["devices udid, can be null"],
                        "simulators": ["AVD names of emulators to use for tests, can be null (not supported yet)"]
            },
            "environmentVariables": { // additional parameters passed to tests, optional (not supported yet)
                "env1": "value1"
            }
        }
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
