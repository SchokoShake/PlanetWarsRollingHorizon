package games.planetwars.view

import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.agents.rhea.RheaAgent
import games.planetwars.agents.rhea.Crossover
import games.planetwars.agents.rhea.Mutation
import games.planetwars.agents.rhea.ParentSelectionStrategy
import games.planetwars.core.GameParams
import games.planetwars.runners.GameRunner
import games.planetwars.core.GameStateFactory
import xkg.jvm.AppLauncher

fun main() {
    val gameParams = GameParams(numPlanets = 20, maxTicks = 1000)
    val gameState = GameStateFactory(gameParams).createGame()

    val agent1 = CarefulRandomAgent()
    val agent2 = RheaAgent(
        sequenceLength = 200,
        populationSize = 30,
        numberElites = 3,
            mutation = Mutation.Uniform(0.8),
        evaluationOpponentAgent = DoNothingAgent(),
        parentSelectionStrategy = ParentSelectionStrategy.Roulette,
        crossover = Crossover.Uniform
    )

    val gameRunner = GameRunner(agent1, agent2, gameParams)

    val title = "${agent1.getAgentType()} : Planet Wars : ${agent2.getAgentType()}"
    AppLauncher(
        preferredWidth = gameParams.width,
        preferredHeight = gameParams.height,
        app = GameView(params = gameParams, gameState = gameState, gameRunner = gameRunner),
        title = title,
        frameRate = 50.0,
    ).launch()
}
