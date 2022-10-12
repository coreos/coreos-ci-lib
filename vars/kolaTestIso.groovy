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
//     marker:             string  -- some identifying text to add to uploaded artifact filenames
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
    def marker = params.get('marker', "");

    // Define a unique token to be added to the file name uploads
    // Prevents multiple runs overwriting same filename in archiveArtifacts
    def token = shwrapCapture("uuidgen | cut -f1 -d-")

    // Create a unique output directory for this run of fcosKola
    def outputDir = shwrapCapture("mktemp -d ${cosaDir}/tmp/kolaTestIso-XXXXX")

    // list of identifiers for each run for log collection
    def ids = []

    testIsoRuns1 = [:]
    testIsoRuns2 = [:]
    testIsoRuns1["metal"] = {
        def id = marker == "" ? "kola-testiso-metal" : "kola-testiso-metal-${marker}"
        ids += id
        def scenariosArg = scenarios == "" ? "" : "--scenarios ${scenarios}"
        shwrap("cd ${cosaDir} && kola testiso -S ${extraArgs} ${scenariosArg} --output-dir ${outputDir}/${id}")
    }
    if (!params['skipMetal4k']) {
        testIsoRuns1["metal4k"] = {
            def id = marker == "" ? "kola-testiso-metal4k" : "kola-testiso-metal4k-${marker}"
            ids += id
            def scenariosArg = scenarios4k == "" ? "" : "--scenarios ${scenarios4k}"
            shwrap("cd ${cosaDir} && kola testiso -S --qemu-native-4k ${extraArgs4k} ${scenariosArg} --output-dir ${outputDir}/${id}")
        }
    }
    if (!params['skipMultipath']) {
        testIsoRuns2["multipath"] = {
            def id = marker == "" ? "kola-testiso-multipath" : "kola-testiso-multipath-${marker}"
            ids += id
            shwrap("cd ${cosaDir} && kola testiso -S --qemu-multipath ${extraArgsMultipath} --scenarios ${scenariosMultipath} --output-dir ${outputDir}/${id}")
        }
    }
    if (!params['skipUEFI']) {
        testIsoRuns2["metalUEFI"] = {
            def id = marker == "" ? "kola-testiso-uefi" : "kola-testiso-uefi-${marker}"
            ids += id
            shwrap("mkdir -p ${outputDir}/${id}")
            shwrap("cd ${cosaDir} && kola testiso -S --qemu-firmware=uefi ${extraArgsUEFI} --scenarios ${scenariosUEFI} --output-dir ${outputDir}/${id}/insecure")
            shwrap("cd ${cosaDir} && kola testiso -S --qemu-firmware=uefi-secure ${extraArgsUEFI} --scenarios ${scenariosUEFI} --output-dir ${outputDir}/${id}/secure")
        }
    }

    stage("Test Live Images") {
        try {
            parallel(testIsoRuns1)
            parallel(testIsoRuns2)
        } finally {
            for (id in ids) {
                shwrap("tar -c -C ${outputDir} ${id} | xz -c9 > ${env.WORKSPACE}/${id}-${token}.tar.xz")
                archiveArtifacts allowEmptyArchive: true, artifacts: "${id}-${token}.tar.xz"
            }
        }
    }
}
