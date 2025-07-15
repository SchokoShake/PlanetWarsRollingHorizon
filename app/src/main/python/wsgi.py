from optuna.storages import RDBStorage
import optuna
from optuna_dashboard import run_server

storage = RDBStorage("sqlite:///db.sqlite3")
run_server(storage)