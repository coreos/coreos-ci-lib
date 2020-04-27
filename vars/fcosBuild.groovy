// Build FCOS, possibly with modifications.
// Available parameters:
//    make:           boolean -- run `make && make install DESTDIR=...`
//    skipInit:       boolean -- assume `cosa init` has already been run
//    makeDirs:     []string  -- extra list of directories from which to `make && make install DESTDIR=...`
//    skipKola:       boolean -- don't automatically run kola on resulting build
//    overlays:     []string  -- list of directories to overlay
//    extraFetchArgs: string  -- extra arguments to pass to `cosa fetch`
//    extraArgs:      string  -- extra arguments to pass to `cosa build`
def call(params = [:]) {
    stage("Build FCOS") {
        shwrap("mkdir -p /srv/fcos")

        if (!params['skipInit']) {
            shwrap("cd /srv/fcos && cosa init https://github.com/coreos/fedora-coreos-config")
        }

        if (params['make']) {
            shwrap("make && make install DESTDIR=/srv/fcos/overrides/rootfs")
        }
        if (params['makeDirs']) {
            params['makeDirs'].each{
                shwrap("make -C ${it} && make -C ${it} install DESTDIR=/srv/fcos/overrides/rootfs")
            }
        }

        if (params['overlays']) {
            params['overlays'].each{
                shwrap("rsync -av ${it}/ /srv/fcos/overrides/rootfs")
            }
        }

        def extraFetchArgs = params.get('extraFetchArgs', "");
        shwrap("cd /srv/fcos && cosa fetch --strict ${extraFetchArgs}")

        def extraArgs = params.get('extraArgs', "");
        shwrap("cd /srv/fcos && cosa build --strict ${extraArgs}")
    }

    if (!params['skipKola']) {
        fcosKola()
    }
}

