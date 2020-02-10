// Build FCOS, possibly with modifications.
// Available parameters:
//    make:         boolean -- run `make && make install DESTDIR=...`
//    makeDirs:   []string  -- extra list of directories from which to `make && make install DESTDIR=...`
//    skipKola:     boolean -- don't automatically run kola on resulting build
//    overlays:   []string  -- list of directories to overlay
def call(params = [:]) {
    stage("Build FCOS") {
        shwrap("""
        mkdir /srv/fcos && cd /srv/fcos
        cosa init https://github.com/coreos/fedora-coreos-config
        """)

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

        shwrap("cd /srv/fcos && cosa build")
    }

    if (!params['skipKola']) {
        fcosKola()
    }
}

