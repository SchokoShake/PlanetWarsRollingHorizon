package games.planetwars.view

import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.GreedyHeuristicAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.rhea.Crossover
import games.planetwars.agents.rhea.FitnessFunction
import games.planetwars.agents.rhea.InitializationMethod
import games.planetwars.agents.rhea.Mutation
import games.planetwars.agents.rhea.ParentSelectionStrategy
import games.planetwars.agents.rhea.RheaAgent
import games.planetwars.core.GameParams
import games.planetwars.core.GameStateFactory
import games.planetwars.core.Player
import games.planetwars.runners.GameRunner
import xkg.jvm.AppLauncher
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
fun main() {
    val gameParams = GameParams(numPlanets =12, maxTicks = 1000,width=400,height=300, edgeSeparation = 50.0, radialSeparation = 3.0)
    val gameState = GameStateFactory(gameParams).createGame()

    val agent1 = getFullList()[1]

    val agent2 = getFullList()[2]

    val gameRunner = GameRunner(agent1, agent2, gameParams)

    val title = "${agent1.getAgentType()} : Planet Wars : ${agent2.getAgentType()}"
    AppLauncher(
        preferredWidth = gameParams.width,
        preferredHeight = gameParams.height,
        app = GameView(params = gameParams, gameState = gameState, gameRunner = gameRunner,
                showInfoFor= setOf(Player.Player1, Player.Player2,Player.Neutral,     ),),
                title = title,
        frameRate = 50.0,
    ).launch()
}
