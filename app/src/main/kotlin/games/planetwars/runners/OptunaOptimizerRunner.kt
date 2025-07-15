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
 * **[UPDATED]** The JSON structure expected from the Optuna client.
 * This now matches the full, hierarchical parameter set.
 */
@Serializable
data class RheaTrialParams(
        // Core Agent Parameters
        val sequenceLength: Int,
        val populationSize: Int,
        val numberElites: Int,
        val useVariableShipCount: Boolean,

        // Parent Selection Strategy (Conditional)
        val parentSelectionStrategy: String,
        val tournament_t: Double? = null,

        // Crossover Method (Conditional)
        val crossover_method: String,
        val crossover_n_point_n: Int? = null,

        // Mutation Method (Conditional)
        val mutation_method: String,
        val mutation_uniform_probability: Double? = null,
        val mutation_n_bit_n: Int? = null,

        // Initialization Method (Conditional)
        val initialization_method: String,
        val isla_percent: Double? = null,

        // Fitness Function (Conditional)
        val fitness_function: String,
        val aggressive_p: Double? = null,
        val aggressive_s: Double? = null,
        val aggressive_t: Double? = null,
        val balanced_g: Double? = null,
        val balanced_s: Double? = null,
        val hybrid_a: Double? = null,
        val hybrid_b: Double? = null,
        val strategic_p: Double? = null,
        val strategic_g: Double? = null,
        val strategic_s: Double? = null,
        val strategic_t: Double? = null
)

// =================================================================================
// ==                        CONCURRENCY CONTROLLER                               ==
// =================================================================================
val parallelism = (Runtime.getRuntime().availableProcessors() / 2).toInt().coerceAtLeast(1)
val limitedParallelismDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(parallelism).asCoroutineDispatcher()
// =================================================================================


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
                println("POST /evaluate handler entered. Attempting to receive payload...")
                try {
                    val trialParams = call.receive<RheaTrialParams>()
                    println("Received trial: popSize=${trialParams.populationSize}, fitness=${trialParams.fitness_function}...")
                    val parameterSet = createParameterSetFromTrial(trialParams)

                    val score = evaluateRheaAgent(parameterSet, numGames = 50)

                    println(" -> Overall Score for trial: ${"%.4f".format(score)}")
                    call.respond(HttpStatusCode.OK, mapOf("score" to score))

                } catch (t: Throwable) {
                    println("!!!!!!!!!!!!!! FATAL ERROR DURING TRIAL !!!!!!!!!!!!!!")
                    t.printStackTrace()
                    println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (t.message
                            ?: "Unknown fatal error")))
                }
            }
        }
    }.start(wait = true)
}

suspend fun evaluateRheaAgent(params: ParameterSet, numGames: Int): Double {
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
    val popSize = 100
    // Opponent list for evaluation remains the same
    val baselineOpponents: List<PlanetWarsPlayer> = listOf(

            RheaAgent(
                    name = "Aggressive",
                    sequenceLength = 200,
                    populationSize = popSize,
                    numberElites = (popSize * 0.08264086470511658).toInt(),
                    mutation = Mutation.Uniform(0.21167624014522854),
                    evaluationOpponentAgent = DoNothingAgent(),
                    parentSelectionStrategy = ParentSelectionStrategy.Tournament(0.41799476491642257),
                    crossover = Crossover.N_Point(1),
                    initializationMethod = InitializationMethod.None,
                    fitnessFunction = FitnessFunction.Aggressive(
                            p = 12.962038495865517,
                            s = 1.5740955651632729,
                            t = 0.9789946264613008
                    ),
                    useVariableShipCount = true,
                    ),

            RheaAgent(
                    name = "Ships",
                    sequenceLength = 106,
                    populationSize = popSize,
                    numberElites = (popSize * 0.11911396819456829).toInt(),
                    mutation = Mutation.Uniform(0.22767074124325212),
                    evaluationOpponentAgent = DoNothingAgent(),
                    parentSelectionStrategy = ParentSelectionStrategy.Tournament(0.22200160795126606),
                    crossover = Crossover.N_Point(1),
                    initializationMethod = InitializationMethod.ISLA(0.8558942212210189),
                    fitnessFunction = FitnessFunction.Ships,
                    useVariableShipCount = true,
                    ),
            )
    println(" -> Starting evaluation against ${baselineOpponents.size} opponents...")
    val results = coroutineScope {
        baselineOpponents.map { opponent ->
            async(limitedParallelismDispatcher) {
                println("    - Playing vs ${opponent.getAgentType()} ($numGames games)...")
                val gameParams = GameParams(numPlanets = 20, maxTicks = 1000)
                val gameRunner = GameRunner(agent1 = rheaAgent.copy(), agent2 = opponent, gameParams = gameParams)
                val matchResults = gameRunner.runGamesConcurrently(numGames, 11)
                val p1Wins = matchResults.getOrDefault(Player.Player1, 0)
                val totalGames = matchResults.values.sum()
                val winRate = if (totalGames > 0) p1Wins.toDouble() / totalGames else 0.0
                println("    - Finished vs ${opponent.getAgentType()}. Win rate: ${"%.3f".format(winRate)}")
                winRate
            }
        }.awaitAll()
    }
    return results.average()
}

