package games.planetwars.agents.rhea

import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.evo.GameStateWrapper
import games.planetwars.agents.evo.SimpleEvoAgent
import games.planetwars.core.*
import kotlin.random.Random

data class GameStateWrapper(
        val gameState: GameState,
        val params: GameParams,
        val player: Player,
        val opponentModel: PlanetWarsAgent = DoNothingAgent(),
) {
    var forwardModel = ForwardModel(gameState, params)

    companion object {
        val shiftBy = 2
    }

    fun getAction(gameState: GameState, from: Float, to: Float): Action {
        // filter the planets that are owned by the player AND have a transporter available
        val myPlanets = gameState.planets.filter { it.owner == player && it.transporter == null }
        // filter the planets that are owned by the player AND have a transporter available
        if (myPlanets.isEmpty()) {
            return Action.doNothing()
        }
        // now find a random target planet
        val otherPlanets = gameState.planets.filter { it.owner == player.opponent() || it.owner == Player.Neutral }
        if (otherPlanets.isEmpty()) {
            return Action.doNothing()
        }
        val source = myPlanets[(from * myPlanets.size).toInt()]
        val target = otherPlanets[(to * otherPlanets.size).toInt()]
        return Action(player, source.id, target.id, source.nShips / 2)
    }

    fun runForwardModel(seq: FloatArray): Double {
        var ix = 0;
        forwardModel = ForwardModel(gameState.deepCopy(), params)
        while (ix < seq.size && !forwardModel.isTerminal()) {
            val from = seq[ix]
            val to = seq[ix + 1]
            val myAction = getAction(gameState, from, to)
            val opponentAction = opponentModel.getAction(gameState)
            val actions = mapOf(player to myAction, player.opponent() to opponentAction)
            forwardModel.step(actions)
            ix += shiftBy
        }
        return scoreDifference()
    }

    fun scoreDifference(): Double {
        // allow standalone use of this as well
        return forwardModel.getShips(player) - forwardModel.getShips(player.opponent())
    }
}

data class VanillaRheaAgent(
        var sequenceLength: Int = 200,
        var populationSize: Int = 20,
        var mutationProbability: Double = 0.5,
        var evaluationOpponentAgent: PlanetWarsAgent = DoNothingAgent(),
        ): PlanetWarsPlayer() {
    data class ScoredSolution(val score: Double, val solution: FloatArray)

    private var predecessor: ScoredSolution? = null

    internal var random = Random

    override fun getAction(gameState: GameState): Action {

        // shift predecessors so they reflect current turn
        // if no predecessor exists create one
        if(predecessor==null){
        val solution = randomSequence(sequenceLength)
            predecessor = ScoredSolution(evaluateSequence(gameState, solution), solution)
    } else {
        // first shift, then fill missing values with random ones
        val nextSeq = fillShiftedSequenceWithRandomValues(shiftLeft(predecessor!!.solution, GameStateWrapper.shiftBy),GameStateWrapper.shiftBy,)
            predecessor = ScoredSolution(evaluateSequence(gameState, nextSeq), nextSeq)
    }

        // mutate Predecessors until populationSize is reached
        for (i in 0 until populationSize) {
            // choose which predecessor to mutate, currently we have only one
            // mutate it
            val mutatedSequence = mutate(predecessor!!.solution, mutationProbability)
            // calculate its score
            val mutatedScore = evaluateSequence(gameState, mutatedSequence)
            if (mutatedScore >= predecessor!!.score) {
                //set new predecessor for future turns if its score is higher than the previous ones
                predecessor = ScoredSolution(mutatedScore, mutatedSequence)
            }
        }
        // return best action
        val wrapper = GameStateWrapper(gameState, params, player)
        val action = wrapper.getAction(gameState, predecessor!!.solution[0], predecessor!!.solution[1])
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
        return "RheaAgent-$sequenceLength-$populationSize-$mutationProbability"
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
        val wrapper = GameStateWrapper(state.deepCopy(), params, player, evaluationOpponentAgent)
        wrapper.runForwardModel(sequence)
        return wrapper.scoreDifference()
    }
}