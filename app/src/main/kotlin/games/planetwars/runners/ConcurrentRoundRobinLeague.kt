package games.planetwars.runners

import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.GreedyHeuristicAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.agents.rhea.Crossover
import games.planetwars.agents.rhea.FitnessFunction
import games.planetwars.agents.rhea.InitializationMethod
import games.planetwars.agents.rhea.Mutation
import games.planetwars.agents.rhea.ParentSelectionStrategy
import games.planetwars.agents.rhea.RheaAgent
import games.planetwars.core.GameParams
import games.planetwars.core.Player
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

fun main() = runBlocking {
    val agents = CSamplePlayerLists().getFullList()
    val league = ConcurrentRoundRobinLeague(agents, gamesPerPair = 250, gameParams = GameParams(numPlanets =20, maxTicks = 1000, edgeSeparation = 50.0))
    val results = league.runRoundRobin()

    // Print sorted results directly to the console
    val sortedResults = results.values.sortedByDescending { it.points }
    for (entry in sortedResults) {
        println("----------------------------------------------------")
        println("${entry.agentName} | Points: ${entry.points} | Games: ${entry.nGames}")
        println("----------------------------------------------------")
        // Sort opponents by name for consistent output
        val sortedOpponents = entry.outcomes.keys.sorted()
        for(opponentName in sortedOpponents) {
            val outcomes = entry.outcomes[opponentName]!!
            println(
                    "\tvs ${opponentName}: " +
                            "W: ${outcomes.wins}, " +
                            "L: ${outcomes.losses}, " +
                            "D: ${outcomes.draws}"
            )
        }
        println()
    }
}


class CSamplePlayerLists {
    fun getRandomTrio(): MutableList<PlanetWarsAgent> {
        return mutableListOf(
                PureRandomAgent(),
                BetterRandomAgent(),
                CarefulRandomAgent(),
        )
    }
    val popSize=142;
    fun getFullList(): MutableList<PlanetWarsAgent> {
        return mutableListOf(
                RheaAgent(
                        name = "Aggressive",
                        sequenceLength = 69,
                        populationSize = popSize,
                        numberElites = (popSize * 0.067).toInt(),
                        mutation = Mutation.n_bit(8),
                        evaluationOpponentAgent = DoNothingAgent(),
                        parentSelectionStrategy = ParentSelectionStrategy.Tournament(0.121),
                        crossover = Crossover.Uniform,
                        initializationMethod = InitializationMethod.None,
                        fitnessFunction = FitnessFunction.Aggressive(
                                p = 12.9,
                                s = 2.5,
                                t = 0.9
                        ),
                        useVariableShipCount = true,
                ),
                RheaAgent(
                        name = "Ships",
                        sequenceLength = 194,
                        populationSize = popSize,
                        numberElites = (popSize * 0.09513517486976772).toInt(),
                        mutation = Mutation.Uniform(0.12031591154944632),
                        evaluationOpponentAgent = DoNothingAgent(),
                        parentSelectionStrategy = ParentSelectionStrategy.Tournament(0.3254484700263845),
                        crossover = Crossover.Uniform,
                        initializationMethod = InitializationMethod.None,
                        fitnessFunction = FitnessFunction.Ships,
                        useVariableShipCount = true,
                ),
                GreedyHeuristicAgent()
        )
    }
}

/**
 * A data class to store the outcome of games against a specific opponent.
 */
data class HeadToHeadOutcome(
        var wins: Int = 0,
        var losses: Int = 0,
        var draws: Int = 0
)

/**
 * Modified LeagueEntry to include a map for head-to-head results.
 */
data class DetailedLeagueEntry(
        val agentName: String,
        var points: Int = 0,
        var nGames: Int = 0,
        var totalTimeAcrossAllGames: Long = 0,
        var totalMovesAcrossAllGames: Int = 0,
        // Map opponent name to the outcome of games
        val outcomes: MutableMap<String, HeadToHeadOutcome> = mutableMapOf()
)


