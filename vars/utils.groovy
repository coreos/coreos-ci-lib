def cosaBuild(params = [:]) {
// Available parameters:
//     gitInstall:         boolean -- install git package
//     gitClone:           boolean -- clone cosa
//     skipUnitTest:       boolean -- do not run unit tests
    stage("Build Cosa") {
        if (params['gitInstall']) {
            shwrap(""" dnf install -y git """)
        }

        if (params['gitClone']) {
            shwrap("""
                git clone https://github.com/coreos/coreos-assembler.git
                cd coreos-assembler && git submodule update --init
                ./build.sh
            """)
        } else {
            shwrap("""
                git submodule update --init  && ./build.sh
            """)
       }
       shwrap("""
           rpm -qa | sort > rpmdb.txt
       """)
       archiveArtifacts artifacts: 'rpmdb.txt'
    }

    if (!params['skipUnitTest']) {
        unitTest()
    }
}

def getCosaDir(params = [:]) {
    if (params['cosaDir']) {
        return params['cosaDir']
    } else {
        return "/srv/fcos"
    }
}

def unitTest() {
    stage("Unit Test") {
        shwrap("""
            make check
            make unittest
        """)
    }
}
