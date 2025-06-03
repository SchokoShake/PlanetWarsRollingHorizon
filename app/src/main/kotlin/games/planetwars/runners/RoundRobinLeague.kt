package games.planetwars.runners

import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.evo.SimpleEvoAgent
import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.agents.rhea.RheaAgent
import games.planetwars.agents.rhea.RheaAgentMultipleParents
import games.planetwars.agents.rhea.Crossover
import games.planetwars.agents.rhea.ParentSelectionStrategy
import games.planetwars.core.GameParams
import games.planetwars.core.Player

fun main() {
//    val agents = SamplePlayerLists().getRandomTrio()
    val agents = SamplePlayerLists().getFullList()
//    agents.add(DoNothingAgent())
    val league = RoundRobinLeague(agents, gamesPerPair = 5)
    val results = league.runRoundRobin()
    // use the League utils to print the results
    val writer = LeagueWriter()
    val leagueResult = LeagueResult(results.values.toList())
    val markdownContent = writer.generateMarkdownTable(leagueResult)
    writer.saveMarkdownToFile(markdownContent)

    // print sorted results directly to console
    val sortedResults = results.toList().sortedByDescending { it.second.points }.toMap()
    for (entry in sortedResults.values) {
        println("${entry.agentName} : ${entry.points} : ${entry.nGames}")
    }

}

class SamplePlayerLists {
    fun getRandomTrio(): MutableList<PlanetWarsAgent> {
        return mutableListOf(
            PureRandomAgent(),
            BetterRandomAgent(),
            CarefulRandomAgent(),
        )
    }

    fun getFullList(): MutableList<PlanetWarsAgent> {
        return mutableListOf(
            RheaAgent(
                sequenceLength = 200,
                populationSize = 30,
                numberElites = 3,
                mutationProbability = 0.8,
                evaluationOpponentAgent = DoNothingAgent(),
                parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                crossover = Crossover.Uniform
            ),
            RheaAgent(
                sequenceLength = 200,
                populationSize = 30,
                numberElites = 3,
                mutationProbability = 0.8,
                evaluationOpponentAgent = DoNothingAgent(),
                parentSelectionStrategy = ParentSelectionStrategy.Rank,
                crossover = Crossover.Uniform
            ),
            RheaAgent(
                sequenceLength = 200,
                populationSize = 30,
                numberElites = 3,
                mutationProbability = 0.8,
                evaluationOpponentAgent = DoNothingAgent(),
                parentSelectionStrategy = ParentSelectionStrategy.Tournament(t=0.3),
                crossover = Crossover.Uniform
            ),
        )
    }
}

data class RoundRobinLeague(
    val agents: List<PlanetWarsAgent>,
    val gamesPerPair: Int = 10,
    val gameParams: GameParams = GameParams(numPlanets = 20),
) {
    fun runPair(agent1: PlanetWarsAgent, agent2: PlanetWarsAgent): Map<Player, Int> {
        val gameRunner = GameRunner(agent1, agent2, gameParams)
        return gameRunner.runGames(gamesPerPair)
    }

    fun runRoundRobin(): Map<String, LeagueEntry> {
        val tStart = System.currentTimeMillis()
        val scores = mutableMapOf<String, LeagueEntry>()
        for (agent in agents) {
            scores[agent.getAgentType()] = LeagueEntry(agent.getAgentType())
        }

        val totalGames = agents.size * (agents.size - 1)
        var gameCounter = 0

        for (i in 0 until agents.size) {
            for (j in 0 until agents.size) {
                if (i == j) continue

                val loopStart = System.currentTimeMillis()

                val agent1 = agents[i]
                val agent2 = agents[j]
                val result = runPair(agent1, agent2)

                val leagueEntry1 = scores[agent1.getAgentType()]!!
                val leagueEntry2 = scores[agent2.getAgentType()]!!
                leagueEntry1.points += result[Player.Player1]!!
                leagueEntry2.points += result[Player.Player2]!!
                leagueEntry1.nGames += gamesPerPair
                leagueEntry2.nGames += gamesPerPair

                gameCounter++
                val elapsed = System.currentTimeMillis() - tStart
                val avgPerGame = elapsed.toDouble() / gameCounter
                val remaining = ((totalGames - gameCounter) * avgPerGame / 1000).toInt() // seconds
                val minutes = remaining / 60
                val seconds = remaining % 60

                // Progress bar
                val progress = gameCounter.toDouble() / totalGames
                val barLength = 40
                val filledLength = (progress * barLength).toInt()
                val bar = "█".repeat(filledLength) + "-".repeat(barLength - filledLength)

                print(
                    "\rProgress: |$bar| ${(progress * 100).toInt()}% " +
                            "($gameCounter/$totalGames) – ETA: ${minutes}m ${seconds}s"
                )
            }
        }

        println("\nRound Robin took ${(System.currentTimeMillis() - tStart) / 1000} seconds")
        return scores
    }

}