/**
 * **[REWRITTEN]** Creates a ParameterSet from the full Optuna trial data.
 * This uses 'when' statements to dynamically construct all parameter objects.
 */
fun createParameterSetFromTrial(trial: RheaTrialParams): ParameterSet {
    val options = ParameterOptions()

    val parentSelectionStrategy = when (trial.parentSelectionStrategy) {
        "Random" -> ParentSelectionStrategy.Random
        "Roulette" -> ParentSelectionStrategy.Roulette
        "Rank" -> ParentSelectionStrategy.Rank
        "Tournament" -> ParentSelectionStrategy.Tournament(trial.tournament_t ?: 0.1)
        else -> throw IllegalArgumentException("Unknown parent selection: ${trial.parentSelectionStrategy}")
    }

    val crossover = when (trial.crossover_method) {
        "None" -> Crossover.None
        "Uniform" -> Crossover.Uniform
        "N_Point" -> Crossover.N_Point(trial.crossover_n_point_n ?: 1)
        else -> throw IllegalArgumentException("Unknown crossover: ${trial.crossover_method}")
    }

    val mutation = when (trial.mutation_method) {
        "Softmax" -> Mutation.Softmax
        "Uniform" -> Mutation.Uniform(trial.mutation_uniform_probability ?: 0.1)
        "n_bit" -> Mutation.n_bit(trial.mutation_n_bit_n ?: 1)
        else -> throw IllegalArgumentException("Unknown mutation: ${trial.mutation_method}")
    }

    val initializationMethod = when (trial.initialization_method) {
        "ISLA" -> InitializationMethod.ISLA(trial.isla_percent ?: 1.0)
        "None" -> InitializationMethod.None
        else -> throw IllegalArgumentException("Unknown initialization method: ${trial.initialization_method}")
    }

    val fitnessFunction = when (trial.fitness_function) {
        "Ratio" -> FitnessFunction.Ratio
        "Ships" -> FitnessFunction.Ships
        "Growth" -> FitnessFunction.Growth
        "Aggressive" -> FitnessFunction.Aggressive(
                p = trial.aggressive_p ?: 100.0,
                s = trial.aggressive_s ?: 1.0,
                t = trial.aggressive_t ?: 1.0
        )

        "Balanced" -> FitnessFunction.Balanced(
                g = trial.balanced_g ?: 200.0,
                s = trial.balanced_s ?: 1.0
        )

        "Hybrid" -> FitnessFunction.Hybrid(
                a = trial.hybrid_a ?: 0.5,
                b = trial.hybrid_b ?: 0.5
        )

        "Strategic" -> FitnessFunction.Strategic(
                p = trial.strategic_p ?: 20.0,
                g = trial.strategic_g ?: 1.0,
                s = trial.strategic_s ?: 1.0,
                t = trial.strategic_t ?: 0.5
        )

        else -> throw IllegalArgumentException("Unknown fitness function: ${trial.fitness_function}")
    }

    return ParameterSet(
            sequenceLength = trial.sequenceLength,
            populationSize = trial.populationSize,
            numberElites = trial.numberElites,
            mutation = mutation,
            parentSelectionStrategy = parentSelectionStrategy,
            crossover = crossover,
            evaluationOpponentAgent = options.evaluationOpponentAgents.first(),
            initializationMethod = initializationMethod,
            fitnessFunction = fitnessFunction,
            useVariableShipCount = trial.useVariableShipCount
    )
}

data class ParameterSet(
        val sequenceLength: Int,
        val populationSize: Int,
        val numberElites: Int,
        val mutation: Mutation,
        val parentSelectionStrategy: ParentSelectionStrategy,
        val crossover: Crossover,
        var evaluationOpponentAgent: PlanetWarsAgent,
        var initializationMethod: InitializationMethod,
        var fitnessFunction: FitnessFunction,
        var useVariableShipCount: Boolean
)

/**
 * **[SIMPLIFIED]** The ParameterOptions class is now much simpler,
 * as most parameters are fully defined by the Python client.
 */
data class ParameterOptions(
        val evaluationOpponentAgents: List<PlanetWarsAgent> = listOf(DoNothingAgent())
)