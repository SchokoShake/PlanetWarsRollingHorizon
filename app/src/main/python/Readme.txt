



First run the OptunaOptimization kotlin script to start the server. then run the pythoin script for training:
python optimize_rhea.py

After killing the optimization Server run the following to mlook at stats:
optuna-dashboard sqlite:///db.sqlite3