package com.bearded.derek.ankicar.utils

val posXVect = Pair<Float, Float>(1F, 0F)
val negXVect = Pair<Float, Float>(-1F, 0F)
val posYVect = Pair<Float, Float>(0F, 1F)
val negYVect = Pair<Float, Float>(0F, -1F)

fun euclidDistance(x: Float, y: Float, prevX: Float, prevY: Float): Double {
    return Math.sqrt(((x-prevX)*(x-prevX)+(y-prevY)*(y-prevY)).toDouble())
}

fun scalarCrossProduct(x1: Float, y1: Float, x2: Float, y2: Float): Double {
    return (x1*y2 - y1*x2).toDouble()
}

fun dotProduct(x1: Float, y1: Float, x2: Float, y2: Float): Double {
    return (x1*x2 + y1*y2).toDouble()
}

fun magnitude(x: Float, y: Float): Double {
    return Math.sqrt((x*x + y*y).toDouble())
}

fun cosineSimilarity(x1: Float, y1: Float, x2: Float, y2: Float): Double {
    return dotProduct(x1, y1, x2, y2)/(magnitude(x1, y1)* magnitude(x2, y2))
}

fun angleBetweenVectors(x1: Float, y1: Float, x2: Float, y2: Float): Double {
    return Math.atan2(scalarCrossProduct(x1, y1, x2, y2), dotProduct(x1, y1, x2, y2))
}