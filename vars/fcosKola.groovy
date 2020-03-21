// Run kola tests on the latest build in the cosa dir
def call(cosaDir = "/srv/fcos") {
    stage('Kola') {
        parallel run: {
            stage("run") {
                def args = ""
                if (shwrapRc("test -d tests/kola") == 0) {
                    args += "--exttest ${env.WORKSPACE}"
                }
                try {
                    shwrap("cd ${cosaDir} && cosa kola run --parallel 8 ${args}")
                } finally {
                    shwrap("tar -c -C ${cosaDir}/tmp kola | xz -c9 > ${env.WORKSPACE}/kola.tar.xz")
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'kola.tar.xz'
                }
                // sanity check kola actually ran and dumped its output in tmp/
                shwrap("test -d ${cosaDir}/tmp/kola")
            }
        },
        run_upgrades: {
            stage("run-upgrade") {
                try {
                    shwrap("cd ${cosaDir} && cosa kola --upgrades")
                } finally {
                    shwrap("tar -c -C ${cosaDir}/tmp kola-upgrade | xz -c9 > ${env.WORKSPACE}/kola-upgrade.tar.xz")
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'kola-upgrade.tar.xz'
                }
                // sanity check kola actually ran and dumped its output in tmp/
                shwrap("test -d ${cosaDir}/tmp/kola-upgrade")
            }
        }
    }
}
