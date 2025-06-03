package games.planetwars.agents.rhea

import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.evo.GameStateWrapper
import games.planetwars.core.*
import kotlin.random.Random

sealed class Crossover {
    abstract fun getParameter(): Double
    data object Uniform : Crossover() {
        override fun toString(): String {
            return "U"
        }
        override fun getParameter() = 1.0
    }
    data object None : Crossover() {
        override fun toString(): String {
            return "No"
        }
        override fun getParameter() = 1.0
    }

    class N_Point(val t: Double) :  Crossover() {
        override fun getParameter() = t
        override fun toString(): String {
            return "N($t)"
        }
    }

}

sealed class ParentSelectionStrategy {
    abstract fun getParameter(): Double

    data object Random : ParentSelectionStrategy() {
        override fun getParameter() = 1.0
    }

    data object Roulette : ParentSelectionStrategy() {
        override fun getParameter() = 1.0
    }

    class Tournament(val t: Double) : ParentSelectionStrategy() {
        override fun getParameter() = t
        override fun toString(): String {
            return "Tournament($t)"
        }
    }

    data object Rank : ParentSelectionStrategy() {
        override fun getParameter() = 1.0
    }
}


data class RheaAgent(
        var sequenceLength: Int = 200,
        var populationSize: Int = 20,
        var numberElites: Int = 5,
        var mutationProbability: Double = 0.5,
        var evaluationOpponentAgent: PlanetWarsAgent = DoNothingAgent(),
        var parentSelectionStrategy: ParentSelectionStrategy = ParentSelectionStrategy.Random,
        var crossover: Crossover = Crossover.None
) : PlanetWarsPlayer() {
    data class ScoredSolution(val score: Double, val solution: FloatArray)

    private var predecessors: MutableList<ScoredSolution> = mutableListOf()

    internal var random = Random

    override fun getAction(gameState: GameState): Action {

        // shift predecessors so they reflect current turn
        // if no predecessor exists create one
        if (predecessors.isEmpty()) {
            for (i in 0 until populationSize) {
                val solution = randomSequence(sequenceLength)
                val score = evaluateSequence(gameState, solution)
                predecessors.add(ScoredSolution(score, solution))
            }
        } else {
            // first shift, then fill missing values with random ones
            predecessors = predecessors.map {
                val shifted = fillShiftedSequenceWithRandomValues(
                        shiftLeft(it.solution, GameStateWrapper.shiftBy),
                        GameStateWrapper.shiftBy
                )
                ScoredSolution(evaluateSequence(gameState, shifted), shifted)
            }.toMutableList()
        }

        // elitism: keep best individuals from predecessors
        val population = predecessors
            .sortedByDescending { it.score }
            .take(minOf(numberElites, populationSize))
            .toMutableList()

        // mutate Predecessors until populationSize is reached
        for (i in population.size until populationSize) {
            // select first parent
            val parent1 = selectParent(predecessors)

            // remove parent1 from the list to avoid selecting it again
            val filtered = predecessors.filter { it !== parent1 }

            // if only one individual exists, fallback to using parent1 again
            val parent2 = if (filtered.isNotEmpty()) {
                selectParent(filtered.toMutableList())
            } else {
                parent1
            }

            // cross over
            val crossoverSequence = crossover(parent1,parent2)

            // mutation
            val mutatedSequence = mutate(crossoverSequence, mutationProbability)

            // calculate its fitness score
            val mutatedScore = evaluateSequence(gameState, mutatedSequence)

            population.add(ScoredSolution(mutatedScore, mutatedSequence))
        }

        // Store new generation for the next turn
        predecessors = population

        // select the best sequence in the population and get its first action
        val best = population.maxByOrNull { it.score }!!
        val wrapper = GameStateWrapper(gameState, params, player)
        val action = wrapper.getAction(gameState, best.solution[0], best.solution[1])
        return action
    }

    private fun crossover(parent1: ScoredSolution, parent2: ScoredSolution): FloatArray {
        return when (crossover) {
            is Crossover.N_Point -> n_pointCrossover(parent1,parent2)
            Crossover.Uniform -> uniformCrossover(parent1,parent2)
            else -> parent1.solution
        }
    }

    private fun uniformCrossover(parent1: ScoredSolution, parent2: ScoredSolution): FloatArray {
        val g1 = parent1.solution
        val g2 = parent2.solution
        require(g1.size == g2.size) { "Genome lengths differ" }

        return FloatArray(g1.size) { i ->
            if (random.nextBoolean()) g1[i] else g2[i]
        }
    }

    private fun n_pointCrossover(parent1: ScoredSolution, parent2: ScoredSolution): FloatArray {
        val g1 = parent1.solution
        val g2 = parent2.solution
        val len = g1.size

        val nPoints=(crossover.getParameter()*len).toInt()

        // unique, sorted cut positions in (0, len)
        val cuts = (1 until len).shuffled(random).take(nPoints).sorted()
        val offspring = FloatArray(len)

        var srcFromFirst = true
        var prev = 0
        for (cut in cuts + len) {
            val src = if (srcFromFirst) g1 else g2
            System.arraycopy(src, prev, offspring, prev, cut - prev)
            srcFromFirst = !srcFromFirst
            prev = cut
        }
        return offspring
    }

    private fun selectParent(predecessors: MutableList<ScoredSolution>): ScoredSolution {
        return when (parentSelectionStrategy) {
            ParentSelectionStrategy.Random -> predecessors[random.nextInt(predecessors.size)]
            is ParentSelectionStrategy.Tournament -> tournamentSelection(predecessors)
            ParentSelectionStrategy.Roulette -> rouletteSelection(predecessors)
            ParentSelectionStrategy.Rank -> rankSelection(predecessors)
        }
    }

    private fun rankSelection(predecessors: MutableList<ScoredSolution>): ScoredSolution {
        // calculate total rank
        val sortedPredecessors = predecessors.sortedByDescending { it.score }
        val totalRank=(1..predecessors.size).sum()
        // choose random value between 0 and total fitness
        val luckyRank=(random.nextDouble()*totalRank).toInt()
        // select parent at chosen cumulative fitness score
        var runningSum = 0
        for ((index, solution) in sortedPredecessors.withIndex()) {
            val rank = index + 1 // Ranks start from 1
            runningSum += rank
            if (runningSum >= luckyRank) {
                return solution // Selected parent
            }
        }

        // In case of an edge case (fallback, should never hit this)
        return sortedPredecessors.last()
    }

    private fun rouletteSelection(predecessors: MutableList<ScoredSolution>): ScoredSolution {
        // 1) Compute minimum raw score to ensure all shifted fitnesses are non-negative
        val minScore = predecessors.minOf { it.score }
        val offset = if (minScore < 0.0) -minScore else 0.0

        // 2) Build a list of shifted (non-negative) fitness values
        val shiftedFitnesses = predecessors.map { it.score + offset }

        // 3) Compute totalFitness; if zero or negligible, fall back to uniform random
        val totalFitness = shiftedFitnesses.sum()
        if (totalFitness <= 0.0) {
            return predecessors[random.nextInt(predecessors.size)]
        }

        // 4) Pick a random threshold in [0, totalFitness)
        val luckyThreshold = random.nextDouble() * totalFitness

        // 5) Traverse cumulatively until the threshold is reached
        var runningSum = 0.0
        for ((index, solution) in predecessors.withIndex()) {
            runningSum += shiftedFitnesses[index]
            if (runningSum >= luckyThreshold) {
                return solution
            }
        }

        // 6) Fallback in case of numerical issues
        return predecessors.last()
    }

    private fun tournamentSelection(predecessors: MutableList<ScoredSolution>): ScoredSolution {
        //pick random t percent of the population
        val selected = predecessors.shuffled().take((parentSelectionStrategy.getParameter() * predecessors.size).toInt())
        //pick best
        return selected.maxBy { it.score }
    }

    private fun mutate(sequence: FloatArray, mutProb: Double): FloatArray {
        val n = sequence.size
        val mutated = FloatArray(n)

        for (i in 0 until n) {
            if (random.nextDouble() < mutProb) {
                mutated[i] = random.nextFloat()
            } else {
                mutated[i] = sequence[i]
            }
        }
        return mutated
    }

    private fun fillShiftedSequenceWithRandomValues(sequence: FloatArray, count: Int): FloatArray {
        val start = sequence.size - count
        for (i in start until sequence.size) {
            sequence[i] = Random.nextFloat()
        }
        return sequence
    }

    private fun shiftLeft(sequence: FloatArray, shiftBy: Int): FloatArray {
        val shiftedSequence = FloatArray(sequence.size)
        for (i in 0 until shiftedSequence.size - shiftBy) {
            shiftedSequence[i] = sequence[i + shiftBy]
        }
        return shiftedSequence
    }

    override fun getAgentType(): String {
        return "RheaAgent-$sequenceLength-$populationSize-$numberElites-$mutationProbability-(${evaluationOpponentAgent.getAgentType()})-$parentSelectionStrategy-$crossover"
    }

    private fun randomSequence(length: Int): FloatArray {
        // random sequence of length n
        val sequence = FloatArray(length)
        for (i in sequence.indices) {
            sequence[i] = random.nextFloat()
        }
        return sequence
    }

    private fun evaluateSequence(state: GameState, sequence: FloatArray): Double {
        evaluationOpponentAgent.prepareToPlayAs(player = player.opponent(), params = params)
        val wrapper = GameStateWrapper(state.deepCopy(), params, player, evaluationOpponentAgent)
        wrapper.runForwardModel(sequence)
        return wrapper.scoreDifference()
    }
}