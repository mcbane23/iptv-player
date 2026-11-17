package com.bane23.iptvplayer.ui.common.icons

import androidx.compose.ui.graphics.vector.PathNode

/**
 * Sprema putanju ikonice za crtanje. Posle zatvaranja oblika („z”) sledeći relativni pomeraj
 * („m”) po pravilu kreće od početka zatvorenog oblika, ali ga crtanje na uređaju računa od
 * poslednje nacrtane tačke. Kad se te dve tačke razlikuju, deo ikonice ode na pogrešno mesto
 * (odsečen ili iskrivljen oblik). Zato se relativni pomeraji ovde pretvaraju u apsolutne, a ako
 * posle „z” odmah sledi crtanje, dodaje se izričit pomeraj na početak oblika.
 */
internal fun absoluteMoves(nodes: List<PathNode>): List<PathNode> {
    val out = ArrayList<PathNode>(nodes.size)
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f
    var closed = false
    for (node in nodes) {
        when (node) {
            is PathNode.MoveTo -> {
                x = node.x
                y = node.y
                startX = x
                startY = y
                out += node
            }

            is PathNode.RelativeMoveTo -> {
                x += node.dx
                y += node.dy
                startX = x
                startY = y
                out += PathNode.MoveTo(x, y)
            }

            PathNode.Close -> {
                x = startX
                y = startY
                out += node
            }

            else -> {
                if (closed) out += PathNode.MoveTo(x, y)
                when (node) {
                    is PathNode.LineTo -> { x = node.x; y = node.y }
                    is PathNode.RelativeLineTo -> { x += node.dx; y += node.dy }
                    is PathNode.HorizontalTo -> x = node.x
                    is PathNode.RelativeHorizontalTo -> x += node.dx
                    is PathNode.VerticalTo -> y = node.y
                    is PathNode.RelativeVerticalTo -> y += node.dy
                    is PathNode.CurveTo -> { x = node.x3; y = node.y3 }
                    is PathNode.RelativeCurveTo -> { x += node.dx3; y += node.dy3 }
                    is PathNode.ReflectiveCurveTo -> { x = node.x2; y = node.y2 }
                    is PathNode.RelativeReflectiveCurveTo -> { x += node.dx2; y += node.dy2 }
                    is PathNode.QuadTo -> { x = node.x2; y = node.y2 }
                    is PathNode.RelativeQuadTo -> { x += node.dx2; y += node.dy2 }
                    is PathNode.ReflectiveQuadTo -> { x = node.x; y = node.y }
                    is PathNode.RelativeReflectiveQuadTo -> { x += node.dx; y += node.dy }
                    is PathNode.ArcTo -> { x = node.arcStartX; y = node.arcStartY }
                    is PathNode.RelativeArcTo -> { x += node.arcStartDx; y += node.arcStartDy }
                    else -> Unit
                }
                out += node
            }
        }
        closed = node is PathNode.Close
    }
    return out
}
