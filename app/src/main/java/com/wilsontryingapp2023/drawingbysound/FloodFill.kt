package com.wilsontryingapp2023.drawingbysound

import android.graphics.Bitmap
import android.graphics.Point
import java.util.*


class FloodFill {
    fun floodFill(image: Bitmap, node: Point?, targetColor: Int, replacementColor: Int) {
        var node = node
        val width = image.width
        val height = image.height
        if (targetColor != replacementColor) {
            val queue: Queue<Point> = LinkedList()
            do {
                var x = node!!.x
                val y = node!!.y
                while (x > 0 && image.getPixel(x - 1, y) == targetColor) {
                    x--
                }
                var spanUp = false
                var spanDown = false
                while (x < width && image.getPixel(x, y) == targetColor) {
                    image.setPixel(x, y, replacementColor)
                    if (!spanUp && y > 0 && image.getPixel(x, y - 1) == targetColor) {
                        queue.add(Point(x, y - 1))
                        spanUp = true
                    } else if (spanUp && y > 0 && image.getPixel(x, y - 1) != targetColor) {
                        spanUp = false
                    }
                    if (!spanDown && y < height - 1 && image.getPixel(x, y + 1) == targetColor) {
                        queue.add(Point(x, y + 1))
                        spanDown = true
                    } else if (spanDown && y < (height - 1) && image.getPixel(
                            x,
                            y + 1
                        ) != targetColor
                    ) {
                        spanDown = false
                    }
                    x++
                }
                node = queue.poll()
            } while (node != null)
        }
    }
}