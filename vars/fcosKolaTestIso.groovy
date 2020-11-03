// Run kola testiso
// Available parameters:
//     cosaDir:        string  -- cosa working directory
//     extraArgs:      string  -- extra arguments to pass to `kola testiso`
//     extraArgs4k:    string  -- extra arguments to pass to 4k `kola testiso`
//     scenarios:      string  -- scenarios to pass to `kola testiso`
//     scenarios4k:    string  -- scenarios to pass to `kola testiso`
//     skipMetal4k:    boolean -- skip metal4k image
def call(params = [:]) {
    def cosaDir = utils.getCosaDir(params)
    def extraArgs = params.get('extraArgs', "");
    def extraArgs4k = params.get('extraArgs4k', "");
    def scenarios = params.get('scenarios', "");
    def scenarios4k = params.get('scenarios4k', "");

    testIsoRuns = [:]
    testIsoRuns["metal"] = {
        try {
            if (params['scenarios']) {
                shwrap("cd ${cosaDir}  && kola testiso -S ${extraArgs} --scenarios ${scenarios} --output-dir tmp/kola-testiso-metal")
            } else {
                shwrap("cd ${cosaDir}  && kola testiso -S ${extraArgs} --output-dir tmp/kola-testiso-metal")
            }
        } finally {
            shwrap("cd ${cosaDir} && tar -cf - tmp/kola-testiso-metal/ | xz -c9 > ${env.WORKSPACE}/kola-testiso-metal.tar.xz")
        }
    }
    if (!params['skipMetal4k']) {
        testIsoRuns["metal4k"] = {
            try {
                if (params['scenarios4k']) {
                    shwrap("cd ${cosaDir} &&  kola testiso -S --qemu-native-4k ${extraArgs4k} --scenarios ${scenarios4k} --output-dir tmp/kola-testiso-metal4k")
                } else {
                    shwrap("cd ${cosaDir} &&  kola testiso -S --qemu-native-4k ${extraArgs4k} --output-dir tmp/kola-testiso-metal4k")
                }
            } finally {
                shwrap("cd ${cosaDir} && tar -cf - tmp/kola-testiso-metal4k/ | xz -c9 > ${env.WORKSPACE}/kola-testiso-metal4k.tar.xz")
            }
        }
    }
    archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-testiso*.tar.xz'

    stage("Test Live Images") {
        parallel(testIsoRuns)
    }
}
