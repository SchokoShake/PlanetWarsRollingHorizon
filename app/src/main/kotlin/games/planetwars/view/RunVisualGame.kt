package games.planetwars.view

import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.agents.evo.SimpleEvoAgent
import games.planetwars.agents.rhea.VanillaRheaAgent
import games.planetwars.core.GameParams
import games.planetwars.runners.GameRunner
import games.planetwars.core.GameStateFactory
import games.planetwars.core.Player
import xkg.jvm.AppLauncher

fun main() {
    val gameParams = GameParams(numPlanets = 20, maxTicks = 1000, width = 1500)
    val gameState = GameStateFactory(gameParams).createGame()
//    val agent2 = BetterRandomAgent()
//    val agent1 = PureRandomAgent()
    val agent2 = SimpleEvoAgent()
    val evaluateOpponent=CarefulRandomAgent()
    evaluateOpponent.prepareToPlayAs(Player.Player2, GameParams())
    val agent1 = VanillaRheaAgent(evaluationOpponentAgent = evaluateOpponent, sequenceLength = 200)

//    val agent1 = games.planetwars.agents.DoNothingAgent()
//    val agent1 = games.planetwars.agents.BetterRandomAgent()
    val gameRunner = GameRunner(agent1, agent2, gameParams)

    val title = "${agent1.getAgentType()} : Planet Wars : ${agent2.getAgentType()}"
    AppLauncher(
        preferredWidth = gameParams.width,
        preferredHeight = gameParams.height,
        app = GameView(params = gameParams,
                gameState = gameState,
                gameRunner = gameRunner,
                showInfoFor = setOf(
                        Player.Player1,
                        Player.Player2,
                        Player.Neutral,
                        )),
        title = title,
        frameRate = 50.0,
    ).launch()
}
