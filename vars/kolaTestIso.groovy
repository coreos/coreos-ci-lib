// Run kola testiso
// Available parameters:
//     cosaDir:            string  -- cosa working directory
//     extraArgs:          string  -- extra arguments to pass to `kola testiso`
//     extraArgs4k:        string  -- extra arguments to pass to 4k `kola testiso`
//     extraArgsMultipath: string  -- extra arguments to pass to multipath `kola testiso`
//     extraArgsUEFI:      string  -- extra arguments to pass to UEFI `kola testiso`
//     scenarios:          string  -- scenarios to pass to `kola testiso`
//     scenarios4k:        string  -- scenarios to pass to `kola testiso`
//     scenariosMultipath: string  -- scenarios to pass to `kola testiso`
//     scenariosUEFI:      string  -- scenarios to pass to `kola testiso`
//     skipMetal4k:        boolean -- skip metal4k image
//     skipMultipath:      boolean -- skip multipath tests
//     skipUEFI:           boolean -- skip UEFI tests
def call(params = [:]) {
    def cosaDir = utils.getCosaDir(params)
    def extraArgs = params.get('extraArgs', "");
    def extraArgs4k = params.get('extraArgs4k', "");
    def extraArgsMultipath = params.get('extraArgsMultipath', "");
    def extraArgsUEFI = params.get('extraArgsUEFI', "");
    def scenarios = params.get('scenarios', "");
    def scenarios4k = params.get('scenarios4k', "");
    // only one test by default
    def scenariosMultipath = params.get('scenariosMultipath', "iso-offline-install");
    // by default, only test that we can boot successfully
    def scenariosUEFI = params.get('scenariosUEFI', "iso-live-login,iso-as-disk");

    testIsoRuns1 = [:]
    testIsoRuns2 = [:]
    testIsoRuns1["metal"] = {
        try {
            def scenariosArg = scenarios == "" ? "" : "--scenarios ${scenarios}"
            shwrap("cd ${cosaDir} && kola testiso -S ${extraArgs} ${scenariosArg} --output-dir tmp/kola-testiso-metal")
        } finally {
            shwrap("cd ${cosaDir} && tar -cf - tmp/kola-testiso-metal/ | xz -c9 > ${env.WORKSPACE}/kola-testiso-metal.tar.xz")
            archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-testiso-metal.tar.xz'
        }
    }
    if (!params['skipMetal4k']) {
        testIsoRuns1["metal4k"] = {
            try {
                def scenariosArg = scenarios4k == "" ? "" : "--scenarios ${scenarios4k}"
                shwrap("cd ${cosaDir} && kola testiso -S --qemu-native-4k ${extraArgs4k} ${scenariosArg} --output-dir tmp/kola-testiso-metal4k")
            } finally {
                shwrap("cd ${cosaDir} && tar -cf - tmp/kola-testiso-metal4k/ | xz -c9 > ${env.WORKSPACE}/kola-testiso-metal4k.tar.xz")
                archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-testiso-metal4k.tar.xz'
            }
        }
    }
    if (!params['skipMultipath']) {
        testIsoRuns2["multipath"] = {
            try {
                shwrap("cd ${cosaDir} && kola testiso -S --qemu-multipath ${extraArgsMultipath} --scenarios ${scenariosMultipath} --output-dir tmp/kola-testiso-multipath")
            } finally {
                shwrap("cd ${cosaDir} && tar -cf - tmp/kola-testiso-multipath/ | xz -c9 > ${env.WORKSPACE}/kola-testiso-multipath.tar.xz")
                archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-testiso-multipath.tar.xz'
            }
        }
    }
    if (!params['skipUEFI']) {
        testIsoRuns2["metalUEFI"] = {
            try {
                shwrap("cd ${cosaDir} && kola testiso -S --qemu-firmware=uefi ${extraArgsUEFI} --scenarios ${scenariosUEFI} --output-dir tmp/kola-testiso-uefi")
                shwrap("cd ${cosaDir} && kola testiso -S --qemu-firmware=uefi-secure ${extraArgsUEFI} --scenarios ${scenariosUEFI} --output-dir tmp/kola-testiso-uefi-secure")
            } finally {
                shwrap("cd ${cosaDir} && tar -cf - tmp/kola-testiso-uefi/ | xz -c9 > ${env.WORKSPACE}/kola-testiso-uefi.tar.xz")
                archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-testiso-uefi.tar.xz'
                shwrap("cd ${cosaDir} && tar -cf - tmp/kola-testiso-uefi-secure/ | xz -c9 > ${env.WORKSPACE}/kola-testiso-uefi-secure.tar.xz")
                archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-testiso-uefi-secure.tar.xz'
            }
        }
    }

    stage("Test Live Images") {
        parallel(testIsoRuns1)
        parallel(testIsoRuns2)
    }
}
