def buildCloudImages(params = [:]) {
// Available parameters:
//    cloudNames:     []string  -- list of clloud names
//    cosaDir:        string    -- cosa working directory
//    userBuilder:    string  -- user account to run cosa commands
    stage("Build Cloud Images") {

        def cosaDir = getCosaDir(params)
        def userBuilder = getUserBuilder(params)
        def clouds = params['cloudNames'];

        parallel clouds.inject([:]) { d, i -> d[i] = {
             cosa_cmd(cosaDir, userBuilder, "buildextend-${i.toLowerCase()}")
        }; d }

        cosa_cmd(cosaDir, userBuilder, "meta --get name")
    }
}

def buildMetal(params = [:]) {
// Available parameters:
//    skipMetal4k:    boolean -- skip metal4k image
//    cosaDir:        string  -- cosa working directory
//    userBuilder:    string  -- user account to run cosa commands

    stage("Build Metal") {

        def cosaDir = getCosaDir(params)
        def userBuilder = getUserBuilder(params)

        parallel metal: {
                cosa_cmd(cosaDir, userBuilder, "buildextend-metal")
        }
        if (!params['skipMetal4k']) {
            metal4k: {
                cosa_cmd(cosaDir, userBuilder, "buildextend-metal4k")
            }
        }
    }
}

def buildLive(params = [:]) { 
// Available parameters:
//     extraArgs:      string  -- extra arguments to pass to `cosa buildextend-live`
//     cosaDir:        string  -- cosa working directory
//    userBuilder:    string  -- user account to run cosa commands

    stage("Build Live Images") {

        def cosaDir = getCosaDir(params)
        def userBuilder = getUserBuilder(params)
        def extraArgs = params.get('extraArgs', "");

        cosa_cmd(cosaDir, userBuilder, "buildextend-live ${extraArgs}")
    }
}

def cliTests(params = [:]) {
// Available parameters:
//     cosaDir:        string  -- cosa working directory
//     userBuilder:    string  -- user account to run commands

    stage("CLI Tests") {

        def cosaDir = getCosaDir(params)
        def userBuilder = getUserBuilder(params)

        shwrap("""
            cd ${cosaDir}
            sudo -u ${userBuilder} ${env.WORKSPACE}/tests/test_pruning.sh
        """)
    }
}

def compress(params = [:]) {
// Available parameters:
//     extraArgs:      string  -- extra arguments to pass to `cosa compress`
//     cosaDir:        string  -- cosa working directory
//     userBuilder:    string  -- user account to run cosa commands

    stage("Compress") {

        def cosaDir = getCosaDir(params)
        def userBuilder = getUserBuilder(params)
        def extraArgs = params.get('extraArgs', "");

        cosa_cmd(cosaDir, userBuilder, "compress ${extraArgs}")
    }
}

def cosaBuild(params = [:]) {
// Available parameters:
//     gitInstall:         boolean -- install git package
//     gitClone:           boolean -- clone cosa
//     skipUnitTest:       boolean -- do not run unit tests

    stage("Build Cosa") {

        if (params['gitInstall']) {
            shwrap(""" dnf install -y git """)
        }

        if (params['gitClone']) {
            shwrap("""
                git clone https://github.com/coreos/coreos-assembler.git
                cd coreos-assembler && git submodule update --init
                ./build.sh
            """)
        } else {
            shwrap("""
                git submodule update --init  && ./build.sh
            """)
       }

       shwrap(""" 
          rpm -qa | sort > rpmdb.txt
        """)
       archiveArtifacts artifacts: 'rpmdb.txt'
    }

    if (!params['skipUnitTest']) {
        unitTest()
    }
}

def cosa_cmd(cosaDir, userBuilder, args) {

    // When you need to skip user
    if(userBuilder == 'skip') {
        shwrap("cd ${cosaDir} && cosa ${args}")
    } else {
        shwrap("cd ${cosaDir} && sudo -u ${userBuilder} cosa ${args}")
    }
}

def getCosaDir(params = [:]) {

    if (params['cosaDir']) {
        return params['cosaDir']
    } else {
        return "/srv/fcos"
    }
}

def getUserBuilder(params = [:]) {

    if (params['userBuilder']) {
        return params['userBuilder']
    } else {
        return "builder"
    }
}

def testLive(params = [:]) {
// Available parameters:
//     skipMetal4k:    boolean -- skip metal4k image
//     extraArgs:      string  -- extra arguments to pass to `kola testiso`
//     cosaDir:        string  -- cosa working directory

    stage("Test Live Images") {

        def cosaDir = getCosaDir(params)
        def extraArgs = params.get('extraArgs', "");

        try {
            parallel metal: {
                shwrap("cd ${cosaDir}  && env TMPDIR=\$(pwd)/tmp/ kola testiso -S ${extraArgs} --output-dir tmp/kola-testiso-metal")
            }

            if (!params['skipMetal4k']) { 
                metal4k: {
                    shwrap("cd ${cosaDir} && env TMPDIR=\$(pwd)/tmp/ kola testiso -S ${extraArgs} --output-dir tmp/kola-testiso-metal4k")
                }
            }
        } finally {
            shwrap("cd ${cosaDir} && tar -cf - tmp/kola-testiso-metal/ | xz -c9 > ${env.WORKSPACE}/kola-testiso-metal.tar.xz")
            if (!params['skipMetal4k']) {
                shwrap("cd ${cosaDir} && tar -cf - tmp/kola-testiso-metal4k/ | xz -c9 > ${env.WORKSPACE}/kola-testiso-metal4k.tar.xz")
            }
            archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-testiso*.tar.xz'
        }
    }
}

def unitTest() { 

    stage("Unit Test") {
            shwrap("""
                make check
                make unittest
            """)
    }
}

def uploadDryRun(params = [:]) {
// Available parameters:
//     cosaDir:        string  -- cosa working directory
//     userBuilder:    string  -- user account to run cosa commands

    def cosaDir = getCosaDir(params)
    def userBuilder = getUserBuilder(params)

    stage("Upload Dry Run") {
        cosa_cmd(cosaDir, userBuilder, "buildupload --dry-run s3 --acl=public-read my-nonexistent-bucket/my/prefix")
    }
}
