// Build FCOS, possibly with modifications.
// Available parameters:
//    make:           boolean -- run `make && make install DESTDIR=...`
//    skipInit:       boolean -- assume `cosa init` has already been run
//    makeDirs:     []string  -- extra list of directories from which to `make && make install DESTDIR=...`
//    skipKola:       boolean -- don't automatically run kola on resulting build
//    overlays:     []string  -- list of directories to overlay
//    extraFetchArgs: string  -- extra arguments to pass to `cosa fetch`
//    extraArgs:      string  -- extra arguments to pass to `cosa build`
//    cosaDir:        string  -- cosa working directory
//    skipDirCreation boolean -- do not create cosa directory
//    changeOwner     string  -- change cosa working direcotry ownership, expectes owner name
def call(params = [:]) {
    stage("Build FCOS") {

        def cosaDir = utils.getCosaDir(params)

        if (!params['skipDirCreation']) {
            shwrap("mkdir -p ${cosaDir}")
        }

        if (params['changeOwner']) {
            shwrap("chown ${params['changeOwner']}: ${cosaDir}")
        }

        if (!params['skipInit']) {
            shwrap("cd ${cosaDir} && cosa init https://github.com/coreos/fedora-coreos-config")
        }

        if (params['make']) {
            shwrap("make && make install DESTDIR=${cosaDir}/overrides/rootfs")
        }
        if (params['makeDirs']) {
            params['makeDirs'].each{
                shwrap("make -C ${it} && make -C ${it} install DESTDIR=${cosaDir}/overrides/rootfs")
            }
        }

        if (params['overlays']) {
            params['overlays'].each{
                shwrap("rsync -av ${it}/ ${cosaDir}/overrides/rootfs")
            }
        }

        def extraFetchArgs = params.get('extraFetchArgs', "");
        shwrap("cd ${cosaDir} && cosa fetch --strict ${extraFetchArgs}")

        def extraArgs = params.get('extraArgs', "");
        shwrap("cd ${cosaDir} && cosa build --strict ${extraArgs}")
    }

    if (!params['skipKola']) {
        fcosKola()
    }
}

