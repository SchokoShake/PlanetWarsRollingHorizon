package games.planetwars.runners

import evodef.EvolutionLogger
import evodef.SearchSpace
import evodef.SolutionEvaluator
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.GreedyHeuristicAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.agents.rhea.Crossover
import games.planetwars.agents.rhea.FitnessFunction
import games.planetwars.agents.rhea.InitializationMethod
import games.planetwars.agents.rhea.Mutation
import games.planetwars.agents.rhea.ParentSelectionStrategy
import games.planetwars.agents.rhea.RheaAgent
import games.planetwars.core.GameParams
import games.planetwars.core.Player
import kotlinx.coroutines.runBlocking
import ntbea.NTupleBanditEA
import ntbea.NTupleSystem
import ntbea.NTupleSystemReport


fun main() {
    println("Starting RheaAgent Hyperparameter Optimization...")

    // 1. Define the search space implementation
    val searchSpace = ParameterSearchSpace()

    // 2. Create our custom evaluator, which holds the search space
    val evaluator = RheaParameterEvaluator(
            searchSpace = searchSpace,
    )

    // 3. Create the NTupleBanditEA instance and configure it
    val ntbea = NTupleBanditEA().setKExplore(1.0)

    // 4. Create the N-Tuple model and set it on the optimizer
    val model = NTupleSystem()
    ntbea.setModel(model)

    // 5. Run the optimization for a set number of evaluations
    val evaluations = 10000
    val bestSolution = ntbea.runTrial(evaluator, evaluations)

    // 6. Print the best result
    val bestParams = RheaParameterEvaluator.decodeSolution(bestSolution)
    // To get the score, we can look at the model's stats for that solution
    val bestScore = ntbea.getModel().getMeanEstimate(bestSolution)

    NTupleSystemReport().setModel(model).printDetailedReport()
    NTupleSystemReport().setModel(model).printSummaryReport()

    println("\n----- Optimization Finished -----")
    println("Total Evals: ${evaluator.nEvals()}")
    println("Best Score (Win Rate): $bestScore")
    println("Best Parameters Found: \n$bestParams")
    println("-----------------------------")
}

// Data class to hold a decoded parameter set
data class RheaParameterSet(
        val sequenceLength: Int, // 100,200,300
        val populationSize: Int, // 10,20,30,40
        val numberElites: Int, // 0.1,0.4,0.6
        val mutation: Mutation, // Uniform(0.2),Uniform(0.5),Uniform(0.8), Softmax, nBit1,nBit2
        val parentSelectionStrategy: ParentSelectionStrategy, //Roulette, Tournament(0.3), Rank
        val crossover: Crossover, // Uniform, None, nPoint
        var evaluationOpponentAgent: PlanetWarsAgent, // DoNothing, Random, BetterRandom
        var initializationMethod: InitializationMethod, // None, 1SLA
        var fitnessFunction: FitnessFunction, // Growth, Ratio, Ships
        var useVariableShipCount: Boolean, // True,False
)

data class RheaParameterOptions(
        val sequenceLength: List<Int> = listOf(200,250, 300),
        val populationSize: List<Int> = listOf(80),
        val elitePercentages: List<Double> = listOf(0.1,0.2,0.3),
        val mutations: List<Mutation> = listOf(
                Mutation.Uniform(0.1),
                Mutation.Uniform(0.2),
                Mutation.Uniform(0.3),
                Mutation.n_bit(20),
                Mutation.n_bit(50),
        ),
        val parentSelectionStrategies: List<ParentSelectionStrategy> = listOf(
                ParentSelectionStrategy.Tournament(0.1),
                ParentSelectionStrategy.Tournament(0.2),
                ParentSelectionStrategy.Tournament(0.3),
        ),
        val crossovers: List<Crossover> = listOf(
                Crossover.Uniform,
                Crossover.N_Point(1),
                Crossover.N_Point(2)
        ),
        val evaluationOpponentAgents: List<PlanetWarsAgent> = listOf(
                DoNothingAgent(),
        ),
        val initializationMethods: List<InitializationMethod> = listOf(
                InitializationMethod.None,
                InitializationMethod.ISLA
        ),
        val fitnessFunctions: List<FitnessFunction> = listOf(
                FitnessFunction.Ratio,
                FitnessFunction.Ships,
                FitnessFunction.Aggressive,
                FitnessFunction.Hybrid,
                FitnessFunction.Balanced
        ),
        val useVariableShipCounts: List<Boolean> = listOf(true)
)



// An implementation of the evodef.SearchSpace interface for our parameters
class ParameterSearchSpace : SearchSpace {
    override fun nDims(): Int = RheaParameterEvaluator.dimensions.size
    override fun nValues(i: Int): Int = RheaParameterEvaluator.dimensions[i]
}

