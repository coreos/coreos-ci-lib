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
//    noForce:        boolean -- don't force a cosa build even if nothing changed
//    noStrict        boolean -- do not run cosa using `--strict' option
//    user:           string  -- user to run the cosa commands
def call(params = [:]) {
    stage("Build FCOS") {
        def cosaDir = utils.getCosaDir(params)
        def extraFetchArgs = params.get('extraFetchArgs', "");
        def extraArgs = params.get('extraArgs', "");

        shwrap("mkdir -p ${cosaDir}")

        if (!params['skipInit']) {
            params['args'] = "init https://github.com/coreos/fedora-coreos-config"
            utils.cosaCmd(params)
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
        if (!params['noStrict']) {
            extraFetchArgs = "--strict ${extraFetchArgs}"
            extraArgs = "--strict ${extraArgs}"
        }
        if (!params['noForce']) {
            extraArgs = "--force ${extraArgs}"
        }

        params['args'] = "fetch ${extraFetchArgs}"
        utils.cosaCmd(params)
        params['args'] = "build ${extraArgs}"
        utils.cosaCmd(params)
    }

    if (!params['skipKola']) {
        fcosKola()
    }
}

