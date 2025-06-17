package games.planetwars.view

import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.agents.rhea.RheaAgent
import games.planetwars.agents.rhea.Crossover
import games.planetwars.agents.rhea.FitnessFunction
import games.planetwars.agents.rhea.InitializationMethod
import games.planetwars.agents.rhea.Mutation
import games.planetwars.agents.rhea.ParentSelectionStrategy
import games.planetwars.core.GameParams
import games.planetwars.runners.GameRunner
import games.planetwars.core.GameStateFactory
import games.planetwars.core.Player
import xkg.jvm.AppLauncher

fun main() {
    val gameParams = GameParams(numPlanets = 20, maxTicks = 1000)
    val gameState = GameStateFactory(gameParams).createGame()

    val agent1 = RheaAgent(
            sequenceLength = 400,
            populationSize = 20,
            numberElites = 8,
            mutation = Mutation.Uniform(0.2),
            evaluationOpponentAgent = RheaAgent(
                    sequenceLength = 100,
                    populationSize = 2,
                    numberElites = 1,
                    mutation = Mutation.Uniform(0.2),
                    evaluationOpponentAgent = DoNothingAgent(),
                    initializationMethod =  InitializationMethod.None,
                    fitnessFunction = FitnessFunction.Ships,
                    useVariableShipCount =  true,
            ),
            parentSelectionStrategy = ParentSelectionStrategy.Tournament(0.2),
            crossover = Crossover.Uniform,
            initializationMethod =  InitializationMethod.ISLA(),
            fitnessFunction = FitnessFunction.Ships,
            useVariableShipCount =  true,
    )
    val agent2 = CarefulRandomAgent()

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
