// Available parameters:
// Run build metal on cosa
//    cosaDir:        string  -- cosa working directory
//    skipMetal4k:    boolean -- skip metal4k image
//    user:           string  -- user account to run cosa commands
def call(params = [:]) {
    buildMetals = [:]
    buildMetals["metal"] = {
        // We can not change params itself due the parallel run 
        def metalparams = params.clone() 
        metalparams['args'] = "buildextend-metal"
        utils.cosaCmd(metalparams)
    }
    if (!params['skipMetal4k']) {
        buildMetals["metal4k"] = {
            def params4k = params.clone()
            params4k['args'] = "buildextend-metal4k"
            utils.cosaCmd(params4k)
        }
    }

    stage("Build Metal") {
        parallel(buildMetals)
    }
}