data class ConcurrentRoundRobinLeague(
        val agents: List<PlanetWarsAgent>,
        val gamesPerPair: Int = 100,
        val gameParams: GameParams = GameParams(numPlanets = 20, maxTicks = 1000),
) {

    private data class GamePairResult(
            val agent1Type: String,
            val agent2Type: String,
            val p1Points: Int,
            val p2Points: Int,
            val agent1Time: Long,
            val agent2Time: Long,
            val totalMoves: Int
    )

    suspend fun runRoundRobin(concurrencyLevel: Int = Runtime.getRuntime().availableProcessors()): Map<String, DetailedLeagueEntry> {
        val tStart = System.currentTimeMillis()
        val scores = agents.associate { agent ->
            agent.getAgentType() to DetailedLeagueEntry(agent.getAgentType()).apply {
                // Initialize the outcomes map for every potential opponent
                agents.filter { it.getAgentType() != agent.getAgentType() }.forEach { opponent ->
                    outcomes[opponent.getAgentType()] = HeadToHeadOutcome()
                }
            }
        }.toMutableMap()

        val dispatcher = Executors.newFixedThreadPool(concurrencyLevel).asCoroutineDispatcher()
        val resultsChannel = Channel<GamePairResult>()
        val totalGames = agents.size * (agents.size - 1) * gamesPerPair

        coroutineScope {
            launch {
                repeat(totalGames) { gameCounter ->
                    val result = resultsChannel.receive()

                    val leagueEntry1 = scores[result.agent1Type]!!
                    val leagueEntry2 = scores[result.agent2Type]!!

                    // Update total points and game counts
                    leagueEntry1.points += result.p1Points
                    leagueEntry2.points += result.p2Points
                    leagueEntry1.nGames += 1
                    leagueEntry2.nGames += 1
                    leagueEntry1.totalTimeAcrossAllGames += result.agent1Time
                    leagueEntry2.totalTimeAcrossAllGames += result.agent2Time
                    leagueEntry1.totalMovesAcrossAllGames += result.totalMoves
                    leagueEntry2.totalMovesAcrossAllGames += result.totalMoves

                    // Update head-to-head outcomes
                    val outcome1 = leagueEntry1.outcomes[result.agent2Type]!!
                    val outcome2 = leagueEntry2.outcomes[result.agent1Type]!!

                    when {
                        result.p1Points > result.p2Points -> { // Agent 1 wins
                            outcome1.wins++
                            outcome2.losses++
                        }
                        result.p2Points > result.p1Points -> { // Agent 2 wins
                            outcome1.losses++
                            outcome2.wins++
                        }
                        else -> { // Draw
                            outcome1.draws++
                            outcome2.draws++
                        }
                    }


                    val currentProgress = gameCounter + 1
                    val elapsed = System.currentTimeMillis() - tStart
                    val avgPerGame = elapsed.toDouble() / currentProgress
                    val remaining = ((totalGames - currentProgress) * avgPerGame / 1000).toInt()
                    val minutes = remaining / 60
                    val seconds = remaining % 60

                    val progress = currentProgress.toDouble() / totalGames
                    val barLength = 40
                    val filledLength = (progress * barLength).toInt()
                    val bar = "█".repeat(filledLength) + "-".repeat(barLength - filledLength)

                    print("\rProgress: |$bar| ${(progress * 100).toInt()}% ($currentProgress/$totalGames) – ETA: ${minutes}m ${seconds}s")
                }
            }

            for (i in agents.indices) {
                for (j in agents.indices) {
                    if (i == j) continue

                    val agent1 = agents[i]
                    val agent2 = agents[j]

                    repeat(gamesPerPair) {
                        launch(dispatcher) {
                            val gameRunner = GameRunner(agent1, agent2, gameParams)
                            val result = gameRunner.runGames(1)
                            val gamePairResult = GamePairResult(
                                    agent1Type = agent1.getAgentType(),
                                    agent2Type = agent2.getAgentType(),
                                    p1Points = result[Player.Player1]!!,
                                    p2Points = result[Player.Player2]!!,
                                    agent1Time = gameRunner.agent1TotalTime,
                                    agent2Time = gameRunner.agent2TotalTime,
                                    totalMoves = gameRunner.totalMoves
                            )
                            resultsChannel.send(gamePairResult)
                        }
                    }
                }
            }
        }

        dispatcher.close()
        resultsChannel.close()

        println("\nRound Robin took ${(System.currentTimeMillis() - tStart) / 1000} seconds")
        return scores
    }
}