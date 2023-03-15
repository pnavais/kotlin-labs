package com.github.pnavais.kotlin.minesweeper

import kotlin.math.min
import kotlin.random.Random
import kotlin.random.nextInt

enum class GameOutcome(val terminal: Boolean) {
    KEEP_GOING(false),
    USER_WON(true),
    USER_FAILED(true),
    USER_EXITED(true),
}

sealed interface Status {
    fun payload(): Char
}

enum class SimpleStatus(private val code: Char) : Status {
    EMPTY('/'),
    MINE('X');

    override fun payload(): Char = this.code
}

data class MineCounterStatus(private var numMines: Int) : Status {
    override fun payload(): Char = numMines.digitToChar()
    fun increase() = numMines++
}

enum class Operation {
    FREE,
    MINE,
    REVEAL,
    EXIT
}

data class Coords(val x: Int, val y: Int)

data class Position(var status: Status, var explored: Boolean = false, var flagged: Boolean = false) {

    override fun toString(): String {
        return if (explored) {
            status.payload().toString()
        } else  if (flagged) {
            "*"
        } else {
            "."
        }
    }
}

class DataUtils {
    companion object {
        /**
         * Prompts the user for a given input until the validation is successful.
         * Then the converter is used to retrieve the target type.
         *
         * @param title The title to ask the user
         * @param checker The validation function
         * @param converter The converter to the target type
         */
        fun <T> promptUser(title: String, checker: (s: String) -> Boolean, converter: (s: String) -> T): T {
            var line = ""
            var isValid = false

            while (!isValid) {
                print(title)
                line = readln()
                isValid = checker(line)
            }

            return converter(line)
        }

        /**
         * Validates the user input line to check if
         * it's in the expected format i.e. : [0-9] [0-9] Operator
         */
        fun isValidUserData(line: String): Boolean {
            val input = line.split(' ')
            return if (input.size >= 3) {
                input[0].toIntOrNull() != null && input[1].toIntOrNull() != null && Operation.values()
                    .firstOrNull { it.name.equals(input[2], true) } != null
            } else {
                (Operation.REVEAL.name.equals(input[0], true)) || (Operation.EXIT.name.equals(input[0], true))
            }
        }

        /**
         * Parses the line to get the expected format input
         */
        fun parseUserData(line: String): Pair<Coords?, Operation> {
            val input = line.split(' ')
            return if (input.size >= 3) {
                Coords(input[0].toInt(), input[1].toInt()) to Operation.valueOf(input[2].uppercase())
            } else {
                null to Operation.valueOf(input[0].uppercase())
            }
        }
    }
}

class MineSweeper(private val numMines: Int, private val x: Int = 9, private val y: Int = 9) {

    private val field: Array<Array<Position>> = Array(x) { Array(y) { Position(SimpleStatus.EMPTY) } }

    private var minesPlaced = 0
    private var minePositions : MutableList<Position> = mutableListOf()
    private var positionsFlagged: MutableMap<Coords, Position> = mutableMapOf()
    private var positionsExplored = 0
    private var numMoves = 0

    /**
     * Starts a new game by requesting in a loop the
     * user operations.
     */
    fun start(): GameOutcome {
        display()
        var gameOutcome = GameOutcome.KEEP_GOING
        while (!gameOutcome.terminal) {
            val (coords, operation) = DataUtils.promptUser(
                "Set/unset mines marks or claim a cell as free: > ",
                { s -> DataUtils.isValidUserData(s) },
                { s -> DataUtils.parseUserData(s) })

            if ((coords != null) && (minesPlaced == 0)) {
                placeMines(coords)
            }

            gameOutcome = doUserAction(coords, operation)
            numMoves++
        }

        when (gameOutcome) {
            GameOutcome.USER_WON -> println("Congratulations! You found all the mines!")
            GameOutcome.USER_FAILED -> println("You stepped on a mine and failed!")
            else -> println("Bye!")
        }

        return gameOutcome
    }

    /**
     * Computes mines for random positions
     * avoiding the first coordinates supplied by the user
     */
    private fun placeMines(coords: Coords) {
        while (minesPlaced != numMines) {
            val posX = Random.nextInt(0 until x)
            val posY = Random.nextInt(0 until y)
            if (((posX != coords.x) || (posY != coords.y)) && (field[posX][posY].status != SimpleStatus.MINE)) {
                field[posX][posY].status = SimpleStatus.MINE
                minePositions.add(field[posX][posY])
                surroundMine(posX, posY)
                minesPlaced++
            }
        }
    }

    /**
     * Update surrounding positions incrementing the mine counter
     */
    private fun surroundMine(posX: Int, posY: Int) {
        var colIdx = 0
        var rowIdx = 0
        // Lookup the whole 3x3 square
        repeat(9) {
            val currentX = posX + colIdx - 1
            val currentY = posY + rowIdx - 1
            // Checkout position is inside the field and skip the center
            if (((currentX in 0 until x) && (currentY in 0 until y)) &&
                (posX != currentX || posY != currentY) &&
                (field[currentX][currentY].status != SimpleStatus.MINE)) {
                val targetPos = field[currentX][currentY].status
                if (targetPos is MineCounterStatus) {
                    targetPos.increase()
                } else {
                    field[currentX][currentY].status = MineCounterStatus(1)
                }
            }
            colIdx = (colIdx + 1) % 3
            if (colIdx == 0) { rowIdx++ }
        }
    }

