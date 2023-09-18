def call(scm, dir) {
    // https://support.cloudbees.com/hc/en-us/articles/226122247-How-to-customize-Checkout-for-Pipeline-Multibranch
    checkout([
         $class: 'GitSCM',
         branches: scm.branches,
         extensions: scm.extensions + [[$class: 'RelativeTargetDirectory', relativeTargetDir: dir, recursiveSubmodules: true]],
         userRemoteConfigs: scm.userRemoteConfigs
    ])
}
