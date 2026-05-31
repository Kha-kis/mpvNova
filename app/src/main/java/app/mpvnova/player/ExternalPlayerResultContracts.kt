package app.mpvnova.player

internal fun externalPlaybackResultCode(
    defaultCode: Int,
    includeTimePos: Boolean,
    playbackComplete: Boolean,
    vimuResultContract: Boolean,
): Int {
    if (!includeTimePos || !vimuResultContract)
        return defaultCode
    return if (playbackComplete) VIMU_RESULT_COMPLETED else VIMU_RESULT_STOPPED
}

internal fun shouldUseVimuResultContract(callerPackage: String?): Boolean {
    return isStremioExternalCaller(callerPackage) || isNuvioExternalCaller(callerPackage)
}

internal fun isStremioExternalCaller(callerPackage: String?): Boolean {
    return callerPackage == "com.stremio.one" ||
        callerPackage == "com.stremio" ||
        callerPackage?.startsWith("com.stremio.") == true
}

private fun isNuvioExternalCaller(callerPackage: String?): Boolean {
    return callerPackage == "com.nuvio.tv" ||
        callerPackage == "com.nuvio" ||
        callerPackage == "app.nuvio.tv" ||
        callerPackage == "app.nuvio" ||
        callerPackage?.startsWith("com.nuvio.") == true ||
        callerPackage?.startsWith("app.nuvio.") == true
}
