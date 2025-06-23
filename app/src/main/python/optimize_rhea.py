import optuna
import requests
import json

# The URL of your Kotlin evaluation server
EVALUATION_URL = "http://127.0.0.1:8080/evaluate"

def objective(trial: optuna.Trial):
    """
    This function is called by Optuna for each trial.
    It suggests parameters, sends them to the Kotlin server for evaluation,
    and returns the score.
    """
    # 1. Define the search space using Optuna's suggestion API
    params = {
        "sequenceLength": trial.suggest_int("sequenceLength", 100, 400),
        "populationSize": trial.suggest_int("populationSize", 60, 200),
        "elitePercentage": trial.suggest_float("elitePercentage", 0.05, 0.25),
        "mutationIndex": trial.suggest_float("mutationIndex", 0.0, 1.0),
        "parentSelectionTournamentSize": trial.suggest_float("parentSelectionTournamentSize", 0.1, 0.6),
        # For discrete choices, use suggest_categorical or suggest_int
        "crossoverIndex": trial.suggest_int("crossoverIndex", 0, 2),
        "initialization": trial.suggest_categorical("initialization", ["None", "ISLA"]),
        "fitnessFunction": trial.suggest_categorical("fitnessFunction", [0, 1, 2, 3, 4]),
    }

    print(f"\nTrial #{trial.number}: Suggesting params: {params}")

    try:
        # 2. Send the parameters to the Kotlin server
        response = requests.post(EVALUATION_URL, json=params) # 5-minute timeout
        response.raise_for_status()  # Raise an exception for bad status codes (4xx or 5xx)

        # 3. Get the score from the response
        result = response.json()
        score = result["score"]

        print(f"Trial #{trial.number}: Received score: {score}")
        return score

    except requests.exceptions.RequestException as e:
        print(f"Error communicating with evaluation server: {e}")
        # Tell Optuna to prune this trial if the evaluation fails
        raise optuna.exceptions.TrialPruned()


if __name__ == "__main__":
    # 1. Create a study. We want to MAXIMIZE the win rate (the score).
    study = optuna.create_study(direction="maximize")

    # 2. Start the optimization. Optuna will call the `objective` function.
    # n_trials is the total number of evaluations to run.
    study.optimize(objective, n_trials=2)

    # 3. Print the best results
    print("\n----- Optimization Finished -----")
    print(f"Number of finished trials: {len(study.trials)}")
    print("Best trial:")
    trial = study.best_trial

    print(f"  Value (Max Win Rate): {trial.value}")
    print("  Params: ")
    for key, value in trial.params.items():
        print(f"    {key}: {value}")