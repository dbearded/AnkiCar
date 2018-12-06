package com.bearded.derek.ankicar.utils

import android.os.AsyncTask
import java.io.File

class Logger private constructor () {

    companion object {
        var filename: String = ""
        fun log(message: String) = logger.log(message)
        private val logger = Logger()
        private var isRunning = false
        private val buffer = mutableListOf<String>()
    }

    private fun log(message: String) {
        if (isRunning) {
            buffer += message
        } else {
            isRunning = true
            newInstance().execute(mutableListOf(message))
        }
    }

    private fun newInstance(): AsyncTask<List<String>, Unit, Unit> {
        return object : AsyncTask<List<String>, Unit, Unit>() {
            override fun onPostExecute(result: Unit?) {
                if (!buffer.isEmpty()) {
                    newInstance().execute(buffer.toList())
                    buffer.clear()
                } else {
                    isRunning = false
                }
            }

            override fun doInBackground(vararg params: List<String>) {
                val dir = File("sdcard/AnkiCar/")
                dir.mkdirs()
                val file = File("sdcard/AnkiCar/$filename.txt")
                if (!file.exists()) {
                    file.createNewFile()
                }

                params[0].forEach {
                    file.appendText(it)
                    file.appendText("\n")
                }
            }
        }
    }
}