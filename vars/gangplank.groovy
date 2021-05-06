// Run gangplank

// Available parameters:
//     artifacts:    list    -- list of artifacts to be built
//     extraFlags    string  -- Extra flags to use
//     image:        string  -- Name of the image to be used
//     mode:         string  -- Gangplank Mode
//     workDir:      string  -- cosa working directory

def buildArtifact(params = [:]) {
    gangplankCmd = getMode(params)
    gangplankCmd = getArtifacts(params, gangplankCmd)
    runGangplank(gangplankCmd)
}

// Available parameters:
//     artifacts:    list    -- list of artifacts to be built
//     extraFlags    string  -- Extra flags to use
//     image:        string  -- Name of the image to be used
//     mode:         string  -- Gangplank Mode
//     minioServeDir string  -- location to service minio from
//     workDir:      string  -- cosa working directory

def buildParallelArtifacts(params = [:]) {
    gangplankCmd = getMode(params)
    if (params['artifacts']) {
        def artifacts = params['artifacts']
        def configFile = startMinio(params)

        // Call one pod for each artifact in parallel
        parallel artifacts.inject([:]) { d, i -> d[i] = {
            startParallel(configFile, "${gangplankCmd} -A ${i} ")
        }; d }
        finalize()
    }
}

// Available parameters:
//     artifacts:    list    -- list of artifacts to be built
//     extraFlags    string  -- Extra flags to use
//     singlePod     boolean -- Run generateSinglePod

def generateSpec(params = [:]) {
    if(params['singlePod']) {
        gangplankCmd = "gangplank generateSinglePod  "
    } else {
        gangplankCmd = "gangplank generate "
    }
    gangplankCmd = getArtifacts(params, gangplankCmd)
    gangplankCmd = getFlags(params, gangplankCmd)
    fileName = "/tmp/jobspec-${UUID.randomUUID()}.spec"
    gangplankCmd += "--yaml-out ${fileName} "
    runGangplank(gangplankCmd)
    return fileName
}

def startParallel(configFile, gangplankCmd) {
    gangplankCmd += "-m ${configFile}"
    runGangplank(gangplankCmd)
}

def startMinio(params =[:]) {
    def configFile = "/tmp/${UUID.randomUUID()}-minio.yaml"
    def minioServeDir = params.get('minioServeDir', "/srv")
    def gangplankCmd = "gangplank minio -m ${configFile} -d ${minioServeDir}"

    // Minio needs to run in background, JENKINS_NODE_COOKIE allows it
    gangplankCmd = "export JENKINS_NODE_COOKIE=dontKillMe && nohup ${gangplankCmd} &"
    runGangplank(gangplankCmd)
    // Workaround - Wait for minio to start
    shwrap("sleep 0.5")

    return configFile
}

def runGangplank(gangplankCmd) {
    try {
        shwrap("${gangplankCmd}")
    } catch (Exception e) {
        print(e)
        currentBuild.result = 'ABORTED'
        error("Error running gangplank.")
    }
}

// Available parameters:
//     artifacts:    list    -- list of artifacts to be built
//     extraFlags    string  -- Extra flags to use
//     mode          string  -- Gangplank Mode
//     spec          string  -- Name of the spec file
def runSpec(params =[:]) {
    if (params['spec']) {
        gangplankCmd = getMode(params)
        gangplankCmd += "--spec ${params['spec']} "
        runGangplank(gangplankCmd)
    }
}

def finalize() {
    runGangplank("gangplank pod -A finalize")
}

def getArtifacts(params =[:], gangplankCmd) {
   if (params['artifacts']) {
        def artifacts = params['artifacts'].join(",")
        gangplankCmd += "--build-artifact ${artifacts} "
        return gangplankCmd
    }
    return gangplankCmd
}

def getFlags(params = [:], gangplankCmd) {
    if (params['extraFlags'])  {
        gangplankCmd += params['extraFlags'] + " "
        return gangplankCmd
    }
    return gangplankCmd
}

def getImage(params = [:], gangplankCmd) {
    if (params['image']) {
        gangplankCmd += "--image ${params['image']} "
        return gangplankCmd
    }
    return gangplankCmd
}

def getMode(params = [:]) {
    gangplankCmd = "gangplank " + params.get('mode', "pod") + " "
    gangplankCmd = getFlags(params, gangplankCmd)
    gangplankCmd = getImage(params, gangplankCmd)
    gangplankCmd = getWorkDir(params, gangplankCmd)
    return gangplankCmd
}

def getWorkDir(params = [:], gangplankCmd) {
    if(params['cosaDir']) {
        gangplankCmd += "--workDir ${utils.getCosaDir(params)} "
        return gangplankCmd
    }
    return gangplankCmd
}

