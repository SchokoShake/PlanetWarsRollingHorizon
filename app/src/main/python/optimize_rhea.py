import optuna
import requests
import json

# The URL of your Kotlin evaluation server
EVALUATION_URL = "http://127.0.0.1:8080/evaluate"

def objective(trial: optuna.Trial):
    """
    Suggests a comprehensive set of hyperparameters for the RheaAgent
    using clear, hierarchical names for Optuna's tracking.
    """
    params = {}

    # 1. Core Agent Parameters
    params["useVariableShipCount"] = True
    params["sequenceLength"] = trial.suggest_int("agent.sequence_length", 50, 200)
    population_size = trial.suggest_int("agent.population_size", 20, 150)
    params["populationSize"] = population_size
    elite_percentage = trial.suggest_float("agent.elite_percentage", 0.05, 0.25)
    params["numberElites"] = int(population_size * elite_percentage)

    # 2. Parent Selection Strategy
    parent_strategy = trial.suggest_categorical("selection.strategy", ["Random", "Roulette", "Tournament", "Rank"])
    params["parentSelectionStrategy"] = parent_strategy
    if parent_strategy == "Tournament":
        params["tournament_t"] = trial.suggest_float("selection.tournament_size", 0.05, 0.5)

    # 3. Crossover Method
    crossover_method = trial.suggest_categorical("crossover.method", ["None", "Uniform", "N_Point"])
    params["crossover_method"] = crossover_method
    if crossover_method == "N_Point":
        params["crossover_n_point_n"] = trial.suggest_int("crossover.n_points", 1, 10)

    # 4. Mutation Method
    mutation_method = trial.suggest_categorical("mutation.method", ["Uniform", "Softmax", "n_bit"])
    params["mutation_method"] = mutation_method
    if mutation_method == "Uniform":
        params["mutation_uniform_probability"] = trial.suggest_float("mutation.uniform_prob", 0.01, 0.8)
    elif mutation_method == "n_bit":
        params["mutation_n_bit_n"] = trial.suggest_int("mutation.n_bits", 1, 15)

    # 5. Initialization Method
    init_method = trial.suggest_categorical("init.method", ["None", "ISLA"])
    params["initialization_method"] = init_method
    if init_method == "ISLA":
        params["isla_percent"] = trial.suggest_float("init.isla_percent", 0.1, 1.0)

    # 6. Fitness Function
    fitness_function = trial.suggest_categorical("fitness.function", ["Ratio", "Ships", "Growth", "Aggressive", "Balanced", "Hybrid", "Strategic"])
    params["fitness_function"] = fitness_function

    if fitness_function == "Aggressive":
        params["aggressive_p"] = trial.suggest_float("fitness.aggressive_planet_weight", 5.0, 30.0)
        params["aggressive_s"] = trial.suggest_float("fitness.aggressive_ship_weight", 0.1, 3.0)
        params["aggressive_t"] = trial.suggest_float("fitness.aggressive_time_weight", 0.1, 3.0)
    elif fitness_function == "Balanced":
        params["balanced_g"] = trial.suggest_float("fitness.balanced_growth_weight", 100.0, 500.0)
        params["balanced_s"] = trial.suggest_float("fitness.balanced_ship_weight", 0.1, 3.0)
    elif fitness_function == "Hybrid":
        a = trial.suggest_float("fitness.hybrid_aggressive_weight", 0.0, 1.0)
        params["hybrid_a"] = a
        params["hybrid_b"] = 1.0 - a
    elif fitness_function == "Strategic":
        params["strategic_p"] = trial.suggest_float("fitness.strategic_planet_weight", 5.0, 30.0)
        params["strategic_g"] = trial.suggest_float("fitness.strategic_growth_weight", 100.0, 500.0)
        params["strategic_s"] = trial.suggest_float("fitness.strategic_ship_weight", 0.1, 3.0)
        params["strategic_t"] = trial.suggest_float("fitness.strategic_time_weight", 0.1, 3.0)

    # --- Communication with Kotlin Server ---
    print(f"\nTrial #{trial.number}: Suggesting params: {json.dumps(params, indent=2)}")
    try:
        response = requests.post(EVALUATION_URL, json=params)
        response.raise_for_status()
        result = response.json()
        score = result["score"]
        print(f"Trial #{trial.number}: Received score: {score:.4f}")
        return score
    except requests.exceptions.RequestException as e:
        print(f"Error communicating with evaluation server: {e}")
        raise optuna.exceptions.TrialPruned()

if __name__ == "__main__":
    study = optuna.create_study(
        storage="sqlite:///db.sqlite3",
        study_name="1000TicksAllParameters",
        load_if_exists=True,
        direction="maximize"
    )
    study.optimize(objective, n_trials=1000)
    

    print("\n----- Optimization Finished -----")
    print(f"Number of finished trials: {len(study.trials)}")
    best_trial = study.best_trial
    print("\nBest trial:")
    print(f"  Value (Max Score): {best_trial.value:.4f}")
    print("  Params: ")
    for key, value in best_trial.params.items():
        print(f"    {key}: {value}")