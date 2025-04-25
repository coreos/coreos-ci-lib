// Run kola testiso
// Available parameters:
//     cosaDir:            string  -- cosa working directory
//     extraArgs:          string  -- extra arguments to pass to `kola testiso`
//     marker:             string  -- some identifying text to add to uploaded artifact filenames
//     skipUEFI:           boolean -- skip UEFI tests
//     skipSecureBoot      boolean -- skip secureboot tests

def call(params = [:]) {
    def cosaDir = utils.getCosaDir(params);
    def extraArgs = params.get('extraArgs', "");
    def marker = params.get('marker', "");


    if (params['skipUEFI']) {
        extraArgs += " --denylist-test *.uefi*"
    }
    if (params['skipSecureBoot']) {
        extraArgs += " --denylist-test *.uefi-secure"
    }
    // Define a unique token to be added to the file name uploads
    // Prevents multiple runs overwriting same filename in archiveArtifacts
    def token = shwrapCapture("uuidgen | cut -f1 -d-")

    // Create a unique output directory for this run of kola
    def outputDir = shwrapCapture("cd ${cosaDir} && cosa shell -- mktemp -d ${cosaDir}/tmp/kola-XXXXX")

    def id = marker == "" ? "kola-testiso" : "kola-testiso-${marker}"

    // We add --inst-insecure here since in CI and in our build pipeline
    // the signatures for the metal images won't have been created yet.
    try {
        shwrap("cd ${cosaDir} && cosa kola testiso --inst-insecure ${extraArgs} --output-dir ${outputDir}/${id}")
    } catch(e) {
        // To cut down on flake errors let's retry the failed tests
        if (shwrapRc("cd ${cosaDir} && cosa shell -- test -f ${outputDir}/${id}/reports/report.json") == 0) {
            def rerun_tests = shwrapCapture("""
                cd ${cosaDir}
                cosa shell -- cat ${outputDir}/${id}/reports/report.json | \
                    jq -r '.tests[] | select(.result == "FAIL") | .name' | \
                    tr '\n' ' '
            """)
            echo "Re-running failed tests (flake detection): ${rerun_tests}"
            shwrap("cd ${cosaDir} && cosa kola testiso --inst-insecure --output-dir ${outputDir}/${id}/rerun ${rerun_tests}")
        } else {
            throw e
        }
    } finally {
        shwrap("cd ${cosaDir} && cosa shell -- tar -C ${outputDir} -c --xz ${id} > ${env.WORKSPACE}/${id}-${token}.tar.xz || :")
        archiveArtifacts allowEmptyArchive: true, artifacts: "${id}-${token}.tar.xz"
        shwrap("cd ${cosaDir} && cosa shell -- /usr/lib/coreos-assembler/kola-junit --classname ${id} --koladir ${outputDir}/${id} --output - > ${env.WORKSPACE}/${id}-${token}.xml || :")
        junit allowEmptyResults: true, skipMarkingBuildUnstable: true, skipPublishingChecks: true, testResults: "${id}-${token}.xml"
    }
}
