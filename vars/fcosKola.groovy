// Run kola tests on the latest build in the cosa dir
// Available parameters:
//    addExtTests:    []string -- list of test paths to run
//    cosaDir:         string  -- cosa working directory
//    parallel:        integer -- number of tests to run in parallel (default: 8)
//    skipUpgrade:     boolean -- skip running `cosa kola --upgrades`
//    build:           string  -- cosa build ID to target
//    platformArgs:    string  -- platform-specific kola args (e.g. '-p aws --aws-ami ...`)
//    extraArgs:       string  -- additional kola args for `kola run` (e.g. `ext.*`)
//    basicScenarios   boolean -- run basic qemu scenarios
def call(params = [:]) {
    def cosaDir = utils.getCosaDir(params)

    // this is shared between `kola run` and `kola run-upgrade`
    def platformArgs = params.get('platformArgs', "");
    def buildID = params.get('build', "latest");

    // This is a bit obscure; what we're doing here is building a map of "name"
    // to "closure" which `parallel` will run in parallel. That way, we can
    // conditionally only add the `run_upgrades` stage if not explicitly
    // skipped.
    kolaRuns = [:]
    kolaRuns["run"] = {
        def args = ""
        // Add the tests/kola directory, but only if it's not the same as the
        // src/config repo which is also automatically added.
        if (shwrapRc("""
            test -d ${env.WORKSPACE}/tests/kola
            configorigin=\$(cd ${cosaDir}/src/config && git config --get remote.origin.url)
            gitorigin=\$(cd ${env.WORKSPACE} && git config --get remote.origin.url)
            test "\$configorigin" != "\$gitorigin"
        """) == 0)
        {
            args += "--exttest ${env.WORKSPACE}"
        }
        def parallel = params.get('parallel', 8);
        def extraArgs = params.get('extraArgs', "");
        def addExtTests = params.get('addExtTests', [])

        for (path in addExtTests) {
            args += " --exttest ${env.WORKSPACE}/${path}"
        }

        try {
            if (params['basicScenarios']) {
                shwrap("cd ${cosaDir} && cosa kola run --basic-qemu-scenarios")
            }
            shwrap("cd ${cosaDir} && cosa kola run --build ${buildID} ${platformArgs} --parallel ${parallel} ${args} ${extraArgs}")
        } finally {
            shwrap("tar -c -C ${cosaDir}/tmp kola | xz -c9 > ${env.WORKSPACE}/kola.tar.xz")
            archiveArtifacts allowEmptyArchive: true, artifacts: 'kola.tar.xz'
        }
        // sanity check kola actually ran and dumped its output in tmp/
        shwrap("test -d ${cosaDir}/tmp/kola")
    }
    if (!params["skipUpgrade"]) {
        kolaRuns['run_upgrades'] = {
            try {
                shwrap("cd ${cosaDir} && cosa kola --upgrades --build ${buildID} ${platformArgs}")
            } finally {
                shwrap("tar -c -C ${cosaDir}/tmp kola-upgrade | xz -c9 > ${env.WORKSPACE}/kola-upgrade.tar.xz")
                archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-upgrade.tar.xz'
            }
            // sanity check kola actually ran and dumped its output in tmp/
            shwrap("test -d ${cosaDir}/tmp/kola-upgrade")
        }
    }

    stage('Kola') {
        parallel(kolaRuns)
    }
}
