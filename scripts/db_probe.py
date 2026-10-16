import json, os, sys
sys.path.insert(0, r"E:\tools\pylibs")
import pymysql

cfg_path = os.path.join(os.path.dirname(__file__), "..", "config", "db.local.json")
cfg = json.load(open(cfg_path, encoding="utf-8"))
try:
    conn = pymysql.connect(host=cfg["host"], port=cfg.get("port", 3306),
                           user=cfg["user"], password=cfg["password"],
                           connect_timeout=10)
    cur = conn.cursor()
    cur.execute("SHOW DATABASES")
    dbs = [r[0] for r in cur.fetchall()]
    print("OK databases:", dbs)
    for db in dbs:
        if db in ("csa", "wpcsa"):
            cur.execute(f"USE `{db}`")
            cur.execute("SHOW TABLES")
            tables = [r[0] for r in cur.fetchall()]
            print(f"  {db}: {len(tables)} tables")
            print("   ", tables[:40])
    conn.close()
except Exception as e:
    print("FAIL", type(e).__name__, e)
    sys.exit(1)