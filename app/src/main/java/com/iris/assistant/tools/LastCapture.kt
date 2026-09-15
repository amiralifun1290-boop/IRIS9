package com.iris.assistant.tools

import java.io.File

/** Simple in-memory pointer to the last photo CameraScreen captured. */
object LastCapture {
    @Volatile
    var file: File? = null
}
