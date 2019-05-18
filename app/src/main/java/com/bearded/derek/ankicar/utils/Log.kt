package com.bearded.derek.ankicar.utils

import android.os.AsyncTask
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object Logger {

    @JvmStatic
    var filename: String = ""
    fun log(message: String) = log2(message)
    const val PATH: String = "sdcard/AnkiCar/"
//        private var isRunning = false
//        private val buffer = mutableListOf<String>()

//    private fun log(message: String) {
//        Log.d("AnkiCar", message)
//        if (isRunning) {
//            buffer += message
//        } else {
//            isRunning = true
//            newInstance().execute(mutableListOf(message))
//        }
//    }

    private fun log2(message: String) {
        GlobalScope.launch(Dispatchers.IO) {
            Log.d("AnkiCar", message)
            File(PATH).mkdirs()
            with(File("$PATH$filename.txt")) {
                if (!exists()) createNewFile()
                appendText(message + "\n")
            }
        }
    }

//    private fun newInstance(): AsyncTask<List<String>, Unit, Unit> {
//        return object : AsyncTask<List<String>, Unit, Unit>() {
//            override fun onPostExecute(result: Unit?) {
//                if (!buffer.isEmpty()) {
//                    newInstance().execute(buffer.toList())
//                    buffer.clear()
//                } else {
//                    isRunning = false
//                }
//            }
//
//            override fun doInBackground(vararg params: List<String>) {
//                val dir = File("sdcard/AnkiCar/")
//                dir.mkdirs()
//                val file = File("sdcard/AnkiCar/$filename.txt")
//                if (!file.exists()) {
//                    file.createNewFile()
//                }
//
//                params[0].forEach {
//                    file.appendText(it)
//                    file.appendText("\n")
//                }
//            }
//        }
//    }
}

fun log(message: String) {
    GlobalScope.launch(Dispatchers.IO) {
        Log.d("AnkiCar", message)
        File(Logger.PATH).mkdirs()
        with(File("${Logger.PATH}${Logger.filename}.txt")) {
            if (!exists()) createNewFile()
            appendText(message + "\n")
        }
    }
}