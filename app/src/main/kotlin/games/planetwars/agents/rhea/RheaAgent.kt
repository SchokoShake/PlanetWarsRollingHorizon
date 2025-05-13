package games.planetwars.agents.rhea

import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.evo.GameStateWrapper
import games.planetwars.core.*
import kotlin.random.Random
import kotlin.random.nextInt


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
        var mutationProbability: Double = 0.5,
        var evaluationOpponentAgent: PlanetWarsAgent = DoNothingAgent(),
        var parentSelectionStrategy: ParentSelectionStrategy = ParentSelectionStrategy.Random
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
        var population = mutableListOf<ScoredSolution>()
        // mutate Predecessors until populationSize is reached
        for (i in 0 until populationSize) {
            // choose which predecessor to mutate, currently we have two so just pick at random
            val parent = selectParent(predecessors)

            // mutate it
            val mutatedSequence = mutate(parent.solution, mutationProbability)

            // calculate its score
            val mutatedScore = evaluateSequence(gameState, mutatedSequence)

            population.add(ScoredSolution(mutatedScore, mutatedSequence))
        }
        // sort population
        population.sortBy { it.score }
        val best = predecessors.maxByOrNull { it.score }!!
        val wrapper = GameStateWrapper(gameState, params, player)
        val action = wrapper.getAction(gameState, best.solution[0], best.solution[1])
        return action
    }

    private fun selectParent(predecessors: MutableList<ScoredSolution>): ScoredSolution {
        return when (parentSelectionStrategy) {
            ParentSelectionStrategy.Random -> predecessors[random.nextInt(predecessors.size)]
            is ParentSelectionStrategy.Tournament -> tournamentSelection(predecessors)
            ParentSelectionStrategy.Roulette -> rouletteSelection(predecessors)
            ParentSelectionStrategy.Rank -> rankSelection(predecessors)
        }
    }

    private fun rankSelection(predecessors: MutableList<RheaAgent.ScoredSolution>): RheaAgent.ScoredSolution {
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
            // calculate total fitness
            val totalFitness=predecessors.sumOf { it.score }
            // choose random value between 0 and total fitness
            val luckyScore=(random.nextDouble()*totalFitness).toInt()
            // select parent at chosen cumulative fitness score
            var runningSum=0.0
            for (solution in predecessors) {
                runningSum += solution.score
                if (runningSum >= luckyScore) {
                    return solution // Selected parent
                }
            }
            return predecessors.last()
        }

        private fun tournamentSelection(predecessors: MutableList<ScoredSolution>): ScoredSolution {
        //pick random t percent of the population
        val selected = predecessors.shuffled().take((parentSelectionStrategy.getParameter() * predecessors.size).toInt())
        //pick best
        return selected.maxBy { it.score }
    }

    private fun mutate(parents: FloatArray, mutProb: Double): FloatArray {
        val n = parents.size
        val mutated = FloatArray(n)
        // possibly crossover parents
        // mutate resulting sequences
        for (i in 0 until n) {
            if (random.nextDouble() < mutProb) {
                mutated[i] = random.nextFloat()
            } else {
                mutated[i] = parents[i]
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
        return "RheaAgent-$sequenceLength-$populationSize-$mutationProbability-(${evaluationOpponentAgent.getAgentType()})-$parentSelectionStrategy"
    }

    // random sequence of length n
    private fun randomSequence(length: Int): FloatArray {
        val sequence = FloatArray(length)
        for (i in sequence.indices) {
            sequence[i] = random.nextFloat()
        }
        return sequence
    }

    private fun evaluateSequence(state: GameState, sequence: FloatArray): Double {
        evaluationOpponentAgent.prepareToPlayAs(player = player.opponent(), params = params);
        val wrapper = GameStateWrapper(state.deepCopy(), params, player, evaluationOpponentAgent)
        wrapper.runForwardModel(sequence)
        return wrapper.scoreDifference()
    }
}