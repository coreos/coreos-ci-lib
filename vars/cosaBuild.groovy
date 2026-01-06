// Build CoreOS, possibly with modifications.
// Available parameters:
//    cosaDir:        string   -- Cosa working directory
//    srcConfig:      string   -- Path or URL to source config repo
//    variant:        string   -- Variant to build
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
//    user:           string   -- Build with username (which is used when running pod as root)
def call(params = [:]) {
    stage("Build") {
        def cosaDir = utils.getCosaDir(params)
        def extraFetchArgs = params.get('extraFetchArgs', "");
        def extraArgs = params.get('extraArgs', "");

        def cmd = "mkdir -p ${cosaDir}"
        if(!params['user']) {
            unprivshwrap(params['user'], cmd)
        } else {
            shwrap(cmd)
        }

        if (!params['srcConfig']) {
            params['srcConfig'] = "https://github.com/coreos/fedora-coreos-config"
        }

        if (!params['skipInit']) {
            def initArgs = ""
            if (params['gitBranch']) {
                initArgs += " --branch ${params['gitBranch']}"
            }
            if (params['variant']) {
                initArgs += " --variant ${params['variant']}"
            }
            utils.cosaCmd(cosaDir: cosaDir, user: params['user'], args: "init ${initArgs} ${params['srcConfig']}")
        }

        if (params['make']) {
            cmd = "make && make install DESTDIR=${cosaDir}/overrides/rootfs"
            if(!params['user']) {
                unprivshwrap(params['user'], cmd)
            } else {
                shwrap(cmd)
            }
        }

        if (params['makeDirs']) {
            params['makeDirs'].each{
                cmd = "make -C ${it} && make -C ${it} install DESTDIR=${cosaDir}/overrides/rootfs"
                if(!params['user']) {
                    unprivshwrap(params['user'], cmd)
                } else {
                    shwrap(cmd)
                }
            }
        }

        if (params['overlays']) {
            params['overlays'].each{
                cmd = "rsync -av ${it}/ ${cosaDir}/overrides/rootfs"
                if(!params['user']) {
                    unprivshwrap(params['user'], cmd)
                } else {
                    shwrap(cmd)
                }
            }
        }
        if (!params['noStrict']) {
            extraFetchArgs = "--strict ${extraFetchArgs}"
            extraArgs = "--strict ${extraArgs}"
        }
        if (!params['noForce']) {
            extraArgs = "--force ${extraArgs}"
        }

        utils.cosaCmd(cosaDir: cosaDir, user: params['user'], args: "fetch ${extraFetchArgs}")
        utils.cosaCmd(cosaDir: cosaDir, user: params['user'], args: "build ${extraArgs}")
        utils.cosaCmd(cosaDir: cosaDir, user: params['user'], args: "osbuild qemu")
    }

    if (!params['skipKola']) {
        kola()
    }
}

