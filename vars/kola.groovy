// Run kola tests on the latest build in the cosa dir
// Available parameters:
//    addExtTests:       []string -- list of test paths to run
//    arch:              string   -- the target architecture
//    cosaDir:           string   -- cosa working directory
//    parallel:          integer  -- number of tests to run in parallel (default: # CPUs)
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
    def arch = params.get('arch', "x86_64");
    def marker = params.get('marker', "kola");
    def rerun = "--rerun"
    if (params.get('disableRerun', false)) {
        rerun = ""
    }
    def archArg = "--arch=${arch}"

    // Define a unique token to be added to the file name uploads
    // Prevents multiple runs overwriting same filename in archiveArtifacts
    def token = shwrapCapture("uuidgen | cut -f1 -d-")

    // Create a unique output directory for this run of kola
    def outputDir = shwrapCapture("cosa shell -- mktemp -d ${cosaDir}/tmp/kola-XXXXX")

    // list of identifiers for each run for log collection
    def ids = []

    // This is a bit obscure; what we're doing here is building a map of "name"
    // to "closure" which `parallel` will run in parallel. That way, we can
    // conditionally only add the `run_upgrades` stage if not explicitly
    // skipped.
    def kolaRuns = [:]
    kolaRuns["run"] = {
        def args = ""
        def id
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
            // symlinked dir (x86_64) or copied dir (multi-arch).
            def name = shwrapCapture("basename \$(git config --get remote.origin.url) .git")
            if (arch == 'x86_64') {
                shwrap("mkdir -p /var/tmp/kola && ln -s ${env.WORKSPACE} /var/tmp/kola/${name}")
            } else {
                shwrap("""
                cosa shell -- mkdir -p /var/tmp/kola
                cosa remote-session sync ${env.WORKSPACE}/ :/var/tmp/kola/${name}/
                """)
            }
            args += "--exttest /var/tmp/kola/${name}"
        }
        def parallel = params.get('parallel', "auto");
        def extraArgs = params.get('extraArgs', "");
        def addExtTests = params.get('addExtTests', [])

        for (path in addExtTests) {
            args += " --exttest ${env.WORKSPACE}/${path}"
        }

        // basic run
        if (!params['skipBasicScenarios']) {
            id = marker == "" ? "kola-basic" : "kola-basic-${marker}"
            ids += id
            shwrap("cosa kola run ${rerun} --output-dir=${outputDir}/${id} --basic-qemu-scenarios")
        }
        // normal run (without reprovision tests because those require a lot of memory)
        id = marker == "" ? "kola" : "kola-${marker}"
        ids += id
        shwrap("cosa kola run ${rerun} --output-dir=${outputDir}/${id} --build=${buildID} ${archArg} ${platformArgs} --tag '!reprovision' --parallel ${parallel} ${args} ${extraArgs}")

        // re-provision tests (not run with --parallel argument to kola)
        id = marker == "" ? "kola-reprovision" : "kola-reprovision-${marker}"
        ids += id
        shwrap("cosa kola run ${rerun} --output-dir=${outputDir}/${id} --build=${buildID} ${archArg} ${platformArgs} --tag reprovision ${args} ${extraArgs}")
    }

    if (!params["skipUpgrade"]) {
        kolaRuns['run_upgrades'] = {
            def id = marker == "" ? "kola-upgrade" : "kola-upgrade-${marker}"
            ids += id
            shwrap("cosa kola ${rerun} --output-dir=${outputDir}/${id} --upgrades --build=${buildID} ${archArg} ${platformArgs}")
        }
    }

    stage('Kola') {
        dir(cosaDir) {
            try {
                if (kolaRuns.size() == 1) {
                    kolaRuns.each { k, v -> v() }
                } else {
                    parallel(kolaRuns)
                }
            } finally {
                for (id in ids) {
                    // sanity check kola actually ran and dumped its output
                    shwrap("cosa shell -- test -d ${outputDir}/${id}")
                    // collect the output
                    shwrap("cosa shell -- tar -c --xz ${outputDir}/${id} > ${env.WORKSPACE}/${id}-${token}.tar.xz")
                    archiveArtifacts allowEmptyArchive: true, artifacts: "${id}-${token}.tar.xz"
                }
            }
        }
    }
}
