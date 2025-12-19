import os
import requests
import psycopg2


class CobaltoSalasService:
    def __init__(self):
        # ==========
        # ENV (obrigatórias)
        # ==========
        self.url = os.environ["COBALTO_URL"]
        self.timeout = int(os.environ["COBALTO_TIMEOUT"])

        self.pg_host = os.environ["PG_HOST"]
        self.pg_port = int(os.environ["PG_PORT"])
        self.pg_db = os.environ["PG_DATABASE"]
        self.pg_user = os.environ["PG_USER"]
        self.pg_password = os.environ["PG_PASSWORD"]

        # ==========
        # API params
        # ==========
        self.params = {
            "rows": -1,
            "_search": "false",
            "sidx": "compartimento_id, compartimento_nome",
            "sord": "asc",
        }

        # ==========
        # Cookies (opcional, mas via env)
        # ==========
        self.cookies = {}
        if "PHPSESSID" in os.environ and os.environ["PHPSESSID"].strip():
            self.cookies["PHPSESSID"] = os.environ["PHPSESSID"].strip()

        self.headers = {
            "Accept": "application/json",
            "User-Agent": "dataharvester",
        }

        # ==========
        # Caches
        # ==========
        self._campus_cache = {}
        self._unidade_cache = {}
        self._predio_cache = {}
        self._comp_seen = set()

    # -----------------------------
    # API
    # -----------------------------
    def fetch(self) -> list[dict]:
        resp = requests.get(
            self.url,
            params=self.params,
            headers=self.headers,
            cookies=self.cookies,
            timeout=self.timeout,
        )
        resp.raise_for_status()

        data = resp.json()
        rows = data.get("rows")

        if not isinstance(rows, list):
            raise RuntimeError("Resposta inválida da API: 'rows' não é lista")

        return rows

    # -----------------------------
    # DB
    # -----------------------------
    def _conn(self):
        return psycopg2.connect(
            host=self.pg_host,
            port=self.pg_port,
            dbname=self.pg_db,
            user=self.pg_user,
            password=self.pg_password,
        )

    # -----------------------------
    # Helpers
    # -----------------------------
    @staticmethod
    def _norm(value: str) -> str:
        return " ".join((value or "").strip().split())

    @staticmethod
    def _to_int(v):
        if v in (None, "", "null"):
            return None
        return int(float(v))

    @staticmethod
    def _to_float(v):
        if v in (None, "", "null"):
            return None
        return float(str(v).replace(",", "."))

    # -----------------------------
    # Get or create
    # -----------------------------
    def _get_or_create(self, cur, table, where_sql, where_vals, insert_sql, cache, cache_key):
        if cache_key in cache:
            return cache[cache_key]

        cur.execute(f"select id from {table} where {where_sql}", where_vals)
        row = cur.fetchone()
        if row:
            cache[cache_key] = row[0]
            return row[0]

        cur.execute(insert_sql, where_vals)
        new_id = cur.fetchone()[0]
        cache[cache_key] = new_id
        return new_id

    # -----------------------------
    # Run
    # -----------------------------
    def run(self):
        records = self.fetch()

        processed = 0

        with self._conn() as conn:
            with conn.cursor() as cur:

                # preload caches
                cur.execute("select id, nome from campus")
                for i, n in cur.fetchall():
                    self._campus_cache[n] = i

                cur.execute("select id, nome from unidade")
                for i, n in cur.fetchall():
                    self._unidade_cache[n] = i

                cur.execute("select id, campusid, nome from predio")
                for i, c, n in cur.fetchall():
                    self._predio_cache[(c, n)] = i

                for r in records:
                    campus_nome = self._norm(r.get("campus_nome"))
                    predio_nome = self._norm(r.get("predio_nome"))
                    unidade_nome = self._norm(r.get("unidade_nome"))
                    comp_nome = self._norm(r.get("compartimento_nome"))

                    if not all([campus_nome, predio_nome, unidade_nome, comp_nome]):
                        continue

                    campusid = self._get_or_create(
                        cur,
                        "campus",
                        "nome = %s",
                        (campus_nome,),
                        "insert into campus(nome) values (%s) returning id",
                        self._campus_cache,
                        campus_nome,
                    )

                    unidadeid = self._get_or_create(
                        cur,
                        "unidade",
                        "nome = %s",
                        (unidade_nome,),
                        "insert into unidade(nome) values (%s) returning id",
                        self._unidade_cache,
                        unidade_nome,
                    )

                    predio_key = (campusid, predio_nome)
                    predioid = self._get_or_create(
                        cur,
                        "predio",
                        "campusid = %s and nome = %s",
                        (campusid, predio_nome),
                        "insert into predio(campusid, nome) values (%s, %s) returning id",
                        self._predio_cache,
                        predio_key,
                    )

                    seen_key = (predioid, comp_nome.lower())
                    if seen_key in self._comp_seen:
                        continue
                    self._comp_seen.add(seen_key)

                    cur.execute(
                        """
                        insert into compartimento
                          (predioid, unidadeid, nome, tipo, pavimento, capacidade, area)
                        values (%s, %s, %s, %s, %s, %s, %s)
                        on conflict (predioid, nome) do update set
                          unidadeid  = excluded.unidadeid,
                          tipo       = excluded.tipo,
                          pavimento  = excluded.pavimento,
                          capacidade = excluded.capacidade,
                          area       = excluded.area
                        """,
                        (
                            predioid,
                            unidadeid,
                            comp_nome,
                            self._norm(r.get("utilizacao_compartimento_descricao")) or "nao_informado",
                            self._to_int(r.get("compartimento_pavimento")),
                            self._to_int(r.get("compartimento_capacidade")),
                            self._to_float(r.get("compartimento_area")),
                        ),
                    )

                    processed += 1

            conn.commit()

        return {
            "rowsFetched": len(records),
            "compartimentosProcessed": processed,
            "campusCached": len(self._campus_cache),
            "unidadeCached": len(self._unidade_cache),
            "predioCached": len(self._predio_cache),
        }
