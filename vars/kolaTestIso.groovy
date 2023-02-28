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

    def id = marker == "" ? "kolatestiso" : "kolatestiso-${marker}"

    // We add --inst-insecure here since in CI and in our build pipeline
    // the signatures for the metal images won't have been created yet.
    try {
        shwrap("cd ${cosaDir} && cosa kola testiso --inst-insecure ${extraArgs} --output-dir ${outputDir}/${id}")
    } finally {
        shwrap("cd ${cosaDir} && cosa shell -- tar -C ${outputDir} -c --xz ${id} > ${env.WORKSPACE}/${id}-${token}.tar.xz || :")
        archiveArtifacts allowEmptyArchive: true, artifacts: "${id}-${token}.tar.xz"
    }
}