    /**
     * Executes the given operation and returns its
     * outcome in terms of gameplay.
     */
    private fun doUserAction(coords: Coords?, operation: Operation): GameOutcome {
        return when (operation) {
            Operation.REVEAL -> {
                reveal()
                GameOutcome.KEEP_GOING
            }
            Operation.EXIT -> GameOutcome.USER_EXITED
            Operation.MINE, Operation.FREE -> {
                var doDisplay = true
                val selectedPosition = field[coords!!.x-1][coords.y-1]
                if (operation == Operation.MINE) {
                    doDisplay = flagMine(coords)
                } else {
                    if (selectedPosition.status == SimpleStatus.MINE) {
                        revealMines()
                    } else {
                        explore(coords.x - 1, coords.y - 1)
                    }
                }
                if (doDisplay) {
                    display()
                }
                checkGameOver(operation, selectedPosition)
            }
        }
    }

    /**
     * Mark or remove mark for a mine at the given
     * position.
     */
    private fun flagMine(coords: Coords) : Boolean {
        var flagged = true
        val posX = min(coords.x - 1, x - 1)
        val posY = min(coords.y - 1, y - 1)
        val currentPosition = field[posX][posY]
        if (!currentPosition.explored) {
            currentPosition.flagged = !currentPosition.flagged
            if (currentPosition.flagged) {
                positionsFlagged[Coords(posX, posY)] = currentPosition
            } else {
                positionsFlagged.remove(Coords(posX, posY))
            }
        } else {
            println("Field already explored!")
            flagged = false
        }
        return flagged
    }

    /**
     * Explore the given position recursively
     * depending on its surroundings
     */
    private fun explore(posX: Int, posY: Int) {
        // Two options :
        // - Position is empty and surrounding positions contain mines -> only current cell is explored
        // - Position is empty and surrounding positions do not contain mines -> explore them recursively

        // Check current position is not a mine
        val currentPosition = field[posX][posY]
        if (currentPosition.status != SimpleStatus.MINE) {
            // Explore current position
            if (!currentPosition.explored) {
                currentPosition.explored = true
                positionsExplored++
            }
            // Check mines nearby
            val nearbyPositions = getNearbyPositions(posX, posY)
            // Explore surroundings recursively
            nearbyPositions.forEach { explore(it.x, it.y) }
        }
    }

    /**
     * Retrieve nearby positions if all of them are free of mines
     * otherwise return an empty list.
     */
    private fun getNearbyPositions(posX: Int, posY: Int) : List<Coords> {
        var colIdx = 0
        var rowIdx = 0
        val nearbyPositions = mutableListOf<Coords>()
        for (x in 1..9) {
            val currentX = posX + colIdx - 1
            val currentY = posY + rowIdx - 1
            // Checkout position is inside the field and skip the center
            if (((currentX in 0 until this.x) && (currentY in 0 until this.y)) &&
                (posX != currentX || posY != currentY)
            ) {
                if (field[currentX][currentY].status != SimpleStatus.MINE) {
                    if (!field[currentX][currentY].explored) {
                        nearbyPositions.add(Coords(currentX, currentY))
                    }
                } else {
                    nearbyPositions.clear()
                    break
                }
            }
            colIdx = (colIdx + 1) % 3
            if (colIdx == 0) {
                rowIdx++
            }
        }
        return nearbyPositions
    }

    /**
     * Checks the game has ended or not
     */
    private fun checkGameOver(operation: Operation, position: Position): GameOutcome {
        return if ((positionsExplored + minesPlaced == (x * y)) // All positions have been explored
            || (positionsFlagged.isNotEmpty() && positionsFlagged.size == numMines && positionsFlagged.all { it.value.status == SimpleStatus.MINE })
        ) // All flagged positions are mines
        {
            GameOutcome.USER_WON
        } else if ((operation != Operation.MINE) && (position.status == SimpleStatus.MINE)) {
            GameOutcome.USER_FAILED
        } else {
            GameOutcome.KEEP_GOING
        }
    }

    /**
     * Reveal mine positions
     */
    private fun revealMines() {
        minePositions.forEach { it.explored = true }
    }

    /**
     * Display the actual contents of the cells
     */
    private fun reveal() {
        display(true)
    }

    /**
     * Displays the contents of the minefield
     */
    private fun display(reveal: Boolean = false) {
        print("\n |")
        repeat(x) { i -> print(i + 1) }
        println("|")
        displaySeparator()
        repeat(y) { colIdx ->
            print("${colIdx + 1}|")
            repeat(x) { i -> print(if (reveal) { field[i][colIdx].status.payload() } else { field[i][colIdx] }) }
            println("|")
        }
        displaySeparator()
    }

    /**
     * Displays a separator line
     */
    private fun displaySeparator() {
        print("-|")
        repeat(x) { print("-") }
        println("|")
    }
}

fun main() {
    val numMines = DataUtils.promptUser(
        "How many mines do you want on the field? > ",
        { s -> s.toIntOrNull() != null },
        Integer::valueOf
    )

    MineSweeper(numMines).start()
}
