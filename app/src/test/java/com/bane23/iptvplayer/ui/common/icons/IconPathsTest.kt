package com.bane23.iptvplayer.ui.common.icons

import androidx.compose.ui.graphics.vector.PathNode
import org.junit.Assert.assertEquals
import org.junit.Test

class IconPathsTest {
    @Test
    fun relativeMoveAfterCloseStartsFromShapeStart() {
        // „M6 6h2v12H6z m3.5 6 l8.5 6V6z”: prvi oblik se završava u (6, 18), a počinje u (6, 6).
        val nodes = listOf(
            PathNode.MoveTo(6f, 6f),
            PathNode.RelativeHorizontalTo(2f),
            PathNode.RelativeVerticalTo(12f),
            PathNode.HorizontalTo(6f),
            PathNode.Close,
            PathNode.RelativeMoveTo(3.5f, 6f),
            PathNode.RelativeLineTo(8.5f, 6f),
            PathNode.VerticalTo(6f),
            PathNode.Close,
        )

        val result = absoluteMoves(nodes)

        assertEquals(PathNode.MoveTo(9.5f, 12f), result[5])
        assertEquals(nodes.size, result.size)
        assertEquals(nodes.take(5), result.take(5))
        assertEquals(nodes.drop(6), result.drop(6))
    }

    @Test
    fun relativeMoveChainKeepsPositions() {
        val nodes = listOf(
            PathNode.MoveTo(3f, 17f),
            PathNode.RelativeHorizontalTo(5f),
            PathNode.RelativeVerticalTo(2f),
            PathNode.HorizontalTo(3f),
            PathNode.Close,
            PathNode.RelativeMoveTo(7f, 0f),
            PathNode.RelativeHorizontalTo(11f),
            PathNode.RelativeVerticalTo(2f),
            PathNode.HorizontalTo(10f),
            PathNode.Close,
            PathNode.RelativeMoveTo(0f, -6f),
        )

        val result = absoluteMoves(nodes)

        assertEquals(PathNode.MoveTo(10f, 17f), result[5])
        assertEquals(PathNode.MoveTo(10f, 11f), result[10])
    }

    @Test
    fun drawingRightAfterCloseGetsExplicitMove() {
        val nodes = listOf(
            PathNode.MoveTo(2f, 2f),
            PathNode.LineTo(8f, 2f),
            PathNode.LineTo(8f, 8f),
            PathNode.Close,
            PathNode.RelativeLineTo(1f, 1f),
        )

        val result = absoluteMoves(nodes)

        assertEquals(
            listOf(
                PathNode.MoveTo(2f, 2f),
                PathNode.LineTo(8f, 2f),
                PathNode.LineTo(8f, 8f),
                PathNode.Close,
                PathNode.MoveTo(2f, 2f),
                PathNode.RelativeLineTo(1f, 1f),
            ),
            result,
        )
    }

    @Test
    fun absolutePathIsUnchanged() {
        val nodes = listOf(
            PathNode.MoveTo(8f, 5f),
            PathNode.VerticalTo(19f),
            PathNode.LineTo(19f, 12f),
            PathNode.Close,
        )

        assertEquals(nodes, absoluteMoves(nodes))
    }
}
