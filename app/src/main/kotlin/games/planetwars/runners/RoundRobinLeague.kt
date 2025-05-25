package games.planetwars.runners

import games.planetwars.agents.DoNothingAgent
import games.planetwars.agents.PlanetWarsAgent
import games.planetwars.agents.evo.SimpleEvoAgent
import games.planetwars.agents.random.BetterRandomAgent
import games.planetwars.agents.random.CarefulRandomAgent
import games.planetwars.agents.random.PureRandomAgent
import games.planetwars.agents.rhea.Crossover
import games.planetwars.agents.rhea.ParentSelectionStrategy
import games.planetwars.agents.rhea.RheaAgent
import games.planetwars.agents.rhea.RheaAgentMultipleParents
import games.planetwars.agents.rhea.VanillaRheaAgent
import games.planetwars.core.GameParams
import games.planetwars.core.Player

fun main() {
//    val agents = SamplePlayerLists().getRandomTrio()
    var agents = mutableListOf<PlanetWarsAgent>(
            RheaAgent(
                    parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                    sequenceLength = 200
                    , mutationProbability = 0.8,
                    crossover = Crossover.Uniform,
                    populationSize = 15),
            RheaAgent(
                    parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                    sequenceLength = 200
                    , mutationProbability = 0.8,
                    populationSize = 15),
            RheaAgent(
                    parentSelectionStrategy = ParentSelectionStrategy.Roulette,
                    sequenceLength = 200
                    , mutationProbability = 0.8,
                    crossover = Crossover.N_Point(0.7),
                    populationSize = 15),
            RheaAgentMultipleParents(
                    parentCount = 5,
                    sequenceLength = 200, evaluationOpponentAgent = CarefulRandomAgent(),
                    mutationProbability = 0.8,
                    populationSize = 15),)


//    agents.add(DoNothingAgent())
    val league = RoundRobinLeague(agents, gamesPerPair = 10)
    val results = league.runRoundRobin()
    // use the League utils to print the results
    println(results)
    val writer = LeagueWriter()
    val leagueResult = LeagueResult(results.values.toList())
    val markdownContent = writer.generateMarkdownTable(leagueResult)
    writer.saveMarkdownToFile(markdownContent)

    // print sorted results directly to console
    val sortedResults = results.toList().sortedByDescending { it.second.points }.toMap()
    for (entry in sortedResults.values) {
        println("${entry.agentName} : ${entry.points} : ${entry.nGames}")
    }

}

class SamplePlayerLists {
    fun getRandomTrio(): MutableList<PlanetWarsAgent> {
        return mutableListOf(
            PureRandomAgent(),
            BetterRandomAgent(),
            CarefulRandomAgent(),
        )
    }

    fun getFullList(): MutableList<PlanetWarsAgent> {
        return mutableListOf(
//            PureRandomAgent(),
            BetterRandomAgent(),
            CarefulRandomAgent(),
            SimpleEvoAgent(
                useShiftBuffer = true,
                nEvals = 30,
                sequenceLength = 400,
                opponentModel = DoNothingAgent(),
                probMutation = 0.8,
            ),
                VanillaRheaAgent(
                        sequenceLength = 400
                        , mutationProbability = 0.8,
                        populationSize = 30)
        )
    }
}

data class RoundRobinLeague(
    val agents: List<PlanetWarsAgent>,
    val gamesPerPair: Int = 10,
    val gameParams: GameParams = GameParams(numPlanets = 20),
) {
    fun runPair(agent1: PlanetWarsAgent, agent2: PlanetWarsAgent): Map<Player, Int> {
        val gameRunner = GameRunner(agent1, agent2, gameParams)
        return gameRunner.runGames(gamesPerPair)
    }

    fun runRoundRobin(): Map<String, LeagueEntry> {
        val t = System.currentTimeMillis()
        val scores = mutableMapOf<String, LeagueEntry>()
        for (agent in agents) {
            // make a new league entry for each agent in a map indexed by agent type
            scores[agent.getAgentType()] = LeagueEntry(agent.getAgentType())
        }
        // play each agent against every other agent as Player1 and Player2
        // but not against themselves
        for (i in 0 until agents.size) {
            for (j in 0 until agents.size) {
                if (i == j) {
                    continue
                }
                val agent1 = agents[i]
                val agent2 = agents[j]
                val result = runPair(agent1, agent2)
                // update the league scores for each agent
                val leagueEntry1 = scores[agent1.getAgentType()]!!
                val leagueEntry2 = scores[agent2.getAgentType()]!!
                leagueEntry1.points += result[Player.Player1]!!
                leagueEntry2.points += result[Player.Player2]!!
                leagueEntry1.nGames += gamesPerPair
                leagueEntry2.nGames += gamesPerPair
            }
        }
        println("Round Robin took ${(System.currentTimeMillis() - t) / 1000} seconds")
        return scores
    }
}