# Lasker Morris Player "Gabor"

Gabor was a project for CS4341, Introduction to Artificial Intelligence, at WPI. I worked on this project with two partners in February-March 2025.

This repository contains two versions of "Gabor," an AI agent to play Lasker Morris. 
- GaborMinimax utilizes the minimax algorithm with iterative deepening and alpha-beta pruning.
- GaborLLM utilizes the Google Gemini LLM API.

Both agents utilize the move format interpreted by Jake Molnia's Lasker Morris referee, available here: https://jake-molnia.github.io/CS4341-referee/

To run either agent, run the `run.txt` file within its subdirectory.

The GaborMinimax subdirectory also contains a README regarding its evaluation, heuristics, and strengths.
The GaborLLM subdirectory also contains a report on our prompt design and the comparison of GaborLLM and GaborMinimax as agents (including how they play against one another.)

GaborMinimax came in second place out of sixteen teams in the final class-wide Lasker Morris player competition.
