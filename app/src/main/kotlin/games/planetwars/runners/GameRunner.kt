package games.planetwars.runners

import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class GameRunner(
    val agent1: PlanetWarsAgent,
    val agent2: PlanetWarsAgent,
    val gameParams: GameParams,
) {
    var gameState: GameState = GameStateFactory(gameParams).createGame()
    var forwardModel: ForwardModel = ForwardModel(gameState.deepCopy(), gameParams)
    // call newGame() to reset the game state and agents in the constructor
    init {
        newGame()
    }

    var agent1TotalTime = 0L
    var agent2TotalTime = 0L
    var totalMoves = 0

    init {
        newGame()
    }

    fun runGame() : ForwardModel {
        newGame()
        while (!forwardModel.isTerminal()) {


            val p1start = System.currentTimeMillis()
            val p1action = Player.Player1 to agent1.getAction(forwardModel.state.deepCopy())
            val p1time = System.currentTimeMillis() - p1start

            val p2start = System.currentTimeMillis()
            val p2action = Player.Player2 to agent2.getAction(forwardModel.state.deepCopy())
            val p2time = System.currentTimeMillis() - p2start


            agent1TotalTime += p1time
            agent2TotalTime += p2time
            totalMoves++

            val actions = mapOf(
                    p1action,
                    p2action
            )
            forwardModel.step(actions)
        }
        return forwardModel
    }

    fun newGame() {
        if (gameParams.newMapEachRun) {
            gameState = GameStateFactory(gameParams).createGame()
        }
        forwardModel = ForwardModel(gameState.deepCopy(), gameParams)
        agent1.prepareToPlayAs(Player.Player1, gameParams)
        agent2.prepareToPlayAs(Player.Player2, gameParams)

        agent1TotalTime = 0L
        agent2TotalTime = 0L
        totalMoves = 0
    }



    fun stepGame() : ForwardModel {
        if (forwardModel.isTerminal()) {
            return forwardModel
        }
        val actions = mapOf(
            Player.Player1 to agent1.getAction(forwardModel.state),
            Player.Player2 to agent2.getAction(forwardModel.state),
        )
        forwardModel.step(actions)
        return forwardModel
    }

    fun runGames(nGames: Int) : Map<Player, Int> {
        val scores = mutableMapOf(Player.Player1 to 0, Player.Player2 to 0, Player.Neutral to 0)
        for (i in 0 until nGames) {
            val finalModel = runGame()
            val winner = finalModel.getLeader()
            scores[winner] = scores[winner]!! + 1
        }
//        println(forwardModel.statusString())

        return scores
    }
    suspend fun runGamesConcurrently(nGames: Int,concurrencyLimit:Int=20): Map<Player, Int> = withContext(Dispatchers.Default) {
        val semaphore = Semaphore(concurrencyLimit)

        val scores = mutableMapOf(Player.Player1 to 0, Player.Player2 to 0, Player.Neutral to 0)

        val jobs = (1..nGames).map {
            async {
                // Each coroutine will suspend here until a permit is available.
                semaphore.withPermit {
                    val runner = GameRunner(agent1, agent2, gameParams)
                    runner.runGame().getLeader()
                }
            }
        }

        val results = jobs.awaitAll()

        for (winner in results) {
            scores[winner] = scores.getOrDefault(winner, 0) + 1
        }

        scores
}
}

fun main() {
    val gameParams = GameParams(numPlanets = 20)
//    val gameState = GameStateFactory(gameParams).createGame()
    val agent1 = PureRandomAgent()
    val agent2 = BetterRandomAgent()
    val gameRunner = GameRunner(agent1, agent2, gameParams)
    val finalModel = gameRunner.runGame()
    println("Game over!")
    println(finalModel.statusString())
    val nGames = 1000
    val t = System.currentTimeMillis()
    val results = gameRunner.runGames(nGames)
    val dt = System.currentTimeMillis() - t
    println(results)
    println("Time per game: ${dt.toDouble() / nGames} ms")
    val nSteps = ForwardModel.nUpdates
    println("Time per step: ${dt.toDouble() / nSteps} ms")

    println("Successful actions: ${ForwardModel.nActions}")
    println("Failed actions: ${ForwardModel.nFailedActions}")

}
