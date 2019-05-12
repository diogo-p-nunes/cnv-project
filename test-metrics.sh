#!/bin/bash

# Ordem de teste:
# Comparacao entre algoritmos - BFS, DFS, A*
# Comparacao entre tamanhos dos mapas, para cada algoritmo:
#	- BFS: 512, 1024
#	- DFS: 512, 1024
#	- A* : 512, 1024
# Comparacao entre distancias ao objectivo, para cada algoritmo:
#	- BFS: diagonal, metade
#	- DFS: diagonal, metade
#	- A* : diagonal, metade

declare -a arr=("java pt.ulisboa.tecnico.cnv.solver.SolverMain -s BFS -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-03-01_10-28-39.dat -o $HOME/output  -yS 25 -xS 25"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s DFS -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-03-01_10-28-39.dat -o $HOME/output  -yS 25 -xS 25"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s ASTAR -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-03-01_10-28-39.dat -o $HOME/output  -yS 25 -xS 25"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s BFS -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-02-27_09-46-42.dat -o $HOME/output  -yS 0 -xS 0"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s BFS -w 1024 -h 1024 -i $HOME/cnv-project/datasets/RANDOM_HILL_1024x1024_2019-03-08_17-00-23.dat -o $HOME/output  -yS 0 -xS 0"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s DFS -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-02-27_09-46-42.dat -o $HOME/output  -yS 0 -xS 0"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s DFS -w 1024 -h 1024 -i $HOME/cnv-project/datasets/RANDOM_HILL_1024x1024_2019-03-08_17-00-23.dat -o $HOME/output  -yS 0 -xS 0"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s ASTAR -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-02-27_09-46-42.dat -o $HOME/output  -yS 0 -xS 0"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s ASTAR -w 1024 -h 1024 -i $HOME/cnv-project/datasets/RANDOM_HILL_1024x1024_2019-03-08_17-00-23.dat -o $HOME/output  -yS 0 -xS 0"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s BFS -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-02-27_09-46-42.dat -o $HOME/output  -yS 0 -xS 0"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s BFS -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-02-27_09-46-42.dat -o $HOME/output  -yS 256 -xS 256"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s DFS -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-02-27_09-46-42.dat -o $HOME/output  -yS 0 -xS 0"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s DFS -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-02-27_09-46-42.dat -o $HOME/output  -yS 256 -xS 256"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s ASTAR -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-02-27_09-46-42.dat -o $HOME/output  -yS 0 -xS 0"
				"java pt.ulisboa.tecnico.cnv.solver.SolverMain -s ASTAR -w 512 -h 512 -i $HOME/cnv-project/datasets/RANDOM_HILL_512x512_2019-02-27_09-46-42.dat -o $HOME/output  -yS 256 -xS 256")

declare -a info=("BFS, 512, 25, 25"
				 "DFS, 512, 25, 25"
				 "ASTAR, 512, 25, 25"
				 "BFS, 512"
				 "BFS, 1024"
				 "DFS, 512"
				 "DFS, 1024"
				 "ASTAR, 512"
				 "ASTAR, 1024"
				 "BFS, diagonal"
				 "BFS, centro"
				 "DFS, diagonal"
				 "DFS, centro"
				 "ASTAR, diagonal"
				 "ASTAR, centro")
printf "\n"
for (( i = 0; i < ${#arr[@]} ; i++ )); do
    printf "Running [$((i+1))/${#arr[@]}] ... \n"
    printf "(${info[$i]})\n"
    # Run each command in array 
    eval "time" "${arr[$i]}" + " >nul 2>&1"
    printf "Done\n\n"
done