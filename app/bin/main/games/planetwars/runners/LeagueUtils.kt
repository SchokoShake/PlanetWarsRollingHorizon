package games.planetwars.runners

import java.io.File
import java.util.Locale
import kotlin.math.max


data class LeagueEntry(
        val agentName: String,
        var points: Double = 0.0,
        var nGames: Int = 0,
        // Add fields to accumulate total time and moves
        var totalTimeAcrossAllGames: Long = 0L,
        var totalMovesAcrossAllGames: Int = 0
) {
    fun winRate(): Double {
        return if (nGames > 0) 100 * points / nGames else 0.0
    }

    // Function to calculate the average time per turn
    fun averageTimePerTurn(): Double {
        return if (totalMovesAcrossAllGames > 0) {
            totalTimeAcrossAllGames.toDouble() / totalMovesAcrossAllGames
        } else {
            0.0
        }
    }
}

data class LeagueResult(
        val entries: List<LeagueEntry>
) {
    fun getSortedEntries(): List<LeagueEntry> {
        return entries.sortedByDescending { it.winRate() }
    }
}

data class LeagueWriter(
        val outputDir: String = "results/sample/",
        val markdownFilename: String = "league.md",
        val latexFilename: String = "league.tex"
) {

    fun generateMarkdownTable(league: LeagueResult): String {
        val sortedEntries = league.getSortedEntries()
        if (sortedEntries.isEmpty()) {
            return "| Rank | Agent Name | Win Rate % | Played | Time/Turn (ms) |\n|------|------------|----------|--------|----------------|\n"
        }

        // --- 1. Calculate the maximum width needed for each column ---

        // Get all data as strings to measure their length
        val headerTitles = listOf("Rank", "Agent Name", "Win Rate %", "Played", "Time/Turn (ms)")
        val ranks = sortedEntries.mapIndexed { index, _ -> (index + 1).toString() }
        val agentNames = sortedEntries.map { it.agentName }
        val winRates = sortedEntries.map { "%.1f".format(it.winRate()) }
        val gamesPlayed = sortedEntries.map { it.nGames.toString() }
        val avgTimes = sortedEntries.map { "%.2f".format(it.averageTimePerTurn()) }

        // Determine the width by finding the max length of the header or any data cell in that column
        val colWidths = listOf(
                max(headerTitles[0].length, ranks.maxOfOrNull { it.length } ?: 0),
                max(headerTitles[1].length, agentNames.maxOfOrNull { it.length } ?: 0),
                max(headerTitles[2].length, winRates.maxOfOrNull { it.length } ?: 0),
                max(headerTitles[3].length, gamesPlayed.maxOfOrNull { it.length } ?: 0),
                max(headerTitles[4].length, avgTimes.maxOfOrNull { it.length } ?: 0)
        )

        // --- 2. Build the table string with padding ---

        val sb = StringBuilder()

        // Helper function to build a row
        fun buildRow(cells: List<String>): String {
            return "| " + cells.mapIndexed { i, cell -> cell.padEnd(colWidths[i]) }.joinToString(" | ") + " |\n"
        }

        // Build Header
        sb.append(buildRow(headerTitles))

        // Build Separator line
        val separatorCells = colWidths.map { "-".repeat(it) }
        sb.append("|-" + separatorCells.joinToString("-|-") + "-|\n")

        // Build Data Rows
        for (i in sortedEntries.indices) {
            val rowCells = listOf(ranks[i], agentNames[i], winRates[i], gamesPlayed[i], avgTimes[i])
            sb.append(buildRow(rowCells))
        }

        return sb.toString()
    }


    // --- Existing saveMarkdownToFile and all LaTeX functionality (no changes) ---
    fun saveMarkdownToFile(markdownContent: String) {
        val dir = File(outputDir)
        if (!dir.exists()) dir.mkdirs()
        val outputFile = File(dir, markdownFilename)
        outputFile.writeText(markdownContent)
        println("League results saved to ${outputFile.absolutePath}")
    }

    /**
     * Generates a LaTeX table from the league results.
     * Note: This format uses the `booktabs` package for professional-looking rules.
     * Ensure you have `\usepackage{booktabs}` in your LaTeX document's preamble.
     */
    fun generateLatexTable(league: LeagueResult): String {
        val sortedEntries = league.getSortedEntries()
        // Define the LaTeX table header and column alignment {r l r r r} -> right, left, right, right, right
        val header = """
        % For a professional-looking table, add \usepackage{booktabs} to your LaTeX preamble
        \begin{tabular}{r l r r r}
        \toprule
        Rank & Agent Name & Win Rate \% & Played & Time/Turn (ms) \\
        \midrule
        """.trimIndent()

        val footer = """
        \bottomrule
        \end{tabular}
        """.trimIndent()

        val rows = sortedEntries.mapIndexed { index, entry ->
            val rank = index + 1
            // LaTeX requires special characters like '_' to be escaped with a '\'
            val agentName = entry.agentName.replace("_", "\\_")

            // Use German locale to get a comma as the decimal separator, as in the example
            val formattedWinRate = String.format(Locale.GERMAN, "%.1f", entry.winRate())
            val formattedTime = String.format(Locale.GERMAN, "%.2f", entry.averageTimePerTurn())

            // Each column is separated by '&' and each row ends with '\\'
            "   $rank & $agentName & $formattedWinRate & ${entry.nGames} & $formattedTime \\\\"
        }.joinToString("\n")

        return "$header\n$rows\n$footer"
    }

    /**
     * Saves the LaTeX table string to a .tex file.
     */
    fun saveLatexToFile(latexContent: String) {
        val dir = File(outputDir)
        if (!dir.exists()) dir.mkdirs()
        val outputFile = File(dir, latexFilename)
        outputFile.writeText(latexContent)
        println("LaTeX league table saved to ${outputFile.absolutePath}")
    }
}

// Example usage
fun main() {
    val league = LeagueResult(
        listOf(
            LeagueEntry("AlphaBot", 10.0, 20),
            LeagueEntry("BetaAI", 8.0, 4),
            LeagueEntry("GammaSolver", 8.0, 3),
            LeagueEntry("DeltaAgent", 6.0, 5)
        )
    )

    val writer = LeagueWriter()
    val markdownContent = writer.generateMarkdownTable(league)
    writer.saveMarkdownToFile(markdownContent)
    val latexContent = writer.generateLatexTable(league)
    writer.saveLatexToFile(latexContent)
}
