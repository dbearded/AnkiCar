package com.bearded.derek.ankicar.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

object Logger {
    @JvmStatic
    var filename: String = ""
    const val PATH: String = "sdcard/AnkiCar/"
}

fun log(message: String) {
    Log.d("AnkiCar", message)
    GlobalScope.launch(Dispatchers.IO) {
        File(Logger.PATH).mkdirs()
        with(File("${Logger.PATH}${Logger.filename}.txt")) {
            if (!exists()) createNewFile()
            appendText(message + "\n")
        }
    }
}