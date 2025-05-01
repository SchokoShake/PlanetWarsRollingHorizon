package games.planetwars.runners


import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.RemoteAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.agents.random.SlowRandomAgent
import games.planetwars.core.*
import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis


fun main() {
    val gameParams = GameParams(numPlanets = 20)
    val agent1 = PureRandomAgent()
    val agent2 = RemoteAgent("games.planetwars.agents.random.CarefulRandomAgent")
    val gameRunner = GameRunnerCoRoutines(agent1, agent2, gameParams, timeoutMillis = 10)
    val finalModel = gameRunner.runGame()
    println("Game over!")
    println(finalModel.statusString())

    // time to run a bunch of games
    val nGames = 5
    val results = gameRunner.runGames(nGames)
    println(results)
}
