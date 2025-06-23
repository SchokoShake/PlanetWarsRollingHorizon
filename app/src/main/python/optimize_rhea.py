import optuna
import requests
import json

# The URL of your Kotlin evaluation server
EVALUATION_URL = "http://127.0.0.1:8080/evaluate"

def objective(trial: optuna.Trial):
    """
    This function is called by Optuna for each trial.
    It suggests parameters, including CONDITIONAL parameters for fitness
    functions and initialization methods. It then sends them to the
    Kotlin server for evaluation and returns the score.
    """
    # 1. Define the search space using Optuna's suggestion API
    params = {
        "sequenceLength": trial.suggest_int("sequenceLength", 100, 400),
        "populationSize": trial.suggest_int("populationSize", 60, 200),
        "elitePercentage": trial.suggest_float("elitePercentage", 0.05, 0.25),
        "mutationIndex": trial.suggest_float("mutationIndex", 0.0, 1.0),
        "parentSelectionTournamentSize": trial.suggest_float("parentSelectionTournamentSize", 0.1, 0.6),
        "crossoverIndex": trial.suggest_int("crossoverIndex", 0, 2),
    }

    # 2. Suggest Initialization Method (Conditional)
    init_method = trial.suggest_categorical("initialization_method", ["None", "ISLA"])
    params["initialization_method"] = init_method
    if init_method == "ISLA":
        params["isla_percent"] = trial.suggest_float("isla_percent", 0.1, 1.0)

    # 3. Suggest Fitness Function (Conditional)
    fitness_function = trial.suggest_categorical(
        "fitness_function", ["Ratio", "Ships", "Growth", "Aggressive", "Balanced", "Hybrid", "Strategic"]
    )
    params["fitness_function"] = fitness_function

    if fitness_function == "Aggressive":
        params["aggressive_p"] = trial.suggest_float("aggressive_p", 10.0, 20.0)
        params["aggressive_s"] = trial.suggest_float("aggressive_s", 0.5, 2.0)
        params["aggressive_e"] = 15000.0
        params["aggressive_t"] = trial.suggest_float("aggressive_t", 0.5, 2.0)
    elif fitness_function == "Balanced":
        params["balanced_g"] = trial.suggest_float("balanced_g", 100.0, 500.0)
        params["balanced_s"] = trial.suggest_float("balanced_s", 0.5, 2.0)
    elif fitness_function == "Hybrid":
        # To ensure the sum is 1, we only need to suggest one parameter
        a = trial.suggest_float("hybrid_a", 0.0, 1.0)
        params["hybrid_a"] = a
        params["hybrid_b"] = 1.0 - a
    elif fitness_function == "Strategic":
        params["strategic_p"] = trial.suggest_float("strategic_p", 10.0, 20.0)
        params["strategic_g"] = trial.suggest_float("strategic_g", 100.0, 500.0)
        params["strategic_s"] = trial.suggest_float("strategic_s", 0.5, 2.0)
        params["strategic_e"] = 15000.0
        params["strategic_t"] = trial.suggest_float("strategic_t",  0.5, 2.0)


    print(f"\nTrial #{trial.number}: Suggesting params: {json.dumps(params, indent=2)}")

    try:
        # 4. Send the parameters to the Kotlin server
        # Increased timeout for potentially longer evaluations
        response = requests.post(EVALUATION_URL, json=params, timeout=300)
        response.raise_for_status()  # Raise an exception for bad status codes (4xx or 5xx)

        # 5. Get the score from the response
        result = response.json()
        score = result["score"]

        print(f"Trial #{trial.number}: Received score: {score:.4f}")
        return score

    except requests.exceptions.RequestException as e:
        print(f"Error communicating with evaluation server: {e}")
        # Tell Optuna to prune this trial if the evaluation fails
        raise optuna.exceptions.TrialPruned()


if __name__ == "__main__":
    # 1. Create a study. We want to MAXIMIZE the win rate (the score).
    study = optuna.create_study(
        storage="sqlite:///db.sqlite3",
        direction="maximize")

    # 2. Start the optimization. Optuna will call the `objective` function.
    # n_trials is the total number of evaluations to run.
    study.optimize(objective, n_trials=1) # Increased trials for a more thorough search

    # 3. Print the best results
    print("\n----- Optimization Finished -----")
    print(f"Number of finished trials: {len(study.trials)}")
    print("Best trial:")
    trial = study.best_trial

    print(f"  Value (Max Win Rate): {trial.value}")
    print("  Params: ")
    for key, value in trial.params.items():
        print(f"    {key}: {value}")