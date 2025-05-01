package games.planetwars.agents


import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.core.*
import games.planetwars.runners.GameRunnerCoRoutines
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import json_rmi.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

fun main() {
    val agent1 = RemoteAgent("games.planetwars.agents.random.CarefulRandomAgent")
    val agent2 = PureRandomAgent()
    val gameParams = GameParams(
        numPlanets = 4,
        initialNeutralRatio = 0.0,
        maxTicks = 20,
    )
    val runner = GameRunnerCoRoutines(agent1, agent2, gameParams)
    val forwardModel = runner.runGame()
    println("Game over! Final state: $forwardModel")

}

class RemoteAgent (
    private val className: String,
    private val serverUrl: String = "ws://localhost:8080/ws"
) : PlanetWarsPlayer() {

    private lateinit var session: DefaultClientWebSocketSession
    private lateinit var client: HttpClient
    private lateinit var objectId: String

    private fun ensureConnected() {
        if (!::client.isInitialized) {
            client = HttpClient(CIO) { install(WebSockets) }
        }
    }

    override fun prepareToPlayAs(player: Player, params: GameParams, opponent: String?): String {
        runBlocking {
            ensureConnected()
            client.webSocket(serverUrl) {
                session = this
                objectId = initAgent(className)
                invokeRemoteMethod(objectId, "prepareToPlayAs", player, params, opponent?: "Anonymous")
            }
        }
        return getAgentType()
    }

    override fun getAction(gameState: GameState): Action {
        return runBlocking {
            ensureConnected()
            var action: Action = Action.doNothing()
            client.webSocket(serverUrl) {
                session = this
                val response = invokeRemoteMethod(objectId, "getAction", gameState)
                val jsonResp = json.parseToJsonElement(response).jsonObject
                val result = jsonResp["result"]
                if (result != null && result is JsonObject) {
                    action = json.decodeFromJsonElement(Action.serializer(), result)
                    println("Decoded Action: $action")
                }
            }
            action
        }
    }

    override fun getAgentType(): String = "Remote[$className]"

    override fun processGameOver(finalState: GameState) {
        runBlocking {
            ensureConnected()
            client.webSocket(serverUrl) {
                session = this
                invokeRemoteMethod(objectId, "processGameOver", finalState)
                endAgent(objectId)
            }
            client.close()
        }
    }
}
