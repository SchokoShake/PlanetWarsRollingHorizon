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
import games.planetwars.agents.rhea.FitnessFunction
import games.planetwars.agents.rhea.InitializationMethod
import games.planetwars.agents.rhea.Mutation
import games.planetwars.agents.rhea.ParentSelectionStrategy
import games.planetwars.core.GameParams
import games.planetwars.core.Player

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

fun main()=runBlocking  {
//    val agents = SamplePlayerLists().getRandomTrio()
    val agents = CSamplePlayerLists().getFullList()
//    agents.add(DoNothingAgent())
    val league = ConcurrentRoundRobinLeague(agents, gamesPerPair = 10)
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

class CSamplePlayerLists {
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
                        sequenceLength = 100,
                        populationSize = 50,
                        numberElites = 5,

                        mutation = Mutation.Uniform(0.8),
                        evaluationOpponentAgent = DoNothingAgent(),
                        parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                        crossover = Crossover.Uniform,
                        initializationMethod =  InitializationMethod.None,
                        fitnessFunction = FitnessFunction.Ships,
                        useVariableShipCount =  false,
                ),
                RheaAgent(
                        sequenceLength = 100,
                        populationSize = 50,
                        numberElites = 5,
                        mutation = Mutation.Uniform(0.8),
                        evaluationOpponentAgent = DoNothingAgent(),
                        parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                        crossover = Crossover.Uniform,
                        initializationMethod =  InitializationMethod.None,
                        fitnessFunction = FitnessFunction.Ships,
                        useVariableShipCount =  true,
                ),
                RheaAgent(
                        sequenceLength = 100,
                        populationSize = 50,
                        numberElites = 5,
                        mutation = Mutation.Uniform(0.8),
                        evaluationOpponentAgent = DoNothingAgent(),
                        parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                        crossover = Crossover.Uniform,
                        initializationMethod =  InitializationMethod.ISLA,
                        fitnessFunction = FitnessFunction.Growth,
                        useVariableShipCount =  true,
                ),
                RheaAgent(
                        sequenceLength = 100,
                        populationSize = 50,
                        numberElites = 5,
                        mutation = Mutation.Uniform(0.8),
                        evaluationOpponentAgent = DoNothingAgent(),
                        parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                        crossover = Crossover.Uniform,
                        initializationMethod =  InitializationMethod.ISLA,
                        fitnessFunction = FitnessFunction.Ratio,
                        useVariableShipCount =  true,
                ),
                RheaAgent(
                        sequenceLength = 100,
                        populationSize = 50,
                        numberElites = 5,
                        mutation = Mutation.Softmax,
                        evaluationOpponentAgent = DoNothingAgent(),
                        parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                        crossover = Crossover.Uniform,
                        initializationMethod =  InitializationMethod.ISLA,
                        fitnessFunction = FitnessFunction.Growth,
                        useVariableShipCount =  true,
                ),
                RheaAgent(
                        sequenceLength = 100,
                        populationSize = 50,
                        numberElites = 5,
                        mutation = Mutation.Softmax,
                        evaluationOpponentAgent = DoNothingAgent(),
                        parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                        crossover = Crossover.Uniform,
                        initializationMethod =  InitializationMethod.ISLA,
                        fitnessFunction = FitnessFunction.Growth,
                        useVariableShipCount =  true,
                ),
        )
    }
}



data class ConcurrentRoundRobinLeague(
        val agents: List<PlanetWarsAgent>,
        val gamesPerPair: Int = 2,
        val gameParams: GameParams = GameParams(numPlanets = 20),
) {

    /**
     * Eine private Datenklasse, um die Ergebnisse eines einzelnen Spielpaares zu kapseln.
     * Dies hilft uns, die Ergebnisse aus den nebenläufigen Aufgaben sicher zu sammeln.
     */
    private data class GamePairResult(
            val agent1Type: String,
            val agent2Type: String,
            val p1Points: Int,
            val p2Points: Int,
            val agent1Time: Long,
            val agent2Time: Long,
            val totalMoves: Int
    )

    /**
     * Führt das Round-Robin-Turnier nebenläufig aus.
     *
     * @param concurrencyLevel Die maximale Anzahl von Spielen, die gleichzeitig ausgeführt werden sollen.
     * Ein guter Standardwert ist die Anzahl der verfügbaren CPU-Kerne.
     * @return Eine Map mit den finalen Liga-Einträgen für jeden Agenten.
     */
    suspend fun runRoundRobin(concurrencyLevel: Int = Runtime.getRuntime().availableProcessors()): Map<String, LeagueEntry> {
        val tStart = System.currentTimeMillis()
        val scores = agents.associate { it.getAgentType() to LeagueEntry(it.getAgentType()) }.toMutableMap()

        // Erstelle einen Dispatcher mit einer festen Anzahl von Threads.
        val dispatcher = Executors.newFixedThreadPool(concurrencyLevel).asCoroutineDispatcher()

        // Erstelle einen Channel, um Ergebnisse von abgeschlossenen Spielen zu empfangen.
        val resultsChannel = Channel<GamePairResult>()
        val totalGames = agents.size * (agents.size - 1)*gamesPerPair

        coroutineScope {
            // 1. Starte den "Konsumenten": eine einzelne Coroutine, die auf Ergebnisse wartet.
            //    Dieser Block läuft parallel zu den Spielen.
            launch {
                repeat(totalGames) { gameCounter ->
                    // Empfange das nächste verfügbare Ergebnis vom Channel.
                    // Diese Zeile pausiert, bis ein Spiel sein Ergebnis sendet.
                    val result = resultsChannel.receive()

                    // Aktualisiere die Scores, sobald ein Ergebnis eintrifft.
                    val leagueEntry1 = scores[result.agent1Type]!!
                    val leagueEntry2 = scores[result.agent2Type]!!
                    leagueEntry1.points += result.p1Points
                    leagueEntry2.points += result.p2Points
                    leagueEntry1.nGames += 1
                    leagueEntry2.nGames += 1
                    leagueEntry1.totalTimeAcrossAllGames += result.agent1Time
                    leagueEntry2.totalTimeAcrossAllGames += result.agent2Time
                    leagueEntry1.totalMovesAcrossAllGames += result.totalMoves
                    leagueEntry2.totalMovesAcrossAllGames += result.totalMoves

                    // Aktualisiere die Fortschrittsanzeige in Echtzeit.
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

            // 2. Starte die "Produzenten": alle Spiel-Paare als nebenläufige Aufgaben.
            for (i in agents.indices) {
                for (j in agents.indices) {
                    if (i == j) continue

                    val agent1 = agents[i]
                    val agent2 = agents[j]

                    repeat(gamesPerPair){
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
                            // Sende das Ergebnis an den Channel, sobald das Spiel fertig ist.
                            resultsChannel.send(gamePairResult)
                        }
                    }
                }
            }
        } // coroutineScope wartet, bis sowohl der Konsument als auch alle Produzenten fertig sind.

        // Aufräumen
        dispatcher.close()
        resultsChannel.close()

        println("\nRound Robin took ${(System.currentTimeMillis() - tStart) / 1000} seconds")
        return scores
    }
}