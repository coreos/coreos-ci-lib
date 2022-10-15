// Run kola testiso
// Available parameters:
//     arch:               string  -- the target architecture
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
    def arch = params.get('arch', "x86_64");
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
    def outputDir = shwrapCapture("cosa shell -- mktemp -d ${cosaDir}/tmp/kolaTestIso-XXXXX")

    // list of identifiers for each run for log collection
    def ids = []

    def testIsoRuns1 = [:]
    def testIsoRuns2 = [:]
    testIsoRuns1["${arch}:kola:metal"] = {
        def id = marker == "" ? "kola-testiso-metal" : "kola-testiso-metal-${marker}"
        ids += id
        def scenariosArg = scenarios == "" ? "" : "--scenarios ${scenarios}"
        shwrap("cosa kola testiso -S ${extraArgs} ${scenariosArg} --output-dir ${outputDir}/${id}")
    }
    if (!params['skipMetal4k']) {
        // metal4k test doesn't work on s390x for now
        // https://github.com/coreos/fedora-coreos-tracker/issues/1261
        // and testiso for s390x doesn't support iso installs either
        if (arch != 's390x') {
            testIsoRuns1["${arch}:kola:metal4k"] = {
                def id = marker == "" ? "kola-testiso-metal4k" : "kola-testiso-metal4k-${marker}"
                ids += id
                def scenariosArg = scenarios4k == "" ? "" : "--scenarios ${scenarios4k}"
                shwrap("cosa kola testiso -S --qemu-native-4k ${extraArgs4k} ${scenariosArg} --output-dir ${outputDir}/${id}")
            }
        }
    }
    if (!params['skipMultipath']) {
        testIsoRuns2["${arch}:kola:multipath"] = {
            def id = marker == "" ? "kola-testiso-multipath" : "kola-testiso-multipath-${marker}"
            ids += id
            shwrap("cosa kola testiso -S --qemu-multipath ${extraArgsMultipath} --scenarios ${scenariosMultipath} --output-dir ${outputDir}/${id}")
        }
    }
    if (!params['skipUEFI']) {
        // only aarch64 and x86_64 support UEFI. aarch64 UEFI was
        // already tested in the basic run above and secureboot isn't
        // supported in aarch64 so we limit this to x86_64 for now.
        // https://pagure.io/fedora-infrastructure/issue/7361
        // https://github.com/coreos/coreos-assembler/blob/93efb63dcbd63dc04a782e2c6c617ae0cd4a51c8/mantle/platform/qemu.go#L1156
        if (arch == 'x86_64') {
            testIsoRuns2["${arch}:kola:uefi"] = {
                def id = marker == "" ? "kola-testiso-uefi" : "kola-testiso-uefi-${marker}"
                ids += id
                shwrap("cosa shell -- mkdir -p ${outputDir}/${id}")
                shwrap("cosa kola testiso -S --qemu-firmware=uefi ${extraArgsUEFI} --scenarios ${scenariosUEFI} --output-dir ${outputDir}/${id}/insecure")
                shwrap("cosa kola testiso -S --qemu-firmware=uefi-secure ${extraArgsUEFI} --scenarios ${scenariosUEFI} --output-dir ${outputDir}/${id}/secure")
            }
        }
    }

    // Run the Kola tests from the cosaDir
    dir(cosaDir) {
        try {
            parallel(testIsoRuns1)
            parallel(testIsoRuns2)
        } finally {
            for (id in ids) {
                shwrap("cosa shell -- tar -c --xz ${outputDir}/${id} > ${env.WORKSPACE}/${id}-${token}.tar.xz || :")
                archiveArtifacts allowEmptyArchive: true, artifacts: "${id}-${token}.tar.xz"
            }
        }
    }
}
