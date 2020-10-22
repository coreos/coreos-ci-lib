// Run kola tests on the latest build in the cosa dir
// Available parameters:
//    cosaDir:         string  -- cosa working directory
//    parallel:        integer -- number of tests to run in parallel (default: 8)
//    skipUpgrade:     boolean -- skip running `cosa kola --upgrades`
//    build:           string  -- cosa build ID to target
//    extraArgs:       string  -- additional kola args for `kola run` (e.g. `ext.*`)
def call(params = [:]) {
    def cosaDir = "/srv/fcos"
    if (params['cosaDir']) {
        cosaDir = params['cosaDir']
    }

    // this is shared between `kola run` and `kola run-upgrade`
    def buildID = params.get('build', "latest");

    // This is a bit obscure; what we're doing here is building a map of "name"
    // to "closure" which `parallel` will run in parallel. That way, we can
    // conditionally only add the `run_upgrades` stage if not explicitly
    // skipped.
    kolaRuns = [:]
    kolaRuns["run"] = {
        stage("run") {
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
            try {
                shwrap("cd ${cosaDir} && cosa kola run --build ${buildID} --parallel ${parallel} ${args} ${extraArgs}")
            } finally {
                shwrap("tar -c -C ${cosaDir}/tmp kola | xz -c9 > ${env.WORKSPACE}/kola.tar.xz")
                archiveArtifacts allowEmptyArchive: true, artifacts: 'kola.tar.xz'
            }
            // sanity check kola actually ran and dumped its output in tmp/
            shwrap("test -d ${cosaDir}/tmp/kola")
        }
    }
    if (!params["skipUpgrade"]) {
        kolaRuns['run_upgrades'] = {
            stage("run-upgrade") {
                try {
                    shwrap("cd ${cosaDir} && cosa kola --upgrades --build ${buildID}")
                } finally {
                    shwrap("tar -c -C ${cosaDir}/tmp kola-upgrade | xz -c9 > ${env.WORKSPACE}/kola-upgrade.tar.xz")
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-upgrade.tar.xz'
                }
                // sanity check kola actually ran and dumped its output in tmp/
                shwrap("test -d ${cosaDir}/tmp/kola-upgrade")
            }
        }
    }

    stage('Kola') {
        parallel(kolaRuns)
    }
}
