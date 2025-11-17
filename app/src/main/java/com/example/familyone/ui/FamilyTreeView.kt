package com.example.familyone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class FamilyTreeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val linePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        alpha = 180
    }
    
    private val connections = mutableListOf<Connection>()
    
    data class Connection(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val isParentConnection: Boolean = false
    )
    
    fun addConnection(startX: Float, startY: Float, endX: Float, endY: Float, isParent: Boolean = false) {
        connections.add(Connection(startX, startY, endX, endY, isParent))
        invalidate()
    }
    
    fun clearConnections() {
        connections.clear()
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        connections.forEach { connection ->
            if (connection.isParentConnection) {
                // Рисуем прямую линию для родителей
                canvas.drawLine(
                    connection.startX,
                    connection.startY,
                    connection.endX,
                    connection.endY,
                    linePaint
                )
            } else {
                // Рисуем кривую линию от родителя к ребенку
                drawCurvedConnection(canvas, connection)
            }
        }
    }
    
    private fun drawCurvedConnection(canvas: Canvas, connection: Connection) {
        val path = Path()
        path.moveTo(connection.startX, connection.startY)
        
        // Контрольная точка для кривой Безье
        val controlY = (connection.startY + connection.endY) / 2
        
        path.quadTo(
            connection.startX,
            controlY,
            connection.endX,
            connection.endY
        )
        
        canvas.drawPath(path, linePaint)
    }
}

