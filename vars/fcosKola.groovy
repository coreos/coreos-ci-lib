// Run kola tests on the latest build in the cosa dir
def call(cosaDir = "/srv/fcos") {
    stage("Kola") {
        try {
            shwrap("cd ${cosaDir} && cosa kola run --parallel 8")
        } finally {
            shwrap("tar -c -C ${cosaDir}/tmp kola | xz -c9 > ${env.WORKSPACE}/kola.tar.xz")
            archiveArtifacts allowEmptyArchive: true, artifacts: 'kola.tar.xz'
        }
        // sanity check kola actually ran and dumped its output in tmp/
        shwrap("test -d ${cosaDir}/tmp/kola")
    }
}

