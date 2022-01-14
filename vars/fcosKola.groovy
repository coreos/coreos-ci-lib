// Run kola tests on the latest build in the cosa dir
// Available parameters:
//    addExtTests:       []string -- list of test paths to run
//    cosaDir:           string   -- cosa working directory
//    parallel:          integer  -- number of tests to run in parallel (default: 8)
//    skipBasicScenarios boolean  -- skip basic qemu scenarios
//    skipUpgrade:       boolean  -- skip running `cosa kola --upgrades`
//    build:             string   -- cosa build ID to target
//    platformArgs:      string   -- platform-specific kola args (e.g. '-p aws --aws-ami ...`)
//    extraArgs:         string   -- additional kola args for `kola run` (e.g. `ext.*`)
//    disableRerun:      boolean  -- disable reruns of failed tests
//    marker:            string   -- some identifying text to add to uploaded artifact filenames
def call(params = [:]) {
    def cosaDir = utils.getCosaDir(params)

    // this is shared between `kola run` and `kola run-upgrade`
    def platformArgs = params.get('platformArgs', "");
    def buildID = params.get('build', "latest");
    def arch = params.get('arch', "");
    def marker = params.get('marker', "kola");
    def rerun = "--rerun"
    if (params.get('disableRerun', false)) {
        rerun = ""
    }
    if (arch != "") {
        arch = "--arch=${arch}"
    }

    // Define a unique token to be added to the file name uploads
    // Prevents multiple runs overwriting same filename in archiveArtifacts
    def token = shwrapCapture("uuidgen | cut -f1 -d-")

    // Create a unique output directory for this run of fcosKola
    def outputDir = shwrapCapture("mktemp -d ${cosaDir}/tmp/fcosKola-XXXXX")

    // This is a bit obscure; what we're doing here is building a map of "name"
    // to "closure" which `parallel` will run in parallel. That way, we can
    // conditionally only add the `run_upgrades` stage if not explicitly
    // skipped.
    def kolaRuns = [:]
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
            // The workspace name created by Jenkins is messy and dynamic, but
            // kola uses it to derive the test name. Let's fix it by using a
            // symlinked dir.
            def name = shwrapCapture("basename \$(git config --get remote.origin.url) .git")
            shwrap("mkdir -p /var/tmp/kola && ln -s ${env.WORKSPACE} /var/tmp/kola/${name}")
            args += "--exttest /var/tmp/kola/${name}"
        }
        def parallel = params.get('parallel', 8);
        def extraArgs = params.get('extraArgs', "");
        def addExtTests = params.get('addExtTests', [])

        for (path in addExtTests) {
            args += " --exttest ${env.WORKSPACE}/${path}"
        }

        try {
            if (!params['skipBasicScenarios']) {
                shwrap("cd ${cosaDir} && cosa kola run ${rerun} --output-dir=${outputDir}/kola --basic-qemu-scenarios")
            }
            shwrap("cd ${cosaDir} && cosa kola run ${rerun} --output-dir=${outputDir}/kola --build=${buildID} ${arch} ${platformArgs} --parallel ${parallel} ${args} ${extraArgs}")
        } finally {
            shwrap("tar -c -C ${outputDir} kola | xz -c9 > ${env.WORKSPACE}/${marker}-${token}.tar.xz")
            archiveArtifacts allowEmptyArchive: true, artifacts: "${marker}-${token}.tar.xz"
        }
        // sanity check kola actually ran and dumped its output in tmp/
        shwrap("test -d ${outputDir}/kola")
    }
    if (!params["skipUpgrade"]) {
        kolaRuns['run_upgrades'] = {
            try {
                shwrap("cd ${cosaDir} && cosa kola ${rerun} --output-dir=${outputDir}/kola-upgrade --upgrades --build=${buildID} ${arch} ${platformArgs}")
            } finally {
                shwrap("tar -c -C ${outputDir} kola-upgrade | xz -c9 > ${env.WORKSPACE}/${marker}-upgrade-${token}.tar.xz")
                archiveArtifacts allowEmptyArchive: true, artifacts: "${marker}-upgrade-${token}.tar.xz"
            }
            // sanity check kola actually ran and dumped its output in tmp/
            shwrap("test -d ${outputDir}/kola-upgrade")
        }
    }

    stage('Kola') {
        if (kolaRuns.size() == 1) {
            kolaRuns.each { k, v -> v() }
        } else {
            parallel(kolaRuns)
        }
    }
}
