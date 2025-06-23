package games.planetwars.runners

import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.rhea.Crossover
import games.planetwars.agents.rhea.FitnessFunction
import games.planetwars.agents.rhea.InitializationMethod
import games.planetwars.agents.rhea.Mutation
import games.planetwars.agents.rhea.ParentSelectionStrategy
import games.planetwars.agents.rhea.RheaAgent
import games.planetwars.core.GameParams
import games.planetwars.core.Player
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import java.util.concurrent.Executors

/**
 * A data class defining the JSON structure for requests from the Optuna Python client.
 */
@Serializable
data class RheaTrialParams(
        val sequenceLength: Int,
        val populationSize: Int,
        val elitePercentage: Double,
        val mutationIndex: Double,
        val parentSelectionTournamentSize: Double,
        val crossoverIndex: Int,
        val initializationIndex: Int,
        val fitnessFunctionIndex: Int
)

// =================================================================================
// ==                        CONCURRENCY CONTROLLER                               ==
// == This dispatcher limits the number of concurrent simulations, preventing    ==
// == memory spikes and making resource usage more predictable.                  ==
// =================================================================================
// Determine the number of processors, but don't use more than 4 to be safe.
val parallelism = (Runtime.getRuntime().availableProcessors() / 2).toInt().coerceAtLeast(1)
val limitedParallelismDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(parallelism).asCoroutineDispatcher()
// =================================================================================


/**
 * A minimal Ktor server for debugging purposes.
 */
fun main() {
    println("Starting RheaAgent Ktor evaluation server with parallelism of $parallelism...")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json()
        }
        routing {
            get("/") {
                call.respondText("RheaAgent Evaluation Server is running. POST to /evaluate to run a trial.")
            }
            post("/evaluate") {
                // ADDED a log here to confirm the route is being hit immediately.
                println("POST /evaluate handler entered. Attempting to receive payload...")
                try {
                    val trialParams = call.receive<RheaTrialParams>()
                    println("Received trial: popSize=${trialParams.populationSize}, seqLen=${trialParams.sequenceLength}...")
                    val parameterSet = createParameterSetFromTrial(trialParams)

                    // You can reduce numGames to 1 or 5 for a quick test to see if the server responds fast.
                    val score = evaluateRheaAgent(parameterSet, numGames = 20)

                    println(" -> Overall Score for trial: ${"%.4f".format(score)}")
                    call.respond(HttpStatusCode.OK, mapOf("score" to score))

                } catch (t: Throwable) {
                    println("!!!!!!!!!!!!!! FATAL ERROR DURING TRIAL !!!!!!!!!!!!!!")
                    t.printStackTrace()
                    println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (t.message ?: "Unknown fatal error")))
                }
            }
        }
    }.start(wait = true)
}

/**
 * The objective function. This version now uses the LIMITED dispatcher and has more logging.
 */
suspend fun evaluateRheaAgent(params: ParameterSet, numGames: Int): Double {
    // The agent to be evaluated, created with the parameters from Optuna.
    val rheaAgent = RheaAgent(
            populationSize = params.populationSize,
            numberElites = params.numberElites,
            mutation = params.mutation,
            parentSelectionStrategy = params.parentSelectionStrategy,
            crossover = params.crossover,
            sequenceLength = params.sequenceLength,
            evaluationOpponentAgent = params.evaluationOpponentAgent,
            initializationMethod = params.initializationMethod,
            fitnessFunction = params.fitnessFunction,
            useVariableShipCount = params.useVariableShipCount
    )

    // A list of standard agents to play against for a robust evaluation.
    val baselineOpponents: List<PlanetWarsPlayer> = listOf(
            RheaAgent(
                    sequenceLength = 100,
                    populationSize = 159,
                    numberElites = (159*0.1742).toInt(),
                    mutation = Mutation.Uniform(0.07538),
                    evaluationOpponentAgent = DoNothingAgent(),
                    parentSelectionStrategy = ParentSelectionStrategy.Tournament(0.1388),
                    crossover = Crossover.Uniform,
                    initializationMethod =  InitializationMethod.None,
                    fitnessFunction = FitnessFunction.Ratio,
                    useVariableShipCount =  true,
            ),
    )

    println(" -> Starting evaluation against ${baselineOpponents.size} opponents...")

    // Run all opponent matchups using our new, controlled dispatcher
    val results = coroutineScope {
        baselineOpponents.map { opponent ->
            async(limitedParallelismDispatcher) { // <-- USE THE LIMITED DISPATCHER
                println("    - Playing vs ${opponent.javaClass.simpleName} ($numGames games)...")
                val gameParams = GameParams(numPlanets = 10)
                val gameRunner = GameRunner(agent1 = rheaAgent.copy(), agent2 = opponent, gameParams = gameParams)
                val matchResults = gameRunner.runGamesConcurrently(numGames)
                val p1Wins = matchResults.getOrDefault(Player.Player1, 0)
                val totalGames = matchResults.values.sum()
                val winRate = if (totalGames > 0) p1Wins.toDouble() / totalGames else 0.0
                println("    - Finished vs ${opponent.javaClass.simpleName}. Win rate: ${"%.3f".format(winRate)}")
                winRate
            }
        }.awaitAll()
    }

    return results.average()
}


fun createParameterSetFromTrial(trial: RheaTrialParams): ParameterSet {
    val options = ParameterOptions()
    val numberElites = (trial.populationSize * trial.elitePercentage).toInt().coerceAtLeast(1)
    return ParameterSet(
            sequenceLength = trial.sequenceLength,
            populationSize = trial.populationSize,
            numberElites = numberElites,
            mutation = Mutation.Uniform(trial.mutationIndex),
            parentSelectionStrategy = ParentSelectionStrategy.Tournament(trial.parentSelectionTournamentSize),
            crossover = options.crossovers[trial.crossoverIndex],
            evaluationOpponentAgent = options.evaluationOpponentAgents[0],
            initializationMethod = options.initializationMethods[trial.initializationIndex],
            fitnessFunction = options.fitnessFunctions[trial.fitnessFunctionIndex],
            useVariableShipCount = true
    )
}

data class ParameterSet(val sequenceLength: Int,
                        val populationSize: Int,
                        val numberElites: Int,
                        val mutation: Mutation,
                        val parentSelectionStrategy: ParentSelectionStrategy,
                        val crossover: Crossover,
                        var evaluationOpponentAgent: PlanetWarsAgent,
                        var initializationMethod: InitializationMethod,
                        var fitnessFunction: FitnessFunction,
                        var useVariableShipCount: Boolean )
data class ParameterOptions(
        val mutations: List<Mutation> = listOf(Mutation.Uniform(0.1), Mutation.n_bit(20)),
        val crossovers: List<Crossover> = listOf(Crossover.Uniform, Crossover.N_Point(1), Crossover.N_Point(2)),
        val evaluationOpponentAgents: List<PlanetWarsAgent> = listOf(DoNothingAgent()),
        val initializationMethods: List<InitializationMethod> = listOf(InitializationMethod.None, InitializationMethod.ISLA()),
        val fitnessFunctions: List<FitnessFunction> = listOf(
                FitnessFunction.Ratio,
                FitnessFunction.Ships,
                FitnessFunction.Aggressive(),
                FitnessFunction.Hybrid(),
                FitnessFunction.Balanced()
        )
)
