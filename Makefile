.PHONY: all
## default: Compile le bot et ses dépendances avec gradlew dans le dossier ./build/libs/ .
all:
	./gradlew build

.PHONY: clean
## clean: Supprimer les fichiers générés lors de la compilation. (Dossier ./build).
clean:
	./gradlew clean

.PHONY: run
## run: Compile le bot puis l'exécute sur la JVM local. (Tester avec openjdk 21).
run: all
	java -jar ./build/libs/*.jar

.PHONY: help
## help: Affiche la liste des cibles suivie d'une courte description.
help: $(MAKEFILE_LIST)
	@echo "\nUsage:\n  make [cible]\n\nListe des cibles :"
	@sed -n 's/^##//p' $< | column -t -s ':' |  sed -e 's/^/ /'
	@echo