// The evaluator now correctly implements the full SolutionEvaluator interface
class RheaParameterEvaluator(
        val searchSpace: SearchSpace,
        val baselineOpponent: PlanetWarsPlayer = RheaAgent(
                populationSize = 60,
                numberElites = 6,
                mutation = Mutation.Uniform(0.5),
                parentSelectionStrategy = ParentSelectionStrategy.Tournament(0.2),
                crossover = Crossover.N_Point(2),
                sequenceLength = 200,
                evaluationOpponentAgent = DoNothingAgent(),
                initializationMethod = InitializationMethod.ISLA,
                fitnessFunction = FitnessFunction.Ships,
                useVariableShipCount = true,
        ),
) : SolutionEvaluator {

    private var evaluations: Int = 0
    val logger = EvolutionLogger()

    override fun evaluate(solution: IntArray): Double {
        evaluations++ // Crucial: Increment the evaluation counter

        val params = decodeSolution(solution)
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
                useVariableShipCount = params.useVariableShipCount,
        )
            val gameParams = GameParams(numPlanets = 20)
            val gameRunner = GameRunner(agent1 = rheaAgent, agent2 = baselineOpponent, gameParams = gameParams)
            val results = gameRunner.runGames(1)


        val p1Wins = results[Player.Player1] ?: 0
        val p2Wins = results[Player.Player2] ?: 0

        // The evaluation function is +1 for a win, -1 for a loss, and 0 for a draw.
        var fitness = when {
            p1Wins > p2Wins -> 1.0
            p2Wins > p1Wins -> -1.0
            else -> 0.0
        }


        println("Eval #$evaluations -> Fitness: $fitness -> Agent: $rheaAgent")
        return fitness
    }

    // --- Required methods from the SolutionEvaluator interface ---
    override fun searchSpace(): SearchSpace = this.searchSpace
    override fun nEvals(): Int = this.evaluations
    override fun logger(): EvolutionLogger = this.logger
    override fun reset() {
        evaluations = 0
        logger.reset()
    }
    override fun optimalFound(): Boolean {
        return false
    }

    override fun optimalIfKnown(): Double? {
        return null
    }


    companion object {
        // An instance of all possible options to choose from.
        val options = RheaParameterOptions()

        /**
         * The dimensions of the search space. Each value represents the number of choices
         * for a parameter. This is essential for an algorithm that generates the 'solution' array,
         * as it defines the valid range for each integer (e.g., solution[0] must be in 0..2).
         */
        val dimensions = intArrayOf(
                options.sequenceLength.size,
                options.populationSize.size,
                options.elitePercentages.size,
                options.mutations.size,
                options.parentSelectionStrategies.size,
                options.crossovers.size,
                options.evaluationOpponentAgents.size,
                options.initializationMethods.size,
                options.fitnessFunctions.size,
                options.useVariableShipCounts.size
        )

        /**
         * Decodes an integer array (a "chromosome") into a full RheaAgentConfiguration
         * by using the integers as indices to select parameters from the options lists.
         *
         * @param solution An array of integers representing the chosen parameters.
         * @return A RheaAgentConfiguration object ready to be used to create a RheaAgent.
         */
        fun decodeSolution(solution: IntArray): RheaParameterSet {
            // --- Basic Parameter Selection using Indices ---
            val sequenceLength = options.sequenceLength[solution[0]]
            val populationSize = options.populationSize[solution[1]]
            val elitePercentage = options.elitePercentages[solution[2]]
            val mutation = options.mutations[solution[3]]
            val parentSelection = options.parentSelectionStrategies[solution[4]]
            val crossover = options.crossovers[solution[5]]
            val opponentAgent = options.evaluationOpponentAgents[solution[6]]
            val initialization = options.initializationMethods[solution[7]]
            val fitness = options.fitnessFunctions[solution[8]]
            val useVariableShips = options.useVariableShipCounts[solution[9]]

            // --- Derived Parameter Calculation ---
            // numberElites is still calculated based on the chosen populationSize and elitePercentage.
            val numberElites = (populationSize * elitePercentage).toInt().coerceAtLeast(1)

            // --- Construct and Return the Final Configuration ---
            return RheaParameterSet(
                    sequenceLength = sequenceLength,
                    populationSize = populationSize,
                    numberElites = numberElites,
                    mutation = mutation,
                    parentSelectionStrategy = parentSelection,
                    crossover = crossover,
                    evaluationOpponentAgent = opponentAgent,
                    initializationMethod = initialization,
                    fitnessFunction = fitness,
                    useVariableShipCount = useVariableShips
            )
        }
    }
}