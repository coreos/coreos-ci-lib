// Build CoreOS, possibly with modifications.
// Available parameters:
//    extraArgs:      string   -- Extra arguments to pass to `cosa build`
//    extraFetchArgs: string   -- Extra arguments to pass to `cosa fetch`
//    gitBranch       string   -- Git Branch for fedora-coreos-config
//    noForce:        boolean  -- Do not force a cosa build even if nothing changed
//    noStrict        boolean  -- Do not run cosa using `--strict' option
//    overlays:       []string -- List of directories to overlay
//    skipInit:       boolean  -- Assume `cosa init` has already been run
//    skipKola:       boolean  -- Do not automatically run kola on resulting build
def call(params = [:]) {
    stage("Build") {
        def extraFetchArgs = params.get('extraFetchArgs', "");
        def extraArgs = params.get('extraArgs', "");

        if (!params['skipInit']) {
            def branchArg = ""
            if (params['gitBranch']) {
                branchArg = "--branch ${params['gitBranch']}"
            }
            shwrap("cosa init ${branchArg} https://github.com/coreos/fedora-coreos-config")
        }

        if (params['overlays']) {
            params['overlays'].each{
                shwrap("rsync -av ${it}/ overrides/rootfs")
            }
        }
        if (!params['noStrict']) {
            extraFetchArgs = "--strict ${extraFetchArgs}"
            extraArgs = "--strict ${extraArgs}"
        }
        if (!params['noForce']) {
            extraArgs = "--force ${extraArgs}"
        }

        shwrap("cosa fetch ${extraFetchArgs}")
        shwrap("cosa build ${extraArgs}")
    }

    if (!params['skipKola']) {
        kola()
    }
}

