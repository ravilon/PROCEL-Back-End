import os
from dotenv import load_dotenv

from Service.cobaltoSalas import CobaltoSalasService


def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    load_dotenv(os.path.join(base_dir, ".env"))

    service = CobaltoSalasService()
    result = service.run()

    print("Carga concluída:")
    for k, v in result.items():
        print(f"  - {k}: {v}")


if __name__ == "__main__":
    main()
