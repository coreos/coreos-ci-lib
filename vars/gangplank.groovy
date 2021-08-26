// Run gangplank

// Available parameters:
//     artifacts:    list    -- list of artifacts to be built
//     extraFlags:   string  -- Extra flags to use
//     image:        string  -- Name of the image to be used
//     mode:         string  -- Gangplank Mode
//     workDir:      string  -- Cosa working directory

def buildArtifact(params = [:]) {
    gangplankCmd = _getMode(params)
    gangplankCmd = _getArtifacts(params, gangplankCmd)
    _runGangplank(gangplankCmd)
}

// Available parameters:
//     artifacts:     list    -- List of artifacts to be built
//     extraFlags:    string  -- Extra flags to use
//     image:         string  -- Name of the image to be used
//     mode:          string  -- Gangplank Mode
//     minioServeDir: string  -- Location to service minio from
//     workDir:       string  -- Cosa working directory

def buildParallelArtifacts(params = [:]) {
    gangplankCmd = _getMode(params)
    if (params['artifacts']) {
        def artifacts = params['artifacts']
        def configFile = startMinio(params)

        // Call one pod for each artifact in parallel
        parallel artifacts.inject([:]) { d, i -> d[i] = {
            startParallel(configFile, "${gangplankCmd} -A ${i} ")
        }; d }
        _finalize(params)
    }
}

// Available parameters:
//     artifacts:    list    -- List of artifacts to be built
//     extraFlags:   string  -- Extra flags to use
//     singlePod:    boolean -- Run generateSinglePod

def generateSpec(params = [:]) {
    if(params['singlePod']) {
        gangplankCmd = "gangplank generateSinglePod  "
    } else {
        gangplankCmd = "gangplank generate "
    }
    gangplankCmd = _getArtifacts(params, gangplankCmd)
    gangplankCmd = _getFlags(params, gangplankCmd)
    fileName = "/tmp/jobspec-${UUID.randomUUID()}.spec"
    gangplankCmd += "--yaml-out ${fileName} "
    _runGangplank(gangplankCmd)
    return fileName
}

// Available parameters:
//     configFile:   string  -- Name of the Minio config file previously created
//     gangplankCmd: string  -- Gangplank command to run
def startParallel(configFile, gangplankCmd) {
    gangplankCmd += "-m ${configFile}"
    _runGangplank(gangplankCmd)
}

// Available parameters:
//     artifacts:    list    -- List of artifacts to be built
//     extraFlags:   string  -- Extra flags to use
//     image:        string  -- Name of the image to be used
//     mode:         string  -- Gangplank Mode
//     minioServeDir string  -- Location to service minio from
//     workDir:      string  -- Cosa working directory
def startMinio(params =[:]) {
    def configFile = "/tmp/${UUID.randomUUID()}-minio.yaml"
    def minioServeDir = params.get('minioServeDir', "/srv")
    def gangplankCmd = "gangplank minio -m ${configFile} -d ${minioServeDir}"

    // Minio needs to run in background, JENKINS_NODE_COOKIE allows it
    gangplankCmd = "export JENKINS_NODE_COOKIE=dontKillMe && nohup ${gangplankCmd} &"
    _runGangplank(gangplankCmd)
    // Workaround - Wait for minio to start
    shwrap("sleep 0.5")

    return configFile
}

def _runGangplank(gangplankCmd) {
    try {
        shwrap("${gangplankCmd}")
    } catch (Exception e) {
        print(e)
        currentBuild.result = 'ABORTED'
        error("Error running gangplank.")
    }
}

// Available parameters:
//     artifacts:     list    -- List of artifacts to be built
//     extraFlags:    string  -- Extra flags to use
//     mode:          string  -- Gangplank Mode
//     spec:          string  -- Name of the spec file
def runSpec(params =[:]) {
    if (params['spec']) {
        gangplankCmd = _getMode(params)
        gangplankCmd += "--spec ${params['spec']} "
        _runGangplank(gangplankCmd)
    } else {
        error("runSpec requires a spec param.")
    }
}

// Available parameters:
//     cmd:           string  -- the single command to run
//     mode:          string  -- Gangplank Mode
//     extraFlags:    string  -- Extra flags to use
def runSingleCmd(params = [:]) {
    if (params['cmd']) {
        gangplankCmd = _getMode(params)
        gangplankCmd += " --singleCmd \"${params['cmd']}\""
        _runGangplank(gangplankCmd)
    } else {
        error("runSingleCmd requires a cmd param.")
    }
}

// A function to wrap runSpec and runSingleCmd based on params
// builder host. Accepts a params map with the usual parameters for
// a call to runSpec or runSingleCmd.
def runGangplank(params = [:]) {
    if (params['spec']) {
        runSpec(params)
    } else {
        runSingleCmd(params)
    }
}

def _finalize(params = [:]) {
    gangplankCmd = "gangplank pod "
    gangplankCmd = _getImage(params, gangplankCmd)
    gangplankCmd = _getBucket(params, gangplankCmd)
    gangplankCmd += " -A finalize"
    _runGangplank(gangplankCmd)
}

def _getArtifacts(params =[:], gangplankCmd) {
   if (params['artifacts']) {
        def artifacts = params['artifacts'].join(",")
        gangplankCmd += "--build-artifact ${artifacts} "
        return gangplankCmd
    }
    return gangplankCmd
}

def _getFlags(params = [:], gangplankCmd) {
    if (params['extraFlags'])  {
        gangplankCmd += params['extraFlags'] + " "
        return gangplankCmd
    }
    return gangplankCmd
}

def _getImage(params = [:], gangplankCmd) {
    if (params['image']) {
        gangplankCmd += "--image ${params['image']} "
        return gangplankCmd
    }
    return gangplankCmd
}

def _getArch(params = [:], gangplankCmd) {
    if (params['arch']) {
        gangplankCmd += "--arch ${params['arch']} "
        return gangplankCmd
    }
    return gangplankCmd
}

def _getMode(params = [:]) {
    gangplankCmd = "gangplank " + params.get('mode', "pod") + " "
    gangplankCmd = _getFlags(params, gangplankCmd)
    gangplankCmd = _getImage(params, gangplankCmd)
    gangplankCmd = _getArch(params, gangplankCmd)
    gangplankCmd = _getWorkDir(params, gangplankCmd)
    return gangplankCmd
}

def _getWorkDir(params = [:], gangplankCmd) {
    if(params['cosaDir']) {
        gangplankCmd += "--workDir ${utils.getCosaDir(params)} "
        return gangplankCmd
    }
    return gangplankCmd
}
