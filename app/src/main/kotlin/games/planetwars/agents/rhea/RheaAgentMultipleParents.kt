package games.planetwars.agents.rhea

import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.random.Random

data class RheaAgentMultipleParents(
        var sequenceLength: Int = 200,
        var populationSize: Int = 20,
        var parentCount: Int = 2,
        var mutationProbability: Double = 0.5,
        var evaluationOpponentAgent: PlanetWarsAgent = DoNothingAgent(),
        ): PlanetWarsPlayer() {

    data class ScoredSolution(val score: Double, val solution: FloatArray)

    private var predecessors: MutableList<ScoredSolution> = mutableListOf()

    internal var random = Random

    override fun getAction(gameState: GameState): Action {

        // shift predecessors so they reflect current turn
        // if no predecessor exists create one
        if(predecessors.isEmpty()) {
            for (i in 0 until parentCount){
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

        // mutate Predecessors until populationSize is reached
        for (i in 0 until populationSize) {
            // choose which predecessor to mutate, currently we have two so just pick at random
            val parent = predecessors[random.nextInt(predecessors.size)]

            // mutate it
            val mutatedSequence = mutate(parent.solution, mutationProbability)

            // calculate its score
            val mutatedScore = evaluateSequence(gameState, mutatedSequence)

            // Replace worst if better
            val worst = predecessors.minByOrNull { it.score }!!
            if (mutatedScore >= worst.score) {
                predecessors.remove(worst)
                predecessors.add(ScoredSolution(mutatedScore, mutatedSequence))
            }
        }

        val best = predecessors.maxByOrNull { it.score }!!
        val wrapper = GameStateWrapper(gameState, params, player)
        val action = wrapper.getAction(gameState, best.solution[0], best.solution[1])
        return action
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
        val shiftedSequence  = FloatArray(sequence.size)
        for (i in 0 until shiftedSequence.size - shiftBy) {
            shiftedSequence[i] = sequence[i + shiftBy]
        }
        return shiftedSequence
    }

    override fun getAgentType(): String {
        return "RheaAgentMultipleParents-$sequenceLength-$populationSize-$parentCount-$mutationProbability"
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
        evaluationOpponentAgent.prepareToPlayAs(player.opponent(),params)
        val wrapper = GameStateWrapper(state.deepCopy(), params, player, evaluationOpponentAgent)
        wrapper.runForwardModel(sequence)
        return wrapper.scoreDifference()
    }
}