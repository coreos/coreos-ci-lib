// Build CoreOS, possibly with modifications.
// Available parameters:
//    cosaDir:        string   -- Cosa working directory
//    extraArgs:      string   -- Extra arguments to pass to `cosa build`
//    extraFetchArgs: string   -- Extra arguments to pass to `cosa fetch`
//    gitBranch       string   -- Git Branch for fedora-coreos-config
//    make:           boolean  -- Run `make && make install DESTDIR=...`
//    makeDirs:       []string -- Extra list of directories from which to `make && make install DESTDIR=...`
//    noForce:        boolean  -- Do not force a cosa build even if nothing changed
//    noStrict        boolean  -- Do not run cosa using `--strict' option
//    overlays:       []string -- List of directories to overlay
//    skipInit:       boolean  -- Assume `cosa init` has already been run
//    skipKola:       boolean  -- Do not automatically run kola on resulting build
def call(params = [:]) {
    stage("Build") {
        def cosaDir = utils.getCosaDir(params)
        def extraFetchArgs = params.get('extraFetchArgs', "");
        def extraArgs = params.get('extraArgs', "");

        shwrap("mkdir -p ${cosaDir}")

        // if the cosa dir is the default, also add a symlink from /srv/fcos
        // for backwards compatibility
        shwrap("""
            if [ ${cosaDir} = /srv/coreos ]; then
                ln -s coreos /srv/fcos
            fi
        """)

        if (!params['skipInit']) {
            def branchArg = ""
            if (params['gitBranch']) {
                branchArg = "--branch ${params['gitBranch']}"
            }
            shwrap("cd ${cosaDir} && cosa init ${branchArg} https://github.com/coreos/fedora-coreos-config")
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

        shwrap("cd ${cosaDir} && cosa fetch ${extraFetchArgs}")
        shwrap("cd ${cosaDir} && cosa build ${extraArgs}")
    }

    if (!params['skipKola']) {
        kola()
    }
}

