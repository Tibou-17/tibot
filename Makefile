CONF_FILE ?= bot_conf.toml

.PHONY: all
## default: Compile le bot et ses dépendances avec gradlew dans le dossier ./build/libs/ .
all:
	./gradlew build

.PHONY: clean
## clean: Supprimer les fichiers générés lors de la compilation. (Dossier ./build).
clean:
	./gradlew clean

.PHONY: run
## run: Compile le bot puis l'exécute sur la JVM local. (Tester avec openjdk "25.0.3" 2026-04-21).
run: all
	java -jar ./build/libs/*.jar $(CONF_FILE)

## deploy: Compile le bot puis exécute le script deploy (Script non fourni dans le dépôt, vous devez le créer vous-même).
deploy: all
	@if [ -x "./deploy" ]; then \
		./deploy; \
	else \
		echo "\nLe script deploy, n'existe pas ou n'est pas exécutable."; \
		exit 1; \
	fi

.PHONY: help
## help: Affiche la liste des cibles suivie d'une courte description.
help: $(MAKEFILE_LIST)
	@echo "\nUsage:\n  make [cible]\n\nListe des cibles :"
	@sed -n 's/^##//p' $< | column -t -s ':' |  sed -e 's/^/ /'
	@echo
