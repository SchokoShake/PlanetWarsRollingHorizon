package games.planetwars.agents.rhea

import games.planetwars.agents.Action
import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.core.*
import kotlin.math.*
import kotlin.random.Random
data class RheaGameStateWrapper(
        val gameState: GameState,
        val params: GameParams,
        val player: Player,
        val opponentModel: PlanetWarsAgent = DoNothingAgent(),
        val useVariableShipCount: Boolean = false
) {
    var forwardModel = ForwardModel(gameState, params)

    companion object {
        fun shiftBy(useVariableShipCount: Boolean): Int {
            return if (useVariableShipCount) 3 else 2
        }

        fun getPlayerSourcePlanets(forwardModel:ForwardModel,player:Player):List<Planet>{
            return forwardModel.state.planets.filter { it.owner == player && it.transporter == null };
        }
    }

    fun getAction(gameState: GameState, from: Float, to: Float, frac: Float = 0.5f): Action {
        // filter the planets that are owned by the player AND have a transporter available
        val myPlanets = getPlayerSourcePlanets(forwardModel,player)

        if (myPlanets.isEmpty()) {
            return Action.doNothing()
        }

        // choose any planet, not only opponent planets as target.
        // else reinforcement is not possible
        var source=myPlanets.first();
        var target=myPlanets.first();
        try{
            source = myPlanets[(from * myPlanets.size).toInt()]
            target = gameState.planets[(to * gameState.planets.size).toInt()]
        }catch (e:Exception){
            return Action.doNothing()
        }

        val shipsToSend: Double = if (useVariableShipCount) {
            (source.nShips * frac)
        } else {
            source.nShips / 2.0
        }

        if (shipsToSend < 1) {
            return Action.doNothing()
        }

        return Action(player, source.id, target.id, shipsToSend)
    }

    fun runForwardModel(seq: FloatArray): Double {
        val shift = shiftBy(useVariableShipCount)
        var ix = 0
        forwardModel = ForwardModel(gameState.deepCopy(), params)

        while (ix < seq.size && !forwardModel.isTerminal()) {
            val from = seq[ix]
            val to = seq[ix + 1]
            val frac = if (useVariableShipCount) seq[ix + 2] else 0.5f
            val myAction = getAction(gameState, from, to, frac)
            val opponentAction = opponentModel.getAction(gameState)
            val actions = mapOf(player to myAction, player.opponent() to opponentAction)
            forwardModel.step(actions)
            ix += shift
        }
        return scoreDifference()
    }

    fun scoreDifference(): Double {
        // allow standalone use of this as well
        return forwardModel.getShips(player) - forwardModel.getShips(player.opponent())
    }
}


sealed class Crossover {

    data object Uniform : Crossover() {
        override fun toString(): String {
            return "U"
        }
    }

    data object None : Crossover() {
        override fun toString(): String {
            return "No"
        }
    }

    class N_Point(val n: Int) :  Crossover() {
        override fun toString(): String {
            return "N($n)"
        }
    }
}

sealed class Mutation {

    class Uniform(val probability:Double) : Mutation() {
        override fun toString(): String {
            return "U($probability)"
        }
    }

    data object Softmax : Mutation() {
        override fun toString(): String {
            return "SM"
        }
    }

    class n_bit(val n: Int) :  Mutation() {
        fun getParameter() = n
        override fun toString(): String {
            return "N($n)"
        }
    }
}

sealed class InitializationMethod {
    data object ISLA : InitializationMethod() {
        override fun toString(): String {
            return "1SLA"
        }
    }
    data object None : InitializationMethod() {
        override fun toString(): String {
            return "X"
        }
    }
}

sealed class FitnessFunction {
    data object Ratio : FitnessFunction() {
        override fun toString(): String = "R"
    }

    data object Ships : FitnessFunction() {
        override fun toString(): String = "S"
    }

    data object Growth : FitnessFunction() {
        override fun toString(): String = "G"
    }

    data object Aggressive : FitnessFunction() {
        override fun toString(): String = "A"
    }

    data object Balanced : FitnessFunction() {
        override fun toString(): String = "B"
    }

