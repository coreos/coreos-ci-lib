def getCosaDir(params = [:]) {
    if (params['cosaDir']) {
        return params['cosaDir']
    } else {
        return "/srv/fcos"
    }
}
