# LLM-Assisted Repository-Level Generation with Structured Spec-Driven Engineering

> Source: https://arxiv.org/abs/2605.02455
> Collected: 2026-10-01
> Published: 2026-05-04

Authors: Feng, Shuzhao; Chen, Boqi; Meyer, Brett H; Mussbacher, Gunter

## Abstract

State-of-the-art Large Language Models (LLMs) excel in code generation at the function level. However, the output quality significantly declines when scaling to repository-level systems. Current workflows relying only on natural language prompts suffer from inherent ambiguity and a lack of verifiability. To address this, we propose structured spec-driven engineering (SSDE), a paradigm that leverages structured artifacts to guide LLM generation. We argue that structured specifications as LLM inputs make high-quality, repository-level code generation a tangible goal, while at the same time offering superior verifiability, leading to significant potential for improvement. We first investigate the feasibility of this vision through a pilot study generating Model-View-Controller (MVC) business logic for three software systems using five LLMs, and then highlight the potential, challenges, and future roadmap for SSDE.

## Excerpts (verbatim, from the HTML full text)

> Furthermore, while output generated using Gherkin specifications + any model has an overall lower accuracy than the ones generated using natural language specifications + any model (with 6.8% lower TPR and 0.9% higher standard deviation averaged across all combinations), the average TPR of Gherkin specifications outputs beats natural language outputs in 14 out of the 30 combinations we tested (5 LLMs × 2 modeling tools × 3 samples), with their average improvement being +7.7%. This result suggests that there is potential to further improve this approach to surpass natural language in output quality.

> 16.8% ± 1.7%

> LLMs also show good out-of-the-box ability to infer system specifications through domain models, though providing the model layer’s signatures generated from the domain model instead improves average TPR (+7.82%) and lowers standard deviation (-2.47%).

> Table 2 shows a closer view of TPR for individual samples, taking Claude as an example, since it is the overall best-performing model, with its highest configuration average TPR over 80% for 4 out of 6 samples and perfect TPR for several individual runs. From the results, the combination of domain model + Gherkin specifications shows highest average TPR in 3 out of the 6 samples we tested with Claude. Across all LLMs, signature model + natural language specifications shows highest average TPR in 11 out of the 30 LLM-modeling-tool-sample combinations, followed by domain model + Gherkin specifications with 9 out of the 30 combinations.