    data object Hybrid : FitnessFunction() {
        override fun toString(): String = "H"
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
        var mutation:Mutation =Mutation.Uniform(0.5),
        var evaluationOpponentAgent: PlanetWarsAgent = DoNothingAgent(),
        var parentSelectionStrategy: ParentSelectionStrategy = ParentSelectionStrategy.Random,
        var crossover: Crossover = Crossover.None,
        var initializationMethod: InitializationMethod=InitializationMethod.ISLA,
        var fitnessFunction: FitnessFunction=FitnessFunction.Ratio,
        var useVariableShipCount: Boolean = false
) : PlanetWarsPlayer() {

    init {
        val shift = RheaGameStateWrapper.shiftBy(useVariableShipCount)
        sequenceLength *= shift
    }

    data class ScoredSolution(val score: Double, val solution: FloatArray)

    private var predecessors: MutableList<ScoredSolution> = mutableListOf()

    internal var random = Random

    override fun getAction(gameState: GameState): Action {

        val shift = RheaGameStateWrapper.shiftBy(useVariableShipCount)

        // shift predecessors so they reflect current turn
        // if no predecessor exists create one
        if (predecessors.isEmpty()) {
            predecessors=initializePopulation(gameState)
        } else {
            // first shift, then fill missing values with random ones
            predecessors = predecessors.map {
                val shifted = fillShiftedSequenceWithRandomValues(
                    shiftLeft(it.solution, shift),
                    shift
                )
                ScoredSolution(evaluateSequence(gameState, shifted), shifted)
            }.toMutableList()
        }

        // elitism: keep best individuals from predecessors
        val population = predecessors
            .sortedByDescending { it.score }
            .take(minOf(numberElites, populationSize))
            .toMutableList()

        // mutate predecessors until population size is reached
        for (i in population.size until populationSize) {
            // select parents
            val parent1 = selectParent(predecessors)
            val parent2 = selectParent(predecessors)

            // cross over
            val crossoverSequence = crossover(parent1, parent2)

            // mutation
            val mutatedSequence = mutate(crossoverSequence)

            // calculate its fitness score
            val mutatedScore = evaluateSequence(gameState, mutatedSequence)

            population.add(ScoredSolution(mutatedScore, mutatedSequence))
        }

        // Store new generation for the next turn
        predecessors = population

        // select the best sequence in the population and return its first action
        val best = population.maxByOrNull  { it.score }
                ?: return Action.doNothing()     // or other fallback
        val wrapper = RheaGameStateWrapper(gameState, params, player, useVariableShipCount = useVariableShipCount)
        val frac = if (useVariableShipCount) best.solution[2] else 0.5f
        val action = wrapper.getAction(gameState, best.solution[0], best.solution[1], frac)
        return action
    }

    private fun initializePopulation(gameState: GameState): MutableList<ScoredSolution> {
        var initialPopulation=mutableListOf<ScoredSolution>()
        if(initializationMethod is InitializationMethod.ISLA){
            initialPopulation=isla(gameState)
        }else{
            for (i in 0 until populationSize) {
                val solution = randomSequence(sequenceLength)
                val score = evaluateSequence(gameState, solution)
                initialPopulation.add(ScoredSolution(score, solution))
            }
        }
        return initialPopulation
    }

    private fun isla(gameState: GameState): MutableList<ScoredSolution> {
        val population = mutableListOf<ScoredSolution>()

        // Generate solution
        val solution = generateSolution(gameState)
        val scoredSolution = ScoredSolution(evaluateSequence(gameState, solution), solution)
        population.add(scoredSolution)

        for (i in 1 until populationSize) {
            val mutatedSolution = mutate(scoredSolution.solution)
            val mutatedScore = evaluateSequence(gameState, mutatedSolution)
            population.add(ScoredSolution(mutatedScore, mutatedSolution))
        }

        return population
    }

    private fun generateSolution(gameState: GameState): FloatArray {
        val shift = RheaGameStateWrapper.shiftBy(useVariableShipCount)
        val sequence = FloatArray(sequenceLength)
        var currentModel = ForwardModel(gameState.deepCopy(), params)

        for (i in 0 until sequenceLength / shift) {
            // fill randomly if already at endstate
            if (currentModel.isTerminal()) {
                for (j in i until sequenceLength / shift) {
                    sequence[j * shift] = random.nextFloat()
                    sequence[j * shift + 1] = random.nextFloat()
                    if (useVariableShipCount) {
                        sequence[j * shift + 2] = random.nextFloat().coerceIn(0.01f, 1.0f)
                    }
                }
                break
            }

            var bestFromFloat = 0.0f
            var bestToFloat = 0.0f
            var bestFracFloat = 1.0f
            var bestScore = -Double.MAX_VALUE
            var bestNextModel: ForwardModel? = null

            //only select player planets with ships
            val possibleSources = RheaGameStateWrapper.getPlayerSourcePlanets(currentModel,player)

            if (possibleSources.isEmpty()) {
                for (j in i until sequenceLength / shift) {
                    sequence[j * shift] = random.nextFloat()
                    sequence[j * shift + 1] = random.nextFloat()
                    if (useVariableShipCount) {
                        sequence[j * shift + 2] = random.nextFloat().coerceIn(0.01f, 1.0f)
                    }
                }
                break
            } else {
                val shipFractions = if (useVariableShipCount) listOf(0.25f, 0.5f, 0.75f, 1.0f) else listOf(0.5f)

                for (sourcePlanet in possibleSources) {
                    for (destPlanet in currentModel.state.planets) {
                        if (sourcePlanet.id == destPlanet.id) continue

                        for (frac in shipFractions) {
                            val shipsToSend = (sourcePlanet.nShips * frac)

                            val testAction = if (shipsToSend < 1) {
                                Action.doNothing()
                            } else {
                                Action(player, sourcePlanet.id, destPlanet.id, shipsToSend)
                            }

                            val testModel = ForwardModel(currentModel.state.deepCopy(), params)
                            testModel.step(mapOf(player to testAction))

                            val distance = sourcePlanet.position.distance(destPlanet.position)
                            val travelTime = ceil(distance / params.transporterSpeed).toInt()

                            for (tick in 0 until travelTime) {
                                if (testModel.isTerminal()) break
                                testModel.step(emptyMap())
                            }

                            val currentScore = evaluateState(testModel, player)

                            if (currentScore > bestScore) {
                                bestScore = currentScore
                                val sourceIdx = RheaGameStateWrapper.getPlayerSourcePlanets(currentModel,player).indexOf(sourcePlanet)
                                val destIdx = currentModel.state.planets.indexOf(destPlanet)
                                bestFromFloat = sourceIdx.toFloat() / RheaGameStateWrapper.getPlayerSourcePlanets(currentModel,player).size
                                bestToFloat = destIdx.toFloat() / currentModel.state.planets.size
                                bestFracFloat = frac
                                bestNextModel = testModel
                            }
                        }
                    }
                }
            }

            // Add the best found gene pair to our elite sequence
            sequence[i * shift] = bestFromFloat
            sequence[i * shift + 1] = bestToFloat
            if (useVariableShipCount) {
                sequence[i * shift + 2] = bestFracFloat
            }

            // Update the current model to the state after the best action was taken
            currentModel = bestNextModel ?: currentModel // Fallback to old model if no improvement
        }
        return sequence
    }


    private fun crossover(parent1: ScoredSolution, parent2: ScoredSolution): FloatArray {
        return when (crossover) {
            is Crossover.N_Point -> n_pointCrossover(parent1,parent2)
            is Crossover.Uniform -> uniformCrossover(parent1,parent2)
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

        val nPoints=((crossover as Crossover.N_Point).n*len)

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
            is ParentSelectionStrategy.Random -> predecessors[random.nextInt(predecessors.size)]
            is ParentSelectionStrategy.Tournament -> tournamentSelection(predecessors)
            is ParentSelectionStrategy.Roulette -> rouletteSelection(predecessors)
            is ParentSelectionStrategy.Rank -> rankSelection(predecessors)
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
        val minScore = predecessors.minOfOrNull { it.score }
                ?: error("Tournament selection failed")   // or other fallback
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
        return selected.maxByOrNull { it.score }?: throw IllegalStateException("Tournament selection failed. The list of candidates was empty.")
    }

    private fun mutate(sequence: FloatArray): FloatArray {

        return when(mutation){
            is Mutation.Uniform-> {
                 mutateUniform(sequence, mutation as Mutation.Uniform)
            }
            is Mutation.Softmax->mutateSoftmax(sequence,mutation as Mutation.Softmax)
            is Mutation.n_bit->mutateNbit(sequence,mutation as Mutation.n_bit)
        };


    }

    private fun mutateNbit(sequence: FloatArray, nBit: Mutation.n_bit): FloatArray {
        val mutatedSequence = sequence.copyOf()
        val numToMutate = minOf(nBit.n, sequence.size)
        val indicesToMutate = sequence.indices.shuffled().take(numToMutate)

        for (index in indicesToMutate) {
            var newValue: Float
            do {
                newValue = Random.nextFloat()
            } while (newValue == mutatedSequence[index])

            mutatedSequence[index] = newValue
        }

        return mutatedSequence
    }

    private fun mutateSoftmax(sequence: FloatArray, softmax: Mutation.Softmax): FloatArray {
        val mutatedSequence = sequence.copyOf()
        val inputs = DoubleArray(mutatedSequence.size) { i -> 1.0 }

        val exps = inputs.map { exp(it) }
        val sumExps = exps.sum()

        val probabilities = exps.map { it / sumExps }

        val randomVal = Random.nextDouble()
        var cumulativeProb = 0.0
        var indexToMutate = mutatedSequence.size - 1

        for (i in probabilities.indices) {
            cumulativeProb += probabilities[i]
            if (randomVal < cumulativeProb) {
                indexToMutate = i
                break
            }
        }

        var newValue: Float
        do {
            newValue = Random.nextFloat()
        } while (newValue == mutatedSequence[indexToMutate])

        mutatedSequence[indexToMutate] = newValue

        return mutatedSequence
    }

    private fun mutateUniform(sequence: FloatArray,uniformMutation:Mutation.Uniform): FloatArray {
        val n = sequence.size
        val mutated = FloatArray(n)

        for (i in 0 until n) {
            if (random.nextDouble() < uniformMutation.probability) {
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
        return "RheaAgent-$sequenceLength-$populationSize-$numberElites-$mutation-(${evaluationOpponentAgent.getAgentType()})-$parentSelectionStrategy-$crossover-$fitnessFunction-$initializationMethod-$useVariableShipCount"
    }

    private fun randomSequence(length: Int): FloatArray {
        // random sequence of length n
        val sequence = FloatArray(length)
        for (i in sequence.indices) {
            sequence[i] = random.nextFloat()
        }
        return sequence
    }

    private fun evaluateState(forwardModel: ForwardModel, player: Player): Double {
        return when(fitnessFunction){
            is FitnessFunction.Ratio -> fitnessRatio(forwardModel, player)
            is FitnessFunction.Growth -> fitnessGrowthDiff(forwardModel, player)
            is FitnessFunction.Ships -> forwardModel.getShips(player) - forwardModel.getShips(player.opponent())
            is FitnessFunction.Aggressive -> fitnessAggressive(forwardModel, player)
            is FitnessFunction.Balanced -> fitnessBalanced(forwardModel, player)
            is FitnessFunction.Hybrid -> fitnessHybrid(forwardModel, player)
        }
    }

    private fun fitnessGrowthDiff(forwardModel: ForwardModel, player: Player): Double {
        return forwardModel.state.planets.filter { it.owner==player }.sumOf { it.growthRate }-forwardModel.state.planets.filter { it.owner==player.opponent() }.sumOf { it.growthRate }
    }

    private fun fitnessRatio(forwardModel: ForwardModel, player: Player): Double {
        if (forwardModel.isTerminal()) {
            val winner = forwardModel.getLeader()
            return when (winner) {
                player -> 1.0
                player.opponent() -> 0.0
                else -> 0.5
            }
        }

        val myShips = forwardModel.getShips(player)
        val opponentShips =forwardModel.getShips(player.opponent())
        val totalShips = myShips + opponentShips

        return if (totalShips > 0) {
            myShips / totalShips
        } else {
            0.5
        }
    }

    private fun fitnessAggressive(forwardModel: ForwardModel, player: Player): Double {
        val myPlanets = forwardModel.state.planets.count { it.owner == player }
        val enemyPlanets = forwardModel.state.planets.count { it.owner == player.opponent() }
        val myShips = forwardModel.getShips(player)
        val enemyShips = forwardModel.getShips(player.opponent())
        val gameTick = forwardModel.state.gameTick

        val planetWeight = 100.0
        val shipWeight = 1.0
        val eliminationBonus = 10000.0
        val timePenaltyWeight = 1.0

        val baseScore = (planetWeight * (myPlanets - enemyPlanets)) +
                (shipWeight * (myShips - enemyShips)) -
                (timePenaltyWeight * gameTick)

        return if (enemyPlanets == 0) baseScore + eliminationBonus else baseScore
    }

    private fun fitnessBalanced(forwardModel: ForwardModel, player: Player): Double {
        val myPlanets = forwardModel.state.planets.filter { it.owner == player }
        val enemyPlanets = forwardModel.state.planets.filter { it.owner == player.opponent() }

        val myGrowth = myPlanets.sumOf { it.growthRate }
        val enemyGrowth = enemyPlanets.sumOf { it.growthRate }
        val myShips = forwardModel.getShips(player)
        val enemyShips = forwardModel.getShips(player.opponent())

        val growthWeight = 200.0
        val shipWeight = 1.0

        return (growthWeight * (myGrowth - enemyGrowth)) +
                (shipWeight * (myShips - enemyShips))
    }

    private fun fitnessHybrid(forwardModel: ForwardModel, player: Player): Double {
        val alpha = 0.5 // weight for aggressive
        val beta = 0.5  // weight for balanced
        return alpha * fitnessAggressive(forwardModel, player) + beta * fitnessBalanced(forwardModel, player)
    }

    private fun evaluateSequence(state: GameState, sequence: FloatArray): Double {
        evaluationOpponentAgent.prepareToPlayAs(player = player.opponent(), params = params)
        val wrapper = RheaGameStateWrapper(state.deepCopy(), params, player, evaluationOpponentAgent, useVariableShipCount = useVariableShipCount)
        wrapper.runForwardModel(sequence)

        return evaluateState(wrapper.forwardModel,player)
    }
}